package se.sprinto.hakan.masonline.mas;

import java.util.List;

public record MasGameView(
        String gameId,
        String status,
        List<PlayerView> players,
        List<CardView> hand,
        List<TableEventView> events,
        PendingOfferView pendingOffer,
        TableEventView visibleRoundOneTrick,
        String activePlayerId,
        String activePlayerName,
        int deckCount,
        String trumpSuit,
        boolean youAreActive,
        boolean youAreReceiver,
        boolean canStart,
        boolean canStartRoundTwo,
        boolean canSendFromDeck,
        boolean canPickupRoundTwo,
        boolean mustPickupRoundTwo,
        boolean roundOneFinished,
        boolean roundTwo,
        boolean gameFinished,
        String loserName,
        String loserTitle,
        boolean waiting,
        boolean youAreHost,
        List<LoserTitleSuggestion> loserTitleSuggestions,
        List<TableEventView> roundTwoTable,
        int roundTwoTrickSize,
        List<String> playableCardCodes
) {
}
