package se.sprinto.hakan.masonline.card;

public enum Suit {
    CLUBS("clubs", "Klöver"),
    DIAMONDS("diamonds", "Ruter"),
    HEARTS("hearts", "Hjärter"),
    SPADES("spades", "Spader");

    private final String fileName;
    private final String displayName;

    Suit(String fileName, String displayName) {
        this.fileName = fileName;
        this.displayName = displayName;
    }

    public String fileName() {
        return fileName;
    }

    public String displayName() {
        return displayName;
    }
}
