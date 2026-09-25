#!/usr/bin/env python3
"""发版预检：桌面端 FFmpeg 二进制资产。

以 desktop/app/build.gradle.kts 的 ffmpegArtifacts 为唯一真源，逐个条目：

  1. 下载（可用 FFMPEG_CACHE_DIR 复用已下载文件）
  2. 校验 SHA256 —— 与配置不符即失败（这是构建期的真实断点）
  3. 解包定位 ffmpeg 可执行文件
  4. 解析二进制头部，确认 CPU 架构与 map key 的 os/arch 一致

第 4 步不是过度设计。上游曾出现 macos/amd64 链接实际返回 arm64 二进制的情况：
SHA256 校验照样通过、CI 照样绿，但产出的 Intel 版 DMG 在真机上一运行就崩，
只能靠解包看 Mach-O 头才暴露。所以这里一并拦住。
"""

from __future__ import annotations

import hashlib
import os
import re
import struct
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BUILD_FILE = ROOT / "desktop" / "app" / "build.gradle.kts"
CACHE_DIR = Path(os.environ.get("FFMPEG_CACHE_DIR") or "")

ARTIFACT_RE = re.compile(
    r'"(?P<os>[a-z]+)/(?P<arch>[a-z0-9_]+)"\s+to\s+FFmpegArtifact\('
    r'[^)]*?version\s*=\s*"(?P<version>[^"]+)"'
    r'[^)]*?url\s*=\s*"(?P<url>[^"]+)"'
    r'[^)]*?sha256\s*=\s*"(?P<sha256>[^"]+)"',
    re.S,
)

# map key 的 arch → 允许看到的机器类型（arm64e 是 Apple 自用的 arm64 变体）
EXPECT_ARCH = {
    "amd64": {"x86_64"},
    "arm64": {"arm64", "arm64e"},
}
EXPECT_FORMAT = {"windows": "PE", "linux": "ELF", "macos": "Mach-O"}

ELF_MACHINE = {0x3E: "x86_64", 0xB7: "arm64", 0x03: "i386", 0x28: "arm"}
PE_MACHINE = {0x8664: "x86_64", 0xAA64: "arm64", 0x014C: "i386"}
MACH_CPU = {0x01000007: "x86_64", 0x0100000C: "arm64", 0x01000012: "arm64e", 0x07: "i386"}


def parse_artifacts(text: str) -> list[dict]:
    """解析 build.gradle.kts 的 ffmpegArtifacts map。"""
    items = [m.groupdict() for m in ARTIFACT_RE.finditer(text)]
    if not items:
        raise SystemExit(f"在 {BUILD_FILE} 里没解析到任何 FFmpegArtifact 条目，模式是否变了？")
    return items


def fetch(url: str, dest: Path) -> None:
    """下载到 dest；dest 已存在则视为缓存命中。

    公开 Release 无需鉴权；私有仓库下 ffmpeg-binaries 的资产需要 token，
    故支持 GITHUB_TOKEN 环境变量（CI 里由 workflow 注入）。
    """
    if dest.is_file() and dest.stat().st_size > 0:
        print(f"    [cache] {dest.name} ({dest.stat().st_size} bytes)")
        return

    headers = {"User-Agent": "hmp-preflight-check"}
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, headers=headers)
    last_err: Exception | None = None
    for attempt in range(1, 4):
        try:
            with urllib.request.urlopen(req, timeout=180) as resp, open(dest, "wb") as fh:
                while chunk := resp.read(1 << 20):
                    fh.write(chunk)
            print(f"    [downloaded] {dest.name} ({dest.stat().st_size} bytes)")
            return
        except Exception as exc:  # noqa: BLE001 - 预检要的是重试后的最终结论
            last_err = exc
            print(f"    [retry {attempt}/3] {exc}")
    raise SystemExit(f"下载失败：{url}\n  最后错误：{last_err}")


def detect_binary(name: str, data: bytes) -> tuple[str, set[str]]:
    """返回 (文件格式, 机器类型集合)。"""
    if data[:4] == b"\x7fELF":
        endian = "<" if data[5] == 1 else ">"
        machine = struct.unpack_from(endian + "H", data, 18)[0]
        return "ELF", {ELF_MACHINE.get(machine, hex(machine))}

    if data[:2] == b"MZ":
        pe_off = struct.unpack_from("<I", data, 0x3C)[0]
        if data[pe_off:pe_off + 4] != b"PE\x00\x00":
            return "PE", {"<unknown>"}
        machine = struct.unpack_from("<H", data, pe_off + 4)[0]
        return "PE", {PE_MACHINE.get(machine, hex(machine))}

    magic = data[:4]
    if magic in (b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"):
        endian = ">" if magic == b"\xca\xfe\xba\xbe" else "<"
        nfat = struct.unpack_from(endian + "I", data, 4)[0]
        archs: set[str] = set()
        for i in range(min(nfat, 8)):
            off = 8 + i * 20
            cpu = struct.unpack_from(endian + "I", data, off)[0]
            archs.add(MACH_CPU.get(cpu, hex(cpu)))
        return "Mach-O(universal)", archs

    if magic in (b"\xcf\xfa\xed\xfe", b"\xce\xfa\xed\xfe"):
        cpu = struct.unpack_from("<I", data, 4)[0]
        return "Mach-O", {MACH_CPU.get(cpu, hex(cpu))}

    if magic == b"\xfe\xed\xfa\xcf":
        cpu = struct.unpack_from(">I", data, 4)[0]
        return "Mach-O", {MACH_CPU.get(cpu, hex(cpu))}

    return "<unknown>", {"<unknown>"}


def locate_executable(zf: zipfile.ZipFile) -> str | None:
    """在 zip 里找 ffmpeg / ffmpeg.exe，兼容各来源不同的目录层级。"""
    candidates = [
        n for n in zf.namelist()
        if not n.endswith("/") and Path(n).name in ("ffmpeg", "ffmpeg.exe")
    ]
    if not candidates:
        return None
    # 同名多份时取最短路径（通常是真正的可执行文件，而非符号链接副本）
    return sorted(candidates, key=len)[0]


def main() -> int:
    artifacts = parse_artifacts(BUILD_FILE.read_text(encoding="utf-8"))
    failures: list[str] = []

    print(f"共 {len(artifacts)} 个 FFmpeg 条目（真源 {BUILD_FILE.relative_to(ROOT)}）\n")

    workdir = Path(tempfile.mkdtemp(prefix="ffmpeg-preflight-"))
    if CACHE_DIR:
        workdir = CACHE_DIR
        workdir.mkdir(parents=True, exist_ok=True)

    for art in artifacts:
        key = f"{art['os']}/{art['arch']}"
        filename = art["url"].rsplit("/", 1)[-1]
        print(f"== {key}  v{art['version']}")
        print(f"   {filename}")

        local = workdir / filename
        try:
            fetch(art["url"], local)
        except SystemExit as exc:
            failures.append(f"{key}: {exc}")
            continue

        # ① SHA256 —— 构建期的真实断点
        digest = hashlib.sha256()
        with open(local, "rb") as fh:
            for block in iter(lambda: fh.read(1 << 20), b""):
                digest.update(block)
        actual = digest.hexdigest()
        if actual != art["sha256"]:
            failures.append(
                f"{key}: SHA256 不匹配\n"
                f"     配置期望 {art['sha256']}\n"
                f"     实际下载 {actual}\n"
                f"     → 构建会在 downloadFFmpeg 处中断；请更新 build.gradle.kts 或重传 Release 资产"
            )
            print(f"    [XX] SHA256 不匹配：期望 {art['sha256'][:12]}… 实际 {actual[:12]}…")
        else:
            print(f"    [OK] SHA256 一致")

        # ② 架构 —— 校验过了也可能装错框架
        try:
            with zipfile.ZipFile(local) as zf:
                member = locate_executable(zf)
                if member is None:
                    failures.append(f"{key}: zip 里找不到 ffmpeg / ffmpeg.exe")
                    print("    [XX] zip 内没有可执行文件")
                    continue
                data = zf.read(member)
        except zipfile.BadZipFile as exc:
            failures.append(f"{key}: zip 解包失败（{exc}）")
            print(f"    [XX] zip 损坏：{exc}")
            continue

        fmt, machines = detect_binary(member, data)
        expect_fmt = EXPECT_FORMAT.get(art["os"], "<unknown>")
        expect_arch = EXPECT_ARCH.get(art["arch"], set())

        fmt_ok = fmt.startswith(expect_fmt)
        # 通用二进制允许含目标架构即可；ELF/PE 必须精确
        arch_ok = bool(expect_arch & machines) if expect_fmt == "Mach-O" else machines <= expect_arch

        print(f"    [{'OK' if fmt_ok else 'XX'}] 格式 {fmt}（期望 {expect_fmt}）")
        print(f"    [{'OK' if arch_ok else 'XX'}] 架构 {sorted(machines)}（期望 {sorted(expect_arch)}）")

        if not fmt_ok:
            failures.append(f"{key}: 二进制格式是 {fmt}，期望 {expect_fmt}（{len(data)} bytes, 成员 {member}）")
        if not arch_ok:
            failures.append(
                f"{key}: 二进制架构是 {sorted(machines)}，期望 {sorted(expect_arch)}"
                f" —— 常见于上游把另一架构的包挂在了这个链接上"
            )

    print()
    if failures:
        print("========== FFmpeg 资产预检失败 ==========")
        for f in failures:
            print(f"  - {f}")
        return 1

    print("========== FFmpeg 资产预检通过 ==========")
    for art in artifacts:
        print(f"  [OK] {art['os']}/{art['arch']}  v{art['version']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
