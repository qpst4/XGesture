#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-cards.py: 校验业务代码是否遵守《AGENTS.md》第五条 UI 卡片规范。

规则：
1. 业务设置页面严禁直接 import 或调用 `top.yukonga.miuix.kmp.basic.Card`。
2. 必须使用 `groupedCardItems` / `MiuixGroupedTabSettingsCard` / `CardSegment`。
"""

import os
import re
import sys
from pathlib import Path

# 底层基础设施与基础卡片封装
INFRA_WHITELIST = {
    "core/design/src/main/java/com/slideindex/app/ui/miuix/GroupedCardItems.kt",
    "core/design/src/main/java/com/slideindex/app/ui/miuix/MiuixSettingsCards.kt",
    "core/design/src/main/java/com/slideindex/app/ui/miuix/FloatingCard.kt",
    "core/design/src/main/java/com/slideindex/app/ui/miuix/MiuixSettingsTipCard.kt",
    "core/design/src/main/java/com/slideindex/app/ui/miuix/SettingsPageHintCard.kt",
    "app/src/main/java/com/slideindex/app/ui/settings/components/PermissionCard.kt",
}

# 独立系统悬浮窗 / 历史专属浮层（非设置页列表）
OVERLAY_LEGACY_WHITELIST = {
    "app/src/main/java/com/slideindex/app/activity/ShortcutImagePreviewActivity.kt",
    "app/src/main/java/com/slideindex/app/freezer/FreezerGridUi.kt",
    "app/src/main/java/com/slideindex/app/message/AppMessageOverlayPort.kt",
    "app/src/main/java/com/slideindex/app/overlay/MicrophoneControlOverlay.kt",
    "app/src/main/java/com/slideindex/app/overlay/PinToScreenDialog.kt",
    "app/src/main/java/com/slideindex/app/screensearch/ScreenSearchIndicatorCard.kt",
    "app/src/main/java/com/slideindex/app/screensearch/ScreenSearchPanelCard.kt",
    "app/src/main/java/com/slideindex/app/ui/M3eSharedUi.kt",
    "app/src/main/java/com/slideindex/app/ui/OnboardingScreen.kt",
    "app/src/main/java/com/slideindex/app/ui/ShellCommandPanelDialogs.kt",
    "app/src/main/java/com/slideindex/app/ui/notificationhistory/NotificationHistoryAppGroupHeader.kt",
    "app/src/main/java/com/slideindex/app/ui/notificationhistory/NotificationHistoryFilterBar.kt",
    "app/src/main/java/com/slideindex/app/ui/notificationrule/NotificationRuleActionPicker.kt",
    "app/src/main/java/com/slideindex/app/ui/notificationrule/NotificationRuleConditionEditor.kt",
    "app/src/main/java/com/slideindex/app/ui/onboarding/OnboardingPracticeStep.kt",
    "app/src/main/java/com/slideindex/app/ui/ringlauncher/RingLauncherSlotIconEditorScreen.kt",
}

ROOT_DIR = Path(__file__).resolve().parent.parent

MIUIX_CARD_IMPORT = re.compile(r"import\s+top\.yukonga\.miuix\.kmp\.basic\.Card\b")
M3_CARD_IMPORT = re.compile(r"import\s+androidx\.compose\.material3\.Card\b")
EXPLICIT_MIUIX_CARD_CALL = re.compile(r"top\.yukonga\.miuix\.kmp\.basic\.Card\s*\(")
CARD_CALL_PATTERN = re.compile(r"(?<![A-Za-z0-9_])(?:Card|MiuixCard)\s*\(")

def check_file(path: Path) -> list:
    rel_path = path.relative_to(ROOT_DIR).as_posix()
    if rel_path in INFRA_WHITELIST or rel_path in OVERLAY_LEGACY_WHITELIST:
        return []

    errors = []
    try:
        content = path.read_text(encoding="utf-8")
    except Exception as e:
        return [f"{rel_path}: 无法读取文件 ({e})"]

    has_miuix_card_import = bool(MIUIX_CARD_IMPORT.search(content))
    has_m3_card_import = bool(M3_CARD_IMPORT.search(content))

    for line_idx, line in enumerate(content.splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith("//") or stripped.startswith("*"):
            continue

        if MIUIX_CARD_IMPORT.search(line):
            errors.append(f"{rel_path}:{line_idx}: 违规导入 `top.yukonga.miuix.kmp.basic.Card`。请遵照《AGENTS.md》第 5.1 条改用 `CardSegment` / `groupedCardItems`。")

        if EXPLICIT_MIUIX_CARD_CALL.search(line):
            errors.append(f"{rel_path}:{line_idx}: 违规直接构造 `top.yukonga.miuix.kmp.basic.Card(...)`。\n  行内容: {stripped}\n  请遵照《AGENTS.md》第 5.1 条改用 `CardSegment` / `groupedCardItems`。")

        # 如果导入了 Miuix Card 且未导入 M3 Card，调用的 Card(...) 必是 Miuix Card
        if has_miuix_card_import and not has_m3_card_import and CARD_CALL_PATTERN.search(line):
            errors.append(f"{rel_path}:{line_idx}: 违规直接构造 Miuix `Card(...)`。\n  行内容: {stripped}\n  请遵照《AGENTS.md》第 5.1 条，使用 `groupedCardItems`、`MiuixGroupedTabSettingsCard` 或 `CardSegment`。")

    return errors

def main():
    search_dirs = [ROOT_DIR / "app", ROOT_DIR / "feature", ROOT_DIR / "core"]
    all_errors = []
    scanned_count = 0

    for s_dir in search_dirs:
        if not s_dir.exists():
            continue
        for kt_file in s_dir.rglob("*.kt"):
            scanned_count += 1
            errs = check_file(kt_file)
            if errs:
                all_errors.extend(errs)

    print(f"[check-cards] 共扫描 {scanned_count} 个 Kotlin 文件。")
    if all_errors:
        print("\n❌ 发现以下不符合 UI 卡片规范的违规调用：\n")
        for err in all_errors:
            print(f"  - {err}")
        print("\n请查阅《AGENTS.md》第五条完成规范化改造后再提交。")
        sys.exit(1)
    else:
        print("✅ 全工程业务设置界面卡片规范校验通过（0 违规老旧 Card 调用）！")
        sys.exit(0)

if __name__ == "__main__":
    main()
