package forge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparableOpTest {
    @Test
    void comparesValuesOutsideTheIntegerCache() {
        assertTrue(ComparableOp.EQUALS.apply(Integer.valueOf("1000"), Integer.valueOf("1000")));
        assertFalse(ComparableOp.NOT_EQUALS.apply(Integer.valueOf("1000"), Integer.valueOf("1000")));
        assertTrue(ComparableOp.NOT_EQUALS.apply(1000, 1001));
        assertTrue(ComparableOp.LESS_THAN.apply(1000, 1001));
        assertTrue(ComparableOp.GREATER_THAN.apply(1001, 1000));
        assertTrue(ComparableOp.GT_OR_EQUAL.apply(1000, 1000));
        assertTrue(ComparableOp.LT_OR_EQUAL.apply(1000, 1000));
        assertFalse(ComparableOp.LESS_THAN.apply(1000, 1000));
        assertFalse(ComparableOp.GREATER_THAN.apply(1000, 1000));
    }

    @Test
    void nullOperandsNeverMatch() {
        for (ComparableOp operator : ComparableOp.values()) {
            assertFalse(operator.apply(null, 1));
            assertFalse(operator.apply(1, null));
            assertFalse(operator.apply(null, null));
        }
    }
}
