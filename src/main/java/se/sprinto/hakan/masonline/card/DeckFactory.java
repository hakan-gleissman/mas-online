package se.sprinto.hakan.masonline.card;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

public final class DeckFactory {

    private DeckFactory() {
    }

    public static Deque<Card> shuffledDeck() {
        List<Card> cards = new ArrayList<>();
        for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
                cards.add(new Card(rank, suit));
            }
        }
        Collections.shuffle(cards);
        return new ArrayDeque<>(cards);
    }
}
