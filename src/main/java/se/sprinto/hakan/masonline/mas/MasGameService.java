package se.sprinto.hakan.masonline.mas;

import org.springframework.stereotype.Service;
import se.sprinto.hakan.masonline.card.Card;

import java.security.SecureRandom;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class MasGameService {

    private static final int STARTING_HAND_SIZE = 3;
    private static final int MIN_PLAYERS_TO_START = 3;
    private final Map<String, MasGame> games = new LinkedHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public synchronized List<MasLobbyView> lobby() {
        return games.values().stream()
                .sorted(Comparator.comparing(MasGame::createdAt).reversed())
                .map(game -> new MasLobbyView(
                        game.id(),
                        game.players().size(),
                        statusLabel(game.status()),
                        game.status() == MasGameStatus.WAITING
                ))
                .toList();
    }

    public synchronized String createGame() {
        String id = randomId();
        games.put(id, new MasGame(id));
        return id;
    }

    public synchronized MasGame game(String gameId) {
        MasGame game = games.get(gameId);
        if (game == null) {
            throw new IllegalArgumentException("Matchen finns inte.");
        }
        return game;
    }

    public synchronized MasPlayer join(String gameId, String playerName) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.WAITING) {
            throw new IllegalStateException("Matchen har redan börjat.");
        }
        String cleanName = playerName == null || playerName.isBlank() ? "Spelare " + (game.players().size() + 1) : playerName.trim();
        MasPlayer player = new MasPlayer(UUID.randomUUID().toString(), cleanName);
        game.players().add(player);
        if (game.hostPlayerId() == null) {
            game.hostPlayerId(player.id());
        }
        game.events().add(TableEvent.message(cleanName + " gick med i matchen."));
        return player;
    }

    public synchronized void suggestLoserTitle(String gameId, String playerId, String title) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.WAITING) {
            throw new IllegalStateException("Förslag kan bara skickas innan matchen startar.");
        }
        MasPlayer player = requirePlayer(game, playerId);
        String cleanTitle = cleanLoserTitle(title);
        boolean alreadyExists = game.loserTitleSuggestions().stream()
                .anyMatch(suggestion -> suggestion.title().equalsIgnoreCase(cleanTitle));
        if (!alreadyExists) {
            game.loserTitleSuggestions().add(new LoserTitleSuggestion(UUID.randomUUID().toString(), player.id(), player.name(), cleanTitle));
            game.events().add(TableEvent.message(player.name() + " föreslog dagens " + cleanTitle + "."));
        }
    }

    public synchronized void selectLoserTitle(String gameId, String playerId, String suggestionId) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.WAITING) {
            throw new IllegalStateException("Förlorartitel måste väljas innan matchen startar.");
        }
        if (!playerId.equals(game.hostPlayerId())) {
            throw new IllegalStateException("Bara den som skapade matchen kan välja förlorartitel.");
        }
        LoserTitleSuggestion suggestion = game.loserTitleSuggestions().stream()
                .filter(candidate -> candidate.id().equals(suggestionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Förslaget finns inte."));
        game.selectedLoserTitle(suggestion.title());
        game.events().add(TableEvent.message("Förloraren utses till dagens " + suggestion.title() + "."));
    }

    public synchronized void start(String gameId, String playerId) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.WAITING) {
            throw new IllegalStateException("Matchen har redan börjat.");
        }
        if (!playerId.equals(game.hostPlayerId())) {
            throw new IllegalStateException("Bara den som skapade matchen kan starta spelet.");
        }
        if (game.players().size() < MIN_PLAYERS_TO_START) {
            throw new IllegalStateException("Minst tre spelare behövs för att starta.");
        }
        if (game.selectedLoserTitle() == null) {
            game.selectedLoserTitle("förlorare");
        }
        game.prepareDeck();
        for (MasPlayer player : game.players()) {
            drawUntilThree(game, player);
        }
        game.status(MasGameStatus.ROUND_ONE);
        game.activePlayerId(game.players().get(random.nextInt(game.players().size())).id());
        game.events().add(TableEvent.message("Omgång 1 har börjat. " + playerName(game, game.activePlayerId()) + " är aktiv spelare."));
    }

    public synchronized void sendFromHand(String gameId, String playerId, String cardCode) {
        MasGame game = requireRoundOneWithoutPending(gameId, playerId);
        MasPlayer sender = requirePlayer(game, playerId);
        Card sentCard = sender.removeCard(cardCode);
        createOffer(game, sender, sentCard);
    }

    public synchronized void sendFromDeck(String gameId, String playerId) {
        MasGame game = requireRoundOneWithoutPending(gameId, playerId);
        MasPlayer sender = requirePlayer(game, playerId);
        if (game.deck().isEmpty()) {
            throw new IllegalStateException("Högen är slut.");
        }
        Card sentCard = game.deck().removeFirst();
        game.lastDrawnCard(sentCard);
        if (game.deck().isEmpty()) {
            sender.hand().add(sentCard);
            game.roundTwoStarterPlayerId(sender.id());
            finishRoundOneBecauseDeckIsEmpty(game);
            return;
        }
        createOffer(game, sender, sentCard);
    }

    public synchronized void receiverPickup(String gameId, String playerId) {
        MasGame game = game(gameId);
        PendingOffer offer = requirePendingForReceiver(game, playerId);
        MasPlayer receiver = requirePlayer(game, playerId);
        receiver.hand().add(offer.sentCard());
        String text = receiver.hasSuit(offer.sentCard())
                ? receiver.name() + " valde att ta upp " + offer.sentCard().displayName() + "."
                : receiver.name() + " hade inte färgen och tog upp " + offer.sentCard().displayName() + ".";
        game.events().add(TableEvent.resolved(
                text,
                offer.sentCard(),
                null,
                false
        ));
        game.pendingOffer(null);
        game.activePlayerId(nextPlayerIdAfter(game, receiver.id()));
        refillHands(game);
        finishRoundOneIfDeckIsEmpty(game);
    }

    public synchronized String receiverRespond(String gameId, String playerId, String cardCode) {
        MasGame game = game(gameId);
        PendingOffer offer = requirePendingForReceiver(game, playerId);
        MasPlayer receiver = requirePlayer(game, playerId);
        MasPlayer sender = requirePlayer(game, offer.senderId());
        Card responseCard = receiver.removeCard(cardCode);
        if (responseCard.suit() != offer.sentCard().suit()) {
            receiver.hand().add(responseCard);
            throw new IllegalArgumentException("Mottagaren måste spela samma färg.");
        }
        TableEvent resolvedEvent;
        if (responseCard.beats(offer.sentCard())) {
            receiver.wonCards().add(offer.sentCard());
            receiver.wonCards().add(responseCard);
            game.activePlayerId(receiver.id());
            resolvedEvent = TableEvent.resolved(
                    receiver.name() + " lade högre i samma färg och tog sticket.",
                    offer.sentCard(),
                    responseCard,
                    true
            );
        } else {
            sender.wonCards().add(offer.sentCard());
            sender.wonCards().add(responseCard);
            game.activePlayerId(sender.id());
            resolvedEvent = TableEvent.resolved(
                    receiver.name() + " lade lägre i samma färg. " + sender.name() + " tog sticket och fortsätter.",
                    offer.sentCard(),
                    responseCard,
                    true
            );
        }
        game.events().add(resolvedEvent);
        String visibleTrickId = UUID.randomUUID().toString();
        game.visibleRoundOneTrick(new VisibleRoundOneTrick(visibleTrickId, resolvedEvent));
        game.lastPlayedCard(responseCard);
        game.pendingOffer(null);
        refillHands(game);
        finishRoundOneIfDeckIsEmpty(game);
        return visibleTrickId;
    }

    public synchronized boolean clearVisibleRoundOneTrick(String gameId, String visibleTrickId) {
        MasGame game = game(gameId);
        VisibleRoundOneTrick visibleTrick = game.visibleRoundOneTrick();
        if (visibleTrick == null || !visibleTrick.id().equals(visibleTrickId)) {
            return false;
        }
        game.visibleRoundOneTrick(null);
        return true;
    }

    public synchronized void startRoundTwo(String gameId) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.ROUND_ONE_FINISHED) {
            throw new IllegalStateException("Omgång 1 måste vara klar innan omgång 2 startar.");
        }
        for (MasPlayer player : game.players()) {
            player.hand().clear();
            player.hand().addAll(player.wonCards());
            player.wonCards().clear();
        }
        game.roundTwoTable().clear();
        game.visibleRoundOneTrick(null);
        game.status(MasGameStatus.ROUND_TWO);
        String starterId = roundTwoStarter(game)
                .orElseThrow(() -> new IllegalStateException("Ingen spelare har kort till omgång 2."));
        startRoundTwoTrick(game, starterId);
        game.events().add(TableEvent.message("Omgång 2 har börjat. " + playerName(game, starterId) + " startar första sticket."));
        finishIfRoundTwoDone(game);
    }

    public synchronized void playRoundTwoCard(String gameId, String playerId, String cardCode) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.ROUND_TWO) {
            throw new IllegalStateException("Omgång 2 är inte igång.");
        }
        if (!playerId.equals(game.activePlayerId())) {
            throw new IllegalStateException("Det är inte din tur.");
        }
        MasPlayer player = requirePlayer(game, playerId);
        Card selectedCard = player.hand().stream()
                .filter(card -> card.code().equals(cardCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Kortet finns inte på handen."));
        validateRoundTwoCard(game, player, selectedCard);
        Card playedCard = player.removeCard(cardCode);
        game.roundTwoTable().add(new RoundTwoPlay(player.id(), player.name(), playedCard));
        game.lastPlayedCard(playedCard);
        game.events().add(TableEvent.message(player.name() + " lade " + playedCard.displayName() + "."));

        if (roundTwoTrickIsComplete(game)) {
            String nextStarterId = nextRoundTwoStarter(game, player.id());
            completeRoundTwoTrick(game, nextStarterId);
        } else {
            game.activePlayerId(nextPlayerWithCardsAfter(game, player.id()));
        }
    }

    public synchronized void pickupRoundTwoCard(String gameId, String playerId) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.ROUND_TWO) {
            throw new IllegalStateException("Omgång 2 är inte igång.");
        }
        if (!playerId.equals(game.activePlayerId())) {
            throw new IllegalStateException("Det är inte din tur.");
        }
        if (game.roundTwoTable().isEmpty()) {
            throw new IllegalStateException("Det finns inget kort att ta upp.");
        }
        MasPlayer player = requirePlayer(game, playerId);
        if (!canPickupRoundTwo(game, player)) {
            throw new IllegalStateException("Du kan lägga ett kort och ska inte ta upp.");
        }
        boolean mustPickup = mustPickupRoundTwo(game, player);
        RoundTwoPlay pickedUpPlay = game.roundTwoTable().removeLast();
        player.hand().add(pickedUpPlay.card());
        int remainingPileSize = game.roundTwoTable().size();
        String pickupText = mustPickup
                ? player.name() + " kunde inte lägga högre och tog upp " + pickedUpPlay.card().displayName() + "."
                : player.name() + " valde att ta upp " + pickedUpPlay.card().displayName() + ".";
        if (remainingPileSize == 0) {
            pickupText += " Högen är tom.";
        } else {
            pickupText += " " + remainingPileSize + " kort ligger kvar i högen.";
        }
        game.events().add(TableEvent.message(pickupText));

        String nextPlayerId = nextPlayerWithCardsAfter(game, player.id());
        if (game.roundTwoTable().isEmpty()) {
            startRoundTwoTrick(game, nextPlayerId);
        } else {
            game.activePlayerId(nextPlayerId);
        }
        finishIfRoundTwoDone(game);
    }

    public synchronized MasGameView view(String gameId, String viewerId) {
        MasGame game = game(gameId);
        MasPlayer viewer = findPlayer(game, viewerId).orElse(null);
        List<CardView> hand = viewer == null ? List.of() : sortedHand(viewer.hand()).stream().map(this::cardView).toList();
        PendingOfferView pendingOffer = game.pendingOffer().map(offer -> new PendingOfferView(
                offer.senderId(),
                playerName(game, offer.senderId()),
                offer.receiverId(),
                playerName(game, offer.receiverId()),
                cardView(offer.sentCard())
        )).orElse(null);
        String activeName = game.activePlayerId() == null ? null : playerName(game, game.activePlayerId());
        boolean youAreReceiver = pendingOffer != null && pendingOffer.receiverId().equals(viewerId);
        boolean youAreHost = viewerId != null && viewerId.equals(game.hostPlayerId());
        boolean playerCanAct = (game.status() == MasGameStatus.ROUND_ONE || game.status() == MasGameStatus.ROUND_TWO)
                && viewerId != null
                && viewerId.equals(game.activePlayerId())
                && game.pendingOffer().isEmpty();
        boolean mustPickupRoundTwo = game.status() == MasGameStatus.ROUND_TWO
                && viewer != null
                && playerCanAct
                && mustPickupRoundTwo(game, viewer);
        boolean canPickupRoundTwo = game.status() == MasGameStatus.ROUND_TWO
                && viewer != null
                && playerCanAct
                && canPickupRoundTwo(game, viewer);
        List<String> playableCardCodes = viewer == null || !playerCanAct
                ? List.of()
                : playableCardCodes(game, viewer);
        return new MasGameView(
                game.id(),
                statusLabel(game.status()),
                game.players().stream()
                        .map(player -> new PlayerView(
                                player.id(),
                                player.name(),
                                player.hand().size(),
                                player.wonCards().size(),
                                player.id().equals(game.activePlayerId()),
                                player.id().equals(viewerId)
                        ))
                        .toList(),
                hand,
                latestEvent(game).stream().map(this::textOnlyEventView).toList(),
                pendingOffer,
                game.visibleRoundOneTrick() == null ? null : eventView(game.visibleRoundOneTrick().event()),
                game.activePlayerId(),
                activeName,
                game.deck().size(),
                game.trumpSuit() == null ? null : game.trumpSuit().displayName(),
                playerCanAct,
                youAreReceiver,
                game.status() == MasGameStatus.WAITING && game.players().size() >= MIN_PLAYERS_TO_START && youAreHost,
                game.status() == MasGameStatus.ROUND_ONE_FINISHED,
                game.status() == MasGameStatus.ROUND_ONE && game.pendingOffer().isEmpty() && !game.deck().isEmpty(),
                canPickupRoundTwo,
                mustPickupRoundTwo,
                game.status() == MasGameStatus.ROUND_ONE_FINISHED,
                game.status() == MasGameStatus.ROUND_TWO,
                game.status() == MasGameStatus.FINISHED,
                game.loserName(),
                game.selectedLoserTitle(),
                game.status() == MasGameStatus.WAITING,
                youAreHost,
                List.copyOf(game.loserTitleSuggestions()),
                game.roundTwoTable().stream().map(this::roundTwoPlayView).toList(),
                game.roundTwoTrickParticipantIds().size(),
                playableCardCodes
        );
    }

    private MasGame requireRoundOneWithoutPending(String gameId, String playerId) {
        MasGame game = game(gameId);
        if (game.status() != MasGameStatus.ROUND_ONE) {
            throw new IllegalStateException("Omgång 1 är inte igång.");
        }
        if (!playerId.equals(game.activePlayerId())) {
            throw new IllegalStateException("Det är inte din tur.");
        }
        if (game.pendingOffer().isPresent()) {
            throw new IllegalStateException("Mottagaren måste svara först.");
        }
        return game;
    }

    private void createOffer(MasGame game, MasPlayer sender, Card sentCard) {
        String receiverId = nextPlayerIdAfter(game, sender.id());
        MasPlayer receiver = requirePlayer(game, receiverId);
        game.lastPlayedCard(sentCard);
        game.pendingOffer(new PendingOffer(sender.id(), receiver.id(), sentCard));
        game.events().add(TableEvent.sent(sender.name() + " skickade " + sentCard.displayName() + " till " + receiver.name() + ".", sentCard));
        if (!receiver.hasSuit(sentCard)) {
            game.events().add(TableEvent.message(receiver.name() + " har inte färgen och måste ta upp kortet."));
        }
    }

    private PendingOffer requirePendingForReceiver(MasGame game, String playerId) {
        PendingOffer offer = game.pendingOffer()
                .orElseThrow(() -> new IllegalStateException("Det finns inget kort att svara på."));
        if (!offer.receiverId().equals(playerId)) {
            throw new IllegalStateException("Det är mottagaren som måste svara.");
        }
        return offer;
    }

    private void refillHands(MasGame game) {
        for (MasPlayer player : game.players()) {
            drawUntilThree(game, player);
        }
    }

    private void drawUntilThree(MasGame game, MasPlayer player) {
        while (player.hand().size() < STARTING_HAND_SIZE && !game.deck().isEmpty()) {
            Card drawnCard = game.deck().removeFirst();
            game.lastDrawnCard(drawnCard);
            player.hand().add(drawnCard);
            if (game.deck().isEmpty()) {
                game.roundTwoStarterPlayerId(player.id());
            }
        }
    }

    private void finishRoundOneIfDeckIsEmpty(MasGame game) {
        if (game.deck().isEmpty() && game.status() == MasGameStatus.ROUND_ONE) {
            finishRoundOneBecauseDeckIsEmpty(game);
        }
    }

    private void finishRoundOneBecauseDeckIsEmpty(MasGame game) {
        if (game.status() != MasGameStatus.ROUND_ONE) {
            return;
        }
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.activePlayerId(null);
        game.pendingOffer(null);
        game.players().forEach(player -> {
            player.wonCards().addAll(player.hand());
            player.hand().clear();
        });
        if (game.lastDrawnCard() != null) {
            game.trumpSuit(game.lastDrawnCard().suit());
            game.events().add(TableEvent.message("Omgång 1 är slut. Trumf i omgång 2 blir " + game.trumpSuit().displayName() + "."));
        } else {
            game.events().add(TableEvent.message("Omgång 1 är slut."));
        }
    }

    private void validateRoundTwoCard(MasGame game, MasPlayer player, Card selectedCard) {
        if (game.roundTwoTable().isEmpty()) {
            return;
        }
        Card previousCard = game.roundTwoTable().getLast().card();
        List<Card> sameSuitCards = player.hand().stream()
                .filter(card -> card.suit() == previousCard.suit())
                .toList();
        if (sameSuitCards.isEmpty()) {
            return;
        }
        boolean canBeatPrevious = sameSuitCards.stream().anyMatch(card -> card.rank().value() > previousCard.rank().value());
        if (!canBeatPrevious && selectedCard.suit() == game.trumpSuit() && previousCard.suit() != game.trumpSuit()) {
            return;
        }
        if (canBeatPrevious && (selectedCard.suit() != previousCard.suit() || selectedCard.rank().value() <= previousCard.rank().value())) {
            throw new IllegalArgumentException("Du måste följa färg och lägga högre än föregående kort.");
        }
        if (!canBeatPrevious) {
            throw new IllegalArgumentException("Du kan inte lägga högre och måste ta upp kortet.");
        }
    }

    private List<String> playableCardCodes(MasGame game, MasPlayer player) {
        return player.hand().stream()
                .filter(card -> canPlayCard(game, player, card))
                .map(Card::code)
                .toList();
    }

    private boolean canPlayCard(MasGame game, MasPlayer player, Card selectedCard) {
        try {
            validateRoundTwoCard(game, player, selectedCard);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void startRoundTwoTrick(MasGame game, String starterId) {
        game.roundTwoTable().clear();
        game.roundTwoTrickParticipantIds().clear();
        game.roundTwoActedPlayerIds().clear();
        List<MasPlayer> playersWithCards = playersWithCardsFrom(game, starterId);
        game.roundTwoTrickParticipantIds().addAll(playersWithCards.stream().map(MasPlayer::id).toList());
        game.activePlayerId(starterId);
    }

    private boolean mustPickupRoundTwo(MasGame game, MasPlayer player) {
        if (game.roundTwoTable().isEmpty()) {
            return false;
        }
        Card previousCard = game.roundTwoTable().getLast().card();
        List<Card> sameSuitCards = player.hand().stream()
                .filter(card -> card.suit() == previousCard.suit())
                .toList();
        boolean hasHigherSameSuit = sameSuitCards.stream().anyMatch(card -> card.rank().value() > previousCard.rank().value());
        boolean canTrump = previousCard.suit() != game.trumpSuit()
                && player.hand().stream().anyMatch(card -> card.suit() == game.trumpSuit());
        return !sameSuitCards.isEmpty() && !hasHigherSameSuit && !canTrump;
    }

    private boolean canPickupRoundTwo(MasGame game, MasPlayer player) {
        if (mustPickupRoundTwo(game, player)) {
            return true;
        }
        if (game.trumpSuit() == null || game.roundTwoTable().isEmpty()) {
            return false;
        }
        Card previousCard = game.roundTwoTable().getLast().card();
        List<Card> sameSuitCards = player.hand().stream()
                .filter(card -> card.suit() == previousCard.suit())
                .toList();
        if (sameSuitCards.isEmpty()) {
            return true;
        }
        boolean hasHigherSameSuit = sameSuitCards.stream().anyMatch(card -> card.rank().value() > previousCard.rank().value());
        if (!hasHigherSameSuit && previousCard.suit() != game.trumpSuit()
                && player.hand().stream().anyMatch(card -> card.suit() == game.trumpSuit())) {
            return true;
        }
        return previousCard.suit() == game.trumpSuit()
                && sameSuitCards.stream().anyMatch(card -> card.rank().value() > previousCard.rank().value());
    }

    private boolean roundTwoTrickIsComplete(MasGame game) {
        return game.roundTwoTable().size() >= game.roundTwoTrickParticipantIds().size();
    }

    private void completeRoundTwoTrick(MasGame game, String nextStarterId) {
        game.roundTwoTable().clear();
        game.events().add(TableEvent.message("Sticket är komplett och korten kastas bort."));
        finishIfRoundTwoDone(game);
        if (game.status() == MasGameStatus.ROUND_TWO) {
            startRoundTwoTrick(game, nextStarterId);
        }
    }

    private void finishIfRoundTwoDone(MasGame game) {
        List<MasPlayer> playersWithCards = game.players().stream()
                .filter(player -> !player.hand().isEmpty())
                .toList();
        if (playersWithCards.size() <= 1 && game.status() == MasGameStatus.ROUND_TWO) {
            game.status(MasGameStatus.FINISHED);
            game.activePlayerId(null);
            game.roundTwoTrickParticipantIds().clear();
            game.roundTwoActedPlayerIds().clear();
            game.roundTwoTable().clear();
            if (playersWithCards.size() == 1) {
                game.loserName(playersWithCards.getFirst().name());
                game.events().add(TableEvent.message(playersWithCards.getFirst().name() + " är dagens " + game.selectedLoserTitle() + "."));
            } else {
                game.events().add(TableEvent.message("Spelet är slut."));
            }
        }
    }

    private String cleanLoserTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Förslag får inte vara tomt.");
        }
        String cleanTitle = title.trim().replaceAll("\\s+", " ");
        if (cleanTitle.toLowerCase().startsWith("dagens ")) {
            cleanTitle = cleanTitle.substring("dagens ".length()).trim();
        }
        if (cleanTitle.isBlank()) {
            throw new IllegalArgumentException("Förslag får inte vara tomt.");
        }
        if (cleanTitle.length() > 40) {
            throw new IllegalArgumentException("Förslag får vara max 40 tecken.");
        }
        return cleanTitle;
    }

    private String nextRoundTwoStarter(MasGame game, String lastPlayerId) {
        return findPlayer(game, lastPlayerId)
                .filter(player -> !player.hand().isEmpty())
                .map(MasPlayer::id)
                .orElseGet(() -> nextPlayerWithCardsAfter(game, lastPlayerId));
    }

    private String nextPlayerWithCardsAfter(MasGame game, String playerId) {
        List<MasPlayer> players = game.players();
        int startIndex = 0;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                startIndex = i;
                break;
            }
        }
        for (int offset = 1; offset <= players.size(); offset++) {
            MasPlayer candidate = players.get((startIndex + offset) % players.size());
            if (!candidate.hand().isEmpty()) {
                return candidate.id();
            }
        }
        return playerId;
    }

    private Optional<MasPlayer> firstPlayerWithCards(MasGame game) {
        return game.players().stream().filter(player -> !player.hand().isEmpty()).findFirst();
    }

    private Optional<String> roundTwoStarter(MasGame game) {
        if (game.roundTwoStarterPlayerId() != null
                && findPlayer(game, game.roundTwoStarterPlayerId()).filter(player -> !player.hand().isEmpty()).isPresent()) {
            return Optional.of(game.roundTwoStarterPlayerId());
        }
        return firstPlayerWithCards(game).map(MasPlayer::id);
    }

    private List<MasPlayer> playersWithCardsFrom(MasGame game, String starterId) {
        List<MasPlayer> players = game.players();
        int startIndex = 0;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(starterId)) {
                startIndex = i;
                break;
            }
        }
        int firstIndex = startIndex;
        return java.util.stream.IntStream.range(0, players.size())
                .mapToObj(offset -> players.get((firstIndex + offset) % players.size()))
                .filter(player -> !player.hand().isEmpty())
                .toList();
    }

    private String nextPlayerIdAfter(MasGame game, String playerId) {
        List<MasPlayer> players = game.players();
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id().equals(playerId)) {
                return players.get((i + 1) % players.size()).id();
            }
        }
        throw new IllegalArgumentException("Spelaren finns inte.");
    }

    private MasPlayer requirePlayer(MasGame game, String playerId) {
        return findPlayer(game, playerId).orElseThrow(() -> new IllegalArgumentException("Spelaren finns inte."));
    }

    private Optional<MasPlayer> findPlayer(MasGame game, String playerId) {
        return game.players().stream().filter(player -> player.id().equals(playerId)).findFirst();
    }

    private String playerName(MasGame game, String playerId) {
        return findPlayer(game, playerId).map(MasPlayer::name).orElse("Okänd spelare");
    }

    private String randomId() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            id.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        if (games.containsKey(id.toString())) {
            return randomId();
        }
        return id.toString();
    }

    private String statusLabel(MasGameStatus status) {
        return switch (status) {
            case WAITING -> "Väntar på spelare";
            case ROUND_ONE -> "Omgång 1";
            case ROUND_ONE_FINISHED -> "Omgång 1 klar";
            case ROUND_TWO -> "Omgång 2";
            case FINISHED -> "Spelet är slut";
        };
    }

    private CardView cardView(Card card) {
        return new CardView(card.code(), card.displayName(), card.imagePath(), card.suit().name());
    }

    private TableEventView textOnlyEventView(TableEvent event) {
        return new TableEventView(event.text(), null, null, false);
    }

    private TableEventView eventView(TableEvent event) {
        return new TableEventView(
                event.text(),
                event.sentCard() == null ? null : cardView(event.sentCard()),
                event.responseCard() == null ? null : cardView(event.responseCard()),
                event.trickHidden()
        );
    }

    private TableEventView roundTwoPlayView(RoundTwoPlay play) {
        return new TableEventView(play.playerName(), cardView(play.card()), null, false);
    }

    private List<TableEvent> latestEvent(MasGame game) {
        if (game.events().isEmpty()) {
            return List.of();
        }
        return List.of(game.events().getLast());
    }

    private List<Card> sortedHand(List<Card> hand) {
        return hand.stream()
                .sorted(Comparator.comparing((Card card) -> card.suit().name()).thenComparing(card -> card.rank().value()))
                .toList();
    }
}
