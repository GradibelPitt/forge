package forge.game.ability.effects;

import forge.game.GameEntityCounterTable;
import forge.game.ability.AbilityKey;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.spellability.AbilitySub;
import forge.game.spellability.SpellAbility;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Paths;
import java.util.Collections;
import forge.util.Localizer;

public class BranchEffectTest {
    @BeforeClass
    public void initializeLocalizer() {
        final String languages = Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString();
        Localizer.getInstance().initialize("en-US", languages);
    }

    @Test
    public void selectedBranchInheritsReplacementContext() {
        final Card host = new Card(1, null);
        final SpellAbility parent = new AbilitySub(ApiType.Branch, host, null, Collections.emptyMap());
        final SpellAbility selectedBranch = new AbilitySub(ApiType.Cleanup, host, null, Collections.emptyMap());
        final GameEntityCounterTable counterTable = new GameEntityCounterTable();
        parent.setReplacingObject(AbilityKey.CounterTable, counterTable);

        BranchEffect.inheritReplacingObjects(parent, selectedBranch);

        Assert.assertSame(selectedBranch.getReplacingObject(AbilityKey.CounterTable), counterTable);
    }
}
