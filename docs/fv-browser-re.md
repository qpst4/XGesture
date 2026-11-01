# FV 1.6.4 悬浮窗浏览器 —— 逆向记录

> 来源：`D:\SystemTemp\fv164\sources`（jadx 反编译）+ `D:\SystemTemp\fv.apk`（资源/字符串）。
> 结论均标注文件与行号，可复核。

## 1. 组件与实例模型

```
ShowWebUI (FooInternalUI, 布局 foo_webui)        外壳：标题栏 + 关闭
 └─ FVWebWidget (FrameLayout, 5056 行)           主体：WebView + 浮动按钮 + 全部逻辑
     ├─ ObservableWebView → NestedScrollWebView  滚动回调
     ├─ WebViewClient   (class m, 1306-1820)
     ├─ WebChromeClient (class n, 1836-2792)
     ├─ DownloadListener(class o, 2808-2836)
     └─ MyJS            (class MyJS, 357-749)    19 个 @JavascriptInterface
FVWebviewActivity                               影子 Activity（仅提供 Activity token）
```

**多开 = 多 widget 实例，不是 tab。**
`WebChromeClient.onCreateWindow`（2487）→ `o1("web", params)` 新建 `FVWebWidget` →
`WebViewTransport.setWebView(b10.f12124d)` → 新 widget 入 UI 栈（`E0(...)` / `D(...)`）。
每个实例独立 WebView、独立返回栈。

### 1.1 多标签的资源模型（FV 能开很多个的原因）

FV 并不限制标签数量，靠的是**后台标签一律 onPause()**：

| 机制 | 代码位置 | 作用 |
| :- | :- | :- |
| 切走时暂停 | N1():4311 → webView.onPause()，标记 12130f=true | 停止 JS 定时器与渲染 |
| 延迟 2s 再暂停 | N1() 里 postDelayed(z, 2000) | 避免快速切换时反复 pause/resume |
| 切回时恢复 | S1():4396 → webView.onResume() | 恢复 |
| 状态保存 | getState():4736 → saveState(Bundle) | 页面可销毁后重建 |
| 状态恢复 | R1(Bundle):4386 → 
estoreState | 含前进后退栈 |
| 历史清理 | 4480、3050、3180 → clearHistory() | 打开新页时控内存 |

注意：**没有用 pauseTimers()**（那是进程级的，会误伤激活页）。

结论：标签数上限不是靠"少开"，而是靠"后台全暂停"。每个后台 WebView 仍占内存
（约 30~60MB，暂停后更低），因此仍需一个**总内存保护**（见 §14）。

## 2. 窗口 / 生命周期

- `FVWebviewActivity`（151 行）
  - 启动 flags `335544320` = `NEW_TASK | CLEAR_TOP`
  - `onCreate`：<API23 用 `Theme.NoDisplay`；无 `setContentView`
  - `onResume`：`postDelayed(() -> moveTaskToBack(true), 500)` → 立刻退后台
  - 单例 `f1413c`；`f1415e` 为暂存回调（`onCreate` 后 100ms 逐个执行）
  - 用途：文件选择（`startActivityForResult(11)`）、权限、输入法
- 网页活在浮层窗口的 View 里，前台应用不被替换 → 不触发 YouTube PiP

## 3. WebView 配置（`y1()`，4958-5056）

```java
settings.setBuiltInZoomControls(true); setSupportZoom(true); setDisplayZoomControls(false);
settings.setJavaScriptEnabled(true);
settings.setSaveFormData(pref("web_save_form", true));
settings.setTextZoom(100);
// UA：系统默认；若 i0.r.e() 非空则覆盖
settings.setUseWideViewPort(true); setLoadWithOverviewMode(true);
settings.setDatabasePath(files+"/databases"); setDatabaseEnabled(true);
settings.setGeolocationDatabasePath(...); setGeolocationEnabled(true);
settings.setDomStorageEnabled(true);
settings.setCacheMode(-1);                 // LOAD_DEFAULT
settings.setSavePassword(false);
settings.setJavaScriptCanOpenWindowsAutomatically(true);
settings.setSupportMultipleWindows(true);  // ← 支持 window.open（配 onCreateWindow）
CookieManager.setAcceptCookie(true);
settings.setMixedContentMode(0);           // ALWAYS_ALLOW
CookieManager.setAcceptThirdPartyCookies(webView, pref("web_save_cookie", true));
webView.addJavascriptInterface(myJS, "globalfooviewobject");
```

暗色：`ALGORITHMIC_DARKENING`（<33 用 `FORCE_DARK`）；不支持则注入 `fooviewDarkMode`。

## 4. 外部契约 `f0.z`（8 个回调）

```java
void a(WebView, int)                 // 进度
void b(WebView, String)              // 标题
void c(WebView, String, Bitmap)      // 图标
void d(byte[])                       // 数据
void e(WebView, String, int)         // 页面结束（url + 错误码）
void f(url, ua, disposition, mime, len, referer)  // 下载
void g(WebView, Bitmap)              // 截图/缩略图
void h()                             // 点击（接管外壳）
```

## 5. WebViewClient（class m）

| 覆写 | 行为 |
| :- | :- |
| `onPageStarted` | 按 `notShowWebImage` 决定是否拦图片 |
| `onPageFinished` | 隐藏进度、记 `f12165s`、`C1()` 注入、回调 `f0.z.e(...)`；`about:blank` 多分支处理 |
| `onReceivedError` | 错误页 |
| `onReceivedSslError` | 弹窗（`s1(int)` 5 种文案） |
| `shouldInterceptRequest` | 广告/图片拦截（`needBlockUrl`） |
| `shouldOverrideUrlLoading` | `v1(url)`：`intent:`/`tel:`/`wtai:`/`market:`/`mailto:`/`sms:` 转 Intent；`lens.google.com` 直接吞掉 |

## 6. WebChromeClient（class n）——12 个点

| 覆写 | 行为 |
| :- | :- |
| `onProgressChanged` | 进度 + 浮动按钮显隐 |
| `onReceivedTitle` | 标题 |
| `onJsAlert/Confirm/Prompt` | 自绘对话框 |
| `onPermissionRequest` | 读 `web_allow_microphone`/`web_allow_camera`（0 允许/1 拒绝/2 询问）逐项 grant |
| `onPermissionRequestCanceled` | 收等待框 |
| `onGeolocationPermissionsShowPrompt` | 读 `web_allow_location` |
| `onShowFileChooser` | 影子 Activity `startActivityForResult(11)` |
| `onCreateWindow` | **新建 widget**（见 §1） |
| `onShowCustomView/onHideCustomView` | 全屏视频 |
| `onDownloadStart` | 交 `f0.z.f(...)`；`blob:` 走 `n1()` |
| `onConsoleMessage` | 日志 |

## 7. JS 注入（`C1()`，3344）

页面结束时注入一个大脚本（字段 3293-3298 拼接）：

```js
javascript:if(typeof fooViewOverrideSelect != 'function'){
  ...17 个脚本片段...
};fooViewOverrideSelect(<bool>);fooviewOverrideExecCmd();
window.onbeforeunload=null;
```

注入的函数（22 个，实证提取）：

| 分组 | 函数 |
| :- | :- |
| 取词 | `fooviewFindAllTexts`、`fooViewFindElement(x,y,w,h)`、`fooviewIsElementsInViewport`、`fooViewInjectCss` |
| 表单接管 | `fooViewOverrideSelect`、`fooViewOverrideOneDocumentSelect`、`fooViewSetSelect`、`fooviewSetTextAreaContent` |
| 日期接管 | `fooViewOverrideOneDocumentDate`、`fooViewSetDate/DateMonth/DateTime/DateWeek` |
| 滚动 | `fooViewCanScrollUp`、`fooViewCanScrollHorizontal2`、`fooViewGetTranslateY` |
| 视频 | `fooViewCheckVideosInDoc`、`fooviewEnterVideoFullscreenMode` |
| 其它 | `fooviewExecCmdFunc`、`fooviewOverrideExecCmd`、`fooviewGetProperty`、`fooviewHideUrlElement` |

点击命中：`l1(x,y)` → `javascript:fooViewFindElement(x,y,w,h)`（**用 JS 做元素命中，而非无障碍**）。

## 8. JS 桥（`MyJS`，19 个 `@JavascriptInterface`）

`bodyReady` / `jQueryLoaded` / `canScrollUp` / `canScrollHorizontal` / `needBlockUrl` /
`disableWriteClipboard` / `onGetSelectedText` / `onGetRectText` / `onGetProperty` / `saveHtml` /
`showSelectDate*`（4 个）/ `showSelectValues` / `setCurrentVideoInfo` /
`onGetBingTranslateFromTo` / `onWindowError`

## 9. 触摸 / 浮动按钮（`dispatchTouchEvent`，4512）

- DOWN：`C1()`、`requestFocus`、记 `L0/M0/N0`、`postDelayed(Q0, q.f23757a)`、`D1(false)`
- MOVE：竖移 > `K0` → 滚动（`S0=2`）；横移 → 左右滑（`S0=1`），据此显示“全屏/浮窗”按钮
- UP：500ms 内且位移 < `K0` → 点击，`Q1()` + `f0.z.h()`
- `D1(boolean)` = `requestDisallowInterceptTouchEvent(...)`（把滚动交给 WebView 或外层）
- 浮动按钮用 `CircleImageView(32dp)` + `addView/updateViewLayout` 贴着选中区域

## 10. 下载

`onDownloadStart` → 取当前页作 referer → `blob:` 内部处理，其它交 `f0.z.f(...)`
（FV 下载模块，多线程设置 `enable_multi_thread_download`）。

## 11. 相关设置键

`web_save_form`(true)、`web_save_cookie`(true)、`notShowWebImage`(false)、
`webPageWriteClipboard`(1)、`web_allow_location`(0)、`web_allow_microphone`(0)、
`web_allow_camera`(0)、`defaultWebUA`、`webUseSecurityKeyboard`(false)、
`enable_multi_thread_download`、`def_image_search_engine`。

## 12. 图片搜索（独立链路，非浏览器）

`z4/d.java`：`POST https://lens.google.com/v3/upload?hl=en-US&ep=gsbubb<ts>`，
multipart boundary `U1DCBvfRB8uKxu-pX-R-854T-dkBP8UH`，字段 `encoded_image`(JPEG q50, 最长边 800)
+ `hl`/`ep`/`st`；头：桌面 Chrome23 UA、Host、Origin/Referer=images.google.com、lens Cookie；
`setInstanceFollowRedirects(false)` → 读 303 的 `Location` 当结果页。
其它引擎：iqdb(`e`)、SauceNAO(`f`)、TinEye(`i`)、Sogou(`h`)、Amazon(`a`)，通用基类 `z4.c`（存本地文件交网页）。

## 13.5 P0 实测结论（2026-11-01，真机 MEIZU 21 / API 31）

在真实浮层环境（无障碍 Service + TYPE_APPLICATION_OVERLAY 窗口 + 我们的 overlay 体系）验证四项：

| # | 试验 | 结果 |
| :- | :- | :- |
| ① | 输入法 | ✅ 正常：浮层窗口里 EditText 可聚焦、键盘弹出，无需特殊处理 |
| ② | 表单/弹窗（系统默认） | ❌ 全灭：<select>、<input type=date/time> 无反应；lert 无反应；confirm 立即返回 cancel；prompt 返回 null |
| ② | 表单/弹窗（自绘接管） | ✅ 全部可用：自绘 Dialog(overlay 窗口类型) 实现 alert/confirm/prompt；JS 拦截 + @JavascriptInterface 实现 select 列表与日期/时间选择，回写后 dispatch input/change |
| ③ | 文件上传 | ⚠️ 桥接启动成功，但系统文件选择器被浮层挡住 → 需在打开选择器期间隐藏/降低浮层（复用图片搜索面板的 OverlayFileChooserSuppressor） |
| ④ | 多标签内存 | 成本按**不同站点**计：10 个真实站点 → 6 个渲染进程 ≈480MB（单个重站点可达 390MB）；21 个同站页面 → 4 个进程 ≈150MB；应用进程基线 ≈600MB |

关键教训：
- **服务/浮层上下文没有 Activity 令牌，任何 AlertDialog/系统 Dialog 都会 BadTokenException** → 所有对话框必须自绘。
- <select> / 日期控件必须 JS 接管（与 FV 当年的 ooViewOverrideSelect 同一动因），并设 
eadOnly 防止原生控件抢事件。
- 文件选择器不是“不能用”，而是**层级被浮层压住**，靠 suppress 解决。
- 标签内存要按“活跃站点数”管，不是按“标签个数”。

## 14. 过时设计（移植时应删除或替换）

| FV 的做法 | 状态 | 现代替代 |
| :- | :- | :- |
| setDatabasePath / setDatabaseEnabled / setGeolocationDatabasePath | API 19 起废弃且无效 | 删除 |
| setSavePassword(false) | API 18 起无效 | 删除 |
| setSaveFormData(pref) | API 26 起废弃 | 删除 |
| onReceivedError(WebView,int,String,String) | API 23 弃用 | onReceivedError(WebView, WebResourceRequest, WebResourceError) |
| WebViewFeature.FORCE_DARK | AndroidX WebKit 弃用 | setAlgorithmicDarkeningAllowed(true) |
| 注入 JS 做暗色（ooviewDarkMode） | 只为兼容老 WebView | 现代用算法暗色，删除注入 |
| ooviewHideUrlElement（按 URL 隐藏 iframe 去广告） | 原始、易误伤 | shouldInterceptRequest + 规则表 |
| DOM 遍历取词（ooviewFindAllTexts/ooViewFindElement） | 当年必须；现在 WebView 会把文本暴露给无障碍 | 无障碍节点 或 valuateJavascript("window.getSelection()")；仅 Canvas/图片文字仍需 OCR |

仍然必需的：影子 Activity（文件选择/权限）、自绘 JS 弹窗、onCreateWindow、
onShowCustomView（全屏视频）、DownloadListener、<select>/日期接管（需真机验证是否仍需）。

新增注意事项（FV 当年没有）：UA 缩减（UA-CH）让自定义 UA 不可靠、第三方 Cookie 收紧、
setMixedContentMode(ALWAYS_ALLOW) 应改 COMPATIBILITY_MODE、Android 10+ 后台起 Activity 受限。

## 15. 我们做的话：多标签方案

| 方案 | 内存 | 状态保真 | 结论 |
| :- | :- | :- | :- |
| **A. 每标签独立 WebView，非激活 onPause()** | 中高（暂停后显著下降） | 完整 | ✅ 采用，上限按内存动态（8~12） |
| B. A + 超时 saveState() 后 destroy()，切回重建 | 低 | 丢 JS 状态 | 作为兜底（如非激活 > 10 分钟） |
| C. FV 式：每页一个 widget 多窗 | 最高 | 完整 | 不学 |

实现要点：
- 切走：webView.onPause()（不要用进程级 pauseTimers()）；
- 切回：webView.onResume()；
- 退出标签：saveState() → destroy() → 
emoveView；
- 恢复：
estoreState(bundle)；
- 内存保护：ActivityManager.memoryClass + 标签数上限，超限回收最久未用。

## 13. 仍未逆向（需反编译资源）

- 布局 `foo_webui` / `foo_widget_web` 的结构（标题栏、进度条、浮动按钮的实际排布）
- `b2.dkmd` 等脚本常量原文（在 resources 数组里）
- 视频识别 → 下载/投屏链路（`setCurrentVideoInfo` 的消费方）
