package forge.gui;

import forge.GuiDesktop;
import forge.Singletons;
import forge.game.card.CardFaceView;
import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FButton;
import forge.toolbox.FMouseAdapter;
import forge.toolbox.FSkin;
import forge.toolbox.FTextField;
import forge.util.Localizer;
import forge.view.FView;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import javax.swing.SwingUtilities;
import javax.swing.ImageIcon;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = { "UnitTest", "fast" }, timeOut = 15_000)
public class ListChooserTest {
    @BeforeClass
    public void initializeGuiBase() throws Exception {
        if (GuiBase.getInterface() == null) {
            GuiBase.setInterface(new GuiDesktop());
        }
        Localizer.getInstance().initialize("en-US", Paths.get("..", "forge-gui", "res", "languages")
                .toAbsolutePath().normalize().toString());
        initializeTestSkin();
        final Field view = Singletons.class.getDeclaredField("view");
        view.setAccessible(true);
        view.set(null, FView.SINGLETON_INSTANCE);
        SwingUtilities.invokeAndWait(() -> {
            FView.SINGLETON_INSTANCE.getFrame().setLocation(-10_000, -10_000);
            FView.SINGLETON_INSTANCE.getFrame().setSize(800, 600);
            FView.SINGLETON_INSTANCE.getFrame().setVisible(true);
        });
    }

    @AfterClass
    public void closeTestFrame() throws Exception {
        SwingUtilities.invokeAndWait(() -> FView.SINGLETON_INSTANCE.getFrame().setVisible(false));
    }

    private static void initializeTestSkin() throws Exception {
        final Field enumColor = FSkin.Colors.class.getDeclaredField("color");
        enumColor.setAccessible(true);
        for (final FSkin.Colors color : FSkin.Colors.values()) {
            enumColor.set(color, Color.DARK_GRAY);
        }

        final Field baseColors = FSkin.SkinColor.class.getDeclaredField("baseColors");
        baseColors.setAccessible(true);
        @SuppressWarnings("unchecked") final HashMap<FSkin.Colors, FSkin.SkinColor> colors =
                (HashMap<FSkin.Colors, FSkin.SkinColor>) baseColors.get(null);
        colors.clear();
        final Constructor<FSkin.SkinColor> colorConstructor =
                FSkin.SkinColor.class.getDeclaredConstructor(FSkin.Colors.class);
        colorConstructor.setAccessible(true);
        for (final FSkin.Colors color : FSkin.Colors.values()) {
            colors.put(color, colorConstructor.newInstance(color));
        }

        final Method setBaseFont = FSkin.SkinFont.class.getDeclaredMethod("setBaseFont", Font.class);
        setBaseFont.setAccessible(true);
        setBaseFont.invoke(null, new Font(Font.SANS_SERIF, Font.PLAIN, 12));

        final Field iconsField = FSkin.SkinIcon.class.getDeclaredField("icons");
        iconsField.setAccessible(true);
        @SuppressWarnings("unchecked") final Map<FSkinProp, FSkin.SkinIcon> icons =
                (Map<FSkinProp, FSkin.SkinIcon>) iconsField.get(null);
        final Constructor<FSkin.SkinIcon> iconConstructor =
                FSkin.SkinIcon.class.getDeclaredConstructor(ImageIcon.class);
        iconConstructor.setAccessible(true);
        icons.put(FSkinProp.ICO_BLANK, iconConstructor.newInstance(
                new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB))));
    }

    @Test
    public void tenThousandCandidateIndexBuildDoesNotBlockDialogConstruction() throws Exception {
        final List<CardFaceView> many = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            many.add(new CardFaceView("Canonical " + i, "译名 " + i));
        }
        final AtomicInteger visits = new AtomicInteger();
        final long startedAt = System.nanoTime();
        final ListChooser<CardFaceView> chooser = onEdt(() -> new ListChooser<>(
                "Choose a card name", 0, 1, many, face -> {
                    visits.incrementAndGet();
                    return face.displayName();
                }));
        final long constructionMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        assertTrue(constructionMillis < 500, "dialog construction performed candidate indexing on EDT");
        assertEquals(visits.get(), 0, "background index executed arbitrary caller display callback");
        onEdt(() -> {
            chooser.getLstChoices().getPreferredSize();
            chooser.getLstChoices().setSize(480, 420);
            chooser.getLstChoices().doLayout();
            return null;
        });
        assertEquals(visits.get(), 0,
                "JList preferred-size/layout traversed candidate renderers on EDT");
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!chooser.isSearchIndexReadyForTesting()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("background index did not complete");
            }
            Thread.sleep(10);
        }
        assertEquals(visits.get(), 0, "background index executed arbitrary caller display callback");
    }

    @Test
    public void cardNameSearchInteractionKeepsCanonicalCandidatesAndRejectsStaleInput() throws Exception {
        final List<CardFaceView> candidates = candidates();
        final ListChooser<CardFaceView> chooser = onEdt(() -> new ListChooser<>(
                "Choose a card name", 0, 1, candidates, CardFaceView::displayName));
        final FTextField field = chooser.getCardSearchFieldForTesting();
        final FButton button = chooser.getCardSearchButtonForTesting();
        assertNotNull(field);
        assertNotNull(button);
        final AtomicInteger invalidContentsEvents = new AtomicInteger();
        onEdt(() -> {
            chooser.getLstChoices().getModel().addListDataListener(new ListDataListener() {
                @Override public void intervalAdded(final ListDataEvent e) { }
                @Override public void intervalRemoved(final ListDataEvent e) { }
                @Override public void contentsChanged(final ListDataEvent e) {
                    invalidContentsEvents.incrementAndGet();
                }
            });
            return null;
        });

        // An IME composition must not submit its uncommitted text.
        onEdt(() -> {
            chooser.startInputCompositionForTesting();
            field.setText("园");
            pressKey(field, KeyEvent.VK_ENTER);
            return null;
        });
        Thread.sleep(250);
        assertFalse(onEdt(chooser::isSearchPendingForTesting));
        assertEquals(onEdt(chooser::getDisplayedItemsForTesting).size(), candidates.size());
        onEdt(() -> {
            chooser.finishInputCompositionForTesting();
            return null;
        });
        waitForSearch(chooser);
        assertEquals(onEdt(chooser::getDisplayedItemsForTesting).get(0).getName(), "Garden of Hope");

        // A newer manual-button query wins even if an older result is still pending.
        onEdt(() -> {
            field.setText("Garden");
            assertTrue(chooser.isSearchPendingForTesting());
            assertFalse(chooser.getLstChoices().isEnabled());
            assertFalse(chooser.isConfirmEnabledForTesting());
            chooser.getLstChoices().setSelectedIndex(0);
            final MouseEvent staleDoubleClick = new MouseEvent(chooser.getLstChoices(), MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, 2, 2, 2, false, MouseEvent.BUTTON1);
            for (final java.awt.event.MouseListener listener : chooser.getLstChoices().getMouseListeners()) {
                if (listener instanceof FMouseAdapter) {
                    ((FMouseAdapter) listener).onLeftDoubleClick(staleDoubleClick);
                }
            }
            assertTrue(chooser.getDialogResultForTesting() != 0);
            button.doClick();
            field.setText("Island");
            button.doClick();
            return null;
        });
        waitForSearch(chooser);
        final List<CardFaceView> latest = onEdt(chooser::getDisplayedItemsForTesting);
        assertEquals(latest.size(), 1);
        assertEquals(latest.get(0).getName(), "Island");

        // Enter in the field searches; Down and Up move into visible results.
        onEdt(() -> {
            field.setText("Candidate");
            pressKey(field, KeyEvent.VK_ENTER);
            return null;
        });
        waitForSearch(chooser);
        onEdt(() -> {
            pressKey(field, KeyEvent.VK_DOWN);
            assertEquals(chooser.getLstChoices().getSelectedIndex(), 0);
            chooser.getLstChoices().clearSelection();
            pressKey(field, KeyEvent.VK_UP);
            assertEquals(chooser.getLstChoices().getSelectedIndex(),
                    chooser.getDisplayedItemsForTesting().size() - 1);
            return null;
        });

        // No result clears selection and disables confirmation.
        onEdt(() -> {
            field.setText("definitely absent");
            button.doClick();
            return null;
        });
        waitForSearch(chooser);
        assertTrue(onEdt(chooser::getDisplayedItemsForTesting).isEmpty());
        assertFalse(onEdt(chooser::isConfirmEnabledForTesting));
        assertEquals(onEdt(() -> chooser.getLstChoices().getSelectedIndex()).intValue(), -1);

        // Double click confirms only a currently visible canonical candidate.
        onEdt(() -> {
            field.setText("Garden of Hope");
            button.doClick();
            return null;
        });
        waitForSearch(chooser);
        onEdt(() -> {
            chooser.getLstChoices().setSelectedIndex(0);
            final MouseEvent event = new MouseEvent(chooser.getLstChoices(), MouseEvent.MOUSE_CLICKED,
                    System.currentTimeMillis(), 0, 2, 2, 2, false, MouseEvent.BUTTON1);
            for (final java.awt.event.MouseListener listener : chooser.getLstChoices().getMouseListeners()) {
                if (listener instanceof FMouseAdapter) {
                    ((FMouseAdapter) listener).onLeftDoubleClick(event);
                }
            }
            return null;
        });
        assertEquals(onEdt(chooser::getDialogResultForTesting).intValue(), 0);
        assertEquals(invalidContentsEvents.get(), 0);

    }

    private static List<CardFaceView> candidates() {
        final List<CardFaceView> result = new ArrayList<>();
        result.add(new CardFaceView("Garden of Hope", "希望花园"));
        result.add(new CardFaceView("Island", "海岛"));
        for (int i = 0; i < 28; i++) {
            result.add(new CardFaceView("Candidate " + i, "候选 " + i));
        }
        return result;
    }

    private static void pressKey(final FTextField field, final int keyCode) {
        final KeyEvent event = new KeyEvent(field, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
                0, keyCode, KeyEvent.CHAR_UNDEFINED);
        for (final java.awt.event.KeyListener listener : field.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }

    private static void waitForSearch(final ListChooser<?> chooser) throws Exception {
        final long deadline = System.nanoTime() + 5_000_000_000L;
        while (onEdt(chooser::isSearchPendingForTesting)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("search did not complete");
            }
            Thread.sleep(10);
        }
    }

    private static <T> T onEdt(final Callable<T> action) throws Exception {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.call();
        }
        final FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
