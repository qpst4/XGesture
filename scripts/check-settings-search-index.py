#!/usr/bin/env python3
"""校验「设置搜索索引」覆盖了导航里注册的全部无参设置页。

为什么需要它
-----------
索引（`SettingsSearchIndex.kt`）是人工维护的静态表。`R.string` 改名会编译失败，但**新增页面
忘了加索引不会有任何报错**——用户只是搜不到。这个脚本把三类问题列出来：

1. 缺页：导航里注册了、索引里没有 → 用户搜不到这个设置页；
2. 死链：索引里有、导航里没注册 → 点结果会撞 `NavDisplay` 的
   `No entry … registered for`（`RegisteredNavEntries` 里是 `IllegalStateException`）；
3. 陈旧：索引里的 `AppNavKey` 在 `AppNavKey.kt` 里已经不存在（编译期也会报，这里给一行提示）。

只校验**无参路由**（`data object`）：带参路由是 picker / 详情 / 槽位配置，脱离上下文单独打开
没有意义，本来就不进索引，见 `IGNORED_ROUTES`。

用法
----
    python scripts/check-settings-search-index.py          # 有问题 exit 1
    python scripts/check-settings-search-index.py --list   # 顺带打印收录的页面数
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NAV_DIR = ROOT / "app" / "src" / "main" / "java" / "com" / "slideindex" / "app" / "ui" / "navigation"
INDEX_FILE = (
    ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "slideindex"
    / "app"
    / "ui"
    / "search"
    / "SettingsSearchIndex.kt"
)

# 不进索引的路由：键 → 为什么。
# 这些都是流程内页面（选谁 / 选哪个动作 / 编辑草稿 / 结果详情），给它们一条搜索结果没有意义：
# 用户不会搜"选哪个应用"，而搜到了也没法脱离当时的上下文打开。
_FLOW_PAGES = (
    "ActivityShortcutAddFile",
    "ActivityShortcutAddImage",
    "ActivityShortcutPickApp",
    "ActivityShortcutPickAppShortcut",
    "ActivityShortcutPickAppShortcutPresets",
    "ActivityShortcutPickShell",
    "ClipboardFloatBlacklistPick",
    "ClipboardLsposedWhitelistPick",
    "ExtensionBackTapActionMyShortcuts",
    "ExtensionBackTapActionPick",
    "ExtensionBackTapActionPickApp",
    "ExtensionBackTapActionPresetShortcuts",
    "FileSharePickApp",
    "FloatBallSearchEnginePresetPicker",
    "HomeCornerGestureInnerZoneActionPick",
    "HomeCornerGestureInnerZoneMyShortcuts",
    "HomeCornerGestureInnerZonePickApp",
    "HomeCornerGestureInnerZonePresetShortcuts",
    "HomeExcludedAppsPick",
    "HomePreviousAppBlacklistPick",
    "HoneycombLauncherAdd",
    "HoneycombLauncherMyShortcuts",
    "HoneycombLauncherPickApp",
    "HoneycombLauncherPresetShortcuts",
    "OtpBlockedAppsPick",
    "ShakeGestureBlacklistPick",
    "ShakeIndependentAppPick",
    "TranslateAppPicker",
)
# 标题是硬编码中文（i18n 迁移的遗留），没有 R.string 可用；补索引前得先把标题迁进资源。
_HARDCODED_TITLE_PAGES = (
    "LinkRules",
    "LinkExtractionRules",
    "LinkDispatchHistory",
    "FileShareEngineSettings",
)
# 调试/结果页，不是设置项。
_DEBUG_PAGES = (
    "ExtensionShortcutTest",
    "ShellCommandResult",
)

IGNORED_ROUTES: dict[str, str] = {
    **dict.fromkeys(_FLOW_PAGES, "选择/编辑流程页，脱离上下文打开没有意义"),
    **dict.fromkeys(_HARDCODED_TITLE_PAGES, "标题硬编码，未进 strings.xml"),
    **dict.fromkeys(_DEBUG_PAGES, "调试/结果页，不是设置项"),
}

ENTRY_RE = re.compile(r"(?:hiltEntry|entry)<AppNavKey\.(\w+)>")
INDEX_RE = re.compile(
    r"page\(\s*R\.string\.\w+,\s*MainBottomNavDestination\.\w+,\s*AppNavKey\.(\w+)"
)
OBJECT_RE = re.compile(r"@Serializable\s+data\s+object\s+(\w+)\s*:\s*AppNavKey")


def nav_sources() -> str:
    return "\n".join(
        p.read_text(encoding="utf-8", errors="replace")
        for p in sorted(NAV_DIR.glob("*.kt"))
    )


def main() -> int:
    # Windows 控制台默认 GBK：不显式改成 UTF-8 会把中文提示打成乱码。
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if not INDEX_FILE.is_file():
        print(f"索引文件不存在: {INDEX_FILE}", file=sys.stderr)
        return 1

    nav = nav_sources()
    registered = set(ENTRY_RE.findall(nav))
    objects = set(OBJECT_RE.findall((NAV_DIR / "AppNavKey.kt").read_text(encoding="utf-8")))
    index_text = INDEX_FILE.read_text(encoding="utf-8")
    indexed = set(INDEX_RE.findall(index_text))

    routable = registered & objects
    missing = sorted(routable - indexed - set(IGNORED_ROUTES))
    dangling = sorted(indexed - registered)
    stale = sorted(indexed - objects)

    if missing:
        print("缺页：以下路由在导航里注册了，但索引里没有（用户搜不到）")
        for key in missing:
            print(f"  - {key}")
        print()
    if dangling:
        print("死链：以下路由在索引里，但没有任何 tab 注册（点结果会崩）")
        for key in dangling:
            print(f"  - {key}")
        print()
    if stale:
        print("陈旧：以下路由在索引里，但 AppNavKey.kt 里已不存在")
        for key in stale:
            print(f"  - {key}")
        print()

    print(
        f"注册路由 {len(registered)} / 无参可路由 {len(routable)} / "
        f"已收录 {len(indexed)} / 显式忽略 {len(IGNORED_ROUTES)}"
    )
    return 1 if (missing or dangling or stale) else 0


if __name__ == "__main__":
    sys.exit(main())
