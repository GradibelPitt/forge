package forge.card.mana;

import forge.card.MagicColor;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaCostShardHybridPhyrexianTest {
    private static final Map<String, ManaCostShard> ALL_TWO_COLOR_PHYREXIAN_SHARDS = new LinkedHashMap<>();

    static {
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("W/U/P", ManaCostShard.WUP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("W/B/P", ManaCostShard.WBP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("U/B/P", ManaCostShard.UBP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("U/R/P", ManaCostShard.URP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("B/R/P", ManaCostShard.BRP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("B/G/P", ManaCostShard.BGP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("R/W/P", ManaCostShard.RWP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("R/G/P", ManaCostShard.RGP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("G/W/P", ManaCostShard.GWP);
        ALL_TWO_COLOR_PHYREXIAN_SHARDS.put("G/U/P", ManaCostShard.GUP);
    }

    @Test
    void definesEveryTwoColorPhyrexianCombination() {
        assertEquals(10, ALL_TWO_COLOR_PHYREXIAN_SHARDS.size());

        for (final Map.Entry<String, ManaCostShard> entry : ALL_TWO_COLOR_PHYREXIAN_SHARDS.entrySet()) {
            final String symbol = entry.getKey();
            final ManaCostShard shard = entry.getValue();

            assertSame(shard, ManaCostShard.parseNonGeneric(symbol), symbol);
            assertEquals("{" + symbol + "}", shard.toString(), symbol);
            assertEquals(symbol.replace("/", ""), shard.getImageKey(), symbol);
            assertTrue(shard.isPhyrexian(), symbol);
            assertTrue(shard.isMultiColor(), symbol);
            assertEquals(2, Integer.bitCount(shard.getColorMask()), symbol);
        }
    }

    @Test
    void acceptsEitherPrintedColorAndRejectsTheOtherThreeColors() {
        final byte[] colors = {
                MagicColor.WHITE,
                MagicColor.BLUE,
                MagicColor.BLACK,
                MagicColor.RED,
                MagicColor.GREEN
        };

        for (final Map.Entry<String, ManaCostShard> entry : ALL_TWO_COLOR_PHYREXIAN_SHARDS.entrySet()) {
            final ManaCostShard shard = entry.getValue();
            for (final byte color : colors) {
                assertEquals(
                        shard.isColor(color),
                        shard.canBePaidWithManaOfColor(color),
                        entry.getKey() + " payment with color mask " + color
                );
            }
        }
    }

    @Test
    void parsingIsIndependentOfTheOrderUsedByCardScripts() {
        for (final Map.Entry<String, ManaCostShard> entry : ALL_TWO_COLOR_PHYREXIAN_SHARDS.entrySet()) {
            final String[] parts = entry.getKey().split("/");
            final String reversed = parts[1] + "/" + parts[0] + "/P";

            assertSame(entry.getValue(), ManaCostShard.parseNonGeneric(reversed), reversed);
            assertFalse(reversed.equals(entry.getValue().toShortString()));
        }
    }
}
