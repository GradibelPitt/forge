from pathlib import Path
import tempfile
import unittest

from PIL import Image

from tools.generate_full_art_card import (
    load_translations,
    load_svg_layer,
    localized_fields,
    parse_card,
    render_card,
)


ROOT = Path(__file__).resolve().parents[1]


class FullArtCardGeneratorTest(unittest.TestCase):
    def test_name_frame_svg_rasterizes_to_a_visible_rgba_layer(self):
        svg = ROOT / "tools" / "MTG_牌框_SVG_完全透明内框_v2" / "牌名框_完全透明内框.svg"
        layer = load_svg_layer(svg, (1200, 201))

        self.assertEqual("RGBA", layer.mode)
        self.assertEqual((1200, 201), layer.size)
        self.assertGreater(layer.getchannel("A").getbbox()[2], 0)

    def test_parse_card_reads_stable_forge_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            card = Path(tmp) / "card.txt"
            card.write_text(
                "Name:测试传奇\n"
                "ManaCost:4 R R\n"
                "Types:Legendary Creature Gnoll\n"
                "PT:10/10\n"
                "Oracle:第一行。\\n第二行。\n",
                encoding="utf-8",
            )

            fields = parse_card(card)

            self.assertEqual("测试传奇", fields["Name"])
            self.assertEqual("4 R R", fields["ManaCost"])
            self.assertEqual("Legendary Creature Gnoll", fields["Types"])
            self.assertEqual("10/10", fields["PT"])
            self.assertEqual("第一行。\n第二行。", fields["Oracle"])

    def test_render_card_outputs_cardsmith_style_jpeg(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            art = root / "art.jpg"
            output = root / "output.jpg"
            Image.new("RGB", (900, 1200), (90, 35, 25)).save(art)

            render_card(
                art_path=art,
                output_path=output,
                fields={
                    "Name": "测试传奇",
                    "ManaCost": "4 R R",
                    "Types": "Legendary Creature Gnoll",
                    "PT": "10/10",
                    "Oracle": "第一行规则文字。\n第二行规则文字。",
                },
                set_code="PH01",
                collector_number="8a",
                artist="Custom",
            )

            self.assertTrue(output.is_file())
            with Image.open(output) as image:
                self.assertEqual("JPEG", image.format)
                self.assertEqual((1500, 2092), image.size)
                self.assertNotEqual(image.getpixel((125, 170)), image.getpixel((100, 100)))

    def test_matching_zh_cn_entry_overrides_display_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            translation = Path(tmp) / "cardnames-zh-CN.txt"
            translation.write_text(
                "Chainbreaker Hogger|破链灾星霍格|传奇生物～豺狼人|超级延势\\n中文规则。\n",
                encoding="utf-8",
            )
            fields = {
                "Name": "Chainbreaker Hogger",
                "Types": "Legendary Creature Gnoll",
                "Oracle": "English rules.",
                "ManaCost": "4 R R R R",
                "PT": "10/10",
            }

            localized = localized_fields(fields, load_translations(translation))

            self.assertEqual("破链灾星霍格", localized["Name"])
            self.assertEqual("传奇生物～豺狼人", localized["Types"])
            self.assertEqual("超级延势\n中文规则。", localized["Oracle"])
            self.assertEqual("4 R R R R", localized["ManaCost"])

    def test_missing_zh_cn_entry_keeps_script_fields(self):
        fields = {"Name": "No Translation", "Types": "Creature", "Oracle": "Rules."}
        self.assertEqual(fields, localized_fields(fields, {}))


if __name__ == "__main__":
    unittest.main()
