package forge.game;

import org.tinylog.Logger;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Per-game actor used while the game thread is synchronously waiting for UI
 * input. The lane owns no executor: the waiting game thread recursively pumps
 * the accepted work, so nested inputs cannot deadlock and different games
 * remain independent. The queue bound limits accepted action count, not wall
 * clock latency: an already running or accepted action may itself be slow.
 */
final class GameInputActionLane {
    static final int MAX_PENDING_ACTIONS = 4_096;

    static final class Scope {
        private final GameInputActionLane lane;
        private final long epoch;
        private final long id;
        private final String name;
        private final ArrayDeque<Runnable> pending = new ArrayDeque<>();
        private Runnable criticalCleanup;
        private Runnable terminationCleanup;
        private long accepted;
        private long completed;
        private boolean sealed;
        private boolean terminationRequested;

        private Scope(final GameInputActionLane lane, final long epoch,
                final long id, final String name) {
            this.lane = lane;
            this.epoch = epoch;
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name + "#" + id;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Set<Scope> scopes = Collections.newSetFromMap(
            new IdentityHashMap<>());
    private final ArrayDeque<Scope> executingScopes = new ArrayDeque<>();
    private long epoch = 1;
    private long nextScopeId = 1;
    private int pendingCount;
    private Thread pumpOwner;
    private int pumpDepth;

    Scope begin(final String name) {
        lock.lock();
        try {
            final Scope scope = new Scope(this, epoch, nextScopeId++,
                    name == null || name.isEmpty() ? "input" : name);
            scopes.add(scope);
            return scope;
        } finally {
            lock.unlock();
        }
    }

    boolean submit(final Scope scope, final Runnable action) {
        if (action == null) {
            throw new NullPointerException("action");
        }
        lock.lock();
        try {
            if (!isCurrent(scope) || scope.sealed
                    || scope.terminationRequested
                    || pendingCount >= MAX_PENDING_ACTIONS) {
                return false;
            }
            scope.pending.addLast(action);
            scope.accepted++;
            pendingCount++;
            changed.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Accepts one idempotent cleanup even when the ordinary queue is full.
     * Acceptance seals the scope atomically, so no later normal UI action can
     * starve Cancel. Already accepted normal actions retain FIFO order and the
     * cleanup runs after them. This guarantees eventual cleanup for bounded
     * action bodies; it is deliberately not a real-time Cancel guarantee.
     */
    boolean submitCritical(final Scope scope, final Runnable cleanup) {
        if (cleanup == null) {
            throw new NullPointerException("cleanup");
        }
        lock.lock();
        try {
            if (!isCurrent(scope) || scope.terminationRequested) {
                return false;
            }
            if (scope.criticalCleanup == null) {
                scope.criticalCleanup = cleanup;
                scope.accepted++;
            }
            scope.sealed = true;
            changed.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    void setTerminationCleanup(final Scope scope, final Runnable cleanup) {
        if (cleanup == null) {
            throw new NullPointerException("cleanup");
        }
        lock.lock();
        try {
            if (isCurrent(scope) && !scope.terminationRequested) {
                scope.terminationCleanup = cleanup;
            }
        } finally {
            lock.unlock();
        }
    }

    void seal(final Scope scope) {
        lock.lock();
        try {
            if (isKnown(scope)) {
                scope.sealed = true;
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    boolean isExecuting(final Scope scope) {
        lock.lock();
        try {
            return isKnown(scope) && pumpOwner == Thread.currentThread()
                    && executingScopes.peekLast() == scope;
        } finally {
            lock.unlock();
        }
    }

    boolean isAccepting(final Scope scope) {
        lock.lock();
        try {
            return isCurrent(scope) && !scope.sealed
                    && !scope.terminationRequested;
        } finally {
            lock.unlock();
        }
    }

    boolean abandon(final Scope scope) {
        lock.lock();
        try {
            if (!isKnown(scope) || scope.accepted != 0
                    || !scope.pending.isEmpty()
                    || scope.criticalCleanup != null
                    || executingScopes.contains(scope)) {
                return false;
            }
            scope.sealed = true;
            scopes.remove(scope);
            changed.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    int activeScopeCount() {
        lock.lock();
        try {
            return scopes.size();
        } finally {
            lock.unlock();
        }
    }

    void pumpUntil(final Scope scope, final BooleanSupplier inputReleased) {
        if (inputReleased == null) {
            throw new NullPointerException("inputReleased");
        }
        boolean interrupted = false;
        enterPump(scope);
        try {
            while (true) {
                Runnable action;
                lock.lock();
                try {
                    while (true) {
                        if (!isKnown(scope)) {
                            return;
                        }

                        // Seal under the same lock used by submit. Once the
                        // release is observed no late UI action can sneak in
                        // after the drain check and strand the game thread.
                        if (inputReleased.getAsBoolean()) {
                            scope.sealed = true;
                        }

                        action = scope.pending.pollFirst();
                        if (action != null) {
                            pendingCount--;
                            executingScopes.addLast(scope);
                            break;
                        }
                        if (scope.criticalCleanup != null) {
                            action = scope.criticalCleanup;
                            scope.criticalCleanup = null;
                            executingScopes.addLast(scope);
                            break;
                        }
                        if (scope.sealed
                                && scope.completed == scope.accepted) {
                            scopes.remove(scope);
                            return;
                        }
                        try {
                            // Inputs normally wake the lane through submit or
                            // seal. Poll infrequently as a fallback for legacy
                            // callers that release the raw input latch without
                            // invoking the actor wake-up hook.
                            changed.awaitNanos(100_000_000L);
                        } catch (final InterruptedException ex) {
                            interrupted = true;
                        }
                    }
                } finally {
                    lock.unlock();
                }

                try {
                    action.run();
                } catch (final RuntimeException | Error failure) {
                    // A broken UI action must not poison the per-game actor or
                    // prevent a queued Cancel/cleanup action from running.
                    Logger.error(failure, "Input action failed in {}", scope);
                } finally {
                    lock.lock();
                    try {
                        if (executingScopes.peekLast() == scope) {
                            executingScopes.removeLast();
                        } else {
                            executingScopes.removeLastOccurrence(scope);
                        }
                        scope.completed++;
                        changed.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        } finally {
            leavePump();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Invalidates every outstanding token and wakes every waiter. This is an
     * epoch reset rather than a permanent shutdown: a restarted game may open
     * a fresh input scope, while old UI/network messages are rejected.
     */
    void terminate() {
        lock.lock();
        try {
            epoch = epoch == Long.MAX_VALUE ? 1 : epoch + 1;
            for (final Scope scope : scopes) {
                scope.terminationRequested = true;
                scope.sealed = true;
                pendingCount -= scope.pending.size();
                // Termination discards ordinary UI work but still executes the
                // registered engine cleanup on the owning pump thread.
                scope.completed += scope.pending.size();
                scope.pending.clear();
                if (scope.criticalCleanup == null
                        && scope.terminationCleanup != null) {
                    scope.criticalCleanup = scope.terminationCleanup;
                    scope.accepted++;
                }
            }
            if (pendingCount < 0) {
                pendingCount = 0;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private boolean isCurrent(final Scope scope) {
        return isKnown(scope) && scope.epoch == epoch;
    }

    private boolean isKnown(final Scope scope) {
        return scope != null && scope.lane == this && scopes.contains(scope);
    }

    private void enterPump(final Scope scope) {
        lock.lock();
        try {
            if (!isKnown(scope)) {
                return;
            }
            final Thread current = Thread.currentThread();
            if (pumpOwner == null) {
                pumpOwner = current;
            } else if (pumpOwner != current) {
                throw new IllegalStateException(
                        "A game input lane may only have one pump thread");
            }
            pumpDepth++;
        } finally {
            lock.unlock();
        }
    }

    private void leavePump() {
        lock.lock();
        try {
            if (pumpOwner != Thread.currentThread() || pumpDepth == 0) {
                return;
            }
            if (--pumpDepth == 0) {
                pumpOwner = null;
                executingScopes.clear();
                changed.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }
}
