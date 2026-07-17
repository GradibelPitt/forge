package forge.gamemodes.match.input;

import forge.ai.ComputerUtilMana;
import forge.ai.ManaPaymentPreview;
import forge.ai.PlayerControllerAi;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.game.Game;
import forge.game.GameAction;
import forge.game.card.Card;
import forge.game.card.CardView;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.player.PlayerController.FullControlFlag;
import forge.game.player.PlayerView;
import forge.game.player.actions.PayManaFromPoolAction;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.spellability.SpellAbilityView;
import forge.game.zone.ZoneType;
import forge.gui.FThreads;
import forge.player.PlayableManaAbilityUtil;
import forge.player.PlayerControllerHuman;
import forge.util.ITriggerEvent;
import forge.util.Localizer;
import forge.util.TextUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Mana-payment input whose game-state work is serialized by its Game actor. */
public abstract class InputPayMana extends InputSyncronizedBase {
    private static final long serialVersionUID = 718128600948280315L;
    private static final ZoneType[] PAYMENT_SOURCE_ZONES = {
            ZoneType.Hand, ZoneType.Battlefield, ZoneType.Graveyard,
            ZoneType.Exile, ZoneType.Command
    };

    protected int phyLifeToLose;
    protected final Player player;
    protected final Game game;
    protected ManaCostBeingPaid manaCost;
    protected final SpellAbility saPaidFor;
    protected boolean effect;
    protected boolean mandatory;

    private final ArrayDeque<CardIdentity> delayedCards = new ArrayDeque<>();
    private final AtomicBoolean autoActionPending = new AtomicBoolean();
    private final AtomicInteger pendingPaymentActions = new AtomicInteger();
    private final AtomicBoolean stopQueued = new AtomicBoolean();
    private final AtomicBoolean cleanupStarted = new AtomicBoolean();
    private final AtomicLong uiGeneration = new AtomicLong();

    private volatile GameAction.InputActionScope inputActionScope;
    private volatile Set<CardIdentity> actionableIdentities = Set.of();
    private volatile Map<Integer, CardIdentity> actionableByCardId = Map.of();
    private volatile boolean paid;
    private boolean initialized;
    private boolean paidForStatePushed;
    private boolean wasFloatingMana;
    private Map<CardIdentity, Card> laneCandidates = new LinkedHashMap<>();

    protected InputPayMana(final PlayerControllerHuman controller,
            final SpellAbility saPaidFor0, final Player player0,
            final boolean effect0) {
        super(controller);
        player = player0;
        game = player.getGame();
        saPaidFor = saPaidFor0;
        effect = effect0;
    }

    @Override
    public final void showAndWait() {
        FThreads.assertExecutedByEdt(false);
        if (game.isGameOver()) {
            super.relaseLatchWhenGameIsOver();
            return;
        }
        final GameAction.InputActionScope scope = game.getAction()
                .beginInputActionScope(getClass().getSimpleName());
        inputActionScope = scope;
        game.getAction().setInputActionTermination(scope,
                this::performStopOnLane);
        Throwable setupFailure = null;

        if (!game.getAction().invokeInputAction(scope, () -> {
            try {
                initializeInputStateOnLane();
                initialized = true;
                refreshStateOnLane();
            } catch (final RuntimeException | Error failure) {
                guiLog.error(failure, "Unable to initialize mana-payment input");
                performStopOnLane();
            }
        })) {
            game.getAction().abandonInputActionScope(scope);
            super.relaseLatchWhenGameIsOver();
            inputActionScope = null;
            return;
        }

        try {
            getController().getInputQueue().setInput(this);
        } catch (final RuntimeException | Error failure) {
            setupFailure = failure;
            requestStopFromAnyThread();
        }

        try {
            game.getAction().pumpInputActionsUntil(scope,
                    this::isLatchReleased);
        } finally {
            game.getAction().sealInputActionScope(scope);
            if (!cleanupStarted.get()) {
                performStopOnLane();
            }
            inputActionScope = null;
        }

        if (setupFailure instanceof RuntimeException) {
            throw (RuntimeException) setupFailure;
        }
        if (setupFailure instanceof Error) {
            throw (Error) setupFailure;
        }
    }

    /** Runs once as the first accepted action for this input. */
    protected void initializeInputStateOnLane() {
        wasFloatingMana = !player.getManaPool().isEmpty();
        if (wasFloatingMana) {
            FThreads.invokeInEdtNowOrLater(() -> getController().getGui()
                    .showManaPool(PlayerView.get(player)));
        }
    }

    @Override
    public final void stop() {
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope != null && game.getAction().isExecutingInputAction(scope)) {
            performStopOnLane();
            return;
        }
        if (!requestStopFromAnyThread()) {
            super.relaseLatchWhenGameIsOver();
        }
    }

    @Override
    public final void relaseLatchWhenGameIsOver() {
        if (!requestStopFromAnyThread()) {
            super.relaseLatchWhenGameIsOver();
        }
    }

    @Override
    protected final void onLatchReleased() {
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope != null) {
            game.getAction().sealInputActionScope(scope);
        }
    }

    private boolean requestStopFromAnyThread() {
        if (cleanupStarted.get() || !stopQueued.compareAndSet(false, true)) {
            return cleanupStarted.get() || stopQueued.get();
        }
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope != null && game.getAction().invokeCriticalInputAction(scope,
                this::performStopOnLane)) {
            return true;
        }
        stopQueued.set(false);
        return false;
    }

    private void performStopOnLane() {
        if (!cleanupStarted.compareAndSet(false, true)) {
            super.relaseLatchWhenGameIsOver();
            return;
        }
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope != null) {
            game.getAction().sealInputActionScope(scope);
        }
        super.stop();
    }

    @Override
    protected final void onStop() {
        uiGeneration.incrementAndGet();
        actionableIdentities = Set.of();
        actionableByCardId = Map.of();
        laneCandidates = new LinkedHashMap<>();
        pendingPaymentActions.set(0);
        autoActionPending.set(false);
        delayedCards.clear();

        Throwable failure = null;
        try {
            if (paidForStatePushed) {
                saPaidFor.setManaCostBeingPaid(null);
                player.popPaidForSA();
                paidForStatePushed = false;
            }
        } catch (final RuntimeException | Error ex) {
            failure = ex;
        }
        try {
            onManaInputStoppedOnLane();
        } catch (final RuntimeException | Error ex) {
            failure = preserveFirstFailure(failure, ex);
        }
        try {
            FThreads.invokeInEdtNowOrLater(() -> {
                getController().clearActionableCards();
                if (wasFloatingMana) {
                    getController().getGui().hideManaPool(
                            PlayerView.get(player));
                }
            });
        } catch (final RuntimeException | Error ex) {
            failure = preserveFirstFailure(failure, ex);
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
    }

    private static Throwable preserveFirstFailure(final Throwable first,
            final Throwable next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Subclasses may clean up additional game state here. */
    protected void onManaInputStoppedOnLane() {
    }

    protected final void markPaidForStatePushed() {
        paidForStatePushed = true;
    }

    @Override
    protected final boolean onCardSelected(final Card card,
            final List<Card> otherCardsToSelect,
            final ITriggerEvent triggerEvent) {
        final CardIdentity selected = CardIdentity.of(card);
        if (selected == null) {
            return false;
        }
        final List<CardIdentity> others = new ArrayList<>();
        if (otherCardsToSelect != null) {
            for (final Card other : otherCardsToSelect) {
                final CardIdentity identity = CardIdentity.of(other);
                if (identity != null) {
                    others.add(identity);
                }
            }
        }
        return submitManualPaymentAction(
                () -> selectCardOnLane(selected, others));
    }

    /** InputProxy uses this to avoid resolving or probing card abilities on the UI thread. */
    final boolean selectCardById(final int cardId,
            final List<Integer> otherCardIds) {
        final CardIdentity selected = actionableByCardId.get(cardId);
        if (selected == null) {
            return false;
        }
        final List<CardIdentity> others = new ArrayList<>();
        if (otherCardIds != null) {
            for (final Integer otherCardId : otherCardIds) {
                if (otherCardId == null) {
                    continue;
                }
                final CardIdentity identity = actionableByCardId.get(
                        otherCardId);
                if (identity != null) {
                    others.add(identity);
                }
            }
        }
        return submitManualPaymentAction(
                () -> selectCardOnLane(selected, others));
    }

    @Override
    public final boolean selectAbility(final SpellAbility ability) {
        if (ability == null || ability.getHostCard() == null) {
            return false;
        }
        final AbilityIdentity identity = AbilityIdentity.of(ability);
        return submitManualPaymentAction(() -> activateManaAbilityOnLane(
                identity.card, identity));
    }

    @Override
    protected final void onPlayerSelected(final Player selected,
            final ITriggerEvent triggerEvent) {
        if (selected != null) {
            selectPlayerById(selected.getId());
        }
    }

    final void selectPlayerById(final int playerId) {
        submitManualPaymentAction(() -> {
            final Player selected = game.getPlayer(playerId);
            if (selected != null) {
                onPlayerSelectedOnLane(selected);
            }
        });
    }

    protected void onPlayerSelectedOnLane(final Player selected) {
    }

    @Override
    protected final void onCancel() {
        requestStopFromAnyThread();
    }

    @Override
    protected final void onOk() {
        if (!supportAutoPay()) {
            return;
        }
        submitAutoPaymentAction();
    }

    public final void useManaFromPool(final byte colorCode) {
        submitManualPaymentAction(() -> {
            if (player.getManaPool().tryPayCostWithColor(colorCode, saPaidFor,
                    manaCost, saPaidFor.getPayingMana())) {
                if (getController().macros() != null) {
                    getController().macros().addRememberedAction(
                            new PayManaFromPoolAction(colorCode));
                }
            }
        });
    }

    private boolean submitManualPaymentAction(final Runnable action) {
        return submitPaymentAction(action, false);
    }

    private boolean submitAutoPaymentAction() {
        if (!autoActionPending.compareAndSet(false, true)) {
            return false;
        }
        final boolean accepted = submitPaymentAction(
                this::payManaAutomaticallyOnLane, true);
        if (!accepted) {
            autoActionPending.set(false);
        }
        return accepted;
    }

    private boolean submitPaymentAction(final Runnable action,
            final boolean autoAction) {
        if (action == null || isFinished() || cleanupStarted.get()) {
            return false;
        }
        pendingPaymentActions.incrementAndGet();
        final GameAction.InputActionScope scope = inputActionScope;
        final boolean accepted = scope != null && game.getAction()
                .invokeInputAction(scope, () -> {
                    try {
                        if (!cleanupStarted.get() && !game.isGameOver()) {
                            action.run();
                        }
                    } catch (final RuntimeException | Error failure) {
                        guiLog.error(failure, "Mana-payment action failed");
                    } finally {
                        pendingPaymentActions.decrementAndGet();
                        if (autoAction) {
                            autoActionPending.set(false);
                        }
                        if (!cleanupStarted.get()) {
                            refreshStateOnLane();
                        }
                    }
                });
        if (!accepted) {
            pendingPaymentActions.decrementAndGet();
        }
        return accepted;
    }

    private void selectCardOnLane(final CardIdentity selected,
            final List<CardIdentity> others) {
        if (getController().getGui().isLibgdxPort()) {
            for (final CardIdentity other : others) {
                final Card card = resolveCard(other);
                if (card != null && !getAllManaAbilities(card).isEmpty()) {
                    delayedCards.addLast(CardIdentity.of(card));
                }
            }
            if (!activateManaAbilityOnLane(selected, null)) {
                activateDelayedCardOnLane();
            }
            return;
        }
        activateManaAbilityOnLane(selected, null);
    }

    private boolean activateDelayedCardOnLane() {
        while (!delayedCards.isEmpty() && manaCost != null
                && !manaCost.isPaid()) {
            if (activateManaAbilityOnLane(delayedCards.removeFirst(), null)) {
                return true;
            }
        }
        if (manaCost != null && manaCost.isPaid()) {
            delayedCards.clear();
        }
        return false;
    }

    protected List<SpellAbility> getAllManaAbilities(final Card card) {
        return PlayableManaAbilityUtil.getPlayableManaAbilities(card, player);
    }

    @Deprecated
    public final List<SpellAbility> getUsefulManaAbilities(final Card card) {
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope == null || !game.getAction().isExecutingInputAction(scope)) {
            return new ArrayList<>();
        }
        final List<SpellAbility> abilities = new ArrayList<>();
        if (card == null || card.getController() != player) {
            return abilities;
        }
        byte colorCanUse = 0;
        for (final byte color : ManaAtom.MANATYPES) {
            if (manaCost.isAnyPartPayableWith(color, player.getManaPool())) {
                colorCanUse |= color;
            }
        }
        if (manaCost.isAnyPartPayableWith((byte) ManaAtom.GENERIC,
                player.getManaPool())) {
            colorCanUse |= ManaAtom.GENERIC;
        }
        for (final SpellAbility ability : getAllManaAbilities(card)) {
            ability.setActivatingPlayer(player);
            if (ability.isManaAbilityFor(saPaidFor, colorCanUse)) {
                abilities.add(ability);
            }
        }
        return abilities;
    }

    @Override
    public final String getActivateAction(final Card card) {
        final CardIdentity identity = CardIdentity.of(card);
        return identity != null && actionableIdentities.contains(identity)
                ? Localizer.getInstance().getMessage("lblPayManaWithCard")
                : null;
    }

    final String getActivateAction(final int cardId) {
        return actionableByCardId.containsKey(cardId)
                ? Localizer.getInstance().getMessage("lblPayManaWithCard")
                : null;
    }

    private boolean activateManaAbilityOnLane(final CardIdentity cardIdentity,
            final AbilityIdentity requestedAbility) {
        final Card card = resolveCard(cardIdentity);
        if (card == null || manaCost == null || manaCost.isPaid()) {
            return false;
        }

        byte colorCanUse = 0;
        byte colorNeeded = 0;
        for (final byte color : ManaAtom.MANATYPES) {
            if (manaCost.isAnyPartPayableWith(color, player.getManaPool())) {
                colorCanUse |= color;
            }
            if (manaCost.needsColor(color, player.getManaPool())) {
                colorNeeded |= color;
            }
        }
        if (manaCost.isAnyPartPayableWith((byte) ManaAtom.GENERIC,
                player.getManaPool())) {
            colorCanUse |= ManaAtom.GENERIC;
        }
        if (colorCanUse == 0) {
            return false;
        }

        final List<SpellAbility> playable = getAllManaAbilities(card);
        final SpellAbility chosen;
        if (requestedAbility != null) {
            chosen = resolveAbility(playable, requestedAbility);
            if (chosen == null) {
                return false;
            }
        } else {
            chosen = chooseManaAbilityOnLane(card, playable, colorCanUse);
            if (chosen == null) {
                return false;
            }
        }

        if (colorNeeded == 0 && saPaidFor.getHostCard() != null
                && saPaidFor.getHostCard()
                .hasSVar("ManaNeededToAvoidNegativeEffect")) {
            for (final String color : saPaidFor.getHostCard()
                    .getSVar("ManaNeededToAvoidNegativeEffect").split(",")) {
                colorCanUse |= ManaAtom.fromName(color);
            }
        }
        if (saPaidFor.tracksManaSpent()) {
            colorCanUse = ColorSet.WUBRG.getColor();
        }
        final ColorSet colors = ColorSet.fromMask(
                colorNeeded == 0 ? colorCanUse : colorNeeded);
        int producedColorMask = 0;
        for (final byte color : ManaAtom.MANATYPES) {
            if (chosen.canProduce(MagicColor.toShortString(color))
                    && colors.hasAnyColor(color)) {
                producedColorMask |= color;
            }
        }
        chosen.setActivatingPlayer(player);
        chosen.setManaExpressChoice(ColorSet.fromMask(producedColorMask));

        if (PlaySpellAbility.playSpellAbility(getController(), player, chosen)) {
            // A nested activation-cost payment may restore its own saved
            // conversion matrix before the produced mana is auto-applied.
            prepareRefreshStateOnLane();
            boolean restrictionsMet = true;
            for (final AbilityManaPart part : chosen.getAllManaParts()) {
                if (!part.meetsManaRestrictions(saPaidFor)) {
                    restrictionsMet = false;
                    break;
                }
            }
            if (restrictionsMet && !player.getController().isFullControl(
                    FullControlFlag.NoPaymentFromManaAbility)) {
                player.getManaPool().payManaFromAbility(saPaidFor, manaCost,
                        chosen);
            }
        }
        return true;
    }

    private SpellAbility chooseManaAbilityOnLane(final Card card,
            final List<SpellAbility> abilities, final byte colorCanUse) {
        final Map<SpellAbilityView, SpellAbility> usable =
                new LinkedHashMap<>();
        for (final SpellAbility ability : abilities) {
            ability.setActivatingPlayer(player);
            if (!ability.isManaAbilityFor(saPaidFor, colorCanUse)) {
                continue;
            }
            usable.put(ability.getView(), ability);
        }
        if (usable.isEmpty()) {
            return null;
        }
        if (usable.size() == 1) {
            return usable.values().iterator().next();
        }
        // This call is made by the serialized game actor. GUI implementations
        // marshal their own modal work to EDT/GL/Netty as appropriate.
        final List<SpellAbility> ordered = new ArrayList<>(usable.values());
        ordered.sort((left, right) -> Integer.compare(
                left.getId(), right.getId()));
        return getController().getAbilityToPlay(card, ordered, null);
    }

    private SpellAbility resolveAbility(final List<SpellAbility> abilities,
            final AbilityIdentity requested) {
        for (final SpellAbility ability : abilities) {
            if (ability.getId() == requested.abilityId) {
                return ability;
            }
        }
        return null;
    }

    private Card resolveCard(final CardIdentity identity) {
        if (identity == null) {
            return null;
        }
        Card card = laneCandidates.get(identity);
        if (isCurrentCard(card, identity)) {
            return card;
        }
        card = game.findById(identity.cardId);
        return isCurrentCard(card, identity) ? card : null;
    }

    private boolean isCurrentCard(final Card card,
            final CardIdentity identity) {
        return card != null && card.getGame() == game
                && card.getId() == identity.cardId
                && card.getGameTimestamp() == identity.gameTimestamp
                && card.getZone() != null && card.getZone().contains(card);
    }

    protected void payManaAutomaticallyOnLane() {
        // Real Auto is deliberately not authorized by the advisory preview.
        // Re-run Forge's exact payment path against current state.
        player.runWithController(
                () -> ComputerUtilMana.payManaCost(manaCost, saPaidFor,
                        player, effect),
                new PlayerControllerAi(game, player,
                        player.getOriginalLobbyPlayer()));
    }

    protected boolean supportAutoPay() {
        return true;
    }

    private void refreshStateOnLane() {
        if (!initialized || cleanupStarted.get() || manaCost == null) {
            return;
        }
        prepareRefreshStateOnLane();
        uiGeneration.incrementAndGet();
        if (manaCost.isPaid()) {
            if (!paid) {
                paid = true;
                done();
            }
            requestStopFromAnyThread();
            return;
        }

        final long generation = uiGeneration.get();
        final String message = getMessage();
        final CardView hostView = saPaidFor.getHostCard() == null ? null
                : saPaidFor.getHostCard().getView();
        FThreads.invokeInEdtNowOrLater(() -> publishPromptOnUi(
                generation, message, hostView));
    }

    /** Restores any payment-scoped engine state before a prompt refresh. */
    protected void prepareRefreshStateOnLane() {
    }

    private void publishPromptOnUi(final long generation,
            final String message, final CardView hostView) {
        if (!isCurrentAndLive(generation)) {
            return;
        }
        updateButtons();
        showMessage(message, hostView);
        getController().clearActionableCards();
        final boolean collectActionable = getController()
                .shouldCollectPaymentActionableCards();
        submitAdvisoryPreview(generation, collectActionable);
    }

    protected void updateButtons() {
        if (supportAutoPay()) {
            getController().getGui().updateButtons(getOwner(),
                    Localizer.getInstance().getMessage("lblAuto"),
                    Localizer.getInstance().getMessage("lblCancel"),
                    true, !mandatory, true);
        } else {
            getController().getGui().updateButtons(getOwner(), "",
                    Localizer.getInstance().getMessage("lblCancel"),
                    false, !mandatory, false);
        }
    }

    private void submitAdvisoryPreview(final long generation,
            final boolean collectActionable) {
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope == null || generation != uiGeneration.get()
                || cleanupStarted.get()) {
            return;
        }
        game.getAction().invokeInputAction(scope, () -> {
            // Stale work is rejected before any candidate or ability walk.
            if (generation != uiGeneration.get() || cleanupStarted.get()
                    || game.isGameOver()) {
                return;
            }
            try {
                final CandidateSnapshot snapshot = collectCandidatesOnLane(
                        collectActionable);
                if (generation != uiGeneration.get()
                        || cleanupStarted.get()) {
                    return;
                }
                final ManaPaymentPreview.Result preview = supportAutoPay()
                        ? computeAdvisoryPreview(new ManaCostBeingPaid(manaCost),
                                snapshot.cards)
                        : null;
                if (generation != uiGeneration.get()
                        || cleanupStarted.get()) {
                    return;
                }
                laneCandidates = snapshot.byIdentity;
                actionableIdentities = Collections.unmodifiableSet(
                        new HashSet<>(snapshot.byIdentity.keySet()));
                final Map<Integer, CardIdentity> byCardId =
                        new LinkedHashMap<>();
                for (final CardIdentity identity
                        : snapshot.byIdentity.keySet()) {
                    byCardId.putIfAbsent(identity.cardId, identity);
                }
                actionableByCardId = Collections.unmodifiableMap(byCardId);
                final List<CardView> emphasized = preview == null
                        ? List.of() : resolvePreviewViews(preview, snapshot);
                FThreads.invokeInEdtNowOrLater(() -> publishPreviewOnUi(
                        generation, snapshot.views, emphasized));
            } catch (final RuntimeException | Error failure) {
                // Highlight/advisory failure never changes Auto authority.
                guiLog.error(failure, "Mana-payment advisory preview failed");
            }
        });
    }

    /** A nested input invalidates UI snapshots but does not seal this scope. */
    final void onInputDeactivated() {
        uiGeneration.incrementAndGet();
        actionableIdentities = Set.of();
        actionableByCardId = Map.of();
    }

    protected ManaPaymentPreview.Result computeAdvisoryPreview(
            final ManaCostBeingPaid cost,
            final Iterable<Card> explicitCandidates) {
        return ManaPaymentPreview.preview(cost, saPaidFor, player, effect,
                explicitCandidates);
    }

    private CandidateSnapshot collectCandidatesOnLane(
            final boolean collectActionable) {
        final Map<CardIdentity, Card> byIdentity = new LinkedHashMap<>();
        final List<Card> candidates = new ArrayList<>();
        final List<CardView> views = new ArrayList<>();
        for (final ZoneType zone : PAYMENT_SOURCE_ZONES) {
            for (final Card card : player.getCardsIn(zone)) {
                // The overwhelmingly common non-mana card path is local O(1)
                // and never expands all possible spell/activated abilities.
                if (card.getManaAbilities().isEmpty()) {
                    continue;
                }
                final List<SpellAbility> playable;
                try {
                    playable = getAllManaAbilities(card);
                } catch (final RuntimeException failure) {
                    guiLog.error(failure,
                            "Unable to inspect a mana source during payment");
                    continue;
                }
                if (playable.isEmpty()) {
                    continue;
                }
                final CardIdentity identity = CardIdentity.of(card);
                if (identity == null || byIdentity.putIfAbsent(identity,
                        card) != null) {
                    continue;
                }
                candidates.add(card);
                if (collectActionable) {
                    views.add(card.getView());
                }
            }
        }
        return new CandidateSnapshot(byIdentity, candidates, views);
    }

    private List<CardView> resolvePreviewViews(
            final ManaPaymentPreview.Result preview,
            final CandidateSnapshot snapshot) {
        final List<CardView> result = new ArrayList<>();
        for (final ManaPaymentPreview.Source source : preview.getSources()) {
            final Card card = snapshot.byIdentity.get(new CardIdentity(
                    source.getCardId(), source.getGameTimestamp()));
            if (card != null) {
                result.add(card.getView());
            }
        }
        return result;
    }

    private void publishPreviewOnUi(final long generation,
            final List<CardView> actionable,
            final List<CardView> emphasized) {
        if (!isCurrentAndLive(generation)) {
            return;
        }
        getController().pushPaymentActionableCards(actionable, emphasized);
    }

    private boolean isCurrentAndLive(final long generation) {
        return generation == uiGeneration.get() && !isFinished()
                && !cleanupStarted.get() && !game.isGameOver()
                && getController().getInputQueue().getInput() == this;
    }

    @Override
    public final void showMessage() {
        if (isFinished() || cleanupStarted.get()) {
            return;
        }
        final GameAction.InputActionScope scope = inputActionScope;
        if (scope != null) {
            game.getAction().invokeInputAction(scope,
                    this::refreshStateOnLane);
        }
    }

    protected abstract void done();

    /** Called only by the serialized per-game input actor. */
    protected abstract String getMessage();

    @Override
    public final String toString() {
        final ManaCostBeingPaid cost = manaCost;
        return TextUtil.concatNoSpace("PayManaBase ",
                cost == null ? "?" : cost.toString(), " left");
    }

    public final boolean isPaid() {
        return paid;
    }

    public final boolean isActivatingManaAbility() {
        return pendingPaymentActions.get() > 0;
    }

    protected volatile String messagePrefix;

    public final void setMessagePrefix(final String prompt) {
        messagePrefix = prompt;
    }

    static final class CardIdentity {
        private final int cardId;
        private final long gameTimestamp;

        CardIdentity(final int cardId, final long gameTimestamp) {
            this.cardId = cardId;
            this.gameTimestamp = gameTimestamp;
        }

        static CardIdentity of(final Card card) {
            return card == null ? null : new CardIdentity(card.getId(),
                    card.getGameTimestamp());
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof CardIdentity)) {
                return false;
            }
            final CardIdentity identity = (CardIdentity) other;
            return cardId == identity.cardId
                    && gameTimestamp == identity.gameTimestamp;
        }

        @Override
        public int hashCode() {
            return 31 * cardId + Long.hashCode(gameTimestamp);
        }
    }

    private static final class AbilityIdentity {
        private final CardIdentity card;
        private final int abilityId;

        private AbilityIdentity(final CardIdentity card,
                final int abilityId) {
            this.card = card;
            this.abilityId = abilityId;
        }

        private static AbilityIdentity of(final SpellAbility ability) {
            return new AbilityIdentity(CardIdentity.of(ability.getHostCard()),
                    ability.getId());
        }
    }

    private static final class CandidateSnapshot {
        private final Map<CardIdentity, Card> byIdentity;
        private final List<Card> cards;
        private final List<CardView> views;

        private CandidateSnapshot(final Map<CardIdentity, Card> byIdentity,
                final List<Card> cards, final List<CardView> views) {
            this.byIdentity = byIdentity;
            this.cards = cards;
            this.views = views;
        }
    }
}
