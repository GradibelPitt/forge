import hashlib
import unittest
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
FORGE_ROOT = ROOT.parent
CARD = ROOT / "cards" / "green" / "青玉魔像.txt"
EDITION = ROOT / "editions" / "Placeholder_Set.txt"
ART_BACKUP = ROOT / "tools" / "card-artwork" / "Jade_Golem_照片-1.jpg"
ART = ROOT / "cards" / "pictures" / "PH01" / "青玉魔像.artcrop.jpg"
ZH_CN = FORGE_ROOT / "forge-gui" / "res" / "languages" / "cardnames-zh-CN.txt"

ORACLE = (
    "本局游戏每有一个青玉魔像在你的操控下进过战场，青玉魔像便得+1/+1。"
)
ENGLISH_ORACLE = (
    "CARDNAME gets +1/+1 for each Jade Golem that entered the battlefield "
    "under your control this game."
)


class JadeGolemContractTest(unittest.TestCase):
    def test_first_golem_creates_its_controllers_counter_emblem_immediately(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        self.assertIn("Name:青玉魔像", lines)
        self.assertIn("ManaCost:G", lines)
        self.assertIn("Types:Artifact Creature Golem", lines)
        self.assertIn("PT:0/0", lines)
        self.assertFalse(any("Mode$ NewGame" in line for line in lines))

        create_trigger = next(
            line for line in lines if line.startswith("T:Mode$ ChangesZone")
        )
        self.assertIn("ValidCard$ Card.Self", create_trigger)
        self.assertIn("Destination$ Battlefield", create_trigger)
        self.assertIn("Execute$ CreateJadeCounterEmblem", create_trigger)
        self.assertIn("CheckSVar$ JadeEmblemCount", create_trigger)
        self.assertIn("SVarCompare$ EQ0", create_trigger)
        self.assertIn("Static$ True", create_trigger)

        create_emblem = next(
            line
            for line in lines
            if line.startswith("SVar:CreateJadeCounterEmblem:")
        )
        self.assertIn("DB$ Effect", create_emblem)
        self.assertIn("Name$ Emblem — 青玉魔像计数", create_emblem)
        self.assertIn("Triggers$ InitializeJadeCount,TrackJadeGolems", create_emblem)
        self.assertIn("StaticAbilities$ ScaleJadeGolems", create_emblem)
        self.assertIn("Duration$ Permanent", create_emblem)
        self.assertIn("Unique$ True", create_emblem)
        self.assertIn("ConditionCheckSVar$ JadeEmblemCount", create_emblem)
        self.assertIn("ConditionSVarCompare$ EQ0", create_emblem)
        self.assertIn(
            "SVar:JadeEmblemCount:Count$ValidCommand "
            "Effect.YouCtrl+namedEmblem — 青玉魔像计数",
            lines,
        )

        initialize = next(
            line for line in lines if line.startswith("SVar:InitializeJadeCount:")
        )
        self.assertIn("Mode$ Always", initialize)
        self.assertIn("TriggerZones$ Command", initialize)
        self.assertIn("CheckSVar$ JadeCount", initialize)
        self.assertIn("SVarCompare$ EQ0", initialize)
        self.assertIn("Static$ True", initialize)
        self.assertIn("Execute$ AddInitialJadeCount", initialize)

        initial_counter = next(
            line for line in lines if line.startswith("SVar:AddInitialJadeCount:")
        )
        self.assertIn("Defined$ Self", initial_counter)
        self.assertIn("CounterType$ STORAGE", initial_counter)
        self.assertIn("CounterNum$ 1", initial_counter)

    def test_emblem_tracks_only_its_controller_and_scales_all_current_golems(self):
        lines = CARD.read_text(encoding="utf-8").splitlines()

        tracker = next(
            line for line in lines if line.startswith("SVar:TrackJadeGolems:")
        )
        self.assertIn("Destination$ Battlefield", tracker)
        self.assertIn("ValidCard$ Card.named青玉魔像+YouCtrl", tracker)
        self.assertIn("TriggerZones$ Command", tracker)
        self.assertIn("Static$ True", tracker)
        self.assertIn("Execute$ AddJadeCount", tracker)

        scale = next(
            line for line in lines if line.startswith("SVar:ScaleJadeGolems:")
        )
        self.assertIn("Mode$ Continuous", scale)
        self.assertIn("Affected$ Card.named青玉魔像+YouCtrl", scale)
        self.assertIn("AddPower$ JadeCount", scale)
        self.assertIn("AddToughness$ JadeCount", scale)

        increment = next(
            line for line in lines if line.startswith("SVar:AddJadeCount:")
        )
        self.assertIn("Defined$ Self", increment)
        self.assertIn("CounterType$ STORAGE", increment)
        self.assertIn("CounterNum$ 1", increment)
        self.assertIn("SVar:JadeCount:Count$CardCounters.STORAGE", lines)
        self.assertFalse(any("CounterType$ P1P1" in line for line in lines))
        self.assertIn(f"Oracle:{ENGLISH_ORACLE}", lines)

    def test_registration_localization_art_and_documentation(self):
        self.assertIn(
            "105 C 青玉魔像 @Custom",
            EDITION.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            f"青玉魔像|青玉魔像|神器生物～魔像|{ORACLE}",
            ZH_CN.read_text(encoding="utf-8").splitlines(),
        )
        self.assertIn(
            "| 青玉魔像 | `{G}`，0/0 神器生物～魔像 | "
            "`cards/green/青玉魔像.txt` | 105 |",
            (ROOT / "CARDS.md").read_text(encoding="utf-8"),
        )

        self.assertTrue(ART_BACKUP.is_file())
        self.assertEqual(
            "2CE67614CF4364078404C53D316919E60DFC3D6E5B9F56925FBE68BC7459C1C8",
            hashlib.sha256(ART_BACKUP.read_bytes()).hexdigest().upper(),
        )
        self.assertTrue(ART.is_file())
        with Image.open(ART) as image:
            self.assertEqual("JPEG", image.format)
            self.assertEqual("RGB", image.mode)
            self.assertAlmostEqual(1.37, image.width / image.height, delta=0.01)


if __name__ == "__main__":
    unittest.main()
