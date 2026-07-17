package forge.ai;

import forge.card.MagicColor;
import forge.card.mana.ManaAtom;
import forge.card.mana.ManaCostShard;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardPlayOption;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPartMana;
import forge.game.cost.CostTap;
import forge.game.mana.Mana;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.mana.ManaPool;
import forge.game.player.Player;
import forge.game.spellability.AbilityManaPart;
import forge.game.spellability.SpellAbility;
import forge.game.staticability.StaticAbility;
import forge.game.zone.ZoneType;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Strictly side-effect-free, advisory mana preview.
 *
 * <p>This is intentionally not the historical AI {@code test=true} payment
 * path. It never walks a zone, asks a controller, evaluates a dynamic
 * condition, executes a replacement/trigger, inspects global static abilities,
 * or changes an object reachable from the real game. Activated sources must be
 * supplied by an external index, and only a small, statically provable subset
 * is interpreted.</p>
 *
 * <p>An advisory result never authorizes payment. The real game-lane actor must
 * resolve the returned identity values and run the normal payment path again.
 * In particular, callers must not disable an input or skip real payment after
 * {@link Status#ADVISORY_UNPAYABLE}: global effects and repeatable zero-cost
 * sources are deliberately outside this preview.</p>
 */
public final class ManaPaymentPreview {
    private static final int MAX_CANDIDATE_SOURCES = 4_096;
    private static final int MAX_CANDIDATE_INPUTS = 8_192;
    private static final int MAX_MANA_ABILITIES = 8_192;
    private static final int MAX_FIXED_MANA_PER_SOURCE = 64;
    private static final Set<String> SIMPLE_MANA_PARAMS = Set.of(
            "AB", "Cost", "Produced", "Amount", "SpellDescription",
            "ManaDescription");

    private ManaPaymentPreview() {
    }

    /** Advisory states are display hints, never payment authority. */
    public enum Status {
        ADVISORY_PAYABLE,
        ADVISORY_UNPAYABLE,
        REQUIRES_CHOICE,
        RESOURCE_LIMIT,
        INVALID_INPUT,
        ERROR
    }

    /** Pure-value identity of a source in the original game. */
    public static final class Source {
        private final int cardId;
        private final long gameTimestamp;
        private final int uses;
        private final boolean floating;

        private Source(final int cardId, final long gameTimestamp,
                final int uses, final boolean floating) {
            this.cardId = cardId;
            this.gameTimestamp = gameTimestamp;
            this.uses = uses;
            this.floating = floating;
        }

        public int getCardId() {
            return cardId;
        }

        public long getGameTimestamp() {
            return gameTimestamp;
        }

        public int getUses() {
            return uses;
        }

        public boolean isFloating() {
            return floating;
        }
    }

    /** Immutable result safe to hand to a UI thread. */
    public static final class Result {
        private final Status status;
        private final List<Source> sources;
        private final int floatingManaUsed;
        private final int candidateCardsVisited;
        private final int manaAbilityCollectionsRead;
        private final int staticModeSourcesVisited;
        private final String unpaidCost;
        private final String diagnostic;

        private Result(final Status status, final List<Source> sources,
                final int floatingManaUsed, final int candidateCardsVisited,
                final int manaAbilityCollectionsRead,
                final int staticModeSourcesVisited,
                final String unpaidCost, final String diagnostic) {
            this.status = Objects.requireNonNull(status);
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
            this.floatingManaUsed = floatingManaUsed;
            this.candidateCardsVisited = candidateCardsVisited;
            this.manaAbilityCollectionsRead = manaAbilityCollectionsRead;
            this.staticModeSourcesVisited = staticModeSourcesVisited;
            this.unpaidCost = unpaidCost;
            this.diagnostic = diagnostic;
        }

        public Status getStatus() {
            return status;
        }

        public boolean isAdvisoryPayable() {
            return status == Status.ADVISORY_PAYABLE;
        }

        public boolean requiresChoice() {
            return status == Status.REQUIRES_CHOICE;
        }

        public List<Source> getSources() {
            return sources;
        }

        public int getFloatingManaUsed() {
            return floatingManaUsed;
        }

        /** Number of explicitly supplied, deduplicated candidates inspected. */
        public int getCandidateCardsVisited() {
            return candidateCardsVisited;
        }

        /** Number of explicit candidates whose local mana list was read. */
        public int getManaAbilityCollectionsRead() {
            return manaAbilityCollectionsRead;
        }

        /** Always zero: preview never probes global static modes. */
        public int getStaticModeSourcesVisited() {
            return staticModeSourcesVisited;
        }

        public String getUnpaidCost() {
            return unpaidCost;
        }

        public String getDiagnostic() {
            return diagnostic;
        }
    }

    /** Checks floating mana only; activated-source discovery is never implicit. */
    public static Result preview(final ManaCostBeingPaid requestedCost,
            final SpellAbility paidFor, final Player payer,
            final boolean effect) {
        return preview(requestedCost, paidFor, payer, effect, List.of());
    }

    /**
     * Checks floating mana plus an explicitly supplied, already indexed source
     * set. The caller must invoke this on the serialized game/input lane.
     */
    public static Result preview(final ManaCostBeingPaid requestedCost,
            final SpellAbility paidFor, final Player payer,
            final boolean effect, final Iterable<Card> candidateCards) {
        if (requestedCost == null || paidFor == null || payer == null
                || candidateCards == null || paidFor.getHostCard() == null
                || payer.getGame() == null
                || paidFor.getHostCard().getGame() != payer.getGame()) {
            return result(Status.INVALID_INPUT, List.of(), 0, 0, 0, 0,
                    requestedCost == null ? null : requestedCost.toString(),
                    "invalid input");
        }
        if (paidFor.isOffering() && paidFor.getSacrificedAsOffering() == null) {
            return result(Status.REQUIRES_CHOICE, List.of(), 0, 0, 0, 0,
                    requestedCost.toString(), "Offering has no selected card");
        }
        if (paidFor.isEmerge() && paidFor.getSacrificedAsEmerge() == null) {
            return result(Status.REQUIRES_CHOICE, List.of(), 0, 0, 0, 0,
                    requestedCost.toString(), "Emerge has no selected card");
        }

        try {
            final CandidateSnapshot snapshot = snapshotCandidates(candidateCards);
            if (snapshot.limitExceeded) {
                return result(Status.RESOURCE_LIMIT, List.of(), 0, 0, 0, 0,
                        requestedCost.toString(),
                        "candidate source limit exceeded");
            }
            return new Planner(requestedCost, paidFor, payer, effect,
                    snapshot.cards).plan();
        } catch (final RuntimeException ex) {
            return result(Status.ERROR, List.of(), 0, 0, 0, 0,
                    requestedCost.toString(), ex.getClass().getSimpleName());
        }
    }

    private static Result result(final Status status,
            final List<Source> sources, final int floatingManaUsed,
            final int candidateCardsVisited,
            final int manaAbilityCollectionsRead,
            final int staticModeSourcesVisited,
            final String unpaidCost, final String diagnostic) {
        return new Result(status, sources, floatingManaUsed,
                candidateCardsVisited, manaAbilityCollectionsRead,
                staticModeSourcesVisited, unpaidCost, diagnostic);
    }

    private static CandidateSnapshot snapshotCandidates(
            final Iterable<Card> candidates) {
        final List<Card> snapshot = new ArrayList<>();
        final Set<Card> seen = Collections.newSetFromMap(
                new IdentityHashMap<>());
        int inputs = 0;
        for (final Card card : candidates) {
            if (++inputs > MAX_CANDIDATE_INPUTS) {
                return CandidateSnapshot.limitExceeded();
            }
            if (card == null || !seen.add(card)) {
                continue;
            }
            if (snapshot.size() == MAX_CANDIDATE_SOURCES) {
                return CandidateSnapshot.limitExceeded();
            }
            snapshot.add(card);
        }
        return new CandidateSnapshot(Collections.unmodifiableList(snapshot),
                false);
    }

    private static final class CandidateSnapshot {
        private final List<Card> cards;
        private final boolean limitExceeded;

        private CandidateSnapshot(final List<Card> cards,
                final boolean limitExceeded) {
            this.cards = cards;
            this.limitExceeded = limitExceeded;
        }

        private static CandidateSnapshot limitExceeded() {
            return new CandidateSnapshot(List.of(), true);
        }
    }

    private static final class Planner {
        private ManaCostBeingPaid cost;
        private final SpellAbility paidFor;
        private final Player payer;
        private final boolean effect;
        private final List<Card> candidateCards;
        private final DetachedManaPool pool;
        private final List<SimpleManaSource> manaSources = new ArrayList<>();
        private final Set<Card> usedCards = Collections.newSetFromMap(
                new IdentityHashMap<>());
        private final LinkedHashMap<SourceKey, MutableSource> usedSources =
                new LinkedHashMap<>();
        private boolean choiceRequired;
        private boolean unresolvedX;
        private boolean paymentRestriction;
        private String choiceDiagnostic;
        private int floatingManaUsed;
        private int candidateCardsVisited;
        private int manaAbilityCollectionsRead;
        private int manaAbilitiesVisited;

        private Planner(final ManaCostBeingPaid requestedCost,
                final SpellAbility paidFor, final Player payer,
                final boolean effect, final List<Card> candidateCards) {
            this.cost = new ManaCostBeingPaid(requestedCost);
            this.paidFor = paidFor;
            this.payer = payer;
            this.effect = effect;
            this.candidateCards = candidateCards;
            this.pool = new DetachedManaPool(payer);
        }

        private Result plan() {
            if (cost.getXcounter() > 0) {
                unresolvedX = true;
                requireChoice("unresolved X mana value");
            }
            if (paidFor.hasParam("ManaRestriction")) {
                paymentRestriction = true;
                requireChoice("spell mana restriction");
            }
            applyProvableManaConversion();
            if (pool.hasUnsafeFloatingMana()) {
                requireChoice("restricted floating mana");
            }
            if (!paymentRestriction) {
                payFloatingMana();
            }

            if (!cost.isPaid()) {
                collectSimpleManaSources();
                payFromSimpleSources();
            }
            if (!cost.isPaid() && containsPhyrexian(cost)) {
                requireChoice("Phyrexian or life payment requires real payment");
            }

            final Status status;
            final String diagnostic;
            if (unresolvedX) {
                status = Status.REQUIRES_CHOICE;
                diagnostic = choiceDiagnostic;
            } else if (cost.isPaid()) {
                status = Status.ADVISORY_PAYABLE;
                diagnostic = choiceRequired
                        ? "advisory payable without unresolved choices; real payment must revalidate"
                        : "advisory only; real payment must revalidate";
            } else if (choiceRequired) {
                status = Status.REQUIRES_CHOICE;
                diagnostic = choiceDiagnostic;
            } else {
                status = Status.ADVISORY_UNPAYABLE;
                diagnostic = "unpayable with the supplied simple sources";
            }
            return result(status, immutableSources(), floatingManaUsed,
                    candidateCardsVisited, manaAbilityCollectionsRead, 0,
                    cost.toString(), diagnostic);
        }

        private void applyProvableManaConversion() {
            pool.restoreColorReplacements();
            final CardPlayOption mayPlay = paidFor.getMayPlayOption();
            if (!effect && mayPlay != null
                    && (mayPlay.isIgnoreManaCostColor()
                    || mayPlay.isIgnoreManaCostType())) {
                requireChoice("may-play mana conversion");
            }
            if (paidFor.getGrantorStatic() != null
                    && paidFor.getGrantorStatic().hasParam("ManaConversion")) {
                requireChoice("grantor mana conversion");
            }
            if (paidFor.hasParam("ManaConversion")) {
                requireChoice("spell mana conversion");
            }

            final SpellAbility conversionSa =
                    effect && !paidFor.isCastFromPlayEffect() ? null : paidFor;
            final Card card = paidFor.getHostCard();
            final Player owner = card.getOwner();
            if (owner != null) {
                owner.getSpellRuleRegistry().applyManaConversion(pool, card,
                        conversionSa);
                if (!owner.getSpellRuleRegistry().isEmpty()) {
                    requireChoice("player spell rules require real payment revalidation");
                }
            }
            if (payer != owner && !payer.getSpellRuleRegistry().isEmpty()) {
                requireChoice("payer spell rules require real payment revalidation");
            }
        }

        private void payFloatingMana() {
            boolean progress;
            do {
                progress = false;
                final List<ManaCostShard> shards = cost.getUnpaidShards();
                Collections.sort(shards);
                for (final ManaCostShard shard : shards) {
                    if (shard == ManaCostShard.X) {
                        continue;
                    }
                    final Mana mana = chooseFloatingMana(shard);
                    if (mana != null && pool.tryPayCostWithMana(paidFor,
                            cost, mana, true)) {
                        floatingManaUsed++;
                        rememberSource(mana.getSourceCard(), true);
                        progress = true;
                        break;
                    }
                }
            } while (progress && !cost.isPaid());
        }

        private Mana chooseFloatingMana(final ManaCostShard shard) {
            for (final Mana mana : pool.snapshotMana()) {
                if (pool.canPayForShardWithColor(shard, mana.getColor())
                        && (!shard.isSnow() || mana.isSnow())) {
                    return mana;
                }
            }
            return null;
        }

        private void collectSimpleManaSources() {
            for (final Card card : candidateCards) {
                candidateCardsVisited++;
                if (card == null || card.getGame() != payer.getGame()
                        || card.getController() != payer
                        || !card.isInZone(ZoneType.Battlefield)) {
                    continue;
                }
                manaAbilityCollectionsRead++;
                final Iterable<SpellAbility> manaAbilities =
                        card.getManaAbilities();
                for (final SpellAbility ability : manaAbilities) {
                    if (++manaAbilitiesVisited > MAX_MANA_ABILITIES) {
                        requireChoice("mana ability limit exceeded");
                        return;
                    }
                    final SimpleManaSource source = parseSimpleSource(card,
                            ability);
                    if (source != null) {
                        manaSources.add(source);
                    }
                }
            }
        }

        private SimpleManaSource parseSimpleSource(final Card card,
                final SpellAbility ability) {
            for (final StaticAbility staticAbility
                    : card.getStaticAbilities()) {
                if ("AlternativeCost".equals(
                        staticAbility.getParam("Mode"))) {
                    requireChoice("alternative mana activation cost");
                    return null;
                }
            }
            if (ability.getApi() == ApiType.ManaReflected) {
                requireChoice("reflected mana ability");
                return null;
            }
            if (ability.getApi() != ApiType.Mana
                    || ability.getSubAbility() != null
                    || ability.usesTargeting()) {
                requireChoice("non-basic mana ability");
                return null;
            }
            for (final String key : ability.getMapParams().keySet()) {
                if (!SIMPLE_MANA_PARAMS.contains(key)) {
                    requireChoice("dynamic mana ability parameter");
                    return null;
                }
            }
            if (card.isFaceDown() || card.isPhasedOut() || card.isUsedToPay()
                    || card.isDetained() || ability.isSuppressed()) {
                return null;
            }

            final Cost activationCost = ability.getPayCosts();
            if (!isRawTapOrZeroCost(activationCost)) {
                requireChoice("unsupported mana activation cost");
                return null;
            }
            if (activationCost.hasTapCost()
                    && (card.isTapped() || card.isSick())) {
                return null;
            }

            final AbilityManaPart manaPart = ability.getManaPart();
            if (manaPart == null
                    || !manaPart.getManaRestrictions().isEmpty()
                    || !manaPart.getExtraManaRestriction().isEmpty()
                    || manaPart.isAnyMana() || manaPart.isComboMana()
                    || manaPart.isSpecialMana()) {
                requireChoice("non-fixed mana production");
                return null;
            }
            final int amount = parseFixedAmount(ability);
            final List<Byte> produced = parseFixedProduced(
                    manaPart.getOrigProduced(), amount);
            if (amount < 0 || produced == null || produced.isEmpty()) {
                requireChoice("dynamic mana production");
                return null;
            }
            return new SimpleManaSource(card, produced);
        }

        private static boolean isRawTapOrZeroCost(final Cost cost) {
            if (cost == null) {
                return false;
            }
            int taps = 0;
            for (final CostPart part : cost.getCostParts()) {
                if (part instanceof CostTap) {
                    if (++taps > 1) {
                        return false;
                    }
                } else if (!(part instanceof CostPartMana)
                        || !((CostPartMana) part).getMana().isZero()) {
                    return false;
                }
            }
            return true;
        }

        private static int parseFixedAmount(final SpellAbility ability) {
            if (!ability.hasParam("Amount")) {
                return 1;
            }
            final String value = ability.getParam("Amount");
            if (!StringUtils.isNumeric(value)) {
                return -1;
            }
            final int amount;
            try {
                amount = Integer.parseInt(value);
            } catch (final NumberFormatException ex) {
                return -1;
            }
            return amount >= 1 && amount <= MAX_FIXED_MANA_PER_SOURCE
                    ? amount : -1;
        }

        private static List<Byte> parseFixedProduced(final String value,
                final int amount) {
            if (amount < 1 || StringUtils.isBlank(value)) {
                return null;
            }
            final List<Byte> unit = new ArrayList<>();
            for (final String token : value.trim().split(" +")) {
                if (StringUtils.isNumeric(token)) {
                    final int count;
                    try {
                        count = Integer.parseInt(token);
                    } catch (final NumberFormatException ex) {
                        return null;
                    }
                    if (count < 1 || count > MAX_FIXED_MANA_PER_SOURCE) {
                        return null;
                    }
                    for (int i = 0; i < count; i++) {
                        unit.add((byte) ManaAtom.COLORLESS);
                    }
                } else if (token.length() == 1
                        && "WUBRGC".contains(token)) {
                    unit.add(MagicColor.fromName(token));
                } else {
                    return null;
                }
            }
            if ((long) unit.size() * amount > MAX_FIXED_MANA_PER_SOURCE) {
                return null;
            }
            final List<Byte> result = new ArrayList<>(unit.size() * amount);
            for (int i = 0; i < amount; i++) {
                result.addAll(unit);
            }
            return result;
        }

        private void payFromSimpleSources() {
            while (!cost.isPaid()) {
                SimpleManaSource selected = null;
                ManaCostBeingPaid selectedCost = null;
                for (final ManaCostShard shard : orderedShards()) {
                    for (final SimpleManaSource source : manaSources) {
                        if (usedCards.contains(source.card)) {
                            continue;
                        }
                        final ManaCostBeingPaid trial =
                                new ManaCostBeingPaid(cost);
                        if (source.tryPay(trial, shard, pool)
                                && isProgress(cost, trial)) {
                            selected = source;
                            selectedCost = trial;
                            break;
                        }
                    }
                    if (selected != null) {
                        break;
                    }
                }
                if (selected == null) {
                    return;
                }
                cost = selectedCost;
                usedCards.add(selected.card);
                rememberSource(selected.card, false);
            }
        }

        private List<ManaCostShard> orderedShards() {
            final List<ManaCostShard> shards = cost.getUnpaidShards();
            shards.removeIf(shard -> shard == ManaCostShard.X);
            Collections.sort(shards);
            return shards;
        }

        private void requireChoice(final String diagnostic) {
            choiceRequired = true;
            if (choiceDiagnostic == null) {
                choiceDiagnostic = diagnostic;
            }
        }

        private void rememberSource(final Card card,
                final boolean floating) {
            if (card == null) {
                return;
            }
            final SourceKey key = new SourceKey(card.getId(),
                    card.getGameTimestamp(), floating);
            usedSources.computeIfAbsent(key,
                    unused -> new MutableSource(card.getId(),
                            card.getGameTimestamp(), floating)).uses++;
        }

        private List<Source> immutableSources() {
            final List<Source> result = new ArrayList<>();
            for (final MutableSource source : usedSources.values()) {
                result.add(new Source(source.cardId, source.gameTimestamp,
                        source.uses, source.floating));
            }
            return result;
        }
    }

    private static final class SimpleManaSource {
        private final Card card;
        private final List<Byte> produced;

        private SimpleManaSource(final Card card, final List<Byte> produced) {
            this.card = card;
            this.produced = produced;
        }

        private boolean tryPay(final ManaCostBeingPaid trial,
                final ManaCostShard target, final ManaPool pool) {
            boolean used = false;
            for (final byte color : produced) {
                if (target.isSnow() && card.isSnow()
                        && trial.getUnpaidShards(ManaCostShard.S) > 0) {
                    trial.decreaseShard(ManaCostShard.S, 1);
                    used = true;
                    continue;
                }
                if (trial.ai_payMana(MagicColor.toShortString(color), pool)) {
                    used = true;
                }
            }
            return used;
        }
    }

    /** Separate container and matrix; immutable live Mana records are read only. */
    private static final class DetachedManaPool extends ManaPool {
        private boolean unsafeFloatingMana;

        private DetachedManaPool(final Player owner) {
            super(owner);
            for (final Mana mana : owner.getManaPool()) {
                final AbilityManaPart part = mana.getManaAbility();
                if (part != null && (!part.getManaRestrictions().isEmpty()
                        || !part.getExtraManaRestriction().isEmpty())) {
                    unsafeFloatingMana = true;
                    continue;
                }
                addManaNoEvent(mana);
            }
        }

        private boolean hasUnsafeFloatingMana() {
            return unsafeFloatingMana;
        }

        @Override
        public boolean removeMana(final Mana... manaList) {
            boolean removed = false;
            for (final Mana mana : manaList) {
                removed |= removeManaNoEvent(mana);
            }
            return removed;
        }

        @Override
        public boolean removeMana(final Iterable<Mana> manaList) {
            boolean removed = false;
            for (final Mana mana : manaList) {
                removed |= removeManaNoEvent(mana);
            }
            return removed;
        }

        private List<Mana> snapshotMana() {
            final List<Mana> result = new ArrayList<>();
            for (final Mana mana : this) {
                result.add(mana);
            }
            return result;
        }
    }

    private static boolean containsPhyrexian(final ManaCostBeingPaid cost) {
        for (final ManaCostShard shard : cost.getDistinctShards()) {
            if (shard.isPhyrexian()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isProgress(final ManaCostBeingPaid before,
            final ManaCostBeingPaid after) {
        if (after.isPaid()) {
            return true;
        }
        final int beforeCmc = before.getConvertedManaCost();
        final int afterCmc = after.getConvertedManaCost();
        return afterCmc < beforeCmc
                || (afterCmc == beforeCmc
                && after.getUnpaidShards().size()
                < before.getUnpaidShards().size());
    }

    private static final class MutableSource {
        private final int cardId;
        private final long gameTimestamp;
        private final boolean floating;
        private int uses;

        private MutableSource(final int cardId, final long gameTimestamp,
                final boolean floating) {
            this.cardId = cardId;
            this.gameTimestamp = gameTimestamp;
            this.floating = floating;
        }
    }

    private static final class SourceKey {
        private final int cardId;
        private final long gameTimestamp;
        private final boolean floating;

        private SourceKey(final int cardId, final long gameTimestamp,
                final boolean floating) {
            this.cardId = cardId;
            this.gameTimestamp = gameTimestamp;
            this.floating = floating;
        }

        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof SourceKey)) {
                return false;
            }
            final SourceKey key = (SourceKey) other;
            return cardId == key.cardId
                    && gameTimestamp == key.gameTimestamp
                    && floating == key.floating;
        }

        @Override
        public int hashCode() {
            int result = 31 * cardId + Long.hashCode(gameTimestamp);
            result = 31 * result + (floating ? 1 : 0);
            return result;
        }
    }
}
