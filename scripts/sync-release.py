#!/usr/bin/env python3
"""release.toml 的同步与渲染器 —— 发版派生点的唯一执行者。

  inspect [--json]        读仓库实况（当前版本 / 各派生点现值 / 是否有 tag），供人与 agent 判断
  sync --write            把 release.toml 写到各派生点，打印改动清单
  sync --check            重渲染并比对工作树，不一致即非 0 退出（本地与 CI 同一把尺）
  notes [--version X]     渲染 GitHub Release Notes 正文（--out 落盘，默认 stdout）
  version                 只打印当前版本号（CI 取值用，别再 grep gradle.properties）
  assets                  按行打印当前版本的产物文件名（CI 齐全断言用）
  collect [--src --dst]   按 [[artifact]] 的 from_dir/from_glob 收集并改名产物，缺即失败
  mark-void <版本> --reason ...   在 ROADMAP 与站点时间线两处标"未发布"

设计约束（改动前先读）：
  · release.toml 只描述当前版本，历史归归档，所以写入分两种语义：
      覆盖式 = gradle.properties / config.js / Anchor.kt / iOS 三处（只有"当前"）
      追加式 = ROADMAP 版本条目 / 站点时间线条目（每版写一次，之后是档案）
  · 每个写出点用唯一锚点定位，**找不到或歧义即失败**，绝不猜、绝不新建。
  · 追加式遇到该版本已有手写条目就跳过：工程史优先，不重复插一份公共文案。
  · 行尾按各文件原样保留（gradle.properties 在本机是 CRLF，整体改写会造出满屏假 diff）。
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

try:
    import tomllib  # Python 3.11+
except ModuleNotFoundError:  # 3.9 / 3.10
    try:
        import tomli as tomllib
    except ModuleNotFoundError:
        sys.stderr.write("::error::需要 Python 3.11+（内置 tomllib）或 pip install tomli\n")
        raise SystemExit(2)

REPO = Path(__file__).resolve().parents[1]
MANIFEST = REPO / "release.toml"

# 判据：正常版本十几条。要动它先问人 —— 为了让绿灯而放宽检查是最危险的路径。
MAX_ITEMS = 80

GRADLE = REPO / "gradle.properties"
CONFIG_JS = REPO / "site" / "js" / "config.js"
SITE_INDEX = REPO / "site" / "index.html"
ANCHOR = REPO / "shared-ios" / "src" / "iosMain" / "kotlin" / "com" / "hmp" / "ios" / "Anchor.kt"
PROJ_YML = REPO / "ios" / "HMP" / "project.yml"
INFO_PLIST = REPO / "ios" / "HMP" / "HMP" / "Info.plist"
PBXPROJ = REPO / "ios" / "HMP" / "HMP.xcodeproj" / "project.pbxproj"
ROADMAP = REPO / "ROADMAP.md"
CHANGELOG = REPO / "site" / "changelog.html"

RM_TAIL = "## 🛠️ 关键技术演进"


def rm_begin(v):
    return f"<!-- BEGIN SYNCED RELEASE ENTRY v{v}（scripts/sync-release.py 追加，勿手改本块） -->"


def rm_end(v):
    return f"<!-- END SYNCED RELEASE ENTRY v{v} -->"


class Fail(Exception):
    pass


def rel(p):
    return p.relative_to(REPO).as_posix()


def read(p):
    if not p.is_file():
        raise Fail(f"文件不存在：{rel(p)}")
    with open(p, "r", encoding="utf-8", newline="") as f:
        return f.read()


def write(p, text):
    with open(p, "w", encoding="utf-8", newline="") as f:
        f.write(text)


def eol_of(text):
    return "\r\n" if "\r\n" in text else "\n"


def derive_code(version):
    parts = version.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise Fail(f"version 必须是 MAJOR.MINOR.PATCH，当前 {version!r}")
    a, b, c = (int(x) for x in parts)
    return a * 10000 + b * 1000 + c


# ---------------------------------------------------------------- 解析真源
def load():
    m = tomllib.loads(read(MANIFEST))
    version = str(m.get("version", "")).strip()
    if not version:
        raise Fail("release.toml 缺 version")
    code = derive_code(version)

    sections = m.get("section") or []
    if not sections:
        raise Fail("release.toml 没有任何 [[section]]，发布说明会是空的")
    total = 0
    for s in sections:
        if not str(s.get("title", "")).strip():
            raise Fail("有 [[section]] 缺 title")
        items = s.get("items") or []
        if not items:
            raise Fail(f"section「{s['title']}」没有 items")
        for it in items:
            if not str(it).strip():
                raise Fail(f"section「{s['title']}」里有空条目")
        total += len(items)
    if total > MAX_ITEMS:
        raise Fail(f"notes 共 {total} 条，超过上限 {MAX_ITEMS}：先确认没把内部章节抄进来；"
                   f"确实是大版本要改 MAX_ITEMS，属判据变更，得人批")

    artifacts = m.get("artifact") or []
    if not artifacts:
        raise Fail("release.toml 没有 [[artifact]]，产物表会是空的")
    for a in artifacts:
        if not str(a.get("label", "")).strip():
            raise Fail("有 [[artifact]] 缺 label")
        if not str(a.get("file", "")).strip():
            raise Fail(f"[[artifact]] {a.get('label')} 缺 file")
        if "{version}" not in str(a.get("file", "")) and not a.get("versioned") is False:
            raise Fail(f"[[artifact]] {a['label']} 的 file 没带 {{version}} 占位；"
                       f"确实与版本无关的产物（如校验和）请写 versioned = false")

    return {
        "version": version,
        "date": str(m.get("date", "") or "").strip(),
        "code": code,
        "sections": [{"title": str(s["title"]).strip(), "items": [str(i) for i in s["items"]]} for s in sections],
        "artifacts": [{
            "label": str(a["label"]).strip(),
            "file": str(a.get("file", "")).strip(),
            "site_key": str(a.get("site_key", "") or "").strip(),
            "hint": str(a.get("hint", "") or "").strip(),
            "in_table": a.get("in_table", True) is not False,
            "versioned": a.get("versioned", True) is not False,
            "from_dir": str(a.get("from_dir", "") or "").strip(),
            "from_glob": str(a.get("from_glob", "") or "").strip(),
        } for a in artifacts],
        "notes_footer": str(m.get("notes_footer", "") or "").strip(),
    }


# ---------------------------------------------------------------- 两种渲染
BOLD_RE = re.compile(r"\*\*(.+?)\*\*")
CODE_RE = re.compile(r"`(.+?)`")


def to_html(text):
    s = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    s = BOLD_RE.sub(r"<strong>\1</strong>", s)
    s = CODE_RE.sub(r"<code>\1</code>", s)
    return s


def artifact_cell(a, v):
    cell = f"`{a['file'].format(version=v)}`"
    if a["hint"]:
        cell += f"（{a['hint']}）"
    return cell


def render_notes(m):
    v = m["version"]
    out = [f"## Hearable Music Player v{v}", ""]
    for s in m["sections"]:
        out += [f"### {s['title']}", ""]
        out += [f"- {i}" for i in s["items"]]
        out.append("")
    out += ["---", "", "### 📥 产物", "| 平台 | 文件 |", "|------|------|"]
    out += [f"| {a['label']} | {artifact_cell(a, v)} |" for a in m["artifacts"] if a["in_table"]]
    if m["notes_footer"]:
        out += ["", f"> {m['notes_footer']}"]
    out.append("")
    return "\n".join(out)


def changelog_entry(m, nl="\n", latest=True):
    v = m["version"]
    e = [f"    <!-- v{v} -->", '    <div class="cl-entry">', '      <div class="cl-head">',
         f'        <span class="cl-version">v{v}</span>']
    if m["date"]:
        e.append(f'        <span class="cl-date">{m["date"]}</span>')
    if latest:
        e.append('        <span class="cl-latest">Latest</span>')
    e += ['      </div>', '      <div class="cl-card">']
    for s in m["sections"]:
        e += ['        <div class="cl-section">',
              f'          <div class="cl-section-title">{to_html(s["title"])}</div>',
              "          <ul>"]
        e += [f"            <li>{to_html(i)}</li>" for i in s["items"]]
        e += ["          </ul>", "        </div>"]
    e += ["      </div>", "    </div>"]
    return nl.join(e)


def roadmap_block_text(m, nl):
    lines = [rm_begin(m["version"])]
    lines.append(f"### v{m['version']} ({m['date']})" if m["date"] else f"### v{m['version']}")
    for s in m["sections"]:
        lines.append(f"- **{s['title']}**")
        lines += [f"  - {i}" for i in s["items"]]
    lines.append(rm_end(m["version"]))
    return nl.join(lines) + nl


# ---------------------------------------------------------------- 写出点
def sub1(text, pattern, repl, where):
    if not re.search(pattern, text):
        raise Fail(f"{where}：找不到锚点 /{pattern}/ —— 目标文件形状变了，先确认再改脚本")
    return re.sub(pattern, repl, text, count=1)


# 所有值模式一律用 [^..\r\n] 而不是 \S / . / $：仓库里 gradle.properties 与若干
# Xcode 文件在本机是 CRLF，$ 与 \S 的组合会在 \r 前失配（实测踩过）。


def overwrite_steps(m):
    """返回 [(文件, 原文, 新文, 说明)]。"""
    v, code = m["version"], m["code"]
    steps = []

    t = read(GRADLE)
    n = sub1(t, r"(?m)^hmp\.versionCode=[^\r\n]*", f"hmp.versionCode={code}", "gradle.properties hmp.versionCode")
    n = sub1(n, r"(?m)^hmp\.versionName=[^\r\n]*", f"hmp.versionName={v}", "gradle.properties hmp.versionName")
    steps.append((GRADLE, t, n, f"hmp.versionName→{v} / versionCode→{code}"))

    t = read(CONFIG_JS)
    n = sub1(t, r"(?m)^([ \t]*version:[ \t]*')[^'\r\n]*(')", rf"\g<1>{v}\g<2>", "config.js version")
    note = f"site version→{v}"
    if m["date"]:
        n = sub1(n, r"(?m)^([ \t]*released:[ \t]*')[^'\r\n]*(')", rf"\g<1>{m['date']}\g<2>", "config.js released")
        note += f" / released→{m['date']}"
    steps.append((CONFIG_JS, t, n, note))

    t = read(SITE_INDEX)
    n = sub1(t, r'("softwareVersion"\s*:\s*")[^"\r\n]*(")', rf"\g<1>{v}\g<2>", "index.html JSON-LD softwareVersion")
    steps.append((SITE_INDEX, t, n, f"JSON-LD softwareVersion→{v}"))

    t = read(ANCHOR)
    hit = re.search(r'SHARED_IOS_FRAMEWORK_VERSION: String = "\d+\.\d+\.\d+(-[^"\r\n]*)"', t)
    suffix = hit.group(1) if hit else ""  # 保住 -a1 这类聚合框架构建锚点
    n = sub1(t, r'(SHARED_IOS_FRAMEWORK_VERSION: String = ")[^"\r\n]*(")', rf"\g<1>{v}{suffix}\g<2>", "Anchor.kt")
    steps.append((ANCHOR, t, n, f"framework→{v}{suffix}"))

    t = read(PROJ_YML)
    n = t
    for key in ("CFBundleShortVersionString", "MARKETING_VERSION"):
        n = sub1(n, rf'(?m)^([ \t]*{key}:[ \t]*")[^"\r\n]*(")', rf"\g<1>{v}\g<2>", f"project.yml {key}")
    steps.append((PROJ_YML, t, n, f"iOS project.yml→{v}（2 处）"))

    t = read(INFO_PLIST)
    n = sub1(t, r"(<key>CFBundleShortVersionString</key>\s*<string>)[^<]*(</string>)",
             rf"\g<1>{v}\g<2>", "Info.plist CFBundleShortVersionString")
    steps.append((INFO_PLIST, t, n, f"Info.plist→{v}"))

    t = read(PBXPROJ)
    pat = r"(?m)^([ \t]*MARKETING_VERSION = )[^;\r\n]+(;)"
    cnt = len(re.findall(pat, t))
    if cnt == 0:
        raise Fail("pbxproj：找不到 MARKETING_VERSION —— 工程结构变了，先确认再改脚本")
    n = re.sub(pat, rf"\g<1>{v}\g<2>", t)
    steps.append((PBXPROJ, t, n, f"pbxproj MARKETING_VERSION×{cnt}→{v}"))
    return steps


def roadmap_step(m):
    v = m["version"]
    t = read(ROADMAP)
    nl = eol_of(t)
    if rm_begin(v) in t:  # 已有本脚本写的块 → 覆盖块内内容
        i = t.index(rm_begin(v))
        j = t.index(rm_end(v)) + len(rm_end(v)) + len(nl)
        new = t[:i] + roadmap_block_text(m, nl) + t[j:]
        return (ROADMAP, t, new, f"v{v} 生成块已存在 → 刷新块内内容")
    if re.search(rf"(?m)^### v{re.escape(v)}\b", t):  # 手写的工程史条目，不插第二份
        return (ROADMAP, t, t, f"v{v} 已有手写条目 → 跳过")
    if RM_TAIL not in t:
        raise Fail(f"ROADMAP.md：找不到插入锚点「{RM_TAIL}」")
    idx = t.index(RM_TAIL)
    new = t[:idx] + roadmap_block_text(m, nl) + nl + t[idx:]
    # partition() 会把分隔符本身丢掉 —— 曾因此把「## 🛠️ 关键技术演进」整行删掉。
    # 追加式写入的不变量就是"只增不减"，所以每次插入后都自证锚点还在。
    if RM_TAIL not in new or len(new) <= len(t):
        raise Fail("内部错误：ROADMAP 插入后丢失了章节标题，已中止写入")
    return (ROADMAP, t, new, f"v{v} 条目追加到版本历史末尾")


CL_BEGIN_TXT = "    <!-- BEGIN GENERATED (scripts/sync-release.py) -->"
CL_END_TXT = "    <!-- END GENERATED -->"
# 站点这几个 html/js 在本机是 CRLF（实测 688/688），锚点与插入块都必须按文件自身行尾来，
# 硬写 "\n" 会让锚点永不命中。
CL_CONTAINER = '<div class="changelog-timeline">'


def _insert_entry(m, t):
    v = m["version"]
    nl = eol_of(t)
    if f"    <!-- v{v} -->" in t:
        return t, f"v{v} 站点条目已存在 → 跳过插入"
    entry = changelog_entry(m, nl)
    if CL_BEGIN_TXT in t:
        mark = CL_BEGIN_TXT + nl
        return t.replace(mark, mark + entry + nl + nl, 1), f"v{v} 插入站点生成区顶部"
    i = t.find(CL_CONTAINER)
    if i < 0:
        raise Fail(f"changelog.html：找不到 {CL_CONTAINER} 容器")
    j = i + len(CL_CONTAINER)
    if t[j:j + len(nl)] != nl:
        raise Fail("changelog.html：changelog-timeline 容器后面不是行尾，结构变了先确认")
    block = nl.join([CL_BEGIN_TXT, entry, CL_END_TXT, ""]) + nl
    new = t[:j + len(nl)] + block + t[j + len(nl):]
    if CL_CONTAINER not in new or len(new) <= len(t):
        raise Fail("内部错误：changelog 插入后丢失了时间线容器，已中止写入")
    return new, f"v{v} 建立生成区并插入条目"


def _demote_older(m, t):
    """除当前版以外的条目降级：时间线只该有一个 Latest，旧条目不许再挂动态版本号钩子。

    追加新条目时，上一版的 `Latest` 徽章与 `data-site="version"`（由 config.js 填值）不会自己消失，
    实测会同时出现两个 Latest，且上一版标题被填成新版本号却挂着旧内容 —— 站点是公开面，
    这种错用户一眼看得到。幂等：重复跑无变化。
    """
    v = m["version"]
    nl = eol_of(t)
    i = t.find(CL_CONTAINER)
    if i < 0:
        return t, "无时间线容器，跳过降级"
    marks = [(i + mm.start(), mm.group(1))
             for mm in re.finditer(r"(?m)^    <!-- v(\d+\.\d+\.\d+) -->", t[i:])]
    if not marks:
        return t, "时间线里没有版本条目"
    out = [t[:marks[0][0]]]
    touched = []
    for k, (pos, ver) in enumerate(marks):
        end = marks[k + 1][0] if k + 1 < len(marks) else len(t)
        blk = t[pos:end]
        if ver == v:
            out.append(blk)
            continue
        new_blk = blk.replace('<span class="cl-version" data-site="version"></span>',
                              f'<span class="cl-version">v{ver}</span>')
        new_blk = re.sub(r'[ \t]*<span class="cl-latest">[^<]*</span>' + re.escape(nl),
                         "", new_blk)
        if new_blk != blk:
            touched.append(f"v{ver}")
        out.append(new_blk)
    note = f"降级旧条目（Latest / 动态版本号）：{', '.join(touched)}" if touched else "旧条目已是降级态"
    return "".join(out), note


def changelog_step(m):
    # 插入与降级必须合成一步：两步各自从同一份原文算 diff，后写的会把先写的覆盖掉，
    # 结果 --check 永远红、而且每跑一次就在两种状态之间来回跳。
    t0 = read(CHANGELOG)
    t1, n1 = _insert_entry(m, t0)
    t2, n2 = _demote_older(m, t1)
    return (CHANGELOG, t0, t2, f"{n1}；{n2}")


def all_steps(m):
    return overwrite_steps(m) + [roadmap_step(m), changelog_step(m)]


# ---------------------------------------------------------------- 交叉核对
def crosscheck(m):
    """返回提示清单；不一致直接 Fail。--check 与 inspect 共用。"""
    v = m["version"]
    tips = []
    cfg = read(CONFIG_JS)
    # 不能用 `assets:\s*\{(.*?)\}`：值里的 `{v}` 占位符自带花括号，非贪婪会在第一个
    # `{v}` 处截断，于是整个 assets 表被读成空 → 误报"站点下载缺键"（实测踩过）。
    blk = re.search(r"assets:\s*\{(.*)\n[ \t]*\}", cfg, re.S)
    assets = dict(re.findall(r"(\w+):[ \t]*'([^'\r\n]*)'", blk.group(1))) if blk else {}
    if not blk or not assets:
        raise Fail("config.js：解析不到 assets 表（形状变了？），无法核对下载键")
    keys = set()
    for a in m["artifacts"]:
        if not a["site_key"]:
            continue
        keys.add(a["site_key"])
        want = a["file"].format(version=v)
        got = assets.get(a["site_key"], "").replace("{v}", v)
        if not assets.get(a["site_key"]):
            raise Fail(f"站点下载缺键：config.js 的 assets 没有 {a['site_key']}（清单声明了 {want}）")
        if got != want:
            raise Fail(f"站点下载键 {a['site_key']}：config.js 是 {assets[a['site_key']]!r}，清单要求 ...{want.split('-')[-1]!r}")
    for k in assets:
        if k not in keys:
            tips.append(f"config.js 有孤儿下载键 {k} —— 清单里没有对应产物，点开必 404")
    if not re.search(rf"(?m)^### v{re.escape(v)}\b", read(ROADMAP)):
        raise Fail(f"ROADMAP.md 没有 v{v} 条目（跑 sync --write）")
    cl_text = read(CHANGELOG)
    if f"    <!-- v{v} -->" not in cl_text:
        raise Fail(f"site/changelog.html 没有 v{v} 条目（跑 sync --write）")
    n_latest = len(re.findall(r'<span class="cl-latest">', cl_text))
    if n_latest != 1:
        raise Fail(f"站点时间线应有且只有一个 Latest，当前 {n_latest} 个 —— 跑 sync --write 会降级旧条目")
    return tips


def git_tags():
    r = subprocess.run(["git", "tag", "--sort=-v:refname"], cwd=REPO, capture_output=True, text=True)
    return [x.strip() for x in r.stdout.splitlines() if x.strip()]


# ---------------------------------------------------------------- 子命令
def cmd_sync(a):
    m = load()
    steps = all_steps(m)
    changed = [(p, new, d) for p, cur, new, d in steps if cur != new]
    skipped = [d for p, cur, new, d in steps if cur == new]

    if a.check:
        cc_err = None
        tips = []
        try:
            tips = crosscheck(m)
        except Fail as e:
            cc_err = str(e)
        for p, _, d in changed:
            print(f"不一致  {rel(p):<44} {d}")
        if cc_err:
            print(f"核对未过  {cc_err}")
        # 核对失败必须反映到退出码：曾把它只当打印，CI 拿着 0 退出码就放行了"两个 Latest"这类
        # 公开面错误 —— 静默通过正是这轮改造要消灭的东西。
        if changed or cc_err:
            raise Fail(f"sync --check 未过：{len(changed)} 处与 release.toml 不一致"
                       + (f"；{cc_err}" if cc_err else "")
                       + " → 跑 ./gradlew syncVersion 后把产物与 release.toml 一起提交")
        print(f"OK release.toml v{m['version']} → 派生点全一致（{len(skipped)} 项）")
        for s in skipped + tips:
            print(f"  · {s}")
        return

    for p, new, d in changed:
        write(p, new)
        print(f"写出  {rel(p):<44} {d}")
    if not changed:
        print("无需改动：各派生点已等于 release.toml")
    for s in skipped:
        print(f"跳过  {s}")
    # 交叉核对放在写完之后：它描述的是"最终状态"，在写之前跑会把正常的待同步状态误判成错误
    for t in crosscheck(m):
        print(f"提示  {t}")
    if changed:
        print("\n下一步：sync --check 必须绿 → 再跑 preflight")


def cmd_notes(a):
    m = load()
    if a.version and a.version.lstrip("v") != m["version"]:
        raise Fail(f"release.toml 只描述当前版本 v{m['version']}，渲染不了 v{a.version}"
                   f"（历史版本的说明看 ROADMAP / 站点归档）")
    body = render_notes(m)
    print(f"=== v{m['version']} 发布说明正文（{len(body.splitlines())} 行）===", file=sys.stderr)
    if a.out:
        p = Path(a.out)
        with open(p, "w", encoding="utf-8", newline="\n") as f:
            f.write(body)
        print(f"已写 {p}", file=sys.stderr)
    else:
        sys.stdout.write(body)


def cmd_collect(a):
    """按 release.toml 的 from_dir / from_glob 收集并改名产物；缺任何一个直接失败。

    这段逻辑原来在 release.yml 里内联了五份 `cp HMP-v${VERSION}-...`，平台一变就要同时改
    工作流和配置 —— 站点那个指向不存在资产的 AppImage 按钮就是这么留下的。
    """
    m = load()
    src, dst = Path(a.src), Path(a.dst)
    if not src.is_dir():
        raise Fail(f"找不到产物目录 {src}（--src 指错了吗）")
    dst.mkdir(parents=True, exist_ok=True)
    missing, done = [], []
    for art in m["artifacts"]:
        if not art["versioned"] or not art["from_glob"]:
            continue
        base = src / art["from_dir"]
        hits = [h for h in (base.rglob(art["from_glob"]) if base.is_dir() else [])
                if h.is_file() and h.stat().st_size > 0]
        if not hits:
            missing.append(f"{art['label']} ← {art['from_dir']}/{art['from_glob']}")
            continue
        if len(hits) > 1:
            print(f"提示  {art['label']} 匹配到 {len(hits)} 个文件，取第一个：{hits[0].name}")
        target = dst / art["file"].format(version=m["version"])
        shutil.copyfile(hits[0], target)
        done.append(f"{hits[0].name} → {target.name}")
    for d in done:
        print(f"收集  {d}")
    if missing:
        raise Fail("缺产物，拒绝发布：" + "；".join(missing))
    print(f"OK 收集完成 {len(done)} 件 → {dst}")


def cmd_version(a):
    print(load()["version"])


def cmd_assets(a):
    """按行输出当前版本的产物文件名 —— CI 的齐全断言用它，别在工作流里再抄一份名字。"""
    m = load()
    for art in m["artifacts"]:
        print(art["file"].format(version=m["version"]))


def cmd_inspect(a):
    m = load()
    tags = git_tags()
    tagged = f"v{m['version']}" in tags
    steps = all_steps(m)
    drift = {rel(p): (cur != new) for p, cur, new, _d in steps}
    try:
        tips = crosscheck(m)
        ok = True
    except Fail as e:
        tips, ok = [str(e)], False
    data = {
        "version": m["version"], "version_code": m["code"], "date": m["date"] or None,
        "notes_items": sum(len(s["items"]) for s in m["sections"]),
        "latest_tag": tags[0] if tags else None, "tagged": tagged,
        "release_state": "released" if tagged else "unreleased",
        "drift": [k for k, v in drift.items() if v], "checked_files": len(drift), "consistent": ok,
        "notes": tips,
    }
    if a.json:
        print(json.dumps(data, ensure_ascii=False, indent=2))
        return
    print(f"release.toml   v{m['version']}（versionCode {m['code']} 派生，date {data['date'] or '未填'}，文案 {data['notes_items']} 条）")
    print(f"发布状态       {data['release_state']}（最新 tag {data['latest_tag']}）")
    print(f"派生点         {len(drift)} 个文件，不一致 {len(data['drift'])} 个")
    for k, v in drift.items():
        print(f"  {'漂移 ' if v else '一致'} {k}")
    for t in tips:
        print(f"提示  {t}")
    if not tagged:
        print(f"提示  v{m['version']} 还没有 tag —— 版本号一旦进过 master 就不复用（VERSIONING）")


def cmd_mark_void(a):
    v = a.version.lstrip("v")
    derive_code(v)
    t = read(ROADMAP)
    nl = eol_of(t)
    head = re.compile(rf"(?m)^(### v{re.escape(v)})(?: \(([^)]*)\))?")
    mm = head.search(t)
    if not mm:
        raise Fail(f"ROADMAP.md 没有 ### v{v} 条目，标不了作废")
    if "【未发布" in t[mm.start():mm.start() + 200]:
        raise Fail(f"ROADMAP.md 的 v{v} 已标过未发布")
    date = mm.group(2) or ""
    t = t[:mm.start()] + f"### v{v}" + (f" ({date})" if date else "") + f"【未发布：{a.reason}】" + t[mm.end():]
    write(ROADMAP, t)
    print(f"ROADMAP.md：v{v} 标题已加【未发布：{a.reason}】")

    t = read(CHANGELOG)
    anchor = f"    <!-- v{v} -->"
    if anchor not in t:
        raise Fail(f"changelog.html 没有 {anchor} —— 该版本条目不是生成器写的，去手改")
    i = t.index(anchor)
    j = t.index('    <div class="cl-card">', i)
    seg = t[i:j]
    if "cl-void" in seg:
        raise Fail(f"changelog.html 的 v{v} 已标过未发布")
    seg = seg.replace('    <div class="cl-entry">', '    <div class="cl-entry cl-entry--void">', 1)
    seg = seg.replace("      </div>\n", '        <span class="cl-void">未发布</span>\n      </div>\n', 1) \
        if "cl-head" not in seg else seg
    # 徽章插在 cl-head 内最后一个 span 之后
    spans = list(re.finditer(r"[^\n]*<span [^\n]*</span>\n", seg))
    if spans:
        k = spans[-1].end()
        seg = seg[:k] + '        <span class="cl-void">未发布</span>\n' + seg[k:]
    t = t[:i] + seg + t[j:]
    write(CHANGELOG, t)
    print(f"site/changelog.html：v{v} 已加「未发布」徽章")
    print("作废说明的那句话请人在条目里补一条 li —— 结论归人，脚本不动笔")
    print(f"提示：{nl!r} 行尾已按原文件保留")


def main():
    # 输出给 shell 消费（`while read` / `$(...)`），行尾必须是 LF：Windows 上 Python 默认把
    # "\n" 翻成 "\r\n"，消费方就会拿到带 \r 的文件名/版本号 —— 实测能骗过本地演练。
    for s in (sys.stdout, sys.stderr):
        try:
            s.reconfigure(newline="\n", encoding="utf-8", errors="replace")
        except Exception:
            pass
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("sync", help="同步派生点")
    s.add_argument("--write", action="store_true")
    s.add_argument("--check", action="store_true")
    s.set_defaults(func=cmd_sync)
    n = sub.add_parser("notes", help="渲染 Release Notes")
    n.add_argument("--version")
    n.add_argument("--out")
    n.set_defaults(func=cmd_notes)
    i = sub.add_parser("inspect", help="读仓库实况")
    i.add_argument("--json", action="store_true")
    i.set_defaults(func=cmd_inspect)
    vs = sub.add_parser("version", help="只打印当前版本号（CI 取值用）")
    vs.set_defaults(func=cmd_version)
    asm = sub.add_parser("assets", help="按行打印当前版本的产物文件名（CI 齐全断言用）")
    asm.set_defaults(func=cmd_assets)
    cc = sub.add_parser("collect", help="按 release.toml 收集并改名构建产物")
    cc.add_argument("--src", default="artifacts")
    cc.add_argument("--dst", default="release-assets")
    cc.set_defaults(func=cmd_collect)
    mv = sub.add_parser("mark-void", help="标某版未发布")
    mv.add_argument("version")
    mv.add_argument("--reason", required=True)
    mv.set_defaults(func=cmd_mark_void)
    a = ap.parse_args()
    if a.cmd == "sync" and a.write == a.check:
        sys.stderr.write("::error::sync 需要且只需要 --write 或 --check 之一\n")
        return 2
    try:
        a.func(a)
    except Fail as e:
        sys.stderr.write(f"::error::{e}\n")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
