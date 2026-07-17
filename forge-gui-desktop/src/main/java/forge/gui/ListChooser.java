/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package forge.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.Timer;
import java.awt.image.BufferedImage;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import forge.card.CardType;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.game.card.CardFaceView;
import forge.game.card.CounterKeywordType;
import forge.game.card.CounterType;
import forge.localinstance.skin.FSkinProp;
import forge.toolbox.FList;
import forge.toolbox.FButton;
import forge.toolbox.FMouseAdapter;
import forge.toolbox.FOptionPane;
import forge.toolbox.FScrollPane;
import forge.toolbox.FSkin;
import forge.toolbox.FTextField;
import forge.util.ITranslatable;
import forge.util.Localizer;

/**
 * A simple class that shows a list of choices in a dialog. Two properties
 * influence the behavior of a list chooser: minSelection and maxSelection.
 * These two give the allowed number of selected items for the dialog to be
 * closed. A negative value for minSelection suggests that the list is revealed
 * and the choice doesn't matter.
 * <ul>
 * <li>If minSelection is 0, there will be a Cancel button.</li>
 * <li>If minSelection is -1, 0 or 1, double-clicking a choice will also close
 * the dialog.</li>
 * <li>If the number of selections is out of bounds, the "OK" button is
 * disabled.</li>
 * <li>The dialog was "committed" if "OK" was clicked or a choice was double
 * clicked.</li>
 * <li>The dialog was "canceled" if Localizer.getInstance().getMessage("lblCancel") or "X" was clicked.</li>
 * <li>If the dialog was canceled, the selection will be empty.</li>
 * <li>
 * </ul>
 *
 * @param <T>
 *            the generic type
 * @author Forge
 * @version $Id: ListChooser.java 25183 2014-03-14 23:09:45Z drdev $
 */
public class ListChooser<T> {
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        final Thread thread = new Thread(task, "forge-choice-search");
        thread.setDaemon(true);
        return thread;
    });
    // Data and number of choices for the list
    private final List<T> allItems;
    private List<T> displayedItems;
    private final int minChoices, maxChoices;
    private final Function<T, String> display;

    // Flag: was the dialog already shown?
    private boolean called;

    // initialized before; listeners may be added to it
    private final FList<T> lstChoices;
    private final FOptionPane optionPane;
    private final ChooserListModel listModel;
    private CompletableFuture<CardNameSearchIndex<T>> searchIndexFuture;
    private final LatestSearchGeneration searchGeneration = new LatestSearchGeneration();
    private CompletableFuture<?> pendingSearch;
    private Timer searchDebounce;
    private boolean inputMethodComposing;
    private JLabel searchStatus;
    private final boolean cardNameChooser;
    private boolean searchPending;
    private FTextField cardSearchField;
    private FButton cardSearchButton;
    private volatile boolean disposed;

    public ListChooser(final String title, final int minChoices, final int maxChoices, final Collection<T> list, final Function<T, String> display) {
        FThreads.assertExecutedByEdt(true);
        this.minChoices = minChoices;
        this.maxChoices = maxChoices;
        this.display = display;
        this.allItems = Lists.newArrayList(list);
        this.cardNameChooser = !allItems.isEmpty()
                && (allItems.get(0) instanceof ICardFace || allItems.get(0) instanceof CardFaceView);
        this.displayedItems = new ArrayList<>(this.allItems);
        this.listModel = new ChooserListModel();
        this.lstChoices = new FList<>(this.listModel);

        final ImmutableList<String> options;
        if (minChoices == 0) {
            options = ImmutableList.of(Localizer.getInstance().getMessage("lblOK"),Localizer.getInstance().getMessage("lblCancel"));
        } else {
            options = ImmutableList.of(Localizer.getInstance().getMessage("lblOK"));
        }

        if (maxChoices == 1 || minChoices == -1) {
            this.lstChoices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        }

        this.lstChoices.setCellRenderer(new TransformedCellRenderer(display));
        if (cardNameChooser && allItems.size() > 25) {
            // Prevent JList preferred-size/layout from asking the renderer to translate every
            // candidate on the EDT while the immutable search index is built in the worker.
            this.lstChoices.setFixedCellWidth(480);
            this.lstChoices.setFixedCellHeight(Math.max(28,
                    this.lstChoices.getFontMetrics(this.lstChoices.getFont()).getHeight() + 8));
        }

        final FScrollPane listScroller = new FScrollPane(this.lstChoices, true);
        int minWidth = cardNameChooser && allItems.size() > 25 ? 480 : this.lstChoices.getAutoSizeWidth();
        if (this.lstChoices.getModel().getSize() > this.lstChoices.getVisibleRowCount()) {
            minWidth += listScroller.getVerticalScrollBar().getPreferredSize().width;
        }
        listScroller.setMinimumSize(new Dimension(minWidth, listScroller.getMinimumSize().height));

        // Add search field for large lists (same threshold as mobile)
        if (allItems.size() > 25) {
            if (cardNameChooser) {
                final List<T> indexCandidates = new ArrayList<>(allItems);
                // Arbitrary caller renderers remain EDT-only. The worker indexes only the
                // immutable candidate's canonical/ITranslatable fields.
                final Function<T, String> indexDisplay = ListChooser::getDefaultDisplayText;
                this.searchIndexFuture = CompletableFuture.supplyAsync(() -> new CardNameSearchIndex<>(
                        indexCandidates, indexDisplay,
                        () -> disposed || Thread.currentThread().isInterrupted()), SEARCH_EXECUTOR);
            }
            final FTextField searchField = new FTextField.Builder()
                    .ghostText(Localizer.getInstance().getMessage("lblSearch"))
                    .showGhostTextWithFocus()
                    .build();
            if (cardNameChooser) {
                cardSearchField = searchField;
            }
            this.searchDebounce = new Timer(180, e -> {
                if (!inputMethodComposing) {
                    applyFilter(searchField);
                }
            });
            this.searchDebounce.setRepeats(false);
            searchField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { onSearchTextChanged(searchField); }
                @Override public void removeUpdate(DocumentEvent e) { onSearchTextChanged(searchField); }
                @Override public void changedUpdate(DocumentEvent e) { onSearchTextChanged(searchField); }
            });
            searchField.addInputMethodListener(new InputMethodListener() {
                @Override public void inputMethodTextChanged(final InputMethodEvent e) {
                    final int total = e.getText() == null ? 0
                            : e.getText().getEndIndex() - e.getText().getBeginIndex();
                    inputMethodComposing = total > e.getCommittedCharacterCount();
                    if (!inputMethodComposing) {
                        onSearchTextChanged(searchField);
                    }
                }

                @Override public void caretPositionChanged(final InputMethodEvent e) { }
            });
            searchField.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(final KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        if (cardNameChooser) {
                            runFilterNow(searchField);
                        } else {
                            ListChooser.this.commit();
                        }
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        if (searchPending) {
                            e.consume();
                            return;
                        }
                        if (lstChoices.getSelectedIndex() < 0 && !displayedItems.isEmpty()) {
                            lstChoices.setSelectedIndex(0);
                        }
                        lstChoices.requestFocusInWindow();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP && cardNameChooser) {
                        if (!searchPending && lstChoices.getSelectedIndex() < 0 && !displayedItems.isEmpty()) {
                            lstChoices.setSelectedIndex(displayedItems.size() - 1);
                            lstChoices.requestFocusInWindow();
                        }
                        e.consume();
                    }
                }
            });

            final JPanel searchPanel = new JPanel(new BorderLayout(6, 0));
            searchPanel.setOpaque(false);
            searchPanel.add(searchField, BorderLayout.CENTER);
            if (cardNameChooser) {
                final FButton searchButton = new FButton(Localizer.getInstance().getMessage("lblSearch"));
                cardSearchButton = searchButton;
                searchButton.addActionListener(e -> runFilterNow(searchField));
                searchPanel.add(searchButton, BorderLayout.EAST);
            }

            this.searchStatus = new JLabel();
            this.searchStatus.setVisible(false);
            final JPanel resultsPanel = new JPanel(new BorderLayout(0, 2));
            resultsPanel.setOpaque(false);
            if (cardNameChooser) {
                resultsPanel.add(this.searchStatus, BorderLayout.NORTH);
            }
            resultsPanel.add(listScroller, BorderLayout.CENTER);

            final JPanel panel = new JPanel(new BorderLayout(0, 6));
            panel.setOpaque(false);
            panel.add(searchPanel, BorderLayout.NORTH);
            panel.add(resultsPanel, BorderLayout.CENTER);
            if (cardNameChooser) {
                panel.setPreferredSize(new Dimension(Math.max(480, minWidth), 420));
            }

            this.optionPane = new FOptionPane(null, title, null, panel, options, minChoices < 0 ? 0 : -1);
            if (minChoices != -1) {
                this.optionPane.setDefaultFocus(searchField);
            }
        } else {
            this.optionPane = new FOptionPane(null, title, null, listScroller, options, minChoices < 0 ? 0 : -1);
            if (minChoices != -1) {
                this.optionPane.setDefaultFocus(this.lstChoices);
            }
        }

        this.optionPane.setButtonEnabled(0, minChoices <= 0);

        if (minChoices > 0) {
            this.optionPane.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        }

        if (minChoices != -1) {
            this.lstChoices.getSelectionModel().addListSelectionListener(new SelListener());
        }

        this.lstChoices.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(final KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    ListChooser.this.commit();
                }
            }
        });
        this.lstChoices.addMouseListener(new FMouseAdapter() {
            @Override public void onLeftDoubleClick(final MouseEvent e) {
                    ListChooser.this.commit();
            }
        });
    }

    private void scheduleFilter() {
        if (!inputMethodComposing && searchDebounce != null) {
            setSearchPending();
            searchDebounce.restart();
        }
    }

    private void onSearchTextChanged(final FTextField searchField) {
        if (cardNameChooser) {
            scheduleFilter();
        } else {
            applyLegacyFilter(searchField);
        }
    }

    private void applyLegacyFilter(final FTextField searchField) {
        final String text = CardNameSearchIndex.normalize(searchField.getText());
        lstChoices.clearSelection();
        final List<T> matches;
        if (text.isEmpty()) {
            matches = new ArrayList<>(allItems);
        } else {
            final List<T> startsWith = new ArrayList<>();
            final List<T> contains = new ArrayList<>();
            for (final T item : allItems) {
                final String name = CardNameSearchIndex.normalize(getDisplayText(item));
                if (name.startsWith(text)) {
                    startsWith.add(item);
                } else if (name.contains(text)) {
                    contains.add(item);
                }
            }
            startsWith.sort(Comparator.comparingInt(item -> getDisplayText(item).length()));
            matches = new ArrayList<>(startsWith.size() + contains.size());
            matches.addAll(startsWith);
            matches.addAll(contains);
        }
        listModel.replaceItems(matches);
        if (!displayedItems.isEmpty() && maxChoices > 0) {
            lstChoices.setSelectedIndex(0);
        }
    }

    private void runFilterNow(final FTextField searchField) {
        if (searchDebounce != null) {
            searchDebounce.stop();
        }
        if (!inputMethodComposing) {
            applyFilter(searchField);
        }
    }

    private void applyFilter(final FTextField searchField) {
        final String query = searchField.getText();
        setSearchPending();
        final long generation = searchGeneration.begin();
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
        }
        pendingSearch = searchIndexFuture.thenApplyAsync(index -> index.search(query,
                        () -> !searchGeneration.isCurrent(generation) || Thread.currentThread().isInterrupted()), SEARCH_EXECUTOR)
                .whenComplete((results, error) -> SwingUtilities.invokeLater(() -> {
                    if (error == null) {
                        applySearchResults(generation, query, results);
                    } else {
                        applySearchFailure(generation);
                    }
                }));
    }

    private void applySearchResults(final long generation, final String query, final List<T> results) {
        if (disposed || !searchGeneration.isCurrent(generation)) {
            return;
        }
        listModel.replaceItems(results);
        searchPending = false;
        lstChoices.setEnabled(true);
        lstChoices.clearSelection();
        if (searchStatus != null) {
            final boolean noResults = results.isEmpty() && !CardNameSearchIndex.normalize(query).isEmpty();
            searchStatus.setText(noResults
                    ? Localizer.getInstance().getMessageorUseDefault("lblNoSearchResults", "No results for: {0}", query)
                    : "");
            searchStatus.setVisible(noResults);
            searchStatus.getParent().revalidate();
        }
        optionPane.setButtonEnabled(0, minChoices <= 0 && CardNameSearchIndex.normalize(query).isEmpty());
        lstChoices.revalidate();
        lstChoices.repaint();
    }

    private void applySearchFailure(final long generation) {
        if (disposed || !searchGeneration.isCurrent(generation)) {
            return;
        }
        listModel.replaceItems(List.of());
        searchPending = false;
        lstChoices.setEnabled(true);
        lstChoices.clearSelection();
        optionPane.setButtonEnabled(0, false);
        if (searchStatus != null) {
            searchStatus.setText(Localizer.getInstance().getMessageorUseDefault(
                    "lblSearchUnavailable", "Search unavailable. Close and reopen this chooser."));
            searchStatus.setVisible(true);
            searchStatus.getParent().revalidate();
        }
    }

    private String getDisplayText(final T value) {
        if (display != null) {
            return display.apply(value);
        }
        return getDefaultDisplayText(value);
    }

    private static String getDefaultDisplayText(final Object value) {
        if (value instanceof ITranslatable t) {
            return t.getTranslatedName();
        }
        return value != null ? value.toString() : "";
    }

    /**
     * Returns the FList used in the list chooser. this is useful for
     * registering listeners before showing the dialog.
     *
     * @return a {@link javax.swing.JList} object.
     */
    public FList<T> getLstChoices() {
        return this.lstChoices;
    }

    FTextField getCardSearchFieldForTesting() {
        return cardSearchField;
    }

    FButton getCardSearchButtonForTesting() {
        return cardSearchButton;
    }

    boolean isSearchPendingForTesting() {
        return searchPending;
    }

    boolean isSearchIndexReadyForTesting() {
        return searchIndexFuture == null || searchIndexFuture.isDone();
    }

    List<T> getDisplayedItemsForTesting() {
        return List.copyOf(displayedItems);
    }

    boolean isConfirmEnabledForTesting() {
        return optionPane.isButtonEnabled(0);
    }

    int getDialogResultForTesting() {
        return optionPane.getResult();
    }

    void finishInputCompositionForTesting() {
        inputMethodComposing = false;
        onSearchTextChanged(cardSearchField);
    }

    void startInputCompositionForTesting() {
        inputMethodComposing = true;
    }


    /** @return boolean */
    public boolean show() {
        return show(null);
    }

    /**
     * Shows the dialog and returns after the dialog was closed.
     */
    public boolean show(final Collection<T> item) {
        if (this.called) {
            throw new IllegalStateException("Already shown");
        }
        int result;
        do {
            //invoke later so selected item not set until dialog open
            SwingUtilities.invokeLater(() -> {
                if (item != null) {
                    int[] indices = item.stream()
                            .mapToInt(displayedItems::indexOf)
                            .filter(i -> i >= 0)
                            .toArray();
                    lstChoices.setSelectedIndices(indices);
                }
                else {
                    lstChoices.setSelectedIndex(0);
                }
            });
            this.optionPane.setVisible(true);
            result = this.optionPane.getResult();
            if (result != 0) {
                this.lstChoices.clearSelection();
            }
            // can't stop closing by ESC, so repeat if cancelled
        } while (this.minChoices > 0 && result != 0);

        this.optionPane.dispose();
        disposed = true;
        searchGeneration.invalidate();
        if (searchIndexFuture != null) {
            searchIndexFuture.cancel(true);
        }
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
        }
        if (searchDebounce != null) {
            searchDebounce.stop();
        }

        // this assert checks if we really don't return on a cancel if input is mandatory
        assert (this.minChoices <= 0) || (result == 0);
        this.called = true;
        return (result == 0);
    }

    /**
     * Returns if the dialog was closed by pressing "OK" or double clicking an
     * option the last time.
     *
     * @return a boolean.
     */
    public boolean isCommitted() {
        if (!this.called) {
            throw new IllegalStateException("not yet shown");
        }
        return (this.optionPane.getResult() == 0);
    }

    /**
     * Returns the selected indices as a list of integers.
     *
     * @return a {@link java.util.List} object.
     */
    public int[] getSelectedIndices() {
        if (!this.called) {
            throw new IllegalStateException("not yet shown");
        }
        return this.lstChoices.getSelectedIndices();
    }

    /**
     * Returns the selected values as a list of objects. no casts are necessary
     * when retrieving the objects.
     *
     * @return a {@link java.util.List} object.
     */
    public List<T> getSelectedValues() {
        if (!this.called) {
            throw new IllegalStateException("not yet shown");
        }
        return this.lstChoices.getSelectedValuesList();
    }

    /**
     * Returns the (minimum) selected index, or -1.
     *
     * @return a int.
     */
    public int getSelectedIndex() {
        if (!this.called) {
            throw new IllegalStateException("not yet shown");
        }
        return this.lstChoices.getSelectedIndex();
    }

    /**
     * Returns the (first) selected value, or null.
     *
     * @return a T object.
     */
    public T getSelectedValue() {
        if (!this.called) {
            throw new IllegalStateException("not yet shown");
        }
        return this.lstChoices.getSelectedValue();
    }

    /**
     * <p>
     * commit.
     * </p>
     */
    private void commit() {
        if (!searchPending && this.optionPane.isButtonEnabled(0)) {
            optionPane.setResult(0);
        }
    }

    private void setSearchPending() {
        if (!cardNameChooser) {
            return;
        }
        searchPending = true;
        searchGeneration.invalidate();
        if (pendingSearch != null) {
            pendingSearch.cancel(true);
        }
        lstChoices.clearSelection();
        lstChoices.setEnabled(false);
        optionPane.setButtonEnabled(0, false);
    }

    private class ChooserListModel extends AbstractListModel<T> {
        private static final long serialVersionUID = 3871965346333840556L;

        @Override
        public int getSize() {
            return ListChooser.this.displayedItems.size();
        }

        @Override
        public T getElementAt(final int index) {
            return ListChooser.this.displayedItems.get(index);
        }

        void replaceItems(final Collection<T> items) {
            final int oldSize = displayedItems.size();
            if (oldSize > 0) {
                displayedItems = new ArrayList<>();
                fireIntervalRemoved(this, 0, oldSize - 1);
            }
            if (!items.isEmpty()) {
                displayedItems = new ArrayList<>(items);
                fireIntervalAdded(this, 0, displayedItems.size() - 1);
            }
        }
    }

    private class SelListener implements ListSelectionListener {
        @Override
        public void valueChanged(final ListSelectionEvent e) {
            final int num = ListChooser.this.lstChoices.getSelectedIndices().length;
            ListChooser.this.optionPane.setButtonEnabled(0, !searchPending
                    && (num >= ListChooser.this.minChoices) && (num <= ListChooser.this.maxChoices));
        }
    }

    private class TransformedCellRenderer implements ListCellRenderer<T> {
        public final Function<T, String> transformer;
        public final DefaultListCellRenderer defRenderer;

        static ImageIcon emptyIcon = new ImageIcon(new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB));

        /**
         * TODO: Write javadoc for Constructor.
         */
        public TransformedCellRenderer(final Function<T, String> t1) {
            transformer = t1;
            defRenderer = new DefaultListCellRenderer();
        }

        /* (non-Javadoc)
         * @see javax.swing.ListCellRenderer#getListCellRendererComponent(javax.swing.JList, java.lang.Object, int, boolean, boolean)
         */
        @Override
        public Component getListCellRendererComponent(final JList<? extends T> list, final T value, final int index, final boolean isSelected, final boolean cellHasFocus) {
            Component result = defRenderer.getListCellRendererComponent(list, getLabel(value), index, isSelected, cellHasFocus);
            if (value instanceof MagicColor.Color c) {
                defRenderer.setIcon(fromSkinProp(FSkinProp.iconFromColor(c)));
            } else if (value instanceof CardType.CoreType c) {
                defRenderer.setIcon(fromSkinProp(FSkinProp.iconFromCoreType(c)));
            } else if (value instanceof CounterType) {
                if (value instanceof CounterKeywordType c) {
                    defRenderer.setIcon(fromSkinProp(FSkinProp.iconFromKeyword(c.keyword())));
                } else {
                    defRenderer.setIcon(fromSkinProp(null));
                }
            }
            return result;
        }

        protected ImageIcon fromSkinProp(FSkinProp prop) {
            return prop == null ? emptyIcon : FSkin.getImage(prop, 24, 24).getIcon();
        }

        protected String getLabel(final T value) {
            if (!(value instanceof ICardFace) && !(value instanceof CardFaceView)) {
                if (transformer != null) {
                    return transformer.apply(value);
                }
                if (value instanceof ITranslatable t) {
                    return t.getTranslatedName();
                }
                return value != null ? value.toString() : "";
            }
            return CardNameSearchIndex.displayLabel(value, v -> {
                @SuppressWarnings("unchecked") final T typed = (T) v;
                if (transformer != null) {
                    return transformer.apply(typed);
                }
                if (typed instanceof ITranslatable t) {
                    return t.getTranslatedName();
                }
                return typed != null ? typed.toString() : "";
            });
        }
    }
}
