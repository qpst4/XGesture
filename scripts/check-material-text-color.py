#!/usr/bin/env python3
"""扫描「material3 Text 在 miuix 容器里不传 color」导致的静默错色。

背景：miuix 的 `GlassDialogButton` / `GlassCard` / `MiuixButton` 等通过
`top.yukonga.miuix.kmp.theme.LocalContentColor` 下发文字色（Primary → `onPrimary` 白、
Destructive → `error` 红、玻璃卡片 → `onSurfaceContainer`）；而 **material3 的 `Text`**
读的是 material3 自己的 `LocalContentColor`（默认 `Color.Black`）。两者是不同的
CompositionLocal，所以「在 miuix 容器里用 material3 Text 且不传 color」会静默渲染成黑字，
编译器不报错。

分级：
    HIGH  容器确定会换文字色，且默认黑字必然错 —— 必须修
          （`GlassDialogButton` / `GlassCard` / `MiuixButton`）
    WARN  容器会换色但通常仍是深色字，可能只是对比度略差 —— 建议复核
          （`TextButton` / `Card` / `CardSegment` / `GlassIconButton`）
    MEDIUM  用了 `MiuixTheme.textStyles.*` 却没传 color —— 样式意图是 miuix，颜色却取 material3
    LOW   普通页面白底正文 —— 不需要处理

尾随 lambda 会被并入容器区间（Kotlin 里 `MiuixButton(...) { Text(...) }` 的内容在括号之外）。

用法：
    python scripts/check-material-text-color.py [--all]

退出码：存在 HIGH 时返回 1，否则 0。
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCAN_DIRS = ("app", "core", "feature")

M3_TEXT_IMPORT = re.compile(
    r'^import\s+androidx\.compose\.material3\.Text(?:\s+as\s+(\w+))?\s*$', re.M
)
MIUIX_BUTTON_IMPORT = re.compile(
    r'^import\s+top\.yukonga\.miuix\.kmp\.basic\.Button\s*$', re.M
)

# 确定会换文字色的容器
HIGH_CONTAINERS = ("GlassDialogButton", "GlassCard", "MiuixButton")
# 会换色但通常是深色字的容器
WARN_CONTAINERS = ("TextButton", "Card", "CardSegment", "GlassIconButton")


def find_call_spans(text, name, extend_lambda=False):
    """返回 name(...) 的 (start, end) 字符区间。

    Kotlin 的尾随 lambda 写在参数括号之外，容器类调用要把紧跟的 `{ ... }` 也纳入区间，
    否则内容区里的 Text 会被判成"不在容器里"。
    """
    spans = []
    idx = 0
    pat = re.compile(r'(?<![\w.])' + re.escape(name) + r'\s*\(')
    while True:
        m = pat.search(text, idx)
        if not m:
            break
        start = m.start()
        k = m.end() - 1
        depth = 0
        in_str = False
        while k < len(text):
            c = text[k]
            if in_str:
                if c == "\\":
                    k += 2
                    continue
                if c == '"':
                    in_str = False
            else:
                if c == '"':
                    in_str = True
                elif c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                    if depth == 0:
                        break
            k += 1
        end = k
        if extend_lambda:
            j = k + 1
            while j < len(text) and text[j] in " \t\n":
                j += 1
            if j < len(text) and text[j] == "{":
                depth = 0
                in_str = False
                while j < len(text):
                    c = text[j]
                    if in_str:
                        if c == "\\":
                            j += 2
                            continue
                        if c == '"':
                            in_str = False
                    else:
                        if c == '"':
                            in_str = True
                        elif c == "{":
                            depth += 1
                        elif c == "}":
                            depth -= 1
                            if depth == 0:
                                break
                    j += 1
                end = j
        spans.append((start, end))
        idx = end + 1
    return spans


def has_color_arg(span):
    """参数列表顶层是否传了 `color = ...`。"""
    depth = 0
    in_str = False
    i = span.find("(") + 1
    while i < len(span):
        c = span[i]
        if in_str:
            if c == "\\":
                i += 2
                continue
            if c == '"':
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            elif depth == 0 and span.startswith("color", i):
                before = span[i - 1] if i > 0 else " "
                after = span[i + 5:i + 8]
                if not before.isalnum() and after.lstrip().startswith("="):
                    return True
        i += 1
    return False


def build_containers(text):
    high = []
    for name in HIGH_CONTAINERS:
        high.extend(find_call_spans(text, name, extend_lambda=True))
    warn_names = list(WARN_CONTAINERS)
    if MIUIX_BUTTON_IMPORT.search(text):
        warn_names.append("Button")
    warn = []
    for name in warn_names:
        warn.extend(find_call_spans(text, name, extend_lambda=True))
    return high, warn


def classify(start, span, high, warn):
    if any(a < start < b for a, b in high):
        return "HIGH"
    if any(a < start < b for a, b in warn):
        return "WARN"
    if "MiuixTheme.textStyles" in span:
        return "MEDIUM"
    return "LOW"


def main():
    show_all = "--all" in sys.argv
    results = {}
    files_scanned = 0
    total = {"HIGH": 0, "WARN": 0, "MEDIUM": 0, "LOW": 0}
    for d in SCAN_DIRS:
        for dirpath, dirnames, filenames in os.walk(os.path.join(ROOT, d)):
            dirnames[:] = [x for x in dirnames if x != "build"]
            for fn in filenames:
                if not fn.endswith(".kt"):
                    continue
                path = os.path.join(dirpath, fn)
                text = open(path, encoding="utf-8").read()
                m = M3_TEXT_IMPORT.search(text)
                if not m:
                    continue
                files_scanned += 1
                name = m.group(1) or "Text"
                high, warn = build_containers(text)
                hits = []
                for start, end in find_call_spans(text, name):
                    span = text[start:end + 1]
                    if has_color_arg(span):
                        continue
                    level = classify(start, span, high, warn)
                    line_no = text.count("\n", 0, start) + 1
                    hits.append((level, line_no, span.split("\n")[0].strip()[:64]))
                if hits:
                    for level, _l, _h in hits:
                        total[level] += 1
                    results[os.path.relpath(path, ROOT).replace("\\", "/")] = hits

    print(f"[material-text-color] 扫描 {files_scanned} 个使用 material3 Text 的 Kotlin 文件。")
    for path in sorted(results):
        hits = sorted(results[path], key=lambda h: h[1])
        relevant = [h for h in hits if h[0] != "LOW" or show_all]
        if not relevant:
            continue
        print(f"\n{path}")
        for level, line_no, head in relevant:
            print(f"  {level:6s} L{line_no}: {head}")

    print(
        f"\n汇总：HIGH={total['HIGH']}  WARN={total['WARN']}  MEDIUM={total['MEDIUM']}  LOW={total['LOW']}"
        "（LOW=普通白底正文，WARN=建议复核；--all 显示全部）"
    )
    if total["HIGH"] > 0:
        print("❌ HIGH：这些 material3 Text 位于会换文字色的 miuix 容器内且未传 color，必然是黑字。")
        return 1
    print("✅ 未发现必然错色的 material3 Text。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
