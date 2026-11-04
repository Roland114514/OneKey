#!/usr/bin/env python3
"""把 OneKey 的构建产物发布到 Modrinth。

用法（token 从环境变量或项目根目录的 .env 读取，绝不写进仓库）：
    MODRINTH_TOKEN=xxxx python3 scripts/publish_modrinth.py \
        --project onekey --version 1.0.2 --loader fabric \
        --file fabric/build/libs/onekey-fabric-1.0.2.jar \
        --file fabric/build/libs/onekey-fabric-1.0.2-sources.jar

说明：
- Modrinth 的 loader 是「版本级」字段，所以 Fabric 与 NeoForge 各自建一个 version；
- 版本号用 `1.0.2-fabric` / `1.0.2-neoforge` 区分（同一项目内版本号必须唯一）；
- changelog 默认从 CHANGELOG.md 里抽 `## [<version>]` 小节。
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import sys
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://api.modrinth.com/v2"
ROOT = Path(__file__).resolve().parent.parent

# Fabric API 在 Modrinth 上的项目 id（Fabric 版本声明为必需前置）
FABRIC_API_PROJECT_ID = "P7dR8mSH"


def load_env() -> None:
    env_file = ROOT / ".env"
    if not env_file.exists():
        return
    for line in env_file.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip().strip("'\""))


def token() -> str:
    value = os.environ.get("MODRINTH_TOKEN", "").strip()
    if not value:
        sys.exit("缺少 MODRINTH_TOKEN：请设置环境变量，或写进项目根目录的 .env（该文件已被 gitignore）")
    return value


def request(method: str, url: str, *, token_value: str, body: bytes | None = None,
            content_type: str | None = None) -> dict:
    req = urllib.request.Request(url, data=body, method=method)
    req.add_header("Authorization", token_value)
    req.add_header("User-Agent", "OneKey-release-script/1.0 (github.com/Roland114514/OneKey)")
    if content_type:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        sys.exit(f"{method} {url} -> HTTP {exc.code}\n{detail}")


def extract_changelog(version: str) -> str:
    changelog = ROOT / "CHANGELOG.md"
    if not changelog.exists():
        return f"OneKey {version}"
    text = changelog.read_text(encoding="utf-8")
    match = re.search(rf"^## \[{re.escape(version)}\][^\n]*\n(.*?)(?=^## \[|\Z)", text, re.S | re.M)
    if not match:
        return f"OneKey {version}"
    return match.group(1).strip()


def multipart(fields: list[tuple[str, str | bytes, str | None]]) -> tuple[bytes, str]:
    boundary = f"----OneKey{uuid.uuid4().hex}"
    chunks: list[bytes] = []
    for name, value, filename in fields:
        chunks.append(f"--{boundary}\r\n".encode())
        if filename:
            content_type = mimetypes.guess_type(filename)[0] or "application/octet-stream"
            chunks.append(
                f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                f"Content-Type: {content_type}\r\n\r\n".encode()
            )
            chunks.append(value if isinstance(value, bytes) else value.encode())
            chunks.append(b"\r\n")
        else:
            chunks.append(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
            chunks.append(value if isinstance(value, bytes) else value.encode())
            chunks.append(b"\r\n")
    chunks.append(f"--{boundary}--\r\n".encode())
    return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True, help="项目 slug 或 id")
    parser.add_argument("--version", required=True, help="版本号，如 1.0.2")
    parser.add_argument("--loader", required=True, choices=["fabric", "neoforge"])
    parser.add_argument("--file", action="append", required=True, help="要上传的 jar（可重复，第一个作为主文件）")
    parser.add_argument("--game-version", default="26.3")
    parser.add_argument("--version-type", default="release", choices=["release", "beta", "alpha"])
    parser.add_argument("--changelog-file", default=None)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    load_env()
    files = [Path(p) for p in args.file]
    for path in files:
        if not path.exists():
            sys.exit(f"文件不存在：{path}")

    version_number = f"{args.version}-{args.loader}"
    changelog = (Path(args.changelog_file).read_text(encoding="utf-8")
                 if args.changelog_file else extract_changelog(args.version))

    dependencies = []
    if args.loader == "fabric":
        dependencies.append({"project_id": FABRIC_API_PROJECT_ID, "dependency_type": "required"})

    payload = {
        "name": f"OneKey {args.version} ({'Fabric' if args.loader == 'fabric' else 'NeoForge'})",
        "version_number": version_number,
        "changelog": changelog,
        "dependencies": dependencies,
        "game_versions": [args.game_version],
        "version_type": args.version_type,
        "loaders": [args.loader],
        "featured": False,
        "status": "listed",
        "project_id": args.project,
    }

    print(f"[modrinth] 项目={args.project} 版本号={version_number} loader={args.loader} 文件={[f.name for f in files]}")
    print(f"[modrinth] changelog {len(changelog)} 字符")
    if args.dry_run:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return

    token_value = token()
    project = request("GET", f"{API}/project/{args.project}", token_value=token_value)
    print(f"[modrinth] 目标项目：{project['title']} ({project['slug']})")

    existing = request("GET", f"{API}/project/{args.project}/version", token_value=token_value)
    if any(v["version_number"] == version_number for v in existing):
        sys.exit(f"项目里已存在版本 {version_number}，先手动删除旧版本再重发")

    fields: list[tuple[str, str | bytes, str | None]] = [
        ("data", json.dumps(payload, ensure_ascii=False), None),
    ]
    for index, path in enumerate(files):
        fields.append(("file", path.read_bytes(), f"{path.name}" if index else f"{path.name}"))

    body, content_type = multipart(fields)
    result = request("POST", f"{API}/version", token_value=token_value, body=body, content_type=content_type)
    print(f"[modrinth] 已创建版本 {result.get('version_number')} id={result.get('id')}")
    print(f"[modrinth] 链接：https://modrinth.com/mod/{project['slug']}/version/{result.get('id')}")


if __name__ == "__main__":
    main()
