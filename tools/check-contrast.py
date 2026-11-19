#!/usr/bin/env python3
"""WCAG 2.x 对比度检查：ui/theme 下的 8 套预设配色 + 品牌 LightColors/DarkColors。

只依赖标准库。直接运行：
    python tools/check-contrast.py

读取：
    app/src/main/java/com/union/campus/ui/theme/PresetPalettes.kt
    app/src/main/java/com/union/campus/ui/theme/Color.kt
（Color.kt 里的命名常量会被解析并代入 scheme。）

校验项（P0 无障碍要求）：
    * onPrimary / primary、onSecondary / secondary、onTertiary / tertiary、
      onError / error >= 4.5
    * on*Container / *Container >= 4.5（primary/secondary/tertiary/error）
    * primary / secondary / tertiary 作为文字色：
        亮色 vs surfaceContainerHigh，暗色 vs surface >= 4.5
    * outline vs surface、vs surfaceContainerHigh >= 3.0
    * *Fixed 角色对 on*Fixed >= 4.5
    * onSurface / onSurfaceVariant 在页面、卡片和连接分组表面上 >= 4.5
    * surfaceDim / surfaceContainer >= 1.2（PrismSurface 的层级可辨阈值），
      且 surfaceContainerLowest -> Low -> Container -> High -> Highest
      的明度排序单调（亮色递减、暗色递增）

退出码：全部 PASS 为 0，否则为 1。
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
THEME_DIR = ROOT / "app" / "src" / "main" / "java" / "com" / "union" / "campus" / "ui" / "theme"
PRESET_FILE = THEME_DIR / "PresetPalettes.kt"
BRAND_FILE = THEME_DIR / "Color.kt"

MIN_TEXT = 4.5
MIN_OUTLINE = 3.0
MIN_LAYER = 1.2


# ---------------------------------------------------------------------------
# 颜色计算（WCAG 2.x 相对亮度）
# ---------------------------------------------------------------------------

def parse_hex(value: str) -> tuple[int, int, int]:
    value = value.strip().lstrip("#")
    if len(value) == 8:  # 0xAARRGGBB -> RRGGBB
        value = value[2:]
    if len(value) != 6:
        raise ValueError(f"unsupported color literal: {value!r}")
    return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def srgb_to_linear(channel: int) -> float:
    c = channel / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def relative_luminance(rgb: tuple[int, int, int]) -> float:
    r, g, b = (srgb_to_linear(c) for c in rgb)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def contrast_ratio(fg: tuple[int, int, int], bg: tuple[int, int, int]) -> float:
    l1, l2 = relative_luminance(fg), relative_luminance(bg)
    hi, lo = max(l1, l2), min(l1, l2)
    return (hi + 0.05) / (lo + 0.05)


# ---------------------------------------------------------------------------
# Kotlin 解析
# ---------------------------------------------------------------------------

SCHEME_RE = re.compile(
    r"^\s*(?:private\s+)?val\s+(\w+)\s*(?::\s*ColorScheme\s*)?=\s*"
    r"(light|dark)ColorScheme\((.*?)^\s*\)\s*$",
    re.MULTILINE | re.DOTALL,
)
NAMED_VAL_RE = re.compile(
    r"^\s*private\s+val\s+(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{6,8})\)\s*$",
    re.MULTILINE,
)
COLOR_ENTRY_RE = re.compile(r"(\w+)\s*=\s*Color\(0x([0-9A-Fa-f]{6,8})\)")
NAME_ENTRY_RE = re.compile(r"(\w+)\s*=\s*([A-Za-z_]\w*)")


class Scheme:
    def __init__(self, name: str, mode: str, colors: dict[str, tuple[int, int, int]]):
        self.name = name
        self.mode = mode  # "light" | "dark"
        self.colors = colors

    def __getitem__(self, role: str) -> tuple[int, int, int]:
        return self.colors[role]


def parse_named_colors(text: str) -> dict[str, tuple[int, int, int]]:
    return {name: parse_hex(hex_value) for name, hex_value in NAMED_VAL_RE.findall(text)}


def parse_schemes(path: Path) -> list[Scheme]:
    text = path.read_text(encoding="utf-8")
    named = parse_named_colors(text) if path == BRAND_FILE else {}
    schemes = []
    for match in SCHEME_RE.finditer(text):
        name, mode, body = match.group(1), match.group(2), match.group(3)
        colors: dict[str, tuple[int, int, int]] = {}
        for role, hex_value in COLOR_ENTRY_RE.findall(body):
            colors[role] = parse_hex(hex_value)
        for role, identifier in NAME_ENTRY_RE.findall(body):
            if role in colors or identifier in ("Color",):
                continue
            if identifier not in named:
                raise SystemExit(
                    f"{path.name}:{name}: 无法解析 {role} = {identifier}（命名常量缺失）"
                )
            colors[role] = named[identifier]
        missing = {"primary", "onPrimary", "surface", "surfaceContainer"} - colors.keys()
        if missing:
            raise SystemExit(f"{path.name}:{name}: 缺少角色 {sorted(missing)}")
        schemes.append(Scheme(name, mode, colors))
    if not schemes:
        raise SystemExit(f"未在 {path} 中解析到任何 ColorScheme")
    return schemes


# ---------------------------------------------------------------------------
# 校验
# ---------------------------------------------------------------------------

def text_pairs(scheme: Scheme) -> list[tuple[str, str, str, float]]:
    """返回 (标签, 前景角色, 背景角色, 阈值)。"""
    page_bg = "surfaceContainerHigh" if scheme.mode == "light" else "surface"
    pairs: list[tuple[str, str, str, float]] = []

    for role in ("primary", "secondary", "tertiary"):
        pairs.append((f"on{role.capitalize()}/{role}", f"on{role.capitalize()}", role, MIN_TEXT))
        pairs.append((
            f"on{role.capitalize()}Container/{role}Container",
            f"on{role.capitalize()}Container",
            f"{role}Container",
            MIN_TEXT,
        ))
        pairs.append((f"{role} text/{page_bg}", role, page_bg, MIN_TEXT))
        # Color.kt 的品牌色没有定义 *Fixed 角色，只有 PresetPalettes 有。
        fixed = (f"on{role.capitalize()}Fixed", f"{role}Fixed")
        fixed_dim = (f"on{role.capitalize()}FixedVariant", f"{role}FixedDim")
        if all(role_name in scheme.colors for role_name in (*fixed, *fixed_dim)):
            pairs.append((f"{fixed[0]}/{fixed[1]}", fixed[0], fixed[1], MIN_TEXT))
            pairs.append((f"{fixed_dim[0]}/{fixed_dim[1]}", fixed_dim[0], fixed_dim[1], MIN_TEXT))

    pairs.append(("onError/error", "onError", "error", MIN_TEXT))
    pairs.append(("onErrorContainer/errorContainer", "onErrorContainer", "errorContainer", MIN_TEXT))

    # Expressive 连接式分组会使用 Low/High 等不同层级；同时约束正文和说明文字。
    # 仅检查中性表面；彩色容器必须使用上面已经校验的配套 on*Container。
    for background in ("surface", "surfaceDim", *LAYER_ROLES):
        for foreground in ("onSurface", "onSurfaceVariant"):
            pairs.append((f"{foreground}/{background}", foreground, background, MIN_TEXT))

    # outline 既可能画在页面底色上，也可能画在卡片（surfaceContainerHigh）上
    pairs.append(("outline/surface", "outline", "surface", MIN_OUTLINE))
    pairs.append(("outline/surfaceContainerHigh", "outline", "surfaceContainerHigh", MIN_OUTLINE))
    return pairs


LAYER_ROLES = (
    "surfaceContainerLowest",
    "surfaceContainerLow",
    "surfaceContainer",
    "surfaceContainerHigh",
    "surfaceContainerHighest",
)


def check_scheme(scheme: Scheme) -> tuple[list[tuple[str, float, float, bool]], dict]:
    rows = []
    text_ratios = []
    outline_ratios = []
    for label, fg, bg, threshold in text_pairs(scheme):
        if fg not in scheme.colors or bg not in scheme.colors:
            rows.append((f"{label} (missing role)", 0.0, threshold, False))
            continue
        ratio = contrast_ratio(scheme[fg], scheme[bg])
        ok = ratio >= threshold
        rows.append((label, ratio, threshold, ok))
        (outline_ratios if threshold == MIN_OUTLINE else text_ratios).append(ratio)

    # 层级：PrismSurface 的 Page/Card 两级。
    # 亮色 Page=surfaceContainerHigh、Card=surfaceContainerLowest；
    # 暗色 Page=surfaceDim、Card=surfaceContainer。
    if scheme.mode == "light":
        layer_pair = ("surfaceContainerHigh", "surfaceContainerLowest")
    else:
        layer_pair = ("surfaceDim", "surfaceContainer")
    layer_ratio = contrast_ratio(scheme[layer_pair[0]], scheme[layer_pair[1]])
    layer_ok = layer_ratio >= MIN_LAYER
    layer_label = f"{layer_pair[0]}/{layer_pair[1]}"

    luminances = [relative_luminance(scheme[role]) for role in LAYER_ROLES]
    if scheme.mode == "light":
        ordering_ok = all(a > b for a, b in zip(luminances, luminances[1:]))
    else:
        ordering_ok = all(a < b for a, b in zip(luminances, luminances[1:]))

    meta = {
        "layer_ratio": layer_ratio,
        "layer_ok": layer_ok,
        "layer_label": layer_label,
        "ordering_ok": ordering_ok,
        "text_ratios": text_ratios,
        "outline_ratios": outline_ratios,
    }
    return rows, meta


def main() -> int:
    schemes = parse_schemes(PRESET_FILE) + parse_schemes(BRAND_FILE)
    if not schemes:
        print("没有解析到配色方案")
        return 1

    total_failures = 0
    summary: list[tuple[str, float, float, float, bool, bool, int]] = []
    detail_lines: list[str] = []

    for scheme in schemes:
        rows, meta = check_scheme(scheme)
        failures = sum(1 for *_, ok in rows if not ok)
        failures += 0 if meta["layer_ok"] else 1
        failures += 0 if meta["ordering_ok"] else 1
        total_failures += failures

        detail_lines.append(f"== {scheme.name} ({scheme.mode}) ==")
        for label, ratio, threshold, ok in rows:
            detail_lines.append(
                f"  {label:<42} {ratio:5.2f}  {'PASS' if ok else 'FAIL'}  (>= {threshold})"
            )
        detail_lines.append(
            f"  {meta['layer_label'] + ' (layer)':<42} {meta['layer_ratio']:5.2f}  "
            f"{'PASS' if meta['layer_ok'] else 'FAIL'}  (>= {MIN_LAYER})"
        )
        detail_lines.append(
            f"  {'surface container ordering':<42} {'PASS' if meta['ordering_ok'] else 'FAIL'}"
        )
        summary.append(
            (scheme.name, min(meta["text_ratios"]), min(meta["outline_ratios"]),
             meta["layer_ratio"], meta["ordering_ok"], failures == 0, failures)
        )
        detail_lines.append("")

    print("\n".join(detail_lines))
    print("== 汇总 ==")
    print(
        f"  {'scheme':<14} {'min text':>9}  {'min outline':>11}  "
        f"{'layer':>6}  {'ordering':>8}  result"
    )
    for name, min_text, min_outline, layer_ratio, ordering_ok, ok, failures in summary:
        result = "PASS" if ok else f"FAIL ({failures})"
        print(
            f"  {name:<14} {min_text:9.2f}  {min_outline:11.2f}  {layer_ratio:6.2f}  "
            f"{'PASS' if ordering_ok else 'FAIL':>8}  {result}"
        )

    print()
    if total_failures:
        print(f"FAIL: {total_failures} 项未达标")
        return 1
    print("PASS: 全部配色与层级检查达标")
    return 0


if __name__ == "__main__":
    sys.exit(main())
