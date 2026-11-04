#!/usr/bin/env python3
"""把 OneKey 的构建产物发布到 CurseForge。

用法（Project ID 是 CurseForge 项目页 URL 里的数字；API Token 从环境变量或 .env 读取）：
    CURSEFORGE_PROJECT_ID=1234567 CURSEFORGE_TOKEN=xxxx \
        python3 scripts/publish_curseforge.py \
        --version 1.0.3 --loader fabric \
        --file fabric/build/libs/onekey-fabric-1.0.3.jar

说明：
- CurseForge 的 Game Version（26.3）与 Mod Loader（Fabric / NeoForge）是**文件级**字段，
  一次上传一个主 jar，主 jar 与 sources jar 分开上传（sources 用 --release-type beta 或不上传）；
- 上传接口：POST https://minecraft.curseforge.com/api/projects/{projectId}/upload
  （multipart：`metadata` = JSON 字符串，`file` = 文件本体）；
- metadata 支持 `changelog` / `changelogType` / `releaseType` / `gameVersions` / `displayName`；
- 同一文件的重复上传会被 CurseForge 拒绝（400），脚本会先打印提示再继续，由调用方判断。
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

UPLOAD_API = "https://minecraft.curseforge.com/api/projects/{project_id}/upload"
ROOT = Path(__file__).resolve().parent.parent

LOADER_LABELS = {
    "fabric": "Fabric",
    "neoforge": "NeoForge",
}


def load_env_file() -> None:
    """从项目根目录 .env 读取 CURSEFORGE_TOKEN / CURSEFORGE_PROJECT_ID（不覆盖已有环境变量）。"""
    env_path = ROOT / ".env"
    if not env_path.is_file():
        return
    for raw in env_path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        key, value = key.strip(), value.strip().strip('"').strip("'")
        if key and value and key not in os.environ:
            os.environ[key] = value


def extract_changelog(version: str, changelog_file: Path | None) -> str:
    if changelog_file is not None:
        return changelog_file.read_text(encoding="utf-8")

    changelog_path = ROOT / "CHANGELOG.md"
    if not changelog_path.is_file():
        return f"OneKey {version}"

    text = changelog_path.read_text(encoding="utf-8")
    marker = f"## [{version}]"
    start = text.find(marker)
    if start < 0:
        return f"OneKey {version}"
    body_start = text.find("\n", start) + 1
    next_section = text.find("\n## [", body_start)
    return text[body_start: next_section if next_section > 0 else len(text)].strip()


def build_multipart(metadata: dict, file_path: Path, boundary: str) -> bytes:
    parts: list[bytes] = []
    parts.append(f"--{boundary}\r\n".encode())
    parts.append(b'Content-Disposition: form-data; name="metadata"\r\n')
    parts.append(b"Content-Type: application/json\r\n\r\n")
    parts.append(json.dumps(metadata, ensure_ascii=False).encode("utf-8"))
    parts.append(f"\r\n--{boundary}\r\n".encode())
    parts.append(
        f'Content-Disposition: form-data; name="file"; filename="{file_path.name}"\r\n'.encode()
    )
    parts.append(b"Content-Type: application/java-archive\r\n\r\n")
    parts.append(file_path.read_bytes())
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    return b"".join(parts)


def upload(project_id: str, token: str, metadata: dict, file_path: Path) -> dict:
    boundary = f"----OneKeyBoundary{uuid.uuid4().hex}"
    body = build_multipart(metadata, file_path, boundary)
    request = urllib.request.Request(
        UPLOAD_API.format(project_id=project_id),
        data=body,
        method="POST",
        headers={
            "X-Api-Token": token,
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "Accept": "application/json",
            "User-Agent": "OneKey-publisher/1.0",
        },
    )
    with urllib.request.urlopen(request, timeout=180) as response:
        raw = response.read().decode("utf-8", errors="replace")
        return json.loads(raw) if raw.strip() else {}


def main() -> int:
    load_env_file()

    parser = argparse.ArgumentParser(description="发布 OneKey 到 CurseForge")
    parser.add_argument("--project", default=os.environ.get("CURSEFORGE_PROJECT_ID"),
                        help="CurseForge 数字 Project ID（默认取 CURSEFORGE_PROJECT_ID）")
    parser.add_argument("--version", required=True, help="模组版本，如 1.0.3")
    parser.add_argument("--loader", required=True, choices=sorted(LOADER_LABELS),
                        help="目标加载器；决定 gameVersions 里的 loader 名")
    parser.add_argument("--file", required=True, type=Path, action="append",
                        help="要上传的 jar（可重复）")
    parser.add_argument("--game-version", default="26.3", help="Minecraft 版本，默认 26.3")
    parser.add_argument("--release-type", default="release", choices=["release", "beta", "alpha"])
    parser.add_argument("--changelog-file", type=Path, default=None,
                        help="自定义 changelog 文件；缺省从 CHANGELOG.md 抽取对应小节")
    parser.add_argument("--display-name", default=None, help="文件显示名")
    parser.add_argument("--dry-run", action="store_true", help="只打印将要提交的 metadata")

    args = parser.parse_args()

    token = os.environ.get("CURSEFORGE_TOKEN") or os.environ.get("CF_API_TOKEN")
    if not token:
        print("❌ 缺少 CURSEFORGE_TOKEN：请在环境变量或项目根 .env 里配置 CurseForge API Token。", file=sys.stderr)
        return 2
    if not args.project:
        print("❌ 缺少 --project（CurseForge 数字 Project ID）：项目需先在网页创建。", file=sys.stderr)
        return 2

    files = args.file if isinstance(args.file, list) else [args.file]
    missing = [str(f) for f in files if not f.is_file()]
    if missing:
        print(f"❌ 找不到文件：{missing}", file=sys.stderr)
        return 2

    changelog = extract_changelog(args.version, args.changelog_file)
    display_name = args.display_name or f"OneKey {args.version} [{LOADER_LABELS[args.loader]}]"
    metadata = {
        "changelog": changelog,
        "changelogType": "markdown",
        "releaseType": args.release_type,
        "displayName": display_name,
        "gameVersions": [args.game_version, LOADER_LABELS[args.loader]],
    }

    if args.dry_run:
        print("DRY-RUN metadata:")
        print(json.dumps(metadata, ensure_ascii=False, indent=2))
        for file_path in files:
            print(f"  would upload {file_path} ({file_path.stat().st_size} B)")
        return 0

    for file_path in files:
        print(f"→ 上传 {file_path.name}（{file_path.stat().st_size} B，{LOADER_LABELS[args.loader]}）…")
        try:
            result = upload(args.project, token, metadata, file_path)
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")[:500]
            print(f"❌ HTTP {error.code}: {body}", file=sys.stderr)
            return 1
        file_id = result.get("id")
        print(f"✅ fileId={file_id} name={result.get('displayName')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
