import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CARDS = (
    ROOT / "cards" / "multicolor" / "污染者玛法里奥.txt",
    ROOT / "cards" / "white" / "黑锋骑士乌瑟尔.txt",
    ROOT / "cards" / "blue" / "冰霜女巫吉安娜.txt",
    ROOT / "cards" / "black" / "鲜血掠夺者古尔丹.txt",
    ROOT / "cards" / "multicolor" / "天灾领主加尔鲁什.txt",
    ROOT / "cards" / "multicolor" / "虚空之影瓦莉拉.txt",
    ROOT / "cards" / "multicolor" / "死亡猎手雷克萨.txt",
    ROOT / "cards" / "multicolor" / "暗影收割者安度因.txt",
)


class DeathKnightCommanderContractTest(unittest.TestCase):
    def test_all_eight_death_knights_can_be_commanders(self):
        for card in CARDS:
            with self.subTest(card=card.name):
                self.assertTrue(card.is_file(), card)
                text = card.read_text(encoding="utf-8")
                self.assertIn("Types:Legendary Planeswalker ", text)
                self.assertIn("K:CARDNAME can be your commander.", text)

                oracle = next(
                    line for line in text.splitlines() if line.startswith("Oracle:")
                )
                self.assertIn("本牌可用作你的指挥官。", oracle)
                self.assertIn("can be your commander", oracle)


if __name__ == "__main__":
    unittest.main()
