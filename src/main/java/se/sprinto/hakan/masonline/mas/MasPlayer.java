package se.sprinto.hakan.masonline.mas;

import se.sprinto.hakan.masonline.card.Card;

import java.util.ArrayList;
import java.util.List;

public class MasPlayer {

    private final String id;
    private final String name;
    private final List<Card> hand = new ArrayList<>();
    private final List<Card> wonCards = new ArrayList<>();

    public MasPlayer(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<Card> hand() {
        return hand;
    }

    public List<Card> wonCards() {
        return wonCards;
    }

    public boolean hasSuit(Card card) {
        return hand.stream().anyMatch(handCard -> handCard.suit() == card.suit());
    }

    public Card removeCard(String cardCode) {
        Card card = hand.stream()
                .filter(handCard -> handCard.code().equals(cardCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Kortet finns inte på handen."));
        hand.remove(card);
        return card;
    }
}
