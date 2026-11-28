#!/usr/bin/env python3
"""
Update update.json manifest after release and purge jsDelivr cache.
Cross-platform, UTF-8 safe script for CI and local usage.

回填后校验远端清单走指数退避重试：发版 bot 推完 update.json 紧接着就查，raw / jsdelivr
的边缘缓存此时常停在上一版，一次断言会把成功的发版判红（v1.85.0 事故）。
"""
import argparse
import json
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

# Group headers of CHANGELOG.md -> in-app `notes` headings.
ZH_HEADER_TITLES = {
    "added": "新增",
    "changed": "变更",
    "fixed": "修复",
}

# Group headers of CHANGELOG.en.md -> in-app `notesEn` headings.
EN_HEADER_TITLES = {
    "added": "Added",
    "changed": "Changed",
    "fixed": "Fixed",
}


def parse_version_code_from_gradle(gradle_path: Path) -> int:
    content = gradle_path.read_text(encoding="utf-8")
    m = re.search(r"versionCode\s*=\s*(\d+)", content)
    if not m:
        raise ValueError(f"Could not parse versionCode from {gradle_path}")
    return int(m.group(1))


def get_changelog_bullet_notes(
    version: str,
    changelog_path: Path,
    max_items_per_group: int = 8,
    header_to_title: dict[str, str] | None = None,
) -> str:
    v = version.lstrip("v")
    content = changelog_path.read_text(encoding="utf-8")

    header = f"## [{v}]"
    start = content.find(header)
    if start < 0:
        raise ValueError(f"Section '{header}' not found in {changelog_path}")

    after_header = start + len(header)
    remainder = content[after_header:]
    next_match = re.search(r"(?m)^## \[", remainder)
    if next_match:
        section = content[start : after_header + next_match.start()].rstrip()
    else:
        section = content[start:].rstrip()

    header_to_title = header_to_title or {
        "added": "新增",
        "changed": "变更",
        "fixed": "修复",
    }

    groups = []
    current_title = ""
    current_items = []
    plain_bullets = []
    saw_group_header = False
    last_item = None  # list currently being appended to, for wrapped bullet lines

    def close_item():
        """Markdown bullets may be wrapped across lines; append to the open item."""
        nonlocal last_item
        last_item = None

    for line in section.splitlines():
        stripped = line.strip()
        m_group = re.match(r"^###\s+(.+)$", stripped)
        if m_group:
            hdr = m_group.group(1).strip().lower()
            title = header_to_title.get(hdr)
            if title:
                saw_group_header = True
                if current_items:
                    groups.append({"title": current_title, "items": list(current_items)})
                current_title = title
                current_items = []
                close_item()
            continue

        if stripped.startswith("- "):
            item = stripped[2:].strip().replace("**", "").replace("`", "")
            if current_title:
                if max_items_per_group <= 0 or len(current_items) < max_items_per_group:
                    current_items.append(item)
                    last_item = current_items
                else:
                    close_item()
            else:
                plain_bullets.append(item)
                last_item = plain_bullets
            continue

        # Continuation of a wrapped bullet: same line, no list marker.
        if last_item is not None and stripped and not stripped.startswith("#"):
            last_item[-1] = f"{last_item[-1]} {stripped}".strip()

    if current_items:
        groups.append({"title": current_title, "items": list(current_items)})

    if not saw_group_header:
        return "\n".join(plain_bullets)

    out_lines = []
    out_lines.extend(plain_bullets)
    for group in groups:
        if not group["items"]:
            continue
        out_lines.append(f"## {group['title']}")
        for item in group["items"]:
            out_lines.append(f"- {item}")

    return "\n".join(out_lines)


def purge_jsdelivr(repo: str = "qpst4/XGesture") -> None:
    purge_url = f"https://purge.jsdelivr.net/gh/{repo}@main/update.json"
    try:
        req = urllib.request.Request(
            purge_url, headers={"User-Agent": "xgesture-Release-Bot"}
        )
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"Purged jsDelivr cache: {purge_url} (status: {data.get('status')})")
    except Exception as e:
        print(f"WARNING: jsDelivr cache purge failed (non-fatal): {e}", file=sys.stderr)


def verify_remote_url(
    url: str,
    version: str,
    apk_size: int,
    attempts: int = 8,
    initial_delay_sec: float = 5.0,
    max_delay_sec: float = 60.0,
    backoff: float = 2.0,
) -> None:
    """Fetch one manifest URL until it matches, with exponential backoff.

    发版 bot 推完 update.json 紧接着就校验，中间只隔十几秒；而 raw.githubusercontent.com
    与 jsdelivr 的边缘缓存在这段时间里经常还停在上一版（v1.85.0 就是因此把发版流水线判红，
    本地用同一个 APK 复跑两个源都对）。原来的实现只给 raw 1 次机会、jsdelivr 固定 10s×6，
    对"刚推完就查"不够稳，所以统一改成指数退避：默认 8 次、5s 起、60s 封顶（约 5 分钟）。

    只在版本/大小不匹配或网络错误时重试；参数可由 CLI 覆盖，方便本地快速复现。
    """
    last_error: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "xgesture-Release-Bot", "Cache-Control": "no-cache"},
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                data = json.loads(resp.read().decode("utf-8"))
                remote_version = data.get("version")
                remote_size = int(data.get("apkSize", 0))
                if remote_version != version:
                    raise ValueError(
                        f"Remote version mismatch at {url}: {remote_version} (expected {version})"
                    )
                if remote_size != apk_size:
                    raise ValueError(
                        f"Remote apkSize mismatch at {url}: {remote_size} (expected {apk_size})"
                    )
                if attempt > 1:
                    print(
                        f"Verified {url} on attempt {attempt}/{attempts} "
                        f"(version={remote_version}, apkSize={remote_size})"
                    )
                else:
                    print(f"Verified {url} (version={remote_version}, apkSize={remote_size})")
                return
        except Exception as e:
            last_error = e
            if attempt < attempts:
                delay = min(initial_delay_sec * (backoff ** (attempt - 1)), max_delay_sec)
                print(
                    f"WARNING: Verify attempt {attempt}/{attempts} failed for {url}: {e}; "
                    f"retrying in {delay:g}s",
                    file=sys.stderr,
                )
                time.sleep(delay)
    print(
        f"ERROR: Failed to verify {url} after {attempts} attempt(s): {last_error}",
        file=sys.stderr,
    )
    raise last_error  # type: ignore[misc]


def verify_remote(
    version: str,
    apk_size: int,
    repo: str = "qpst4/XGesture",
    attempts: int = 8,
    initial_delay_sec: float = 5.0,
    max_delay_sec: float = 60.0,
) -> None:
    raw_url = f"https://raw.githubusercontent.com/{repo}/main/update.json"
    jsdelivr_url = f"https://cdn.jsdelivr.net/gh/{repo}@main/update.json"
    for url in (raw_url, jsdelivr_url):
        verify_remote_url(
            url,
            version=version,
            apk_size=apk_size,
            attempts=attempts,
            initial_delay_sec=initial_delay_sec,
            max_delay_sec=max_delay_sec,
        )


def main():
    parser = argparse.ArgumentParser(description="Update update.json manifest.")
    parser.add_argument("--version", "-v", required=True, help="Release version (e.g. 1.9.8.2)")
    parser.add_argument("--version-code", type=int, help="Version code integer (default: parse from build.gradle.kts)")
    parser.add_argument("--apk-size", type=int, help="Exact byte size of lite release APK")
    parser.add_argument("--apk-file", help="Path to lite release APK to calculate size automatically")
    parser.add_argument("--apk-file-name", default="", help="Custom APK file name in release URL")
    parser.add_argument("--notes", default="", help="Custom update notes text")
    parser.add_argument("--notes-file", default="", help="File containing update notes text")
    parser.add_argument("--notes-en", default="", help="Custom English update notes text (notesEn)")
    parser.add_argument("--notes-en-file", default="", help="File containing English update notes text")
    parser.add_argument("--from-changelog", action="store_true", default=True, help="Generate notes from CHANGELOG.md")
    parser.add_argument("--max-items-per-group", type=int, default=8, help="Max items per changelog group")
    parser.add_argument("--changelog", default="CHANGELOG.md", help="Path to CHANGELOG.md")
    parser.add_argument(
        "--changelog-en",
        nargs="?",
        const="",
        default="CHANGELOG.en.md",
        help="Path to CHANGELOG.en.md for notesEn (use --changelog-en= to omit English notes)",
    )
    parser.add_argument(
        "--require-notes-en",
        action="store_true",
        help="Fail when no English notes can be resolved (use on releases)",
    )
    parser.add_argument("--manifest", default="update.json", help="Path to update.json")
    parser.add_argument("--gradle-file", default="app/build.gradle.kts", help="Path to build.gradle.kts")
    parser.add_argument("--repo", default="qpst4/XGesture", help="GitHub repo in owner/name format")
    parser.add_argument("--purge-jsdelivr", action="store_true", help="Purge jsDelivr cache")
    parser.add_argument("--purge-only", action="store_true", help="Only purge jsDelivr cache and exit")
    parser.add_argument("--verify-remote", action="store_true", help="Verify raw & jsDelivr remote manifests")
    parser.add_argument("--verify-only", action="store_true", help="Only verify remote manifests and exit")
    parser.add_argument(
        "--verify-attempts",
        type=int,
        default=8,
        help="Attempts for remote manifest verification (default: 8, exponential backoff)",
    )
    parser.add_argument(
        "--verify-initial-delay",
        type=float,
        default=5.0,
        help="Initial retry delay in seconds for remote verification (default: 5)",
    )
    parser.add_argument(
        "--verify-max-delay",
        type=float,
        default=60.0,
        help="Max retry delay in seconds for remote verification (default: 60)",
    )

    args = parser.parse_args()

    v = args.version.lstrip("v")

    if args.purge_only:
        purge_jsdelivr(repo=args.repo)
        return

    if args.verify_only:
        apk_size = args.apk_size
        if apk_size is None and args.apk_file:
            apk_path = Path(args.apk_file)
            if not apk_path.is_file():
                print(f"ERROR: APK file not found: {apk_path}", file=sys.stderr)
                sys.exit(1)
            apk_size = apk_path.stat().st_size
        if apk_size is None or apk_size <= 0:
            print("ERROR: --apk-size or valid --apk-file must be provided (> 0).", file=sys.stderr)
            sys.exit(1)
        verify_remote(
            version=v,
            apk_size=apk_size,
            repo=args.repo,
            attempts=args.verify_attempts,
            initial_delay_sec=args.verify_initial_delay,
            max_delay_sec=args.verify_max_delay,
        )
        return

    # Resolve APK size
    apk_size = args.apk_size
    if apk_size is None and args.apk_file:
        apk_path = Path(args.apk_file)
        if not apk_path.is_file():
            print(f"ERROR: APK file not found: {apk_path}", file=sys.stderr)
            sys.exit(1)
        apk_size = apk_path.stat().st_size

    if apk_size is None or apk_size <= 0:
        print("ERROR: --apk-size or valid --apk-file must be provided (> 0).", file=sys.stderr)
        sys.exit(1)

    # Resolve versionCode
    version_code = args.version_code
    if version_code is None:
        version_code = parse_version_code_from_gradle(Path(args.gradle_file))

    # Resolve notes (Chinese; the in-app dialog shows these to zh locales)
    if args.notes_file:
        resolved_notes = Path(args.notes_file).read_text(encoding="utf-8").strip()
    elif args.notes:
        resolved_notes = args.notes.replace("；", "\n").strip()
    else:
        try:
            resolved_notes = get_changelog_bullet_notes(
                version=v,
                changelog_path=Path(args.changelog),
                max_items_per_group=args.max_items_per_group,
                header_to_title=ZH_HEADER_TITLES,
            )
        except (ValueError, OSError) as e:
            print(f"ERROR: {e}", file=sys.stderr)
            sys.exit(1)

    if not resolved_notes:
        print("ERROR: Resolved update notes are empty.", file=sys.stderr)
        sys.exit(1)

    # Resolve English notes (notesEn). Non-Chinese locales prefer these and fall back to
    # `notes`, so a missing English section degrades gracefully instead of breaking the release.
    resolved_notes_en = ""
    if args.notes_en_file:
        resolved_notes_en = Path(args.notes_en_file).read_text(encoding="utf-8").strip()
    elif args.notes_en:
        resolved_notes_en = args.notes_en.replace(";", "\n").strip()
    elif args.changelog_en:
        changelog_en_path = Path(args.changelog_en)
        if changelog_en_path.is_file():
            try:
                resolved_notes_en = get_changelog_bullet_notes(
                    version=v,
                    changelog_path=changelog_en_path,
                    max_items_per_group=args.max_items_per_group,
                    header_to_title=EN_HEADER_TITLES,
                )
            except ValueError as e:
                # Missing/unreadable English section is not fatal on its own; --require-notes-en
                # below turns it into a hard failure with a readable message.
                print(f"WARNING: {e}", file=sys.stderr)
        else:
            print(f"WARNING: {changelog_en_path} not found; notesEn will be omitted.", file=sys.stderr)

    if args.require_notes_en and not resolved_notes_en:
        print(
            f"ERROR: No English notes for {v}. Add a '## [{v}]' section to {args.changelog_en} "
            f"or pass --notes-en-file.",
            file=sys.stderr,
        )
        sys.exit(1)

    apk_file_name = args.apk_file_name or f"xgesture-{v}-lite.apk"
    apk_url = f"https://github.com/{args.repo}/releases/download/v{v}/{apk_file_name}"

    manifest_data = {
        "version": v,
        "versionCode": version_code,
        "apkUrl": apk_url,
        "apkSize": apk_size,
        "notes": resolved_notes,
    }
    if resolved_notes_en:
        manifest_data["notesEn"] = resolved_notes_en

    manifest_path = Path(args.manifest)
    manifest_path.write_text(
        json.dumps(manifest_data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"Updated {manifest_path}:")
    print(json.dumps(manifest_data, ensure_ascii=False, indent=2))

    if args.purge_jsdelivr:
        purge_jsdelivr(repo=args.repo)

    if args.verify_remote:
        verify_remote(
            version=v,
            apk_size=apk_size,
            repo=args.repo,
            attempts=args.verify_attempts,
            initial_delay_sec=args.verify_initial_delay,
            max_delay_sec=args.verify_max_delay,
        )


if __name__ == "__main__":
    main()
