package se.sprinto.hakan.masonline.card;

public enum Rank {
    TWO(2, "2", "2"),
    THREE(3, "3", "3"),
    FOUR(4, "4", "4"),
    FIVE(5, "5", "5"),
    SIX(6, "6", "6"),
    SEVEN(7, "7", "7"),
    EIGHT(8, "8", "8"),
    NINE(9, "9", "9"),
    TEN(10, "10", "10"),
    JACK(11, "jack", "Knekt"),
    QUEEN(12, "queen", "Dam"),
    KING(13, "king", "Kung"),
    ACE(14, "ace", "Ess");

    private final int value;
    private final String fileName;
    private final String displayName;

    Rank(int value, String fileName, String displayName) {
        this.value = value;
        this.fileName = fileName;
        this.displayName = displayName;
    }

    public int value() {
        return value;
    }

    public String fileName() {
        return fileName;
    }

    public String displayName() {
        return displayName;
    }
}
