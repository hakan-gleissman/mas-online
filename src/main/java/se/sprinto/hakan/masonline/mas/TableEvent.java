package se.sprinto.hakan.masonline.mas;

import se.sprinto.hakan.masonline.card.Card;

import java.time.Instant;

public record TableEvent(
        Instant createdAt,
        String text,
        Card sentCard,
        Card responseCard,
        boolean trickHidden
) {
    static TableEvent message(String text) {
        return new TableEvent(Instant.now(), text, null, null, false);
    }

    static TableEvent sent(String text, Card sentCard) {
        return new TableEvent(Instant.now(), text, sentCard, null, false);
    }

    static TableEvent resolved(String text, Card sentCard, Card responseCard, boolean trickHidden) {
        return new TableEvent(Instant.now(), text, sentCard, responseCard, trickHidden);
    }
}
