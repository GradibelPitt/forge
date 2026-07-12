from __future__ import annotations

import argparse
from io import BytesIO
from pathlib import Path
from typing import Dict

import resvg_py
from PIL import Image, ImageDraw, ImageFont


WIDTH, HEIGHT = 1500, 2092
GOLD = (214, 174, 70, 255)
PALE_GOLD = (246, 222, 143, 255)
INK = (16, 13, 12, 235)
PANEL = (8, 10, 15, 190)
DEFAULT_TRANSLATIONS = Path(
    r"D:\Forge\forge-latest\forge-gui\res\languages\cardnames-zh-CN.txt"
)
FRAME_DIR = Path(__file__).resolve().parent / "MTG_牌框_SVG_完全透明内框_v2"
NAME_FRAME_SVG = FRAME_DIR / "牌名框_完全透明内框.svg"
TEXT_FRAME_SVG = FRAME_DIR / "文字框_完全透明内框.svg"


def parse_card(path: Path) -> Dict[str, str]:
    wanted = {"Name", "ManaCost", "Types", "PT", "Oracle"}
    result: Dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        if ":" not in raw:
            continue
        key, value = raw.split(":", 1)
        if key in wanted and key not in result:
            result[key] = value.replace("\\n", "\n").strip()
    return result


def load_translations(path: Path) -> Dict[str, Dict[str, str]]:
    translations: Dict[str, Dict[str, str]] = {}
    if not path.is_file():
        return translations
    for raw in path.read_text(encoding="utf-8").splitlines():
        fields = raw.split("|", 3)
        if len(fields) != 4 or not fields[0]:
            continue
        internal_name, display_name, types, oracle = fields
        translations[internal_name] = {
            "Name": display_name,
            "Types": types,
            "Oracle": oracle.replace("\\n", "\n"),
        }
    return translations


def localized_fields(fields: Dict[str, str], translations: Dict[str, Dict[str, str]]) -> Dict[str, str]:
    localized = dict(fields)
    translation = translations.get(fields.get("Name", ""))
    if translation:
        localized.update(translation)
    return localized


def load_svg_layer(path: Path, size: tuple[int, int]) -> Image.Image:
    png = resvg_py.svg_to_bytes(svg_path=str(path), width=size[0], height=size[1])
    with Image.open(BytesIO(png)) as image:
        return image.convert("RGBA")


def _font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        Path(r"C:\Windows\Fonts\msyhbd.ttc" if bold else r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\arialbd.ttf" if bold else r"C:\Windows\Fonts\arial.ttf"),
    ]
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size=size)
    return ImageFont.load_default()


def _cover(image: Image.Image, size: tuple[int, int]) -> Image.Image:
    target_ratio = size[0] / size[1]
    source_ratio = image.width / image.height
    if source_ratio > target_ratio:
        new_height = size[1]
        new_width = round(new_height * source_ratio)
    else:
        new_width = size[0]
        new_height = round(new_width / source_ratio)
    resized = image.resize((new_width, new_height), Image.Resampling.LANCZOS)
    left = (new_width - size[0]) // 2
    top = (new_height - size[1]) // 2
    return resized.crop((left, top, left + size[0], top + size[1]))


def _fit_font(draw: ImageDraw.ImageDraw, text: str, max_width: int, start: int, minimum: int = 28) -> ImageFont.FreeTypeFont:
    size = start
    while size > minimum:
        font = _font(size, bold=True)
        if draw.textbbox((0, 0), text, font=font)[2] <= max_width:
            return font
        size -= 2
    return _font(minimum, bold=True)


def _wrap(draw: ImageDraw.ImageDraw, text: str, font: ImageFont.FreeTypeFont, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.splitlines() or [""]:
        words = paragraph.split(" ") if " " in paragraph else list(paragraph)
        current = ""
        separator = " " if " " in paragraph else ""
        for word in words:
            candidate = word if not current else current + separator + word
            if draw.textbbox((0, 0), candidate, font=font)[2] <= max_width:
                current = candidate
            else:
                if current:
                    lines.append(current)
                current = word
        lines.append(current)
    return lines


def _draw_mana(draw: ImageDraw.ImageDraw, mana: str, right: int, center_y: int) -> int:
    tokens = [token for token in mana.split() if token]
    diameter = 62
    gap = 8
    x = right - len(tokens) * diameter - max(0, len(tokens) - 1) * gap
    colors = {"W": (245, 239, 193), "U": (105, 183, 222), "B": (150, 142, 145), "R": (229, 111, 76), "G": (109, 169, 112)}
    font = _font(34, bold=True)
    for token in tokens:
        fill = colors.get(token.upper(), (214, 209, 197))
        box = (x, center_y - diameter // 2, x + diameter, center_y + diameter // 2)
        draw.ellipse(box, fill=fill, outline=(20, 18, 16), width=5)
        bbox = draw.textbbox((0, 0), token, font=font)
        draw.text((x + (diameter - (bbox[2] - bbox[0])) / 2, center_y - (bbox[3] - bbox[1]) / 2 - 4), token, font=font, fill=(20, 18, 16))
        x += diameter + gap
    return x


def render_card(
    art_path: Path,
    output_path: Path,
    fields: Dict[str, str],
    set_code: str,
    collector_number: str,
    artist: str,
) -> None:
    with Image.open(art_path) as source:
        card = _cover(source.convert("RGB"), (WIDTH, HEIGHT)).convert("RGBA")

    overlay = Image.new("RGBA", card.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    draw.rounded_rectangle((28, 28, WIDTH - 28, HEIGHT - 28), radius=58, outline=(0, 0, 0, 255), width=28)

    name_frame = load_svg_layer(NAME_FRAME_SVG, (1350, 226))
    overlay.alpha_composite(name_frame, (75, 70))
    mana_width = max(190, len(fields.get("ManaCost", "").split()) * 70 + 36)
    name_font = _fit_font(draw, fields.get("Name", ""), WIDTH - 220 - mana_width, 78, 42)
    draw.text((105, 113), fields.get("Name", ""), font=name_font, fill=(255, 250, 235), stroke_width=2, stroke_fill=(30, 20, 12))
    _draw_mana(draw, fields.get("ManaCost", ""), WIDTH - 105, 164)

    type_frame = load_svg_layer(TEXT_FRAME_SVG, (1340, 120))
    overlay.alpha_composite(type_frame, (80, 1140))
    type_font = _fit_font(draw, fields.get("Types", ""), WIDTH - 220, 56, 34)
    draw.text((116, 1172), fields.get("Types", ""), font=type_font, fill=(255, 250, 235))

    rules_frame = load_svg_layer(TEXT_FRAME_SVG, (1324, 786))
    overlay.alpha_composite(rules_frame, (88, 1265))
    oracle = fields.get("Oracle", "")
    font_size = 48
    while font_size >= 30:
        rules_font = _font(font_size)
        lines = _wrap(draw, oracle, rules_font, WIDTH - 260)
        line_height = font_size + 14
        if len(lines) * line_height <= 630:
            break
        font_size -= 2
    y = 1325
    for line in lines:
        draw.text((125, y), line, font=rules_font, fill=(255, 255, 255), stroke_width=1, stroke_fill=(0, 0, 0))
        y += line_height

    pt = fields.get("PT", "")
    if pt:
        pt_box = (WIDTH - 365, 1870, WIDTH - 85, 2045)
        draw.rounded_rectangle(pt_box, radius=40, fill=(198, 156, 57, 250), outline=(72, 48, 20, 255), width=10)
        pt_font = _fit_font(draw, pt, 220, 86, 50)
        bbox = draw.textbbox((0, 0), pt, font=pt_font)
        draw.text((WIDTH - 225 - (bbox[2] - bbox[0]) / 2, 1903), pt, font=pt_font, fill=(15, 12, 10))

    footer_font = _font(29, bold=True)
    footer = f"{set_code}  {collector_number}  •  {artist}"
    draw.text((92, 2048), footer, font=footer_font, fill=(255, 255, 255), stroke_width=2, stroke_fill=(0, 0, 0))

    result = Image.alpha_composite(card, overlay).convert("RGB")
    output_path.parent.mkdir(parents=True, exist_ok=True)
    result.save(output_path, "JPEG", quality=95, subsampling=0)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a Cardsmith-style full-art Forge card image.")
    parser.add_argument("--art", type=Path, required=True)
    parser.add_argument("--card", type=Path, required=True)
    parser.add_argument("--set-code", required=True)
    parser.add_argument("--collector-number", required=True)
    parser.add_argument("--artist", default="Custom")
    parser.add_argument("--translations", type=Path, default=DEFAULT_TRANSLATIONS)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    fields = localized_fields(parse_card(args.card), load_translations(args.translations))
    render_card(args.art, args.output, fields, args.set_code, args.collector_number, args.artist)


if __name__ == "__main__":
    main()
