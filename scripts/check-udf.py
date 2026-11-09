#!/usr/bin/env python3
"""UDF 形态门禁（7.5 P1 §4.3）。

五条机械规则**阻塞**合入；另有四类启发式巡检（原 `audit-udf-ui.py` 的 R1/R3/R4/R5）只做**提示**，
不计入退出码 —— 它们依赖变量名/类型名启发式，需要人看一眼才能定性。

按注入类型判定，不按关键字一刀切：
`koinInject<PlatformServices>` / `PlaybackController` / `DialogManager` 等平台门面是允许的，
只有 Repository / UseCase / MasterAgent 这类业务对象才算违规。

用法：
    python scripts/check-udf.py            # 全量
    python scripts/check-udf.py --quiet    # 只输出阻塞结论（隐藏巡检提示）
退出码 0 = 通过，1 = 有违规。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "shared-ui" / "src" / "commonMain" / "kotlin"

# 允许在 composable 里直接注入的平台门面（不是业务对象）。
PLATFORM_FACADES = {
    "PlatformServices",
    "PlaybackController",
    "DialogManager",
    "PresenceBus",
    "ChatEntryBroker",
    "RadioConsoleController",
}

# 组合根 / 启动引导允许直连的少数对象：它们的生命周期就是 App 本身，
# 收进 VM 只会把「启动编排」藏起来。逐条登记，不放宽到普通屏。
COMPOSITION_ROOT_ALLOWLIST = {
    ("AppRoot.kt", "LyricsSettingsUseCase"),
}

# 卡片渲染组件：没有屏级 VM，取的是 agent 权威 StateFlow 的只读投影（§5 卡片渲染缓存）。
# 逐条按「文件 + 类型」登记，不按类型全局放宽。
CARD_RENDER_ALLOWLIST = {
    ("HelloCardRadioStatus.kt", "MasterAgent"),
}

# 散装 StateFlow 的例外：跨屏共享数据投影白名单（§2 第 7 条），逐条登记。
SCATTERED_ALLOWLIST: set[tuple[str, str]] = set()

# 跨屏共享数据投影白名单（§2 第 7 条）：这些 VM 允许暴露多个公开 StateFlow。
SHARED_VM_ALLOWLIST = {
    "SettingsViewModel",
    "RecommendationViewModel",
}

# 应用级（activity scope）VM 白名单：只有这些类型允许用 activityViewModel()。
# 屏级 VM 必须用 koinViewModel() 绑定 entry —— 否则状态会跨屏共享、并在离开页面后残留，
# 那正是 7.5 拆分（Gallery / 曲库设置 / 歌单详情 / 歌单管理）要消灭的形态。
# 例外说明：PlayerScreenViewModel 是**常驻页**（播放页与四个 Tab 一样出栈不销毁，用户 2026-11-10 口径），
# 因此按应用级登记；不要把它当漏网形态再改回 koinViewModel()。
APP_SCOPED_VMS = {
    "AppRootViewModel",
    "DialogManagerViewModel",
    "DialogViewModel",
    "ThemeViewModel",
    "SettingsViewModel",
    "PlaylistViewModel",
    "PlayerScreenViewModel",
    "RecommendationViewModel",
    "UserUsageDataViewModel",
}


def line_of(text: str, offset: int) -> int:
    return text[:offset].count("\n") + 1


def check_screen_content(path: Path, text: str):
    errs = []
    for m in re.finditer(r"^\s*fun\s+(\w+ScreenContent)\s*\(", text, re.M):
        errs.append((line_of(text, m.start()), f"{m.group(1)} 是整屏 content 转发层（§7 禁止新建/保留）"))
    return errs


def check_confirm_chain(path: Path, text: str):
    shows = [(m.start(), m.group(1)) for m in re.finditer(r"var\s+(show\w*Dialog)\s+by\s+remember", text)]
    pends = [(m.start(), m.group(1)) for m in re.finditer(r"var\s+(pending\w*)\s+by\s+remember", text)]
    if shows and pends:
        names = ", ".join([s[1] for s in shows] + [p[1] for p in pends])
        return [(line_of(text, shows[0][0]), f"成对确认链未合并：{names}（§2 应合成一个 nullable 字段）")]
    return []


BASELINE_FILE = ROOT / "scripts" / "udf-inject-baseline.txt"
BASELINE_HITS: list[str] = []


def load_baseline() -> set[tuple[str, str]]:
    """既有债基线：命中只提示，基线外新增即失败。"""
    if not BASELINE_FILE.exists():
        return set()
    entries = set()
    for raw in BASELINE_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "|" not in line:
            continue
        rel, typ = line.split("|", 1)
        entries.add((rel.strip(), typ.strip()))
    return entries


BASELINE = load_baseline()


def _inject_allowed(path: Path, simple: str) -> bool:
    if simple in PLATFORM_FACADES:
        return True
    if (path.name, simple) in COMPOSITION_ROOT_ALLOWLIST:
        return True
    if (path.name, simple) in CARD_RENDER_ALLOWLIST:
        return True
    rel = str(path.relative_to(SRC)).replace("\\", "/").replace("com/hearablemusic/player/ui/", "")
    if (rel, simple) in BASELINE:
        BASELINE_HITS.append(f"{rel}|{simple}")
        return True
    return False


def check_business_inject(path: Path, text: str):
    if path.name.endswith("ViewModel.kt"):
        return []
    errs = []
    # 形态 1：koinInject<T>() 显式泛型调用
    for m in re.finditer(r"koinInject<([\w.]+)>", text):
        simple = m.group(1).split(".")[-1]
        if _inject_allowed(path, simple):
            continue
        errs.append((line_of(text, m.start()), f"composable 直接 koinInject<{simple}>（业务对象应经 VM）"))
    # 形态 2：参数 / 属性默认值注入（x: T = koinInject()）。
    # 首版只查形态 1，漏掉了 AIScreen / AgentConfigScreen 的 masterAgent 参数。
    for m in re.finditer(r"\w+\s*:\s*([\w.]+)\s*=\s*koinInject\s*\(", text):
        simple = m.group(1).split(".")[-1]
        if _inject_allowed(path, simple):
            continue
        errs.append((line_of(text, m.start()), f"参数注入业务对象：{simple} = koinInject()（应经 VM）"))
    # 形态 3：局部声明注入（val m: T = koinInject()）。
    # 形态 1/2 都覆盖不到它 —— HelloCardStack 的 masterAgent 就是从这条缝漏过去的。
    for m in re.finditer(r"\b(?:val|var)\s+\w+\s*:\s*([\w.]+)\??\s*=\s*koinInject\s*\(", text):
        simple = m.group(1).split(".")[-1]
        if _inject_allowed(path, simple):
            continue
        errs.append((line_of(text, m.start()), f"局部声明注入业务对象：{simple} = koinInject()（应经 VM）"))
    return errs


def check_vm_scope(path: Path, text: str):
    """应用级 VM 只能经 activityViewModel() 获取；其余一律 koinViewModel()（绑定 entry）。"""
    errs = []
    for m in re.finditer(r"\b\w+\s*:\s*([\w.]+)\s*=\s*activityViewModel\s*\(\s*\)", text):
        simple = m.group(1).split(".")[-1]
        if simple not in APP_SCOPED_VMS:
            errs.append((line_of(text, m.start()), f"{simple} 不是应用级 VM，却用 activityViewModel()（应 koinViewModel()）"))
    for m in re.finditer(r"activityViewModel\s*<\s*([\w.]+)\s*>", text):
        simple = m.group(1).split(".")[-1]
        if simple not in APP_SCOPED_VMS:
            errs.append((line_of(text, m.start()), f"{simple} 不是应用级 VM，却用 activityViewModel<{simple}>()（应 koinViewModel()）"))
    return errs


def check_scattered_stateflow(path: Path, text: str):
    if not path.name.endswith("ViewModel.kt"):
        return []
    vm_name = path.stem
    if vm_name in SHARED_VM_ALLOWLIST:
        return []
    public_flows = re.findall(r"^\s{4}val\s+(\w+)\s*:\s*StateFlow<", text, re.M)
    ui_like = [f for f in public_flows if f.lower().endswith("uistate")]
    if not ui_like:
        return []
    # 只报 UiState 之外的散装出口；多个 UiState（按屏拆出口）是允许的。
    extra = [
        f for f in public_flows
        if f not in ui_like and (vm_name, f) not in SCATTERED_ALLOWLIST
    ]
    if extra:
        return [(1, f"{vm_name} 除 UiState 外还有散装 StateFlow 出口：{', '.join(extra)}（§2）")]
    return []


# ── 巡检提示（原 scripts/audit-udf-ui.py 的 R1/R3/R4/R5，2026-11-10 并入）──
# 这些规则靠变量名/类型名启发式，可能误报；只提示、不阻塞。

# §5 白名单词条（变量名包含即视为交互态，不算业务状态）
INTERACTION_WORDS = (
    "listState", "pagerState", "scrollState", "drag", "trackWidth", "capsuleWidth",
    "stripSize", "containerHeight", "isDragging", "ProgrammaticScroll", "glowPhase",
    "lastAutoRotateAt", "userLockUntil", "lastInteractionTime", "visible", "isExiting",
    "timerKey", "lastScrollJob", "expanded", "menuExpanded", "showMoreMenu", "showDetail",
    "isSettingsPanelVisible", "isControlsVisible", "showPassword", "anchorCard",
    "lastSongKey", "lastPlaying", "radioStatusCard", "narrativeCard", "prevHeight",
    "previousPage", "sliderPosition", "isSeeking", "showTimerDialog", "focused",
    "rememberCoroutineScope", "scope", "hazeState", "animatable", "offset",
    # 全局对话框内部表单草稿（UdfConventions §5 补充条）：没有宿主 VM
    "inputValue", "currentSelectedIds", "selectedOption", "customMinutesInput",
    # 滑杆拖动中间态 / 渲染块切换
    "sliderValue", "selectedTab",
)

SCREEN_SUFFIXES = ("Screen.kt", "Page.kt", "Sheet.kt", "Dialog.kt")


def _is_comment_line(text: str, offset: int) -> bool:
    line_start = text.rfind("\n", 0, offset) + 1
    row = text[line_start:offset].strip()
    return row.startswith("//") or row.startswith("*")


def hint_screen_state(path: Path, text: str):
    if not path.name.endswith(SCREEN_SUFFIXES):
        return []
    errs = []
    for m in re.finditer(r"\bvar\s+(\w+)\s+by\s+remember", text):
        var = m.group(1)
        if any(w.lower() in var.lower() for w in INTERACTION_WORDS):
            continue
        errs.append((line_of(text, m.start()), f"业务状态仍在 UI：{var}"))
    return errs


def hint_screen_actions(path: Path, text: str):
    if not path.name.endswith(SCREEN_SUFFIXES):
        return []
    errs = []
    for m in re.finditer(r"\bviewModel\.([a-z]\w*)\s*\(", text):
        fn = m.group(1)
        if fn.startswith(("on", "load", "refresh")):
            continue
        errs.append((line_of(text, m.start()), f"直接调用 VM 方法：viewModel.{fn}()"))
    return errs


def hint_screen_business_types(path: Path, text: str):
    if not path.name.endswith(SCREEN_SUFFIXES):
        return []
    errs = []
    for m in re.finditer(r"\b(\w*(?:Repository|UseCase))\b", text):
        if _is_comment_line(text, m.start()):
            continue
        errs.append((line_of(text, m.start()), f"出现业务类型名：{m.group(1)}（看是否直接调用）"))
    return errs


def hint_pseudo_events(path: Path, text: str):
    if not path.name.endswith("ViewModel.kt"):
        return []
    if re.search(r"StateFlow<[^>]*\?>", text) and re.search(r"fun clear\w*\s*\(", text):
        return [(1, "StateFlow 可空 + clearXxx（疑似伪一次性事件）")]
    return []


HINT_RULES = (
    ("R1-state-in-ui", hint_screen_state),
    ("R3-vm-action", hint_screen_actions),
    ("R4-business-type", hint_screen_business_types),
    ("R5-pseudo-event", hint_pseudo_events),
)


RULES = (
    ("screen-content", check_screen_content),
    ("confirm-chain", check_confirm_chain),
    ("business-inject", check_business_inject),
    ("scattered-stateflow", check_scattered_stateflow),
    ("vm-scope", check_vm_scope),
)


def main() -> int:
    quiet = "--quiet" in sys.argv
    if not SRC.exists():
        print(f"[check-udf] 源码目录不存在：{SRC}")
        return 1

    violations = 0
    hints: list[tuple[str, int, str, str]] = []
    for path in sorted(SRC.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        for rule_name, rule in RULES:
            for line, message in rule(path, text):
                violations += 1
                rel = path.relative_to(ROOT)
                print(f"{rel}:{line}: [{rule_name}] {message}")
        for rule_name, rule in HINT_RULES:
            for line, message in rule(path, text):
                hints.append((str(path.relative_to(ROOT)), line, rule_name, message))

    if BASELINE_HITS:
        print(
            f"\n[check-udf] 基线内既有注入债 {len(set(BASELINE_HITS))} 处"
            "（见 scripts/udf-inject-baseline.txt，清理后从基线删除）"
        )

    if hints and not quiet:
        print(f"\n[check-udf] 巡检提示 {len(hints)} 条（启发式，需人工判定；不计入退出码）：")
        for rel, line, rule_name, message in hints[:60]:
            print(f"{rel}:{line}: [{rule_name}] {message}")
        if len(hints) > 60:
            print(f"... 另有 {len(hints) - 60} 条")

    if violations:
        print(f"\n[check-udf] 失败：{violations} 处违规")
        return 1
    if not quiet:
        suffix = f"；巡检提示 {len(hints)} 条（非阻塞）" if hints else ""
        print("[check-udf] 通过：五条机械规则无违规" + suffix)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
