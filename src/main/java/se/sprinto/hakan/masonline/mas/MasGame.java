package se.sprinto.hakan.masonline.mas;

import se.sprinto.hakan.masonline.card.Card;
import se.sprinto.hakan.masonline.card.DeckFactory;
import se.sprinto.hakan.masonline.card.Suit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

public class MasGame {

    private final String id;
    private final Instant createdAt = Instant.now();
    private final List<MasPlayer> players = new ArrayList<>();
    private final Deque<Card> deck = new ArrayDeque<>();
    private final List<TableEvent> events = new ArrayList<>();
    private final List<RoundTwoPlay> roundTwoTable = new ArrayList<>();
    private final List<String> roundTwoTrickParticipantIds = new ArrayList<>();
    private final List<String> roundTwoActedPlayerIds = new ArrayList<>();
    private final List<LoserTitleSuggestion> loserTitleSuggestions = new ArrayList<>();
    private MasGameStatus status = MasGameStatus.WAITING;
    private String hostPlayerId;
    private String activePlayerId;
    private PendingOffer pendingOffer;
    private VisibleRoundOneTrick visibleRoundOneTrick;
    private Card lastPlayedCard;
    private Card lastDrawnCard;
    private Suit trumpSuit;
    private String roundTwoStarterPlayerId;
    private String loserName;
    private String selectedLoserTitle;

    public MasGame(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<MasPlayer> players() {
        return players;
    }

    public Deque<Card> deck() {
        return deck;
    }

    public List<TableEvent> events() {
        return events;
    }

    public List<RoundTwoPlay> roundTwoTable() {
        return roundTwoTable;
    }

    public List<String> roundTwoTrickParticipantIds() {
        return roundTwoTrickParticipantIds;
    }

    public List<String> roundTwoActedPlayerIds() {
        return roundTwoActedPlayerIds;
    }

    public List<LoserTitleSuggestion> loserTitleSuggestions() {
        return loserTitleSuggestions;
    }

    public MasGameStatus status() {
        return status;
    }

    public void status(MasGameStatus status) {
        this.status = status;
    }

    public String hostPlayerId() {
        return hostPlayerId;
    }

    public void hostPlayerId(String hostPlayerId) {
        this.hostPlayerId = hostPlayerId;
    }

    public String activePlayerId() {
        return activePlayerId;
    }

    public void activePlayerId(String activePlayerId) {
        this.activePlayerId = activePlayerId;
    }

    public Optional<PendingOffer> pendingOffer() {
        return Optional.ofNullable(pendingOffer);
    }

    public void pendingOffer(PendingOffer pendingOffer) {
        this.pendingOffer = pendingOffer;
    }

    public VisibleRoundOneTrick visibleRoundOneTrick() {
        return visibleRoundOneTrick;
    }

    public void visibleRoundOneTrick(VisibleRoundOneTrick visibleRoundOneTrick) {
        this.visibleRoundOneTrick = visibleRoundOneTrick;
    }

    public Card lastPlayedCard() {
        return lastPlayedCard;
    }

    public void lastPlayedCard(Card lastPlayedCard) {
        this.lastPlayedCard = lastPlayedCard;
    }

    public Card lastDrawnCard() {
        return lastDrawnCard;
    }

    public void lastDrawnCard(Card lastDrawnCard) {
        this.lastDrawnCard = lastDrawnCard;
    }

    public Suit trumpSuit() {
        return trumpSuit;
    }

    public void trumpSuit(Suit trumpSuit) {
        this.trumpSuit = trumpSuit;
    }

    public String roundTwoStarterPlayerId() {
        return roundTwoStarterPlayerId;
    }

    public void roundTwoStarterPlayerId(String roundTwoStarterPlayerId) {
        this.roundTwoStarterPlayerId = roundTwoStarterPlayerId;
    }

    public String loserName() {
        return loserName;
    }

    public void loserName(String loserName) {
        this.loserName = loserName;
    }

    public String selectedLoserTitle() {
        return selectedLoserTitle;
    }

    public void selectedLoserTitle(String selectedLoserTitle) {
        this.selectedLoserTitle = selectedLoserTitle;
    }

    public void prepareDeck() {
        deck.clear();
        deck.addAll(DeckFactory.shuffledDeck());
    }
}
