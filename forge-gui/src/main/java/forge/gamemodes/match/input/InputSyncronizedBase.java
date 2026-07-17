package forge.gamemodes.match.input;

import forge.util.IHasForgeLog;
import forge.gui.FThreads;
import forge.gui.error.BugReporter;
import forge.player.PlayerControllerHuman;

import java.util.concurrent.CountDownLatch;

public abstract class InputSyncronizedBase extends InputBase implements InputSynchronized, IHasForgeLog {

    private static final long serialVersionUID = 8756177361251703052L;
    private final CountDownLatch cdlDone;

    public InputSyncronizedBase(final PlayerControllerHuman controller) {
        super(controller);
        cdlDone = new CountDownLatch(1);
    }

    @Override
    public void awaitLatchRelease() {
        FThreads.assertExecutedByEdt(false);
        netLog.trace("awaitLatchRelease() starting on {}, thread = {}", this.getClass().getSimpleName(), Thread.currentThread().getName());
        try {
            cdlDone.await();
        } catch (final InterruptedException e) {
            BugReporter.reportException(e);
        }
        netLog.trace("awaitLatchRelease() UNBLOCKED on {}, thread = {}", this.getClass().getSimpleName(), Thread.currentThread().getName());
    }

    @Override
    public void relaseLatchWhenGameIsOver() {
        cdlDone.countDown();
        onLatchReleased();
    }

    protected final boolean isLatchReleased() {
        return cdlDone.getCount() == 0;
    }

    public void showAndWait() {
        getController().getInputQueue().setInput(this);
        awaitLatchRelease();
    }

    @Override
    public void stop() {
        netLog.trace("stop() called on {}, latch count before = {}", this.getClass().getSimpleName(), cdlDone.getCount());
        Throwable failure = null;
        try {
            onStop();
        } catch (final RuntimeException | Error ex) {
            failure = ex;
        }

        try {
            // ensure input won't accept any user actions.
            if (FThreads.isGuiThread()) {
                setFinished();
            } else {
                FThreads.invokeInEdtAndWait(this::setFinished);
            }
        } catch (final RuntimeException | Error ex) {
            failure = preserveFirstFailure(failure, ex);
        }

        try {
            // Identity removal also handles an input that failed after a
            // replacement was pushed above it.
            getController().getInputQueue().removeInputExact(this);
        } catch (final RuntimeException | Error ex) {
            failure = preserveFirstFailure(failure, ex);
        } finally {
            cdlDone.countDown();
            onLatchReleased();
        }
        netLog.trace("stop() done, latch count after = {}", cdlDone.getCount());

        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
    }

    private static Throwable preserveFirstFailure(final Throwable first, final Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    protected void onStop() { }

    /** Lets actor-backed inputs wake their per-game pump after an external release. */
    protected void onLatchReleased() { }
}
