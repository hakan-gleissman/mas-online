package se.sprinto.hakan.masonline.card;

public record Card(Rank rank, Suit suit) {

    public String code() {
        return rank().name() + "_OF_" + suit().name();
    }

    public String imagePath() {
        return "/cards/" + rank().fileName() + "_of_" + suit().fileName() + ".png";
    }

    public String displayName() {
        return rank().displayName() + " i " + suit().displayName();
    }

    public boolean beats(Card other) {
        return suit == other.suit && rank.value() > other.rank.value();
    }
}
