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
package forge.screens.home;

import java.awt.Dimension;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;

import forge.Singletons;
import forge.gui.framework.EDocID;
import forge.gui.framework.FScreen;
import forge.gui.framework.ICDoc;
import forge.gui.framework.ILocalRepaint;
import forge.gui.framework.IVTopLevelUI;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.localinstance.skin.FSkinProp;
import forge.model.FModel;
import forge.screens.home.online.VSubmenuOnlineDecks;
import forge.screens.home.online.VSubmenuOnlineLobby;
import forge.screens.home.sanctioned.VSubmenuConstructed;
import forge.screens.home.sanctioned.VSubmenuDraft;
import forge.screens.home.sanctioned.VSubmenuSealed;
import forge.screens.home.settings.VSubmenuAchievements;
import forge.screens.home.settings.VSubmenuAvatars;
import forge.screens.home.settings.VSubmenuDownloaders;
import forge.screens.home.settings.VSubmenuPreferences;
import forge.screens.home.settings.VSubmenuReleaseNotes;
import forge.toolbox.FLabel;
import forge.toolbox.FScrollPanel;
import forge.toolbox.FSkin;
import forge.toolbox.FSkin.SkinColor;
import forge.toolbox.FSkin.SkinnedPanel;
import forge.view.FView;
import net.miginfocom.swing.MigLayout;

/**
 * Top level view class for home UI drag layout.<br>
 * Uses singleton pattern.<br>
 *
 * <br><br><i>(V at beginning of class name denotes a view class.)</i>
 */

@SuppressWarnings("serial")
public enum VHomeUI implements IVTopLevelUI {
    /** */
    SINGLETON_INSTANCE;

    private final SkinColor clrTheme = FSkin.getColor(FSkin.Colors.CLR_THEME);
    private final SkinColor l00 = clrTheme.stepColor(0);

    private final List<IVSubmenu<? extends ICDoc>> allSubmenus = new ArrayList<>();
    private final Map<EDocID, LblMenuItem> allSubmenuLabels = new HashMap<>();
    private final Map<EMenuGroup, LblGroup> allGroupLabels = new HashMap<>();

    private final PnlMenu pnlMenu = new PnlMenu();
    private final PnlDisplay pnlDisplay = new PnlDisplay();
    private final FScrollPanel pnlSubmenus;

    private final JPanel pageShell = new JPanel(new BorderLayout());
    private final JPanel pageNavigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    private JPanel mainMenu;
    private boolean mainMenuVisible = true;

    private JLabel lblLogo = new FLabel.Builder()
        .icon(FSkin.getIcon(FSkinProp.ICO_LOGO))
        .iconAlignX(SwingConstants.CENTER)
        .iconInBackground(true).iconScaleFactor(1.0).build();

    VHomeUI() {
        // Add main menu containing logo and menu buttons
        final JPanel pnlMainMenu = new JPanel(new MigLayout("w 200px!, ax center, insets 0, gap 0, wrap"));
        pnlMainMenu.setOpaque(false);

        final int logoSize = 170;
        final int logoBottomGap = 4;
        final int pnlMainMenuHeight = logoSize + logoBottomGap;

        pnlMainMenu.add(lblLogo, "w " + logoSize + "px!, h " + logoSize +
                "px!, gap 0 4px 0 " + logoBottomGap + "px");
        pnlMenu.add(pnlMainMenu);
        
        pnlSubmenus = new FScrollPanel(new MigLayout("insets 0, gap 0, wrap, hidemode 3"), true,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        // Add new menu items here (order doesn't matter).
        allSubmenus.add(VSubmenuConstructed.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuDraft.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuSealed.SINGLETON_INSTANCE);
        //allSubmenus.add(VSubmenuWinston.SINGLETON_INSTANCE);

        allSubmenus.add(VSubmenuOnlineLobby.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuOnlineDecks.SINGLETON_INSTANCE);

        allSubmenus.add(VSubmenuPreferences.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuAchievements.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuAvatars.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuDownloaders.SINGLETON_INSTANCE);
        allSubmenus.add(VSubmenuReleaseNotes.SINGLETON_INSTANCE);

        // For each group: init its panel
        final SortedMap<EMenuGroup, JPanel> allGroupPanels = new TreeMap<>();
        for (final EMenuGroup e : EMenuGroup.values()) {
            if (!e.isEnabled()) {
                continue;
            }
            allGroupPanels.put(e, new PnlGroup());
            allGroupPanels.get(e).setVisible(false);
            allGroupPanels.get(e).setLayout(new MigLayout("insets 0, gap 0, wrap"));
            allGroupPanels.get(e).setName(e.toString());
        }

        // For each item: Add to its group, and add to the card layout in right panel.
        for (final IVSubmenu<? extends ICDoc> item : allSubmenus) {
            allSubmenuLabels.put(item.getItemEnum(), new LblMenuItem(item));
            allGroupPanels.get(item.getGroupEnum()).add(
                    allSubmenuLabels.get(item.getItemEnum()), "w 100%!, h 30px!, gap 0 0 1px 1px");
        }

        // For each group: add its title, then its panel, then "click" if necessary.
        for (final EMenuGroup e : allGroupPanels.keySet()) {
            allGroupLabels.put(e, new LblGroup(e));
            pnlSubmenus.add(allGroupLabels.get(e), "w 100%!, h 30px!, gap 0 0 3px 3px");
            pnlSubmenus.add(allGroupPanels.get(e), "w 100%!, gap 0 0 0 0");

            // Expand groups expanded from previous session
            if (FModel.getPreferences().getPrefBoolean(FPref.valueOf("SUBMENU_" + e.toString()))) {
                allGroupLabels.get(e).groupClick(e);
            }
        }

        pnlMenu.add(pnlSubmenus, "w 100%!, h 100% - " + pnlMainMenuHeight + "px!");
        pnlDisplay.setBackground(l00.alphaColor(100));
        pageShell.setOpaque(false);
        pageNavigation.setBackground(new Color(35, 23, 15));
        pageNavigation.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(145, 105, 53)));
        pageShell.add(pageNavigation, BorderLayout.NORTH);
        pageShell.add(pnlDisplay, BorderLayout.CENTER);
        final boolean chinese = FModel.getPreferences().getPref(FPref.UI_LANGUAGE).startsWith("zh");
        mainMenu = createMainMenu(chinese
                ? new String[]{"单人游戏", "多人联机", "套牌编辑", "游戏设置"}
                : new String[]{"Single Player", "Multiplayer", "Deck Editor", "Settings"});
    }

    /** @return {@link javax.swing.JPanel} */
    public JPanel getPnlMenu() {
        return pnlMenu;
    }

    /** @return {@link javax.swing.JPanel} */
    public PnlDisplay getPnlDisplay() {
        return pnlDisplay;
    }

    /** @return {@link forge.toolbox.FScrollPanel} */
    public FScrollPanel getPnlSubmenus() {
        return pnlSubmenus;
    }

    /**
     * 
     * @return Map<EMenuItem, FLabel>
     */
    public Map<EDocID, LblMenuItem> getAllSubmenuLabels() {
        return allSubmenuLabels;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#instantiate()
     */
    @Override
    public void instantiate() {

    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#populate()
     */
    @Override
    public void populate() {
        final JPanel pnl = FView.SINGLETON_INSTANCE.getPnlInsets();
        pnl.removeAll();
        pnl.setBorder(null);
        pnl.setLayout(new BorderLayout());
        pnl.add(mainMenuVisible ? mainMenu : pageShell, BorderLayout.CENTER);
        pnl.revalidate();
        pnl.repaint();
    }

    public boolean isMainMenuVisible() {
        return mainMenuVisible;
    }

    public void showMainMenu() {
        mainMenuVisible = true;
        forge.toolbox.FAbsolutePositioner.SINGLETON_INSTANCE.hideAll();
        forge.menus.MenuUtil.setMenuProvider(CHomeUI.SINGLETON_INSTANCE);
        populate();
    }

    private static void playMenuSound() {
        forge.sound.SoundSystem.instance.play(forge.sound.SoundEffectType.ButtonPress, false);
    }

    private JPanel createMainMenu(final String[] labels) {
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            try {
                return (JPanel) Class.forName("forge.screens.home.HearthHomePanel")
                        .getConstructor(String[].class, java.util.function.IntConsumer.class, Runnable.class)
                        .newInstance(labels, (java.util.function.IntConsumer) this::openMainMenuItem,
                                (Runnable) VHomeUI::playMenuSound);
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                // Builds without the optional JavaFX payload keep the existing destinations available.
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot initialize the 3D home menu", e);
            }
        }
        JPanel fallback = new JPanel(new java.awt.GridBagLayout());
        fallback.setOpaque(false);
        JPanel buttons = new JPanel(new java.awt.GridLayout(0, 1, 0, 16));
        buttons.setOpaque(false);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            JButton button = navigationButton(labels[i], false);
            button.addActionListener(e -> { playMenuSound(); openMainMenuItem(index); });
            buttons.add(button);
        }
        fallback.add(buttons);
        return fallback;
    }

    private void openMainMenuItem(final int index) {
        switch (index) {
        case 0:
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_CONSTRUCTED);
            break;
        case 1:
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_NETWORK);
            break;
        case 2:
            Singletons.getControl().setCurrentScreen(FScreen.DECK_EDITOR_CONSTRUCTED);
            break;
        case 3:
            CHomeUI.SINGLETON_INSTANCE.itemClick(EDocID.HOME_PREFERENCES);
            break;
        default:
            throw new IllegalArgumentException("Unknown home menu item: " + index);
        }
    }

    public void showSubmenu(final EDocID selected) {
        mainMenuVisible = false;
        pageNavigation.removeAll();
        final boolean chinese = FModel.getPreferences().getPref(FPref.UI_LANGUAGE).startsWith("zh");
        JButton back = navigationButton(chinese ? "← 返回主菜单" : "← Main Menu", false);
        back.addActionListener(e -> { playMenuSound(); showMainMenu(); });
        pageNavigation.add(back);
        EMenuGroup group = null;
        for (IVSubmenu<? extends ICDoc> item : allSubmenus) {
            if (item.getItemEnum() == selected) { group = item.getGroupEnum(); break; }
        }
        for (IVSubmenu<? extends ICDoc> item : allSubmenus) {
            if (item.getGroupEnum() != group) { continue; }
            JButton tab = navigationButton(item.getMenuTitle(), item.getItemEnum() == selected);
            tab.addActionListener(e -> {
                playMenuSound();
                CHomeUI.SINGLETON_INSTANCE.itemClick(item.getItemEnum());
            });
            pageNavigation.add(tab);
        }
        populate();
    }

    private JButton navigationButton(final String title, final boolean selected) {
        JButton button = new JButton(title);
        button.setFont(FSkin.getRelativeFont(14).getBaseFont());
        button.setForeground(new Color(239, 214, 168));
        button.setBackground(selected ? new Color(108, 74, 39) : new Color(58, 40, 25));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(selected ? 193 : 117, selected ? 146 : 83, selected ? 75 : 44)),
                BorderFactory.createEmptyBorder(6, 14, 6, 14)));
        button.setFocusPainted(true);
        return button;
    }

    /** */
    public class PnlDisplay extends SkinnedPanel implements ILocalRepaint {
        /** Constructor. */
        public PnlDisplay() {
            this.setOpaque(false);
        }

        @Override
        public void repaintSelf() {
            final Dimension d = this.getSize();
            repaint(0, 0, d.width, d.height);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            final Graphics2D g2d = (Graphics2D) g.create();

            g2d.setColor(this.getBackground());
            g2d.fillRect(0, 0, getWidth(), getHeight());

            g2d.dispose();
        }
    }

    private class PnlMenu extends JPanel {
        private final SkinColor d80 = clrTheme.stepColor(-80);

        public PnlMenu() {
            this.setLayout(new MigLayout("insets 0, gap 0, wrap, hidemode 3"));
            this.setOpaque(false);
        }

        @Override
        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            final LblMenuItem lblSelected = CHomeUI.SINGLETON_INSTANCE.getLblSelected();
            final FScrollPanel scrollPanel = VHomeUI.SINGLETON_INSTANCE.getPnlSubmenus();
            final Graphics2D g2d = (Graphics2D) g.create();
            final int w = getWidth();
            int y1 = 0;
            int y2 = 0;
            int h1 = getHeight();
            int h2 = 0;
            
            if (lblSelected.isShowing()) {
                int scrollPanelTop = scrollPanel.getY();
                int labelTop = lblSelected.getY() + lblSelected.getParent().getY() + scrollPanelTop - scrollPanel.getVerticalScrollBar().getValue();
                y2 = labelTop + lblSelected.getHeight();

                //ensure clipped to scroll panel
                if (y2 > scrollPanelTop) {
                    if (labelTop < scrollPanelTop) {
                        labelTop = scrollPanelTop;
                    }
                    h2 = h1 - y2;
                    h1 = labelTop - y1;
                }
            }

            FSkin.setGraphicsColor(g2d, l00);
            g2d.fillRect(0, y1, w, h1);
            if (h2 > 0) {
                g2d.fillRect(0, y2, w, h2);
            }
            
            int x = w - 8;
            FSkin.setGraphicsGradientPaint(g2d, x, 0, l00, w, 0, d80);
            g2d.fillRect(x, y1, w, h1);
            if (h2 > 0) {
                g2d.fillRect(x, y2, w, h2);
            }

            g2d.dispose();
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onSwitching(forge.gui.framework.FScreen)
     */
    @Override
    public boolean onSwitching(FScreen fromScreen, FScreen toScreen) {
        return true;
    }

    /* (non-Javadoc)
     * @see forge.gui.framework.IVTopLevelUI#onClosing(forge.gui.control.FControl.Screens)
     */
    @Override
    public boolean onClosing(FScreen screen) {
        Singletons.getControl().exitForge();
        return false; //don't allow closing Home tab
    }
}
