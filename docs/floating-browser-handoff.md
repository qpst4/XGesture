# 浮层浏览器 —— 交接文档

> 面向：接手的 AI / 开发者。
> 目标：在 XGesture（`com.slideindex.app`）里做一个 FV 风格的浮层浏览器（卡片式、多标签、自绘 chrome）。
> 本文件包含：现状、代码结构、必须知道的坑、剩余工作、FV 逆向资料路径。

---

## 1. 工程与运行环境

| 项 | 值 |
| :- | :- |
| 主仓库 worktree（正在开发） | `D:\AndroidDev\Projects\cebian-browser` |
| 分支 | `feat/floating-browser` |
| 主 worktree（勿动，含其它未提交内容） | `D:\AndroidDev\Projects\cebian`（分支 `main`） |
| 构建 | `.\gradlew.bat --no-daemon :app:assembleFullDebug --offline --no-build-cache --console=plain` |
| 产物 | `app/build/outputs/apk/full/debug/app-full-debug.apk` |
| 安装 | `adb install -r <apk>`（设备 MEIZU 21，Android 13/API 31+，adb serial 见 `adb devices`） |
| 行尾规范 | 每次改完执行 `python scripts/normalize-eol.py --check <文件>`，必要时 `--write` |

> 注意：`keystore.properties`、`local.properties`、`app/keystore/release.jks` 是 gitignore 的，新建 worktree 后必须从主 worktree 复制，否则安装会签名冲突。

### 调试入口（目前唯一入口）

```bash
adb shell am broadcast -a com.slideindex.app.BROWSER --es url https://www.bing.com
```

接收方：`SlideIndexAccessibilityService.registerBrowserReceiver()`（`onServiceConnected` 注册，`onDestroy` 反注册，`RECEIVER_EXPORTED`）。
**安装后需要等服务重连**（约 5~10 秒）广播才会命中，否则 `Broadcast completed: result=0`（无接收者）。

### 常用调试命令

```bash
# 窗口是否存在、尺寸/位置
adb shell dumpsys window windows | grep -A6 floating-browser

# 我们的日志
adb logcat -d -s XGBrowser

# 清空
adb logcat -c

# 清掉窗口尺寸/全屏记忆（尺寸异常时用）
adb shell run-as com.slideindex.app rm -f shared_prefs/xg_browser.xml
```

---

## 2. 代码结构（`app/src/main/java/com/slideindex/app/overlay/browser/`）

| 文件 | 职责 |
| :- | :- |
| `FloatingBrowserPanel.kt` | 单例。窗口创建/销毁、卡片与全屏、拖动、缩放、尺寸与全屏记忆、多标签管理、Compose chrome（顶栏/底栏/地址栏/标签面板）、原生拖动条、全屏视频容器 |
| `BrowserTab.kt` | 一个标签 = 一个 `WebView` + `WebViewClient` + `WebChromeClient` + JS 桥（`xgBrowser`）+ 表单/日期接管注入 + 下载 + 广告拦截 + UA |
| `BrowserDialogs.kt` | 全部自绘对话框（alert/confirm/prompt/列表选择/日期时间/info）。**不要用 `AlertDialog`**，见 §3 |
| `SlideIndexAccessibilityService.kt`（已修改） | 调试广播入口 |

### 关键设计点

- **窗口类型**：`OverlayWindowTypes.contentPanelWindowType(context)` = `TYPE_APPLICATION_OVERLAY`；
  flags：`LAYOUT_IN_SCREEN | NOT_TOUCH_MODAL | HARDWARE_ACCELERATED`，`softInputMode=ADJUST_RESIZE`。
- **层级**：`root(FrameLayout)`
  - `host(FrameLayout)` — WebView 容器，`paddingTop=46dp`（给顶栏让位），底部不留白（底栏悬浮）
  - `ComposeView` — chrome 覆盖层（**必须在 root 上挂 ViewTreeLifecycleOwner/ViewModelStoreOwner/SavedStateRegistryOwner**，否则崩溃）
  - 原生拖动条 `View`（顶栏左 80%，高 44dp）— 因为 Compose 手势在浮层里会被抢，必须用 View 级触摸
- **标签**：同一时刻只把激活标签的 WebView `addView` 进 host；非激活 `webView.onPause()` + 移出视图树。
- **多开内存模型**（FV 逆向结论 + 实测）：成本按「不同站点」计，Chromium 同站共享渲染进程；
  10 个真实站点 ≈ 6 个渲染进程 ≈ 480MB；21 个同站页面 ≈ 150MB。
- **持久化**：`SharedPreferences("xg_browser")` 存 `w/h/x/y/max`；**读回时必须把 x/y 夹回屏幕内**（曾经存过屏外坐标导致窗口只露一条缝、拖不动）。

---

## 3. 必须知道的坑（每条都踩过）

1. **服务上下文没有 Activity 令牌**
   `AlertDialog.Builder(context).show()` → `WindowManager$BadTokenException: token is not for an application`（直接崩）。
   **所有对话框必须自绘**：`Dialog(context, Theme_Material_Light_Dialog_NoActionBar)` + `window?.setType(overlayType)`。
2. **浮层里系统弹窗全部失效**（实测）：`<select>`、`<input type=date/time>`、`alert/confirm/prompt` 都没反应。
   解决：JS 注入拦截 + `@JavascriptInterface` 回原生 + 自绘弹窗 + `evaluateJavascript` 回写（见 `BrowserTab.injectFormTakeover`）。
   日期/时间控件还要设 `readOnly = true`，否则原生控件会抢走事件。
3. **文件选择器会被浮层压住**
   `WebViewFileChooserBridge.launch(...)` 能启动，但系统选择器在浮层下面。
   解决：`onShowFileChooser` 里先 `rootView.visibility = GONE`，包一层 `ValueCallback`，回调里恢复。
4. **`<video>` 显示成一个大播放键**
   这是 Chromium「需要用户手势才能播放」的遮罩。`settings.mediaPlaybackRequiresUserGesture = false` 解决。
   （Bing 相机页、WebRTC 摄像头页都是这个原因。）
5. **WebView 相机被站点拒绝**
   `settings.userAgentString` 去掉 `; wv` 和 ` Version/4.0`。权限链路：`onPermissionRequest` →
   检查 App 权限 → `request.grant()`；没权限时弹自绘提示，引导到
   `CameraPermissionTrampolineActivity.ensureGranted(context)` / `StashVoicePermissionActivity.ensureGranted(context)`。
6. **Compose 浮层崩溃**：`ViewTreeLifecycleOwner not found from android.widget.FrameLayout`
   → owner 必须设在**窗口根容器**上（不只是 ComposeView）。
7. **Compose 手势在浮层里不可靠**
   `detectDragGestures` + `detectTapGestures` 挂在同一节点会互相抢事件（实测每帧只收到 0.2px 位移）。
   → 顶栏拖动改用原生 `View.setOnTouchListener`。
8. **`✕` 误触**：拖动结束时手指落在关闭按钮上会直接关窗（曾是“贴边后一点就消失”的元凶）。
   现在关闭按钮只在顶栏右侧 56dp 区域。
9. **不要把 `WindowManager.updateViewLayout` 放在高频路径里**
   早期在 `syncCaptureWindowLayout` 里无条件调 `ensurePresentationAttached`，导致空闲主线程 35% 空转（零绘制）。

---

## 3.5 文本选区（重要，容易走弯路）

结论：**不要自己画手柄**。正确做法是给 WebView 一个"占位 ActionMode"：

`kotlin
override fun startActionMode(callback: android.view.ActionMode.Callback?, type: Int): ActionMode? {
    if (type == android.view.ActionMode.TYPE_FLOATING) return DummyActionMode(context)
    return super.startActionMode(callback, type)
}
`

- 返回 **null** → Chromium 认为宿主不支持选择，**会把选区清掉**（表现为长按后一闪就没）；
- 返回一个**空实现**的 ActionMode → 选区与**系统原生手柄**都会保留；
- 那个真正弹不出来的只是**系统浮动工具栏**（PopupWindow 需要 Activity 令牌），所以工具条仍需自绘；
- 选区矩形通过 JS getClientRects() 上报，Android 侧换算：比例 = WebView 物理宽度 / visualViewport.width
  （不要用 devicePixelRatio，无 viewport meta 的页面会算错）；
- 页面滚动要重新上报（scroll 监听 + 80ms 节流），否则工具条不跟随。

## 3.6 已实现的功能清单（2026-11-01）

- 窗口：卡片 92%×78%（可拖、可缩放）、全屏切换、尺寸/全屏记忆、IME 避让（键盘弹出时卡片上移）
- 标签：多标签、后台 onPause、**上限 8 + LRU 回收**、**冷启动懒恢复**（全部条目恢复，只加载激活那个）
- 文本选区：占位 ActionMode 保留系统原生手柄 + 自绘工具条（搜索/翻译/复制/钉屏），随滚动跟随
- 长按：链接菜单（新标签/后台/复制/外部打开）、图片菜单（保存/分享/以图搜图）
- 网页适配：自绘 JS 弹窗、<select>/日期 JS 接管、文件选择（藏浮层）、相机/麦克风授权引导、
  window.open 新标签、全屏视频、自动播放、去 ; wv UA、桌面 UA 开关、主题色顶栏
- 其它：下载（系统 DownloadManager + 面板进度）、广告域名拦截（63 条）、渲染进程崩溃兜底、
  调试测试页 xg://imetest

## 3.7 已知问题（2026-11-02）

- **单元测试暂时跑不起来**：仓库里 FloatBallGestureDetectorTest、ViewModelsTest 因其它重构
  缺少构造参数而编译失败（与浏览器无关）。修好它们之后才能加/跑浏览器相关单测。
- 广告过滤只是域名表（63 条），没有元素隐藏与订阅规则。
- 视频下载/投屏未实现（需要 JS 嗅探 <video> 源）。

## 4. 剩余工作（按建议优先级）

### P0 高

1. **正式入口**
   现在只能 adb 广播。需要新增 `GestureActionType`（例如 `OPEN_FLOATING_BROWSER(81)`）：
   - `app/src/main/java/com/slideindex/app/gesture/GestureAction.kt`（枚举 + 数据类 + 序列化）
   - `ui/picker/GestureActionCatalog.kt`（加入可选动作）
   - `ui/gesturepicker/GestureActionPickerSearch.kt`（图标/名称/描述，共 4 处 when）
   - `ui/GestureActionOutlinedIcons.kt`（2 处 when）
   - `gesture/ActionExecutor.kt`（执行：`FloatingBrowserPanel.show(context, null)`）
   - 字符串资源（多语言 json/xml）
2. **网页取词**：长按选字 → 菜单「搜索 / 复制 / 翻译」。
   实现建议：`WebView.startActionMode` 覆写或 JS `window.getSelection()` 取值；不要搬 FV 的整套 DOM 遍历。

### P1 中

3. 长按链接：新标签打开 / 复制链接 / 外部浏览器。
4. 标签上限与回收：按 `ActivityManager.memoryClass` 定上限，超时 `saveState()` + `destroy()`，切回 `restoreState()`。
5. 进程被杀后恢复标签（持久化 URL 列表 + 位置）。
6. 卡片模式输入法避让验证（键盘弹出是否遮挡内容；未验证）。
7. 取色主题：从 `theme-color` 或页面主图取色，染顶栏/进度条。

### P2 低

8. 上滑切标签手势 / 标签缩略图网格。
9. 桌面 UA 开关。
10. 广告规则表（现在只有 24 个硬编码域名，见 `BrowserTab.AdBlockHosts`）。
11. 下载管理页（现在只 `DownloadManager.enqueue`）。
12. 书签 / 历史 / 无痕。

### P3 差异化

13. 以图搜图接入：Lens v3 上传（见 §5.4），把结果 URL 丢给浏览器打开。

---

## 5. FV 逆向资料（重点）

### 5.1 反编译源码位置

| 内容 | 路径 |
| :- | :- |
| FV 1.6.4 全量反编译 Java | `D:\SystemTemp\fv164\sources` |
| 单类导出（悬浮球 touch） | `D:\SystemTemp\c3.java` |
| FV APK 本体（挖字符串/资源） | `D:\SystemTemp\fv.apk` |
| 完整逆向笔记（浏览器部分） | 本仓库 `docs/fv-browser-re.md` |

> APK 反编译命令（jadx CLI）：`jadx --output-dir <out> D:\SystemTemp\fv.apk`；
> 只挖资源/字符串时可直接用 Python 读 zip 里的 `resources.arsc` / `classes*.dex`。

### 5.2 浏览器核心类（FV）

| 类 | 作用 |
| :- | :- |
| `com/fooview/android/widget/FVWebWidget.java`（5056 行） | 浏览器主体：WebView + 浮动按钮 + 全部逻辑 |
| `com/fooview/android/fooview/ShowWebUI.java` | 外壳（inflate `foo_webui` 布局） |
| `com/fooview/android/FVWebviewActivity.java` | 影子 Activity：无 setContentView，`onResume` 后 500ms `moveTaskToBack` |
| `com/fooview/android/fooclasses/ObservableWebView.java` | 滚动回调 |
| `com/fooview/android/fooclasses/nestedscroll/NestedScrollWebView.java` | 嵌套滚动 |

关键行号（`FVWebWidget.java`）：
- `y1()` 4958-5056：WebView settings 全量配置
- `dispatchTouchEvent` 4512：触摸/浮动按钮逻辑
- `C1()` 3344：每次 `onPageFinished` 注入 JS（含 22 个函数）
- `MyJS` 357-749：19 个 `@JavascriptInterface`
- `class m` 1306：WebViewClient；`class n` 1836：WebChromeClient；`class o` 2808：DownloadListener
- `onCreateWindow` 2487：**新窗口 = 新建 `FVWebWidget` 实例**（FV 的"多标签"实质）
- `N1()` 4311 / `S1()` 4396：后台标签 `onPause` / 切回 `onResume`
- `getState()` 4736 / `R1()` 4386：`saveState` / `restoreState`

### 5.3 FV 的多标签模型（重要结论）

FV **没有传统 tab bar**：每次打开网页 = 新建一个 `ShowWebUI` + `FVWebWidget` 实例，压进 UI 栈。
多开靠「后台实例 `onPause()`」控制资源，不是靠限制数量。
我们的实现不同：单窗口内多 WebView + 只挂激活的那个（更省窗口层级），语义上等价。

### 5.4 FV 的以图搜图（Lens 上传链路）

`D:\SystemTemp\fv164\sources\z4\d.java`（Google）+ `z4/g.java`（multipart 基类）：

```
POST https://lens.google.com/v3/upload?hl=en-US&ep=gsbubb<时间戳>
multipart/form-data，boundary = U1DCBvfRB8uKxu-pX-R-854T-dkBP8UH
字段：encoded_image(JPEG q50，最长边 800) + hl + ep + st
头：UA=桌面 Chrome23 / Host=lens.google.com / Origin+Referer=https://images.google.com /
    Cookie=CookieManager.getCookie("https://lens.google.com/v3/upload?...")
setInstanceFollowRedirects(false) → 读 303 响应的 Location 作为结果页 URL
```

其它引擎：`z4.e` iqdb、`z4.f` SauceNAO、`z4.i` TinEye、`z4.h` Sogou、`z4.a` Amazon；
通用基类 `z4.c`（先把图存本地再交给网页）。

**这套上传逻辑可以直接复用**：拿到结果 URL 后交给 `FloatingBrowserPanel.show(context, url)`。

---

## 6. 验证清单（改动后建议跑一遍）

| # | 场景 | 预期 |
| :- | :- | :- |
| 1 | 广播打开 | 卡片出现，顶栏 `● 域名` + `✕`，底栏胶囊 |
| 2 | 拖顶栏左侧 | 窗口跟随移动，不越界 |
| 3 | 双击顶栏 | 卡片 ⇄ 全屏；重开后保持上一次状态 |
| 4 | 拖右下角蓝弧 | 改尺寸；重开后保持 |
| 5 | 下滑页面 | 底栏自动隐藏；上滑恢复 |
| 6 | `⌂` 地址栏 | 输入 URL 能打开 |
| 7 | `②` 标签面板 | 新建/切换/关闭正常；新标签是独立 WebView |
| 8 | WebRTC 摄像头页 | 出画面（不是大播放键） |
| 9 | 含 `<select>`/日期/alert 的页面 | 自绘弹窗可用 |
| 10 | 文件上传 | 选择器可见并回传 |
| 11 | `target=_blank` | 新标签打开 |

---

## 7. 当前分支状态

- `feat/floating-browser`：3 个新文件（`browser/`）+ `SlideIndexAccessibilityService` 接线，**尚未提交**。
- 主 worktree（`main`）里还留着一个**临时试验面板** `app/src/main/java/com/slideindex/app/overlay/WebViewSpikePanel.kt`（P0 验证用，未提交），
  正式版稳定后应删除，并把其广播入口一并移除。
