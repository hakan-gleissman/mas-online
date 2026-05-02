package se.sprinto.hakan.masonline.mas;

import se.sprinto.hakan.masonline.card.Card;

public record PendingOffer(String senderId, String receiverId, Card sentCard) {
}
