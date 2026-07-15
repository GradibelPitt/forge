package forge.gamemodes.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the root-game and currently active controller sets by identity.
 */
final class ActiveControllerTracker<T> {
    private Object gameIdentity;
    private List<T> baselineControllers = List.of();
    private List<T> activeControllers = List.of();

    synchronized void reset(final Object game, final Collection<? extends T> baseline) {
        gameIdentity = Objects.requireNonNull(game);
        baselineControllers = identityDistinctCopy(baseline);
        activeControllers = baselineControllers;
    }

    synchronized void replaceActive(final Object game, final Collection<? extends T> active) {
        if (gameIdentity != game) {
            return;
        }
        activeControllers = identityDistinctCopy(active);
    }

    synchronized List<T> snapshotForFailure(final Object game) {
        if (gameIdentity != game) {
            return List.of();
        }
        final List<T> result = new ArrayList<>(baselineControllers);
        for (final T controller : activeControllers) {
            addByIdentity(result, controller);
        }
        return List.copyOf(result);
    }

    synchronized void clear(final Object game) {
        if (gameIdentity != game) {
            return;
        }
        gameIdentity = null;
        baselineControllers = List.of();
        activeControllers = List.of();
    }

    private static <T> List<T> identityDistinctCopy(final Collection<? extends T> controllers) {
        Objects.requireNonNull(controllers);
        final List<T> result = new ArrayList<>(controllers.size());
        for (final T controller : controllers) {
            addByIdentity(result, Objects.requireNonNull(controller));
        }
        return List.copyOf(result);
    }

    private static <T> void addByIdentity(final List<T> controllers, final T candidate) {
        for (final T controller : controllers) {
            if (controller == candidate) {
                return;
            }
        }
        controllers.add(candidate);
    }
}
