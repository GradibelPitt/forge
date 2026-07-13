package forge.localinstance.skin;

import forge.card.mana.ManaCostShard;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FSkinPropHybridPhyrexianTest {
    @Test
    void mapsEveryTwoColorPhyrexianShardToItsDedicatedSprite() {
        final Map<ManaCostShard, FSkinProp> expected = new LinkedHashMap<>();
        expected.put(ManaCostShard.WUP, FSkinProp.IMG_MANA_PHRYX_WU);
        expected.put(ManaCostShard.WBP, FSkinProp.IMG_MANA_PHRYX_WB);
        expected.put(ManaCostShard.UBP, FSkinProp.IMG_MANA_PHRYX_UB);
        expected.put(ManaCostShard.URP, FSkinProp.IMG_MANA_PHRYX_UR);
        expected.put(ManaCostShard.BRP, FSkinProp.IMG_MANA_PHRYX_BR);
        expected.put(ManaCostShard.BGP, FSkinProp.IMG_MANA_PHRYX_BG);
        expected.put(ManaCostShard.RWP, FSkinProp.IMG_MANA_PHRYX_RW);
        expected.put(ManaCostShard.RGP, FSkinProp.IMG_MANA_PHRYX_RG);
        expected.put(ManaCostShard.GWP, FSkinProp.IMG_MANA_PHRYX_GW);
        expected.put(ManaCostShard.GUP, FSkinProp.IMG_MANA_PHRYX_GU);

        assertEquals(10, expected.size());
        for (final Map.Entry<ManaCostShard, FSkinProp> entry : expected.entrySet()) {
            assertSame(entry.getValue(), FSkinProp.SHARD_IMG.get(entry.getKey()), entry.getKey().toString());
            assertSame(entry.getValue(), FSkinProp.MANA_IMG.get(entry.getKey().getImageKey()), entry.getKey().toString());
        }
        assertEquals(10, expected.values().stream().distinct().count());
    }
}
