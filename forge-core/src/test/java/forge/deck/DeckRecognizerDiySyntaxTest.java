package forge.deck;

import org.junit.jupiter.api.Test;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class DeckRecognizerDiySyntaxTest {
    @Test void diyNamesAndOfficialQuestionMarksRemainWhole() {
        Pattern pattern = Pattern.compile(DeckRecognizer.REX_CARD_NAME);
        for (String name : new String[] { "十字军光环", "诺兹多姆，青铜守护巨龙", "米斯塔·维斯塔",
                "Emblem — Aya's Cunning Treasure", "test_解除构筑限制", "Continue?", "Lightning Bolt" }) {
            var match = pattern.matcher(name);
            assertTrue(match.matches(), name);
            assertEquals(name, match.group(DeckRecognizer.REGRP_CARD));
        }
    }
    @Test void diyTokenEditionAndExistingOfficialCodesAreAccepted() {
        Pattern pattern = Pattern.compile(DeckRecognizer.REX_SET_CODE);
        for (String code : new String[] { "PH01", "TOKEN_HS", "BT3K", "TMP", "HOC" }) {
            assertTrue(pattern.matcher(code).matches(), code);
        }
        assertFalse(pattern.matcher("../PH01").matches());
    }
}
