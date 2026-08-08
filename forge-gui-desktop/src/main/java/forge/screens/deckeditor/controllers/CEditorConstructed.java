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
package forge.screens.deckeditor.controllers;

import forge.StaticData;
import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.GameType;
import forge.gui.GuiUtils;
import forge.gui.UiCommand;
import forge.gui.framework.FScreen;
import forge.item.PaperCard;
import forge.itemmanager.CardManager;
import forge.itemmanager.ItemManager;
import forge.itemmanager.ItemManagerConfig;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.deckeditor.AddBasicLandsDialog;
import forge.screens.deckeditor.CDeckEditorUI;
import forge.screens.deckeditor.ChangePrintingDialog;
import forge.screens.deckeditor.SEditorIO;
import forge.screens.match.controllers.CDetailPicture;
import forge.toolbox.FComboBox;
import forge.util.ItemPool;
import forge.util.Localizer;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Supplier;

/**
 * Child controller for constructed deck editor UI.
 * This is the least restrictive mode;
 * all cards are available.
 * 
 * <br><br><i>(C at beginning of class name denotes a control class.)</i>
 * 
 * @author Forge
 * @version $Id: CEditorConstructed.java 24868 2014-02-17 05:08:05Z drdev $
 */
public final class CEditorConstructed extends CDeckEditor<Deck> {
    private static final List<String> DEFAULT_CATALOG_SET_CODES = Arrays.asList("BT3K", "PH01");

    static List<String> getDefaultCatalogSetCodes() {
        return DEFAULT_CATALOG_SET_CODES;
    }

    private DeckController<Deck> controller;
    private final List<DeckSection> allSections = new ArrayList<>();
    private ItemPool<PaperCard> normalPool, avatarPool, planePool, schemePool, conspiracyPool,
            commanderPool, dungeonPool, attractionPool, contraptionPool;

    CardManager catalogManager;
    CardManager deckManager;
    private ItemPool<PaperCard> activeCatalogSourcePool;
    private ItemManagerConfig activeCatalogConfig;
    private final ActionListener sectionChangeListener = actionEvent -> {
        final FComboBox<?> cb = (FComboBox<?>) actionEvent.getSource();
        setEditorMode((DeckSection) cb.getSelectedItem());
    };

    //=========== Constructor
    /**
     * Child controller for constructed deck editor UI.
     * This is the least restrictive mode;
     * all cards are available.
     */
    @SuppressWarnings("serial")
    public CEditorConstructed(final CDetailPicture cDetailPicture0) {
        this(cDetailPicture0, GameType.Constructed);
    }

    public CEditorConstructed(final CDetailPicture cDetailPicture0, final GameType gameType0) {
        super(FScreen.DECK_EDITOR_CONSTRUCTED, cDetailPicture0, gameType0);

        boolean wantUnique = false;

        allSections.add(DeckSection.Main);
        allSections.add(DeckSection.Sideboard);

        switch (this.gameType) {
            case Constructed:
                allSections.add(DeckSection.Avatar);
                allSections.add(DeckSection.Conspiracy);

                normalPool = FModel.getAllCards();
                avatarPool = FModel.getAvatarPool();
                conspiracyPool = FModel.getConspiracyPool();

                break;
            case Commander:
                allSections.add(DeckSection.Commander);

                commanderPool = FModel.getCommanderPool();
                normalPool = FModel.getAllCards();

                wantUnique = true;
                break;
            case TinyLeaders:
                allSections.add(DeckSection.Commander);

                commanderPool = FModel.getTinyLeadersCommander();
                normalPool = FModel.getAllCards();

                wantUnique = true;
                break;
            case Oathbreaker:
                allSections.add(DeckSection.Commander);

                commanderPool = FModel.getOathbreakerCommander();
                normalPool = FModel.getAllCards();

                wantUnique = true;
                break;
            case Brawl:
                allSections.add(DeckSection.Commander);

                commanderPool = FModel.getBrawlCommander();
                normalPool = ItemPool.createFrom(FModel.getFormats().get("Brawl").getAllCards(), PaperCard.class);

                wantUnique = true;
                break;
            default:
        }

        allSections.add(DeckSection.Planes);
        allSections.add(DeckSection.Schemes);
        allSections.add(DeckSection.Dungeon);

        planePool = FModel.getPlanechaseCards();
        schemePool = FModel.getArchenemyCards();
        dungeonPool = FModel.getDungeonPool();

        allSections.add(DeckSection.Attractions);
        attractionPool = FModel.getAttractionPool();

        contraptionPool = FModel.getContraptionPool();
        if(!contraptionPool.isEmpty()) //Hide if un-cards are disabled.
            allSections.add(DeckSection.Contraptions);

        catalogManager = new CardManager(getCDetailPicture(), wantUnique, false, false,
                DEFAULT_CATALOG_SET_CODES);
        deckManager = new CardManager(getCDetailPicture(), false, false, false);
        deckManager.setAlwaysNonUnique(true);

        final Localizer localizer = Localizer.getInstance();

        catalogManager.setCaption(localizer.getMessage("lblCatalog"));

        this.setCatalogManager(catalogManager);
        this.setDeckManager(deckManager);

        final Supplier<Deck> newCreator = Deck::new;

        switch (this.gameType) {
            case Constructed:
                this.controller = new DeckController<>(FModel.getDecks().getConstructed(), this, newCreator);
                break;
            case Commander:
                this.controller = new DeckController<>(FModel.getDecks().getCommander(), this, newCreator);
                break;
            case Oathbreaker:
                this.controller = new DeckController<>(FModel.getDecks().getOathbreaker(), this, newCreator);
                break;
            case Brawl:
                this.controller = new DeckController<>(FModel.getDecks().getBrawl(), this, newCreator);
                break;
            case TinyLeaders:
                this.controller = new DeckController<>(FModel.getDecks().getTinyLeaders(), this, newCreator);
                break;
            default:
        }

        getBtnAddBasicLands().setCommand((UiCommand) () -> CEditorConstructed.addBasicLands(CEditorConstructed.this));
    }

    //=========== Overridden from ACEditorBase

    @Override
    protected CardLimit getCardLimit() {
        if (sectionMode == DeckSection.Avatar) {
            return CardLimit.Singleton;
        }
        if (FModel.getPreferences().getPrefBoolean(FPref.ENFORCE_DECK_LEGALITY)) {
            switch (this.gameType) {
                case Constructed:
                    return CardLimit.Default;
                case Commander:
                case Oathbreaker:
                case TinyLeaders:
                case Brawl:
                    return CardLimit.Singleton;
                default:
            }
        }
        return CardLimit.None; //if not enforcing deck legality, don't enforce default limit
    }

    public static void onAddItems(ACEditorBase<PaperCard, Deck> editor, Iterable<Entry<PaperCard, Integer>> items, boolean toAlternate) {
        DeckSection sectionMode = editor.sectionMode;
        DeckController<Deck> controller = editor.getDeckController();

        switch (sectionMode) {
        case Commander:
            int count = editor.getDeckManager().getItemCount();
            if (count > 0) {
                PaperCard newCard = items.iterator().next().getKey(); //can only add one card at a time when on commander section
                if (editor.gameType == GameType.Oathbreaker) { //replace oathbreaker or signature spell if needed
                    for (Entry<PaperCard, Integer> entry : editor.getDeckManager().getPool()) {
                        if (entry.getKey().getRules().canBeOathbreaker() == newCard.getRules().canBeOathbreaker()) {
                            editor.getDeckManager().removeItem(entry.getKey(), entry.getValue());
                            break;
                        }
                    }
                }
                else { //replace existing commander unless new commander is valid partner commander
                    if (count == 1 && newCard.getRules().canBePartnerCommander()) { //replace existing partners regardless
                        PaperCard commander = editor.getDeckManager().getPool().toFlatList().get(0);
                        if (!commander.getRules().canBePartnerCommanders(newCard.getRules())) {
                            editor.getDeckManager().removeAllItems();
                        }
                    }
                    else {
                        editor.getDeckManager().removeAllItems();
                    }
                }
            }
            break;
        case Avatar:
            editor.getDeckManager().removeAllItems();
            break;
        default:
            break;
        }

        ItemPool<PaperCard> itemsToAdd = editor.getAllowedAdditions(items);
        if (itemsToAdd.isEmpty()) { return; }

        if (toAlternate) {
            switch (sectionMode) {
            case Main:
                controller.getModel().getOrCreate(DeckSection.Sideboard).addAll(itemsToAdd);
                break;
            default:
                return; //no other sections should support toAlternate
            }
        }
        else {
            editor.getDeckManager().addItems(itemsToAdd);
        }

        if (editor.getCatalogManager().isInfinite()) {
            //select all added cards in Catalog if infinite
            editor.getCatalogManager().selectItemEntrys(itemsToAdd);
        }
        else {
            //remove all added cards from Catalog if not infinite
            editor.getCatalogManager().removeItems(items);
        }

        controller.notifyModelChanged();
    }

    public static void onRemoveItems(ACEditorBase<PaperCard, Deck> editor, Iterable<Entry<PaperCard, Integer>> items, boolean toAlternate) {
        DeckSection sectionMode = editor.sectionMode;
        DeckController<Deck> controller = editor.getDeckController();

        if (toAlternate) {
            switch (sectionMode) {
            case Main:
                controller.getModel().getOrCreate(DeckSection.Sideboard).addAll(items);
                break;
            case Sideboard:
                controller.getModel().get(DeckSection.Main).addAll(items);
                break;
            default:
                break; //no other sections should support toAlternate
            }
        }
        else if (!editor.getCatalogManager().isInfinite()) {
            editor.getCatalogManager().addItems(items);
        }
        editor.getDeckManager().removeItems(items);

        controller.notifyModelChanged();
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#onAddItems()
     */
    @Override
    protected void onAddItems(Iterable<Entry<PaperCard, Integer>> items, boolean toAlternate) {
        onAddItems(this, items, toAlternate);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#onRemoveItems()
     */
    @Override
    protected void onRemoveItems(Iterable<Entry<PaperCard, Integer>> items, boolean toAlternate) {
        onRemoveItems(this, items, toAlternate);
    }

    public static void buildAddContextMenu(EditorContextMenuBuilder cmb, DeckSection sectionMode, GameType gameType) {
        final Localizer localizer = Localizer.getInstance();
        switch (sectionMode) {
        case Main:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltodeck"));
            cmb.addMoveAlternateItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltosideboard"));
            break;
        case Sideboard:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltosideboard"));
            break;
        case Commander:
            if (gameType == GameType.Oathbreaker) {
                PaperCard pc = cmb.getItemManager().getSelectedItem();
                if (pc != null && pc.getRules().canBeSignatureSpell()) {
                    cmb.addMoveItems(localizer.getMessage("lblSetEdition"), localizer.getMessage("lblassignaturespell"));
                }
                else {
                    cmb.addMoveItems(localizer.getMessage("lblSetEdition"), localizer.getMessage("lblasoathbreaker"));
                }
            }
            else {
                cmb.addMoveItems(localizer.getMessage("lblSetEdition"), localizer.getMessage("lblascommander"));
            }
            break;
        case Avatar:
            cmb.addMoveItems(localizer.getMessage("lblSetEdition"), localizer.getMessage("lblasavatar"));
            break;
        case Schemes:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltoschemedeck"));
            break;
        case Planes:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltoplanardeck"));
            break;
        case Conspiracy:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltoconspiracydeck"));
            break;
        case Dungeon:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltodungeondeck"));
            break;
        case Attractions:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltoattractiondeck"));
            break;
        case Contraptions:
            cmb.addMoveItems(localizer.getMessage("lblAdd"), localizer.getMessage("lbltocontraptiondeck"));
            break;
        }
    }

    public static void buildRemoveContextMenu(EditorContextMenuBuilder cmb, DeckSection sectionMode, boolean foilAvailable) {
        final Localizer localizer = Localizer.getInstance();
        switch (sectionMode) {
        case Main:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromdeck"));
            cmb.addMoveAlternateItems(localizer.getMessage("lblMove"), localizer.getMessage("lbltosideboard"));
            break;
        case Sideboard:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromsideboard"));
            cmb.addMoveAlternateItems("Move", "to deck");
            break;
        case Commander:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblascommander"));
            break;
        case Avatar:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblasavatar"));
            break;
        case Schemes:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromschemedeck"));
            break;
        case Planes:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromplanardeck"));
            break;
        case Conspiracy:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromconspiracydeck"));
            break;
        case Dungeon:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromdungeondeck"));
            break;
        case Attractions:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromattractiondeck"));
            break;
        case Contraptions:
            cmb.addMoveItems(localizer.getMessage("lblRemove"), localizer.getMessage("lblfromcontraptiondeck"));
            break;
        }
        addChangePrintingEntryIfApplicable(cmb);
        if (foilAvailable) {
            cmb.addMakeFoils();
        }
        cmb.addKeyCardToggle();
        cmb.addSetColorID();
    }

    private static void addChangePrintingEntryIfApplicable(EditorContextMenuBuilder cmb) {
        // Hide in finite-pool editors (Quest) where the user could otherwise swap into a printing they don't own.
        if (!CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController().getCatalogManager().isInfinite()) {
            return;
        }
        PaperCard card = cmb.getItemManager().getSelectedItem();
        if (card == null) {
            return;
        }
        if (StaticData.instance().getCommonCards().getAllCardsNoAlt(card.getName()).size() <= 1) {
            return;
        }
        GuiUtils.addMenuItem(cmb.getMenu(),
                Localizer.getInstance().getMessage("lblChangePrinting"),
                null,
                () -> {
                    PaperCard chosen = ChangePrintingDialog.show(card);
                    if (chosen == null) { return; }
                    PaperCard newCard = card.isFoil() ? chosen.getFoiled() : chosen;
                    if (newCard.equals(card)) { return; }
                    CardManager deckManager = (CardManager) cmb.getItemManager();
                    deckManager.removeItem(card, 1);
                    deckManager.addItem(newCard, 1);
                    CDeckEditorUI.SINGLETON_INSTANCE.getCurrentEditorController()
                            .getDeckController().notifyModelChanged();
                },
                true,
                false);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#buildAddContextMenu()
     */
    @Override
    protected void buildAddContextMenu(EditorContextMenuBuilder cmb) {
        buildAddContextMenu(cmb, sectionMode, gameType);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#buildRemoveContextMenu()
     */
    @Override
    protected void buildRemoveContextMenu(EditorContextMenuBuilder cmb) {
        buildRemoveContextMenu(cmb, sectionMode, true);
    }

    /*
     * (non-Javadoc)
     * 
     * @see forge.gui.deckeditor.ACEditorBase#resetTables()
     */
    @Override
    public void resetTables() {
        DeckSection selectedSection = (DeckSection) this.getCbxSection().getSelectedItem();
        if (selectedSection != DeckSection.Main || sectionMode != DeckSection.Main
                || activeCatalogSourcePool != normalPool) {
            // Constructed mode can use all cards, no limitations.
            this.getCbxSection().setSelectedItem(DeckSection.Main);
            this.setEditorMode(DeckSection.Main);
        } else
            this.getDeckManager().setPool(this.controller.getModel().getMain());
    }

    @Override
    public Boolean isSectionImportable(DeckSection section) {
        return allSections.contains(section);
    }

    /*
     * (non-Javadoc)
     * 
     * @see forge.gui.deckeditor.ACEditorBase#getController()
     */
    @Override
    public DeckController<Deck> getDeckController() {
        return this.controller;
    }

    /**
     * Switch between the main deck and the sideboard editor.
     */
    public void setEditorMode(DeckSection sectionMode) {
        if (sectionMode == null) {
            return;
        }
        ItemManager<PaperCard> deckManager = this.getDeckManager();
        switch(sectionMode) {
            case Main:
                setCatalogPool(ItemManagerConfig.CARD_CATALOG, normalPool, true);
                deckManager.setPool(this.controller.getModel().getMain());
                break;
            case Sideboard:
                setCatalogPool(ItemManagerConfig.CARD_CATALOG, normalPool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Sideboard));
                break;
            case Avatar:
                setCatalogPool(ItemManagerConfig.AVATAR_POOL, avatarPool, false);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Avatar));
                break;
            case Planes:
                setCatalogPool(ItemManagerConfig.PLANAR_POOL, planePool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Planes));
                break;
            case Schemes:
                setCatalogPool(ItemManagerConfig.SCHEME_POOL, schemePool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Schemes));
                break;
            case Commander:
                if(gameType == GameType.Constructed)
                    break;
                setCatalogPool(ItemManagerConfig.COMMANDER_POOL, commanderPool, false);
                this.getDeckManager().setPool(this.controller.getModel().getOrCreate(DeckSection.Commander));
                break;
            case Conspiracy:
                setCatalogPool(ItemManagerConfig.CONSPIRACY_DECKS, conspiracyPool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Conspiracy));
                break;
            case Dungeon:
                setCatalogPool(ItemManagerConfig.DUNGEON_DECKS, dungeonPool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Dungeon));
                break;
            case Attractions:
                setCatalogPool(ItemManagerConfig.ATTRACTION_POOL, attractionPool, true);
                deckManager.setPool(this.controller.getModel().getOrCreate(DeckSection.Attractions));
                break;
            case Contraptions:
                setCatalogPool(ItemManagerConfig.CONTRAPTION_POOL, contraptionPool, true);
                deckManager.setPool((this.controller.getModel().getOrCreate(DeckSection.Contraptions)));
                break;
        }

        this.sectionMode = sectionMode;
        this.controller.updateCaptions();
    }

    private void setCatalogPool(final ItemManagerConfig config, final ItemPool<PaperCard> sourcePool,
                                final boolean allowMultipleSelections) {
        final ItemManager<PaperCard> manager = this.getCatalogManager();
        if (activeCatalogConfig != config) {
            manager.setup(config);
        }
        if (activeCatalogConfig != config || activeCatalogSourcePool != sourcePool) {
            manager.setPool(sourcePool, true);
            activeCatalogConfig = config;
            activeCatalogSourcePool = sourcePool;
        }
        manager.setAllowMultipleSelections(allowMultipleSelections);
    }

    public static void addBasicLands(ACEditorBase<PaperCard, Deck> editor) {
        Deck deck = editor.getDeckController().getModel();
        if (deck == null) { return; }

        AddBasicLandsDialog dialog = new AddBasicLandsDialog(deck);
        CardPool landsToAdd = dialog.show();
        if (landsToAdd != null) {
            editor.onAddItems(landsToAdd, false);
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.ACEditorBase#show(forge.Command)
     */
    @Override
    public void update() {
        this.getCatalogManager().setup(ItemManagerConfig.CARD_CATALOG);
        this.getDeckManager().setup(ItemManagerConfig.DECK_EDITOR);

        resetUI();

        final FComboBox cbxSection = this.getCbxSection();
        cbxSection.removeActionListener(sectionChangeListener);
        cbxSection.removeAllItems();
        for (DeckSection section : allSections) {
            cbxSection.addItem(section);
        }
        cbxSection.setVisible(true);

        this.controller.refreshModel();
        cbxSection.addActionListener(sectionChangeListener);
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.controllers.ACEditorBase#canSwitchAway()
     */
    @Override
    public boolean canSwitchAway(boolean isClosing) {
        return SEditorIO.confirmSaveChanges(FScreen.DECK_EDITOR_CONSTRUCTED, false); //ignore isClosing since screen can't close
    }

    /* (non-Javadoc)
     * @see forge.gui.deckeditor.controllers.ACEditorBase#resetUIChanges()
     */
    @Override
    public void resetUIChanges() {
    }
}
