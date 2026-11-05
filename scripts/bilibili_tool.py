#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Bilibili 操作助手（及其猫 · OneKey 专供版）
支持扫码登录、状态检查、动态发布与专栏草稿提交。
"""

import os
import sys
import json
import time
import subprocess
from pathlib import Path

try:
    import requests
    import qrcode
except ImportError:
    print("缺少依赖，请先运行: pip3 install requests qrcode pillow", file=sys.stderr)
    sys.exit(1)

ROOT_DIR = Path(__file__).resolve().parent.parent
COOKIE_FILE = ROOT_DIR / ".bilibili_cookies.json"
QR_IMAGE_FILE = ROOT_DIR / "bili_login_qr.png"

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    ),
    "Referer": "https://www.bilibili.com/",
    "Origin": "https://www.bilibili.com",
}


def load_cookies():
    if not COOKIE_FILE.is_file():
        return None
    try:
        with open(COOKIE_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def save_cookies(cookies: dict):
    with open(COOKIE_FILE, "w", encoding="utf-8") as f:
        json.dump(cookies, f, indent=2, ensure_ascii=False)
    # 限制文件权限
    try:
        os.chmod(COOKIE_FILE, 0o600)
    except Exception:
        pass


def check_login():
    cookies = load_cookies()
    if not cookies:
        return False, None
    url = "https://api.bilibili.com/x/web-interface/nav"
    try:
        res = requests.get(url, headers=HEADERS, cookies=cookies, timeout=10)
        data = res.json()
        if data.get("code") == 0 and data.get("data", {}).get("isLogin"):
            return True, data["data"]
        return False, data
    except Exception as e:
        return False, str(e)


def login():
    print("正在向 B 站申请扫码登录密钥...")
    gen_url = "https://passport.bilibili.com/x/passport-login/web/qrcode/generate"
    try:
        res = requests.get(gen_url, headers=HEADERS, timeout=10)
        data = res.json()
    except Exception as e:
        print(f"申请二维码失败: {e}", file=sys.stderr)
        return False

    if data.get("code") != 0:
        print(f"申请失败: {data.get('message')}", file=sys.stderr)
        return False

    qr_url = data["data"]["url"]
    qrcode_key = data["data"]["qrcode_key"]

    # 1. 保存 PNG 二维码图片
    qr = qrcode.QRCode(border=2)
    qr.add_data(qr_url)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    img.save(str(QR_IMAGE_FILE))
    print(f"二维码图片已保存至: {QR_IMAGE_FILE}")

    # 2. 如果是 macOS，自动弹窗打开预览图片
    if sys.platform == "darwin":
        try:
            subprocess.run(["open", str(QR_IMAGE_FILE)], check=False)
            print("已在屏幕上自动为您弹出二维码图片窗口，请使用手机 B 站 App 扫码！")
        except Exception:
            pass

    # 3. 在终端也打印一份 ASCII 字符二维码以防万一
    print("\n--- 终端字符二维码 ---")
    qr.print_ascii(invert=True)
    print("----------------------\n")
    print("请使用【哔哩哔哩手机 App】扫描二维码并确认登录...")

    poll_url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll"
    start_time = time.time()
    last_status = None

    while time.time() - start_time < 180:
        try:
            pres = requests.get(poll_url, params={"qrcode_key": qrcode_key}, headers=HEADERS, timeout=10)
            pdata = pres.json()
        except Exception:
            time.sleep(2)
            continue

        if pdata.get("code") != 0:
            print(f"轮询异常: {pdata.get('message')}")
            time.sleep(2)
            continue

        data_obj = pdata.get("data", {})
        sub_code = data_obj.get("code")

        if sub_code == 0:
            print("\n🎉 扫码登录成功！正在提取并保存安全凭据...")
            # 提取 cookie
            cookies = pres.cookies.get_dict()
            # 如果存在 refresh_token 也记录下来
            if "refresh_token" in data_obj:
                cookies["refresh_token"] = data_obj["refresh_token"]
            save_cookies(cookies)

            # 验证登录信息
            ok, user_data = check_login()
            if ok and user_data:
                uname = user_data.get("uname", "未知")
                mid = user_data.get("mid", 0)
                level = user_data.get("level_info", {}).get("current_level", 0)
                print(f"当前登录账号: 【{uname}】(UID: {mid}, Lv.{level})")
            else:
                print("凭据已保存，但验证账号信息未立即返回。")

            # 清理临时二维码图片
            try:
                if QR_IMAGE_FILE.exists():
                    QR_IMAGE_FILE.unlink()
            except Exception:
                pass
            return True

        elif sub_code == 86101:
            if last_status != 86101:
                print("状态: 等待扫码中...")
                last_status = 86101
        elif sub_code == 86090:
            if last_status != 86090:
                print("状态: 已扫码！请在手机上点击【确认登录】...")
                last_status = 86090
        elif sub_code == 86038:
            print("状态: 二维码已失效，请重新执行登录。")
            return False
        else:
            print(f"状态: {data_obj.get('message', '未知')} ({sub_code})")

        time.sleep(2)

    print("扫码超时（180秒），请重试。")
    return False


def post_dynamic(text: str):
    cookies = load_cookies()
    if not cookies or "bili_jct" not in cookies:
        print("未检测到有效登录态，请先登录: python3 scripts/bilibili_tool.py login", file=sys.stderr)
        return False

    csrf = cookies["bili_jct"]
    url = f"https://api.bilibili.com/x/dynamic/feed/create/dyn?csrf={csrf}"
    headers = dict(HEADERS)
    headers["Content-Type"] = "application/json"
    headers["Referer"] = "https://t.bilibili.com/"
    headers["Origin"] = "https://t.bilibili.com"

    payload = {
        "dyn_req": {
            "content": {
                "contents": [
                    {
                        "raw_text": text,
                        "type": 1,
                        "biz_id": ""
                    }
                ]
            },
            "scene": 2
        }
    }

    try:
        res = requests.post(url, headers=headers, cookies=cookies, json=payload, timeout=15)
        data = res.json()
        if data.get("code") == 0:
            dyn_id = data.get("data", {}).get("dyn_id_str")
            print(f"🎉 动态发布成功！")
            print(f"动态 ID: {dyn_id}")
            print(f"动态链接: https://t.bilibili.com/{dyn_id}")
            return True
        else:
            print(f"动态发布失败: {data.get('message')} (code: {data.get('code')})", file=sys.stderr)
            return False
    except Exception as e:
        print(f"请求失败: {e}", file=sys.stderr)
        return False


def save_article_draft(title: str, content_html: str, summary: str = "", tags: str = "我的世界,Minecraft,模组"):
    cookies = load_cookies()
    if not cookies or "bili_jct" not in cookies:
        print("未检测到有效登录态，请先登录: python3 scripts/bilibili_tool.py login", file=sys.stderr)
        return False

    csrf = cookies["bili_jct"]
    url = "https://api.bilibili.com/x/article/creative/draft/addupdate"
    headers = dict(HEADERS)
    headers["Content-Type"] = "application/x-www-form-urlencoded; charset=UTF-8"
    headers["Referer"] = "https://member.bilibili.com/platform/upload/text/edit"
    headers["Origin"] = "https://member.bilibili.com"

    data = {
        "title": title,
        "content": content_html,
        "summary": summary[:100] if summary else title,
        "category": 1,  # 游戏区
        "tid": 0,
        "reprint": 0,
        "original": 1,  # 原创
        "tags": tags,
        "csrf": csrf
    }

    try:
        res = requests.post(url, headers=headers, cookies=cookies, data=data, timeout=15)
        res_data = res.json()
        if res_data.get("code") == 0:
            aid = res_data.get("data", {}).get("aid")
            print(f"🎉 专栏草稿保存成功！")
            print(f"草稿编号 (aid): {aid}")
            print(f"草稿编辑与发布链接: https://member.bilibili.com/platform/upload/text/edit?aid={aid}")
            return aid
        else:
            print(f"专栏草稿保存失败: {res_data.get('message')} (code: {res_data.get('code')})", file=sys.stderr)
            return None
    except Exception as e:
        print(f"请求失败: {e}", file=sys.stderr)
        return None


def main():
    if len(sys.argv) < 2:
        print("用法: python3 scripts/bilibili_tool.py [login|status|dynamic|draft]")
        sys.exit(1)

    cmd = sys.argv[1].lower()
    if cmd == "login":
        login()
    elif cmd == "status":
        ok, user_data = check_login()
        if ok:
            uname = user_data.get("uname")
            mid = user_data.get("mid")
            level = user_data.get("level_info", {}).get("current_level")
            print(f"✅ 已登录: 【{uname}】(UID: {mid}, Lv.{level})")
        else:
            print("❌ 未登录或登录已失效")
            sys.exit(1)
    elif cmd == "dynamic":
        if len(sys.argv) < 3:
            print("用法: python3 scripts/bilibili_tool.py dynamic \"动态内容\"")
            sys.exit(1)
        post_dynamic(sys.argv[2])
    elif cmd == "draft":
        if len(sys.argv) < 4:
            print("用法: python3 scripts/bilibili_tool.py draft \"标题\" \"HTML内容\" [摘要]")
            sys.exit(1)
        summary = sys.argv[4] if len(sys.argv) > 4 else ""
        save_article_draft(sys.argv[2], sys.argv[3], summary)
    else:
        print(f"未知子命令: {cmd}")
        sys.exit(1)


if __name__ == "__main__":
    main()
