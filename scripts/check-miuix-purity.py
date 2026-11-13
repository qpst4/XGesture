#!/usr/bin/env python3
"""禁止 Miuix 组件内部混入 Material3。

`ui/miuix/**` 是 Miuix 体系的组件目录：内部一律使用 miuix 的 Text / Icon / 颜色，
否则在半透明玻璃表面上会出现"miuix 底色 + M3 前景色"的错配（蓝底黑字）。

取色管线例外（必须产出 M3 的 ColorScheme），见 ALLOWED。

用法：python scripts/check-miuix-purity.py
退出码：0 通过，1 有违规。
"""

import os
import sys

ROOTS = ["app", "core", "feature"]
FORBIDDEN = "import androidx.compose.material3."
ALLOWED = {
    "AppMaterialColorScheme.kt",
    "MiuixSeedResolver.kt",
    "AppThemeSpecAdapter.kt",
    # 双主题桥：同时提供 MiuixTheme 与 MaterialExpressiveTheme
    "ModuleTheme.kt",
}


def is_miuix_dir(path):
    parts = path.replace("\\", "/").split("/")
    return "ui" in parts and "miuix" in parts


def should_check(path, name):
    """`ui/miuix/**` 目录，以及文件名含 Miuix 的组件（散落在各处的同类组件）。"""
    return is_miuix_dir(path) or "Miuix" in name


def main():
    violations = []
    for root in ROOTS:
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in ("build", ".gradle")]
            for name in filenames:
                if not name.endswith(".kt") or name in ALLOWED:
                    continue
                path = os.path.join(dirpath, name)
                if not should_check(path, name):
                    continue
                with open(path, encoding="utf-8", errors="replace") as f:
                    for lineno, line in enumerate(f, 1):
                        if line.strip().startswith(FORBIDDEN):
                            violations.append((path, lineno, line.strip()))

    if violations:
        print("Miuix 组件里混入了 Material3：")
        for path, lineno, line in violations:
            print(f"  {path}:{lineno}  {line}")
        print("\n改用 miuix 的 Text / Icon / MiuixTheme.colorScheme；")
        print("取色管线（ColorScheme）请在 ALLOWED 里登记。")
        return 1

    print("OK: ui/miuix 下没有 Material3 import")
    return 0


if __name__ == "__main__":
    sys.exit(main())
