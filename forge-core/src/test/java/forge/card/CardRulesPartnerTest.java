package forge.card;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardRulesPartnerTest {
    private static CardRules commander(final String name, final String partnerKeyword) {
        return CardRules.fromScript(List.of(
                "Name:" + name,
                "ManaCost:0",
                "Types:Legendary Creature Human",
                "PT:1/1",
                "K:" + partnerKeyword,
                "Oracle:Test commander."
        ));
    }

    @Test
    void unicodeTypedPartnersCanShareTheCommandZoneOnlyWithinTheirGroup() {
        final CardRules finley = commander("海中向导芬利爵士", "Partner:探险者协会");
        final CardRules elise = commander("启迪者伊利斯", "Partner:探险者协会");
        final CardRules ordinaryPartner = commander("Ordinary Partner", "Partner");
        final CardRules otherGroup = commander("Other Group", "Partner:另一分组");

        assertTrue(finley.canBePartnerCommander());
        assertTrue(elise.canBePartnerCommander());
        assertTrue(finley.canBePartnerCommanders(elise));
        assertTrue(elise.canBePartnerCommanders(finley));
        assertFalse(finley.canBePartnerCommanders(ordinaryPartner));
        assertFalse(finley.canBePartnerCommanders(otherGroup));
    }
}
