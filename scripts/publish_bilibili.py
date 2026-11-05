#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
OneKey B 站专栏草稿创建与动态发布执行脚本（及其猫专供）
"""

import sys
import json
import urllib.parse
from pathlib import Path
import requests

ROOT_DIR = Path(__file__).resolve().parent.parent
COOKIE_FILE = ROOT_DIR / ".bilibili_cookies.json"

if not COOKIE_FILE.is_file():
    print("❌ 未检测到登录凭据，请先运行: python3 scripts/bilibili_tool.py login", file=sys.stderr)
    sys.exit(1)

with open(COOKIE_FILE, "r", encoding="utf-8") as f:
    cookies = json.load(f)

csrf = cookies.get("bili_jct")
if not csrf:
    print("❌ Cookie 中缺少 bili_jct (csrf)，请重新登录", file=sys.stderr)
    sys.exit(1)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
        "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    ),
    "Referer": "https://member.bilibili.com/platform/upload/text/edit",
    "Origin": "https://member.bilibili.com",
}


def build_article_html():
    return """
<h2>1. 模组简介</h2>
<p>OneKey 是一款面向 Minecraft 26.3（Java 25）的轻量化操作与交互优化模组，由 <strong>@25时的奇迹</strong>、<strong>大肥鱼（DeepSeek）</strong> 与 <strong>及其猫（Gemini）</strong> 共同协作开发完成。</p>
<p>模组原生支持 Fabric 与 NeoForge 双加载器，不依赖任何第三方运行时前置库，体积仅 170 KB 左右，旨在用最少的技术侵入，为玩家提供符合原版直觉的连锁挖掘、背包整理与鼠标手势体验。</p>
<hr/>

<h2>2. 核心功能</h2>

<h3>原生连锁破坏（Vein Mining）</h3>
<ul>
  <li><strong>操作方式</strong>：按住 <code>~</code>（波浪号）键挖掘方块即可触发连锁；按住 <code>~</code> 键的同时滑动鼠标滚轮，可在三种形态之间循环切换：
    <ul>
      <li><strong>同类连通 (Vein)</strong>：经典的 26 邻域矿脉与原木扩散，砍树时默认不破坏树叶；</li>
      <li><strong>1x2 矿道</strong>：按准星朝向向前开拓两格高的标准矿道；</li>
      <li><strong>3x3 隧道</strong>：大截面隧道与采石场挖掘。</li>
    </ul>
  </li>
  <li><strong>HUD 实时反馈</strong>：准星对准方块时，屏幕左上角显示半透明面板，实时显示当前挖掘模式、目标方块、预计破坏方块数以及工具就绪状态。</li>
  <li><strong>掉落物与经验球内存聚合</strong>：连锁破坏产生的全部掉落物会在起始点自动合并为满组吐出，经验球合并掉落，避免大量散落实体引起掉帧卡顿。</li>
  <li><strong>工具防爆保护</strong>：当主手工具剩余耐久 ≤ 2 时，连锁会自动刹车中止，防止爆工具；若工具中途损坏变空，也不会空手继续连锁。</li>
  <li><strong>安全闸门与容器保护</strong>：必须持有等级匹配的工具才允许连锁（防空手作弊）；自动过滤箱子、木桶、潜影盒等所有带容器的方块，避免误伤仓库。</li>
</ul>

<h3>鼠标交互手势</h3>
<ul>
  <li><strong>Shift 扫掠转移</strong>：按住 Shift + 鼠标左键划过背包槽位，可连续将划过的整组物品快速搬运至对侧容器；</li>
  <li><strong>右键重复放置</strong>：持物时光标按住右键拖拽划圈，多次经过同一格可连续投入物品；</li>
  <li><strong>滚轮微调</strong>：鼠标悬停在物品上，向上/向下滚动滚轮单件进出；光标抓着整组物品向下滚轮，向目标槽位精准放入 1 件持物（适配 macOS 触控板自然滚动）。</li>
</ul>

<h3>智能整理与槽位锁定</h3>
<ul>
  <li><strong>R 键整理</strong>：支持玩家背包与箱子/木桶/潜影盒/末影箱等存储容器，整理时先自动合并散落在各格的同类堆叠，再按「分类 → 名称 → 数量」进行排序；</li>
  <li><strong>槽位锁定</strong>：按住 <code>Alt + 鼠标左键</code> 锁定关键槽位（支持在原版「控制 → 按键绑定」中自定义组合键与主键）。加锁的槽位在整理与存入时均保持不动。</li>
</ul>

<h3>快速堆叠与智能存入</h3>
<ul>
  <li><strong>V 键快速堆叠</strong>：扫描周围容器，将背包内物品快速并入附近箱子已有的相同堆叠中；</li>
  <li><strong>B 键准星存入</strong>：准星看哪只箱子就存入哪只，只存入该箱子已有的分类物品，绝不占用无关联的空槽位，防止分类箱被杂物污染。</li>
</ul>

<h3>配置界面</h3>
<p>游戏内随时按 <code>K</code> 键可呼出全中文配置菜单，支持对整理、堆叠、手势、连锁四大功能进行独立微调或一键恢复默认。</p>
<hr/>

<h2>3. 联机与服务端特性</h2>
<ul>
  <li><strong>服务端权限优先</strong>：多人联机中，连锁破坏完整遵循原版出生点保护与领地插件权限，严禁越界破坏；</li>
  <li><strong>个性化偏好交集</strong>：客户端状态包随身同步个人偏好（如关闭连锁或调小破坏上限），服务端与服主配置取交集生效，单人与联机体验一致。</li>
</ul>
<hr/>

<h2>4. 环境要求与下载</h2>
<ul>
  <li><strong>游戏版本</strong>：Minecraft 26.3</li>
  <li><strong>运行环境</strong>：Java 25</li>
  <li><strong>加载器支持</strong>：Fabric Loader ≥ 0.20.1 + Fabric API / NeoForge 26.3.0.101+</li>
  <li><strong>开源仓库与发布地址</strong>：<a href="https://github.com/Roland114514/OneKey/releases/tag/v1.1.3">https://github.com/Roland114514/OneKey/releases/tag/v1.1.3</a></li>
</ul>
"""


def create_article_draft():
    url = "https://api.bilibili.com/x/article/creative/draft/addupdate"
    title = "【MC 26.3】OneKey v1.1.3：原生连锁破坏、鼠标手势与智能整理模组"
    content = build_article_html()
    summary = "面向 Minecraft 26.3 的轻量操作优化模组，由 25时的奇迹、大肥鱼（DeepSeek）与 及其猫（Gemini）共创开发，原生支持连锁破坏、鼠标手势与智能整理。"

    data = {
        "title": title,
        "content": content,
        "summary": summary,
        "category": 1,  # 游戏
        "tid": 6,       # 单机游戏
        "reprint": 0,
        "original": 1,
        "tags": "我的世界,Minecraft,模组,Java版,游戏推荐",
        "csrf": csrf
    }

    print("正在保存 B 站专栏草稿...")
    res = requests.post(url, headers=HEADERS, cookies=cookies, data=data, timeout=15)
    r = res.json()
    if r.get("code") == 0:
        aid = r.get("data", {}).get("aid")
        print(f"✅ 专栏草稿保存成功！草稿编号 (aid): {aid}")
        edit_url = f"https://member.bilibili.com/platform/upload/text/edit?aid={aid}"
        print(f"👉 草稿编辑与确认链接: {edit_url}")
        return aid, edit_url
    else:
        print(f"❌ 专栏草稿保存失败: {r.get('message')} (code: {r.get('code')})", file=sys.stderr)
        return None, None


def publish_dynamic(article_url=None):
    url = f"https://api.bilibili.com/x/dynamic/feed/create/dyn?csrf={csrf}"
    headers = dict(HEADERS)
    headers["Content-Type"] = "application/json"
    headers["Referer"] = "https://t.bilibili.com/"
    headers["Origin"] = "https://t.bilibili.com"

    article_text = f"\n详细功能与专栏介绍：{article_url}" if article_url else ""

    text = f"""这是由 @25时的奇迹 、大肥鱼（DeepSeek）和 及其猫（Gemini）协同开发的 Minecraft 操作优化模组 —— OneKey 的 v1.1.3 版本。

模组适配 Minecraft 26.3（Java 25），同时原生支持 Fabric 与 NeoForge 双端，纯 Java 编写且零第三方运行时依赖。

本次 1.1.3 版本重点加入了原生【连锁破坏 (Vein Mining)】功能：
- 按住 ~ 键挖掘即可触发连锁；
- 按住 ~ 键滑动滚轮，可在「同类连通」、「1x2 矿道」、「3x3 隧道」之间实时切换；
- 屏幕左上角带半透明 HUD 实时预览；
- 砍树默认不连带树叶，掉落物与经验球在破坏点自动合并成组生成，并自带严格的工具防爆保护（耐久≤2 刹车）与容器保护。

同时集成了此前版本的鼠标手势（Shift 扫掠、持物滚轮微调）、一键整理（R 键）、槽位锁定（支持自定义按键）与智能存入（V/B 键）。{article_text}

下载与开源仓库：https://github.com/Roland114514/OneKey/releases/tag/v1.1.3"""

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

    print("正在发布 B 站动态...")
    res = requests.post(url, headers=headers, cookies=cookies, json=payload, timeout=15)
    r = res.json()
    if r.get("code") == 0:
        dyn_id = r.get("data", {}).get("dyn_id_str")
        dyn_url = f"https://t.bilibili.com/{dyn_id}"
        print(f"🎉 动态发布成功！")
        print(f"动态 ID: {dyn_id}")
        print(f"动态直达链接: {dyn_url}")
        return dyn_id, dyn_url
    else:
        print(f"❌ 动态发布失败: {r.get('message')} (code: {r.get('code')})", file=sys.stderr)
        return None, None


if __name__ == "__main__":
    aid, edit_url = create_article_draft()
    # 如果草稿创建成功，把专栏草稿编辑链接或专栏地址带给用户
    # 注意：草稿在发布前公众还无法直接访问阅读页面，通常需要用户在创作中心点击一下「发布」或补充封面
    # 先把草稿创建好
