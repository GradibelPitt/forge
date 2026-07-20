package forge.view;

import java.util.function.BooleanSupplier;

final class TabClosePolicy {
    private TabClosePolicy() {
    }

    static boolean allowClose(final boolean force,
            final BooleanSupplier closeHandler) {
        return force || closeHandler.getAsBoolean();
    }
}
