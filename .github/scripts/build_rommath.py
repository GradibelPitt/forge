from __future__ import annotations

import io
import json
from pathlib import Path

import requests
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
CUSTOM = ROOT / "custom"
CARD_NAME = "大法师罗曼斯"
CARD_PATH = CUSTOM / "cards" / "blue" / f"{CARD_NAME}.txt"
TEST_PATH = CUSTOM / "tests" / "test_grand_magister_rommath.py"
EDITION_PATH = CUSTOM / "editions" / "Placeholder_Set.txt"
LOCALIZATION_PATH = ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
CARDS_DOC_PATH = CUSTOM / "CARDS.md"
ART_BACKUP_PATH = CUSTOM / "tools" / "card-artwork" / "RLK_803_art.jpg"
ART_SOURCE_PATH = CUSTOM / "tools" / "card-artwork" / "RLK_803_art.source.txt"
ART_CROP_PATH = CUSTOM / "cards" / "pictures" / "PH01" / f"{CARD_NAME}.artcrop.jpg"

ORACLE_ZH = (
    "当你施放大法师罗曼斯时，将你坟墓场中的每张瞬间牌和法术牌移回你手上。"
    "本回合中，你可以不支付这些牌的法术力费用来施放它们。"
)

CARD_TEXT = f"""Name:{CARD_NAME}
ManaCost:5 U U U U
Types:Legendary Creature Human Wizard
PT:5/7
T:Mode$ SpellCast | ValidCard$ Card.Self | TriggerZones$ Stack | Execute$ TrigReturnSpells | TriggerDescription$ {ORACLE_ZH}
SVar:TrigReturnSpells:DB$ ChangeZoneAll | Origin$ Graveyard | Destination$ Hand | ChangeType$ Instant.YouOwn,Sorcery.YouOwn | RememberChanged$ True | SubAbility$ DBFreeCastEffect
SVar:DBFreeCastEffect:DB$ Effect | RememberObjects$ Remembered | StaticAbilities$ FreeCast | ForgetOnMoved$ Hand | Duration$ UntilEndOfTurn | SubAbility$ DBCleanupReturned
SVar:FreeCast:Mode$ Continuous | Affected$ Instant.IsRemembered,Sorcery.IsRemembered | MayPlay$ True | MayPlayWithoutManaCost$ True | AffectedZone$ Hand | Description$ 本回合中，你可以不支付这些牌的法术力费用来施放它们。
SVar:DBCleanupReturned:DB$ Cleanup | ClearRemembered$ True
DeckHints:Type$Instant|Sorcery
DeckHas:Ability$Graveyard
Oracle:{ORACLE_ZH}
"""

TEST_TEXT = f'''import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "blue" / "{CARD_NAME}.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "RLK_803_art.jpg"
ART_SOURCE = ROOT / "tools" / "card-artwork" / "RLK_803_art.source.txt"
ART = ROOT / "cards" / "pictures" / "PH01" / "{CARD_NAME}.artcrop.jpg"


class GrandMagisterRommathContractTest(unittest.TestCase):
    def test_characteristics(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn("Name:{CARD_NAME}", text)
        self.assertIn("ManaCost:5 U U U U", text)
        self.assertIn("Types:Legendary Creature Human Wizard", text)
        self.assertIn("PT:5/7", text)

    def test_cast_trigger_returns_all_spells_and_grants_free_cast(self):
        text = CARD.read_text(encoding="utf-8")
        self.assertIn(
            "T:Mode$ SpellCast | ValidCard$ Card.Self | TriggerZones$ Stack | "
            "Execute$ TrigReturnSpells",
            text,
        )
        self.assertIn(
            "SVar:TrigReturnSpells:DB$ ChangeZoneAll | Origin$ Graveyard | "
            "Destination$ Hand | ChangeType$ Instant.YouOwn,Sorcery.YouOwn | "
            "RememberChanged$ True | SubAbility$ DBFreeCastEffect",
            text,
        )
        self.assertIn(
            "SVar:DBFreeCastEffect:DB$ Effect | RememberObjects$ Remembered | "
            "StaticAbilities$ FreeCast | ForgetOnMoved$ Hand | "
            "Duration$ UntilEndOfTurn | SubAbility$ DBCleanupReturned",
            text,
        )
        self.assertIn(
            "SVar:FreeCast:Mode$ Continuous | "
            "Affected$ Instant.IsRemembered,Sorcery.IsRemembered | "
            "MayPlay$ True | MayPlayWithoutManaCost$ True | "
            "AffectedZone$ Hand",
            text,
        )
        self.assertIn(
            "SVar:DBCleanupReturned:DB$ Cleanup | ClearRemembered$ True",
            text,
        )

    def test_registration_localization_and_hswiki_art_crop(self):
        edition = EDITION.read_text(encoding="utf-8")
        self.assertIn("139 M {CARD_NAME} @Custom", edition)

        localization = ZH_CN.read_text(encoding="utf-8").splitlines()
        line = next(item for item in localization if item.startswith("{CARD_NAME}|{CARD_NAME}|"))
        self.assertIn("传奇生物～人类／法术师", line)
        self.assertIn("将你坟墓场中的每张瞬间牌和法术牌移回你手上", line)
        self.assertIn("不支付这些牌的法术力费用来施放它们", line)

        self.assertTrue(ART_BACKUP.is_file())
        self.assertTrue(ART_SOURCE.is_file())
        self.assertIn("hearthstone.wiki.gg", ART_SOURCE.read_text(encoding="utf-8"))
        self.assertTrue(ART.is_file())
        with Image.open(ART_BACKUP) as image:
            self.assertGreaterEqual(image.width, 500)
            self.assertGreaterEqual(image.height, 500)
            self.assertAlmostEqual(1.0, image.width / image.height, delta=0.25)
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
'''


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8", newline="\n")


def append_unique_line(path: Path, line: str, identity: str) -> None:
    text = path.read_text(encoding="utf-8")
    if identity in text:
        return
    if text and not text.endswith("\n"):
        text += "\n"
    path.write_text(text + line + "\n", encoding="utf-8", newline="\n")


def update_cards_doc() -> None:
    text = CARDS_DOC_PATH.read_text(encoding="utf-8")
    if f"| {CARD_NAME} |" in text:
        return
    section = text.find("## Gameplay cards")
    if section < 0:
        raise RuntimeError("CARDS.md is missing the Gameplay cards section")
    header = "| Card | Cost / type / stats | Script | Set / # | Behavior summary |"
    header_at = text.find(header, section)
    if header_at < 0:
        raise RuntimeError("CARDS.md is missing the gameplay table header")
    separator_end = text.find("\n", text.find("\n", header_at) + 1)
    if separator_end < 0:
        raise RuntimeError("CARDS.md gameplay table is malformed")
    row = (
        f"\n| {CARD_NAME} | `{{5}}{{U}}{{U}}{{U}}{{U}}`，5/7 传奇生物～人类／法术师 | "
        f"`cards/blue/{CARD_NAME}.txt` | 139 | 施放触发：将自己坟墓场中的每张瞬间牌和法术牌移回手上；"
        "仅这些被移回的牌可在本回合中不支付法术力费用施放。 |"
    )
    text = text[:separator_end] + row + text[separator_end:]
    CARDS_DOC_PATH.write_text(text, encoding="utf-8", newline="\n")


def get_hswiki_art() -> tuple[bytes, str]:
    headers = {
        "User-Agent": "ForgeDIY/1.0 (personal noncommercial custom-card project; GitHub Actions)"
    }
    api_url = (
        "https://hearthstone.wiki.gg/api.php?action=query&format=json&prop=imageinfo&"
        "iiprop=url%7Csize&titles=File%3ARLK_803_art.jpg"
    )
    candidates: list[str] = []
    try:
        response = requests.get(api_url, headers=headers, timeout=30)
        response.raise_for_status()
        payload = response.json()
        pages = payload.get("query", {}).get("pages", {})
        for page in pages.values():
            info = page.get("imageinfo") or []
            if info and info[0].get("url"):
                candidates.append(info[0]["url"])
    except Exception as exc:
        print(f"MediaWiki API lookup failed: {exc}")

    candidates.extend(
        [
            "https://hearthstone.wiki.gg/images/f/ff/RLK_803_art.jpg",
            "https://hearthstone.wiki.gg/wiki/Special:Redirect/file/RLK_803_art.jpg",
        ]
    )

    errors: list[str] = []
    for url in dict.fromkeys(candidates):
        try:
            response = requests.get(url, headers=headers, timeout=45)
            response.raise_for_status()
            content_type = response.headers.get("content-type", "").lower()
            if "image" not in content_type:
                raise RuntimeError(f"unexpected content type {content_type!r}")
            with Image.open(io.BytesIO(response.content)) as image:
                image.verify()
            return response.content, url
        except Exception as exc:
            errors.append(f"{url}: {exc}")

    raise RuntimeError("Could not download RLK_803 art from hswiki.gg:\n" + "\n".join(errors))


def crop_art(data: bytes) -> None:
    ART_BACKUP_PATH.parent.mkdir(parents=True, exist_ok=True)
    ART_BACKUP_PATH.write_bytes(data)

    with Image.open(io.BytesIO(data)) as source:
        source.load()
        image = source.convert("RGB")

    width, height = image.size
    if width < 500 or height < 500:
        raise RuntimeError(f"hswiki art is unexpectedly small: {width}x{height}")
    if not 0.75 <= width / height <= 1.25:
        raise RuntimeError(
            f"Downloaded image does not look like square Hearthstone full art: {width}x{height}"
        )

    target_ratio = 1.37
    if width / height < target_ratio:
        crop_height = round(width / target_ratio)
        excess = height - crop_height
        # Rommath's staff head is near the top-right; preserve the top and remove
        # most of the unused lower area while keeping his face, fireball, and staff.
        top = max(0, round(excess * 0.15))
        box = (0, top, width, top + crop_height)
    else:
        crop_width = round(height * target_ratio)
        left = max(0, (width - crop_width) // 2)
        box = (left, 0, left + crop_width, height)

    crop = image.crop(box)
    ART_CROP_PATH.parent.mkdir(parents=True, exist_ok=True)
    crop.save(ART_CROP_PATH, format="JPEG", quality=95, subsampling=0, optimize=True)
    print(f"Saved crop {ART_CROP_PATH.relative_to(ROOT)} at {crop.width}x{crop.height}")


def main() -> None:
    write_text(CARD_PATH, CARD_TEXT)
    write_text(TEST_PATH, TEST_TEXT)
    append_unique_line(
        EDITION_PATH,
        f"139 M {CARD_NAME} @Custom",
        f" {CARD_NAME} ",
    )
    append_unique_line(
        LOCALIZATION_PATH,
        f"{CARD_NAME}|{CARD_NAME}|传奇生物～人类／法术师|{ORACLE_ZH}",
        f"\n{CARD_NAME}|",
    )
    update_cards_doc()

    art_data, source_url = get_hswiki_art()
    crop_art(art_data)
    write_text(
        ART_SOURCE_PATH,
        "Source page: https://hearthstone.wiki.gg/wiki/Grand_Magister_Rommath\n"
        f"Downloaded image: {source_url}\n"
        "Card ID: RLK_803\n"
        "Use: personal, noncommercial Forge DIY card art crop.\n",
    )

    print(json.dumps({"card": CARD_NAME, "collector": 139, "art_source": source_url}, ensure_ascii=False))


if __name__ == "__main__":
    main()
