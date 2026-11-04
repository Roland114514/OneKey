#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gemini API 命令行调用工具 (Python 3 零外部依赖 / 兼备 google-genai SDK)
支持单次提问、代码分析、上下文文件传入、标准输入管道以及多轮交互会话 (REPL)。
"""

import os
import sys
import ssl
import json
import argparse
import urllib.request
import urllib.error
import urllib.parse
from pathlib import Path

DEFAULT_MODEL = "gemini-3.8-flash"
API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"


def get_ssl_context():
    """获取 SSL 上下文，优先使用 certifi 根证书解决 macOS Python 证书缺失问题"""
    try:
        import certifi
        return ssl.create_default_context(cafile=certifi.where())
    except Exception:
        pass
    try:
        return ssl.create_default_context()
    except Exception:
        return ssl._create_unverified_context()


def load_env_file(env_path: Path):
    """从 .env 文件中加载环境变量（不覆盖已存在的环境变量）"""
    if not env_path.is_file():
        return
    try:
        with open(env_path, "r", encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                k, v = line.split("=", 1)
                k = k.strip()
                v = v.strip().strip("'\"")
                if k not in os.environ and v:
                    os.environ[k] = v
    except Exception:
        pass


def find_and_load_env():
    """按优先级搜寻并加载 .env 文件"""
    # 1. 脚本所在目录及父级目录
    curr = Path(__file__).resolve().parent
    for _ in range(3):
        env_file = curr / ".env"
        if env_file.is_file():
            load_env_file(env_file)
            break
        curr = curr.parent

    # 2. 当前工作目录
    cwd_env = Path.cwd() / ".env"
    if cwd_env.is_file():
        load_env_file(cwd_env)


def get_api_key(args_key: str = None) -> str:
    """获取 API Key，优先级：CLI 参数 > 环境变量 GEMINI_API_KEY > 用户级配置"""
    if args_key:
        return args_key
    
    key = os.environ.get("GEMINI_API_KEY")
    if key:
        return key.strip()

    # 用户主目录备选配置 ~/.config/gemini/key
    home_key_file = Path.home() / ".config" / "gemini" / "key"
    if home_key_file.is_file():
        try:
            return home_key_file.read_text(encoding="utf-8").strip()
        except Exception:
            pass

    return ""


def call_gemini_rest(
    api_key: str,
    contents: list,
    model: str = DEFAULT_MODEL,
    system_instruction: str = None,
    temperature: float = None,
    max_output_tokens: int = None,
    stream: bool = False
) -> dict:
    """
    通过 REST API 调用 Gemini generateContent。
    支持纯 Python 3 标准库，无需额外 pip 依赖。
    """
    url = f"{API_BASE_URL}/{model}:generateContent?key={urllib.parse.quote(api_key)}"

    generation_config = {}
    if temperature is not None:
        generation_config["temperature"] = float(temperature)
    if max_output_tokens is not None:
        generation_config["maxOutputTokens"] = int(max_output_tokens)

    payload = {
        "contents": contents
    }

    if system_instruction:
        payload["system_instruction"] = {
            "parts": [{"text": system_instruction}]
        }

    if generation_config:
        payload["generationConfig"] = generation_config

    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json"}
    )

    try:
        ctx = get_ssl_context()
        with urllib.request.urlopen(req, context=ctx, timeout=120) as resp:
            body = resp.read().decode("utf-8")
            return json.loads(body)
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8", errors="replace")
        try:
            err_json = json.loads(err_body)
            msg = err_json.get("error", {}).get("message", err_body)
        except Exception:
            msg = err_body
        raise RuntimeError(f"HTTP {e.code}: {msg}")
    except urllib.error.URLError as e:
        raise RuntimeError(f"网络连接错误: {e.reason}")


def extract_text(response_json: dict) -> str:
    """提取响应中的正文文本"""
    candidates = response_json.get("candidates", [])
    if not candidates:
        if "error" in response_json:
            raise RuntimeError(response_json["error"].get("message", str(response_json)))
        return ""
    
    first = candidates[0]
    parts = first.get("content", {}).get("parts", [])
    texts = [p.get("text", "") for p in parts if "text" in p]
    return "".join(texts)


def run_interactive(api_key: str, model: str, system_instruction: str = None):
    """多轮交互会话模式 (REPL)"""
    print(f"=== Gemini 交互式终端 (模型: {model}) ===")
    print("输入你的问题，按回车发送。输入 'exit' 或 'quit' 退出，输入 'clear' 清空上下文。\n")

    history = []
    
    while True:
        try:
            user_input = input("You > ").strip()
        except (KeyboardInterrupt, EOFError):
            print("\n已退出。")
            break

        if not user_input:
            continue
        if user_input.lower() in ("exit", "quit"):
            print("已退出。")
            break
        if user_input.lower() == "clear":
            history = []
            print("[上下文已清空]\n")
            continue

        # 追加用户消息
        history.append({
            "role": "user",
            "parts": [{"text": user_input}]
        })

        print("Gemini > ", end="", flush=True)
        try:
            resp = call_gemini_rest(
                api_key=api_key,
                contents=history,
                model=model,
                system_instruction=system_instruction
            )
            reply_text = extract_text(resp)
            print(reply_text)
            print()

            # 追加模型回复
            history.append({
                "role": "model",
                "parts": [{"text": reply_text}]
            })
        except Exception as e:
            print(f"[错误] {e}\n")
            # 移除未成功回复的问题
            if history and history[-1]["role"] == "user":
                history.pop()


def main():
    find_and_load_env()

    parser = argparse.ArgumentParser(
        description="Gemini API 命令行调用工具",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""示例:
  python3 scripts/gemini_api.py "解释一下 Minecraft 26.3 的渲染管线变化"
  python3 scripts/gemini_api.py -f fabric/build.gradle "分析一下这个构建脚本"
  cat crash.log | python3 scripts/gemini_api.py "分析崩溃原因"
  python3 scripts/gemini_api.py -i
"""
    )
    parser.add_argument("prompt", nargs="?", default="", help="提问提示词（留空且非管道输入时默认进入交互模式）")
    parser.add_argument("-m", "--model", default=os.environ.get("GEMINI_MODEL", DEFAULT_MODEL), help=f"模型名称 (默认: {DEFAULT_MODEL})")
    parser.add_argument("-k", "--key", default=None, help="Gemini API Key (默认从环境变量 GEMINI_API_KEY 或 .env 中读取)")
    parser.add_argument("-s", "--system", default=None, help="系统指令 (System Instruction)")
    parser.add_argument("-f", "--file", action="append", default=[], help="附加文件内容作为上下文 (可多次指定)")
    parser.add_argument("-i", "--interactive", action="store_true", help="进入多轮交互模式")
    parser.add_argument("-t", "--temperature", type=float, default=None, help="采样温度 (0.0 - 2.0)")
    parser.add_argument("--max-tokens", type=int, default=None, help="最大输出 Token 数")
    parser.add_argument("-o", "--output", default=None, help="将结果输出到指定文件")
    parser.add_argument("--json", action="store_true", help="输出完整原始 JSON 响应")

    args = parser.parse_args()

    api_key = get_api_key(args.key)
    if not api_key:
        print("[错误] 未找到 Gemini API Key！", file=sys.stderr)
        print("请在 .env 中设置 GEMINI_API_KEY，或设置环境变量 export GEMINI_API_KEY='你的Key'，或通过 -k 参数传入。", file=sys.stderr)
        sys.exit(1)

    # 判断是否进入交互模式
    if args.interactive or (not args.prompt and sys.stdin.isatty() and not args.file):
        run_interactive(api_key=api_key, model=args.model, system_instruction=args.system)
        return

    # 聚合输入内容
    parts = []

    # 1. 附加文件
    for fpath in args.file:
        p = Path(fpath)
        if not p.is_file():
            print(f"[警告] 文件不存在: {fpath}", file=sys.stderr)
            continue
        try:
            content = p.read_text(encoding="utf-8", errors="replace")
            parts.append({"text": f"--- 文件内容 [{p.name}] ---\n{content}\n--- 文件结束 ---\n"})
        except Exception as e:
            print(f"[警告] 读取文件 {fpath} 失败: {e}", file=sys.stderr)

    # 2. 管道输入
    if not sys.stdin.isatty():
        stdin_content = sys.stdin.read()
        if stdin_content:
            parts.append({"text": f"--- 输入数据/日志 ---\n{stdin_content}\n--- 数据结束 ---\n"})

    # 3. 命令行提示词
    if args.prompt:
        parts.append({"text": args.prompt})

    if not parts:
        print("[错误] 没有提供任何提问内容或文件。", file=sys.stderr)
        parser.print_help()
        sys.exit(1)

    contents = [{"role": "user", "parts": parts}]

    try:
        resp = call_gemini_rest(
            api_key=api_key,
            contents=contents,
            model=args.model,
            system_instruction=args.system,
            temperature=args.temperature,
            max_output_tokens=args.max_tokens
        )
    except Exception as e:
        print(f"[错误] 调用 Gemini API 失败: {e}", file=sys.stderr)
        sys.exit(1)

    if args.json:
        result_text = json.dumps(resp, ensure_ascii=False, indent=2)
    else:
        result_text = extract_text(resp)

    if args.output:
        try:
            with open(args.output, "w", encoding="utf-8") as out_f:
                out_f.write(result_text + "\n")
            print(f"[成功] 结果已保存至 {args.output}")
        except Exception as e:
            print(f"[错误] 写入文件失败: {e}", file=sys.stderr)
    else:
        print(result_text)


if __name__ == "__main__":
    main()
