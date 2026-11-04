#!/usr/bin/env python3
"""设计 token 守门（D8-03 三-4 建立；2026-11-04 升级为全量禁止，无基线概念）。

规则表驱动：**一次遍历**所有源文件，把每行交给 `RULES` 里的 7 条规则；
命中产结构化 `Finding`，按文件分组报告。无存量容忍，例外逐行申报。

四组禁令（对应 hmp-design.md §9.1）：
- 尺寸：非 0 裸 `.dp/.sp` → `LocalHmpMetrics` 12 档 / `HMPMetricTokens.Compact` /
  `LayoutConstraints` 具名常量。
- 色值：硬编码 `Color(0x…)` → `ColorTokens` / `AgentStatusColors` / `HelloCardPalette`；
  `java.awt.Color` 是 AWT 绘制色，自动排除。
- 排版：`MaterialTheme.typography` / `m3ComponentTypography` → `LocalHmpTypo` 9 角色。
- 动效：`tween(数字)` / `durationMillis = 数字` / `spring(dampingRatio=…)` → `HmpMotion`；
  `AnimatedVisibility` / `AnimatedContent` / `Crossfade` 必须显式带 spec。

豁免（逐文件列名，不整目录放行）：
- `TOKEN_FILES`：五类 token 定义文件；
- `TYPO_EXEMPT`：M3 投影的装配点与验证用例；
- `MOTION_EXEMPT`：特化动效（氛围背景 / 歌词呈现 / 悬浮窗）。

用法：
    python scripts/check-design-tokens.py                # 人类可读
    python scripts/check-design-tokens.py --json         # 机器可读（CI / 编辑器）
    python scripts/check-design-tokens.py --quiet        # 只输出统计
    python scripts/check-design-tokens.py --path DIR     # 限定扫描范围（调试）
    python scripts/check-design-tokens.py --self-test    # 内置用例自测（不读工作树）
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# 全量禁令全端生效：shared-ui 所有源集 + desktop/app（入口可能手写 MaterialTheme 装配点）
SRC_GATE_ROOTS = [
    ROOT / "shared-ui/src",
    ROOT / "desktop/app/src",
]

# ── 豁免清单 ────────────────────────────────────────────────────────────

# 五类 token 的定义文件：允许出现字面量本身
TOKEN_FILES = frozenset({
    "ColorTokens.kt",
    "AgentStatusColors.kt",
    "HelloCardPalette.kt",
    "TypeScale.kt",
    "HMPTypoTokens.kt",
    "MetricScale.kt",
    "HMPMetricTokens.kt",
    "LayoutConstraints.kt",
    "HmpMotion.kt",
})

# 装配点合法消费 M3 投影；测试文件是投影行为的验证用例
TYPO_EXEMPT = frozenset({
    "AppRoot.kt",
    "HearableMusicPlayerTheme.kt",
    "IosComposePilot.kt",
    "TypeScaleTest.kt",
})

# 特化动效：参数由内容 / 视觉决定，不纳入体系（hmp-design.md §6.1）
MOTION_EXEMPT = {
    "DynamicBackground.kt": "氛围背景：20–120s 循环 + 光斑呼吸，参数由视觉决定",
    "AdvancedLyrics.kt": "歌词呈现：走 LyricsPresentationParams 独立参数对象",
    "KaraokeLyricText.kt": "歌词呈现：同上",
    "FloatingLyricsOverlay.kt": "悬浮窗吸附：平台特化交互",
}

# ── 正则 ────────────────────────────────────────────────────────────────

EXCEPTION = "design-token-exception"

DIMENSION = re.compile(r"(\d+(?:\.\d+)?)\.(?:dp|sp)\b")
COLOR = re.compile(r"(?<!java\.awt\.)Color\(\s*0x[0-9A-Fa-f]{3,8}")
TYPO = re.compile(r"MaterialTheme\.typography|m3ComponentTypography")
TWEEN_NUMBER = re.compile(r"tween(?:<[^>]+>)?\(\s*\d+")
DURATION_NUMBER = re.compile(r"durationMillis\s*=\s*\d+")
SPRING_PARAMS = re.compile(r"spring(?:<[^>]+>)?\(\s*(?:dampingRatio|stiffness)\s*=")
ANIMATED_CALL = re.compile(r"\b(AnimatedVisibility|AnimatedContent|Crossfade)\(")
MOTION_SIGNAL = re.compile(r"HmpMotion\.|animationSpec|transitionSpec")


@dataclass(frozen=True)
class Rule:
    group: str                        # 尺寸 / 色值 / 排版 / 动效
    name: str                         # 规则短名（报告里展示）
    pattern: re.Pattern[str]
    hint: str                         # 修复建议
    skips: frozenset[str] = frozenset()
    skip_zero: bool = False           # 命中值为 0 时放行（`0.dp`）
    window: int = 0                   # >0：向后看 N 行做上下文判定
    require: re.Pattern[str] | None = None  # 窗口内出现即视为合规


def _motion_skips() -> frozenset[str]:
    return TOKEN_FILES | frozenset(MOTION_EXEMPT)


RULES: tuple[Rule, ...] = (
    Rule(
        group="尺寸",
        name="裸 .dp/.sp",
        pattern=DIMENSION,
        hint="改用 LocalHmpMetrics 档位 / HMPMetricTokens.Compact / LayoutConstraints",
        skips=TOKEN_FILES,
        skip_zero=True,
    ),
    Rule(
        group="色值",
        name="硬编码 Color(0x…)",
        pattern=COLOR,
        hint="改用 ColorTokens / AgentStatusColors / HelloCardPalette",
        skips=TOKEN_FILES,
    ),
    Rule(
        group="排版",
        name="M3 typography API",
        pattern=TYPO,
        hint="改用 LocalHmpTypo 的 9 角色（装配点除外）",
        skips=TOKEN_FILES | TYPO_EXEMPT,
    ),
    Rule(
        group="动效",
        name="tween(数字)",
        pattern=TWEEN_NUMBER,
        hint="改用 HmpMotion 场景工厂（page / emphasis / standard / enter / exit / micro）",
        skips=_motion_skips(),
    ),
    Rule(
        group="动效",
        name="durationMillis = 数字",
        pattern=DURATION_NUMBER,
        hint="改用 HmpMotion 的四档常量",
        skips=_motion_skips(),
    ),
    Rule(
        group="动效",
        name="spring(裸参数)",
        pattern=SPRING_PARAMS,
        hint="改用 snappySpring / crispSpring",
        skips=_motion_skips(),
    ),
    Rule(
        group="动效",
        name="Animated* 无显式 spec",
        pattern=ANIMATED_CALL,
        hint="补 HmpMotion / animationSpec / transitionSpec",
        skips=_motion_skips(),
        window=10,
        require=MOTION_SIGNAL,
    ),
)


@dataclass(frozen=True)
class Finding:
    group: str
    rule: str
    file: str
    line: int
    snippet: str
    hint: str

    def as_dict(self) -> dict[str, object]:
        return {
            "group": self.group,
            "rule": self.rule,
            "file": self.file,
            "line": self.line,
            "snippet": self.snippet,
            "hint": self.hint,
        }


@dataclass
class ScanResult:
    files: int = 0
    exceptions: int = 0
    findings: list[Finding] = field(default_factory=list)

    @property
    def ok(self) -> bool:
        return not self.findings

    def stats(self) -> Counter[str]:
        return Counter(f.group for f in self.findings)


def strip_comment(line: str) -> str:
    """去掉整行注释与行尾注释；保留 `http://` 里的冒号。"""
    code = line.strip()
    if code.startswith("//") or code.startswith("*") or code.startswith("/*"):
        return ""
    return re.sub(r"(?<!:)//.*$", "", line)


def _is_zero(match: re.Match[str]) -> bool:
    try:
        return float(match.group(1)) == 0.0
    except (IndexError, ValueError, TypeError):
        return False


def scan_text(text: str, name: str = "Probe.kt", rel: str = "Probe.kt") -> tuple[list[Finding], int]:
    """扫描一段源码文本。`--self-test` 与真实扫描共用这条路径。"""
    lines = text.splitlines()
    findings: list[Finding] = []
    exceptions = 0
    for idx, raw in enumerate(lines):
        if EXCEPTION in raw:
            exceptions += 1
            continue
        code = strip_comment(raw)
        if not code:
            continue
        for rule in RULES:
            if name in rule.skips:
                continue
            for match in rule.pattern.finditer(code):
                if rule.skip_zero and _is_zero(match):
                    continue
                if rule.window:
                    window = " ".join(
                        strip_comment(l) for l in lines[idx: idx + rule.window]
                    )
                    if rule.require and rule.require.search(window):
                        continue
                findings.append(
                    Finding(
                        group=rule.group,
                        rule=rule.name,
                        file=rel,
                        line=idx + 1,
                        snippet=match.group(0),
                        hint=rule.hint,
                    )
                )
    return findings, exceptions


def iter_kt_files(roots: list[Path]):
    for root in roots:
        if not root.is_dir():
            continue
        for path in sorted(root.rglob("*.kt")):
            yield path


def scan(roots: list[Path]) -> ScanResult:
    result = ScanResult()
    for path in iter_kt_files(roots):
        rel = path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else path.as_posix()
        findings, exceptions = scan_text(
            path.read_text(encoding="utf-8"), name=path.name, rel=rel
        )
        result.files += 1
        result.exceptions += exceptions
        result.findings.extend(findings)
    return result


def report_text(result: ScanResult, quiet: bool = False) -> str:
    stats = result.stats()
    summary = (
        f"尺寸 {stats.get('尺寸', 0)} · 色值 {stats.get('色值', 0)} · "
        f"排版 {stats.get('排版', 0)} · 动效 {stats.get('动效', 0)} · "
        f"例外 {result.exceptions} 行"
    )
    if result.ok:
        return f"设计 token 守门通过：{result.files} 文件 / {len(RULES)} 条规则\n  {summary}"
    if quiet:
        return f"设计 token 守门失败：{len(result.findings)} 项 / {result.files} 文件\n  {summary}"

    lines = [f"设计 token 守门失败：{len(result.findings)} 项 / {result.files} 文件", ""]
    by_file: dict[str, list[Finding]] = {}
    for f in result.findings:
        by_file.setdefault(f.file, []).append(f)
    for file, items in sorted(by_file.items()):
        lines.append(f"  {file}")
        for item in sorted(items, key=lambda x: x.line):
            lines.append(f"    {item.line:>4}  [{item.group}] {item.rule}: {item.snippet}")
            lines.append(f"          → {item.hint}")
        lines.append("")
    lines.append(f"  统计：{summary}")
    lines.append("")
    lines.append("  确属例外：在该行尾写 `// design-token-exception: <理由>`。")
    return "\n".join(lines)


def report_json(result: ScanResult) -> str:
    return json.dumps(
        {
            "ok": result.ok,
            "files": result.files,
            "exceptions": result.exceptions,
            "stats": dict(result.stats()),
            "findings": [f.as_dict() for f in result.findings],
        },
        ensure_ascii=False,
        indent=2,
    )


# ── 自测 ────────────────────────────────────────────────────────────────

SELF_TEST_CASES: tuple[tuple[str, bool, str], ...] = (
    ("val x = 16.dp", True, "尺寸"),
    ("val x = 16.sp", True, "尺寸"),
    ("val x = 0.dp", False, ""),
    ("val x = HMPMetricTokens.Compact.cozy", False, ""),
    ("val c = Color(0xFF123456)", True, "色值"),
    ("val c = java.awt.Color(0x4CAF50)", False, ""),
    ("val t = MaterialTheme.typography.body", True, "排版"),
    ("val s = tween(300)", True, "动效"),
    ("val s = tween<Float>(300)", True, "动效"),
    ("val s = tween(durationMillis = 300)", True, "动效"),
    ("val s = HmpMotion.standard()", False, ""),
    ("val s = spring(dampingRatio = 0.7f, stiffness = 400f)", True, "动效"),
    ("val s = HmpMotion.snappySpring<Float>()", False, ""),
    (
        "AnimatedVisibility(visible = true) {\n    Box {}\n}",
        True,
        "动效",
    ),
    (
        "AnimatedVisibility(visible = true, enter = fadeIn(HmpMotion.enter())) {\n    Box {}\n}",
        False,
        "",
    ),
    ("val x = 16.dp // design-token-exception: 探针", False, ""),
    ("// val x = 16.dp", False, ""),
    ("/* val x = 16.dp */", False, ""),
)


def run_self_test() -> int:
    failures: list[str] = []
    for i, (text, should_hit, group) in enumerate(SELF_TEST_CASES, 1):
        findings, _ = scan_text(text)
        hit = bool(findings)
        groups = {f.group for f in findings}
        if hit != should_hit:
            failures.append(f"用例 {i} 命中不符合预期：{text!r} → {findings}")
        elif should_hit and group not in groups:
            failures.append(f"用例 {i} 命中分组错误：期望 {group}，实际 {groups}")
    # 文件名豁免
    exempt, _ = scan_text("val x = 16.dp", name="ColorTokens.kt", rel="ColorTokens.kt")
    if exempt:
        failures.append(f"token 文件豁免失效：{exempt}")
    motion_exempt, _ = scan_text("val s = tween(300)", name="DynamicBackground.kt")
    if motion_exempt:
        failures.append(f"特化动效豁免失效：{motion_exempt}")

    total = len(SELF_TEST_CASES) + 2
    if failures:
        print(f"self-test 失败：{len(failures)}/{total}", file=sys.stderr)
        for f in failures:
            print(f"  - {f}", file=sys.stderr)
        return 1
    print(f"self-test 通过：{total}/{total}（规则 {len(RULES)} 条）")
    return 0


def resolve_roots(raw: list[str] | None) -> list[Path]:
    if not raw:
        return SRC_GATE_ROOTS
    return [p if p.is_absolute() else ROOT / p for p in (Path(x) for x in raw)]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="设计 token 全量守门")
    parser.add_argument("--json", action="store_true", help="输出 JSON（CI / 编辑器）")
    parser.add_argument("--quiet", action="store_true", help="只输出统计")
    parser.add_argument("--path", action="append", help="限定扫描目录（可多次）")
    parser.add_argument("--self-test", action="store_true", help="跑内置用例自测")
    args = parser.parse_args(argv)

    if args.self_test:
        return run_self_test()

    result = scan(resolve_roots(args.path))
    if args.json:
        print(report_json(result))
    else:
        print(report_text(result, quiet=args.quiet), file=sys.stdout if result.ok else sys.stderr)
    return 0 if result.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
