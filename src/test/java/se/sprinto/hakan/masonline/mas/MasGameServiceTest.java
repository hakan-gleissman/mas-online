package se.sprinto.hakan.masonline.mas;

import org.junit.jupiter.api.Test;
import se.sprinto.hakan.masonline.card.Card;
import se.sprinto.hakan.masonline.card.Rank;
import se.sprinto.hakan.masonline.card.Suit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasGameServiceTest {

    @Test
    void newGameCanBeJoinedAndStarted() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        service.join(gameId, "Cia");

        service.start(gameId, first.id());
        MasGameView firstView = service.view(gameId, first.id());
        MasGameView secondView = service.view(gameId, second.id());

        assertThat(firstView.status()).isEqualTo("Omgång 1");
        assertThat(firstView.players()).hasSize(3);
        assertThat(firstView.hand()).hasSize(3);
        assertThat(secondView.hand()).hasSize(3);
        assertThat(firstView.deckCount()).isEqualTo(43);
    }

    @Test
    void onlyWaitingGamesAreJoinableInLobby() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        service.join(gameId, "Anna");
        service.join(gameId, "Bo");
        service.join(gameId, "Cia");
        MasPlayer host = service.game(gameId).players().getFirst();
        service.start(gameId, host.id());

        assertThat(service.lobby()).singleElement().satisfies(match -> {
            assertThat(match.id()).isEqualTo(gameId);
            assertThat(match.joinable()).isFalse();
        });
    }

    @Test
    void threePlayersAreRequiredToStart() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer host = service.join(gameId, "Anna");
        service.join(gameId, "Bo");

        assertThat(service.view(gameId, host.id()).canStart()).isFalse();
        assertThatThrownBy(() -> service.start(gameId, host.id()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Minst tre spelare behövs för att starta.");
    }

    @Test
    void roundOneEndsWhenDeckBecomesEmptyAndTrumpComesFromLastDrawnCard() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer sender = service.join(gameId, "Anna");
        MasPlayer receiver = service.join(gameId, "Bo");
        MasGame game = service.game(gameId);

        Card sentCard = new Card(Rank.TWO, Suit.HEARTS);
        Card finalCard = new Card(Rank.THREE, Suit.HEARTS);
        Card finalDrawnCard = new Card(Rank.ACE, Suit.SPADES);
        game.status(MasGameStatus.ROUND_ONE);
        game.activePlayerId(sender.id());
        game.deck().clear();
        game.deck().add(finalDrawnCard);
        sender.hand().add(sentCard);
        receiver.hand().add(finalCard);

        service.sendFromHand(gameId, sender.id(), sentCard.code());
        service.receiverRespond(gameId, receiver.id(), finalCard.code());

        MasGameView senderView = service.view(gameId, sender.id());
        assertThat(senderView.status()).isEqualTo("Omgång 1 klar");
        assertThat(senderView.roundOneFinished()).isTrue();
        assertThat(senderView.trumpSuit()).isEqualTo("Spader");
        assertThat(senderView.deckCount()).isZero();
        assertThat(senderView.hand()).isEmpty();
        assertThat(senderView.pendingOffer()).isNull();
        assertThat(sender.wonCards()).containsExactly(finalDrawnCard);
        assertThat(receiver.wonCards()).containsExactly(sentCard, finalCard);
        assertThat(senderView.events()).singleElement().satisfies(event -> {
            assertThat(event.text()).isEqualTo("Omgång 1 är slut. Trumf i omgång 2 blir Spader.");
            assertThat(event.sentCard()).isNull();
            assertThat(event.responseCard()).isNull();
            assertThat(event.trickHidden()).isFalse();
        });
    }

    @Test
    void roundOneReceiverCanPickupEvenWhenAbleToPlayHigherSameSuit() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer sender = service.join(gameId, "Anna");
        MasPlayer receiver = service.join(gameId, "Bo");
        MasPlayer next = service.join(gameId, "Cia");
        MasGame game = service.game(gameId);

        Card sentCard = new Card(Rank.TWO, Suit.HEARTS);
        Card higherSameSuit = new Card(Rank.THREE, Suit.HEARTS);
        game.status(MasGameStatus.ROUND_ONE);
        game.activePlayerId(sender.id());
        sender.hand().add(sentCard);
        receiver.hand().add(higherSameSuit);
        game.deck().add(new Card(Rank.FOUR, Suit.CLUBS));
        game.deck().add(new Card(Rank.FIVE, Suit.CLUBS));
        game.deck().add(new Card(Rank.SIX, Suit.CLUBS));
        game.deck().add(new Card(Rank.SEVEN, Suit.CLUBS));
        game.deck().add(new Card(Rank.EIGHT, Suit.CLUBS));
        game.deck().add(new Card(Rank.NINE, Suit.CLUBS));
        game.deck().add(new Card(Rank.TEN, Suit.CLUBS));
        game.deck().add(new Card(Rank.JACK, Suit.CLUBS));

        service.sendFromHand(gameId, sender.id(), sentCard.code());
        service.receiverPickup(gameId, receiver.id());

        MasGameView receiverView = service.view(gameId, receiver.id());
        assertThat(receiverView.hand()).extracting(CardView::code)
                .contains(sentCard.code(), higherSameSuit.code());
        assertThat(receiver.wonCards()).isEmpty();
        assertThat(sender.wonCards()).isEmpty();
        assertThat(game.activePlayerId()).isEqualTo(next.id());
        assertThat(receiverView.events()).first().satisfies(event ->
                assertThat(event.text()).isEqualTo("Bo valde att ta upp 2 i Hjärter.")
        );
    }

    @Test
    void roundTwoStartsFromWonCardsAndEndsWhenOnlyOnePlayerHasCardsLeft() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasPlayer third = service.join(gameId, "Cia");
        MasGame game = service.game(gameId);

        Card firstLead = new Card(Rank.TWO, Suit.HEARTS);
        Card firstRemainingCard = new Card(Rank.KING, Suit.CLUBS);
        Card secondCard = new Card(Rank.THREE, Suit.HEARTS);
        Card thirdCard = new Card(Rank.FOUR, Suit.HEARTS);
        first.wonCards().add(firstLead);
        first.wonCards().add(firstRemainingCard);
        second.wonCards().add(secondCard);
        third.wonCards().add(thirdCard);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        MasGameView firstRoundTwoView = service.view(gameId, first.id());
        assertThat(firstRoundTwoView.roundTwo()).isTrue();
        assertThat(firstRoundTwoView.roundTwoTrickSize()).isEqualTo(3);
        assertThat(firstRoundTwoView.hand()).extracting(CardView::code)
                .containsExactly(firstRemainingCard.code(), firstLead.code());

        service.playRoundTwoCard(gameId, first.id(), firstLead.code());
        service.playRoundTwoCard(gameId, second.id(), secondCard.code());
        service.playRoundTwoCard(gameId, third.id(), thirdCard.code());

        MasGameView view = service.view(gameId, first.id());
        assertThat(view.gameFinished()).isTrue();
        assertThat(view.status()).isEqualTo("Spelet är slut");
        assertThat(view.loserName()).isEqualTo("Anna");
        assertThat(view.roundTwoTable()).isEmpty();
    }

    @Test
    void hostSelectsLoserTitleBeforeStartAndLoserIsNamedByThatTitle() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer host = service.join(gameId, "Anna");
        MasPlayer guest = service.join(gameId, "Bo");

        service.suggestLoserTitle(gameId, guest.id(), "sopa");
        String suggestionId = service.view(gameId, host.id()).loserTitleSuggestions().getFirst().id();
        service.selectLoserTitle(gameId, host.id(), suggestionId);

        MasGame game = service.game(gameId);
        Card hostLead = new Card(Rank.TWO, Suit.HEARTS);
        Card guestCard = new Card(Rank.THREE, Suit.HEARTS);
        Card hostRemaining = new Card(Rank.KING, Suit.CLUBS);
        host.wonCards().add(hostLead);
        host.wonCards().add(hostRemaining);
        guest.wonCards().add(guestCard);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, host.id(), hostLead.code());
        service.playRoundTwoCard(gameId, guest.id(), guestCard.code());

        MasGameView view = service.view(gameId, host.id());
        assertThat(view.gameFinished()).isTrue();
        assertThat(view.loserName()).isEqualTo("Anna");
        assertThat(view.loserTitle()).isEqualTo("sopa");
        assertThat(view.events()).singleElement().satisfies(event ->
                assertThat(event.text()).isEqualTo("Anna är dagens sopa.")
        );
    }

    @Test
    void onlyHostCanSelectLoserTitle() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer host = service.join(gameId, "Anna");
        MasPlayer guest = service.join(gameId, "Bo");

        service.suggestLoserTitle(gameId, guest.id(), "sopa");
        String suggestionId = service.view(gameId, host.id()).loserTitleSuggestions().getFirst().id();

        assertThatThrownBy(() -> service.selectLoserTitle(gameId, guest.id(), suggestionId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bara den som skapade matchen kan välja förlorartitel.");
    }

    @Test
    void roundTwoRequiresHigherSameSuitWhenPlayerCanDoIt() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasGame game = service.game(gameId);

        Card lead = new Card(Rank.TWO, Suit.HEARTS);
        Card requiredAnswer = new Card(Rank.THREE, Suit.HEARTS);
        Card wrongCard = new Card(Rank.KING, Suit.CLUBS);
        first.wonCards().add(lead);
        second.wonCards().add(requiredAnswer);
        second.wonCards().add(wrongCard);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), lead.code());

        assertThatThrownBy(() -> service.playRoundTwoCard(gameId, second.id(), wrongCard.code()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Du måste följa färg och lägga högre än föregående kort.");
    }

    @Test
    void roundTwoPlayerMustPickupTopCardWhenSameSuitCannotBeatPreviousCard() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasGame game = service.game(gameId);

        Card lead = new Card(Rank.THREE, Suit.HEARTS);
        Card lowerSameSuit = new Card(Rank.TWO, Suit.HEARTS);
        first.wonCards().add(lead);
        second.wonCards().add(lowerSameSuit);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), lead.code());

        MasGameView secondView = service.view(gameId, second.id());
        assertThat(secondView.canPickupRoundTwo()).isTrue();
        assertThat(secondView.mustPickupRoundTwo()).isTrue();

        assertThatThrownBy(() -> service.playRoundTwoCard(gameId, second.id(), lowerSameSuit.code()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Du kan inte lägga högre och måste ta upp kortet.");

        service.pickupRoundTwoCard(gameId, second.id());

        MasGameView finishedView = service.view(gameId, second.id());
        assertThat(finishedView.gameFinished()).isTrue();
        assertThat(finishedView.loserName()).isEqualTo("Bo");
        assertThat(finishedView.roundTwoTable()).isEmpty();
        assertThat(finishedView.hand()).extracting(CardView::code)
                .containsExactly(lowerSameSuit.code(), lead.code());
    }

    @Test
    void roundTwoPlayerMayPickupTrumpEvenWhenAbleToBeatIt() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasGame game = service.game(gameId);

        Card trumpLead = new Card(Rank.THREE, Suit.SPADES);
        Card higherTrump = new Card(Rank.FOUR, Suit.SPADES);
        first.wonCards().add(trumpLead);
        second.wonCards().add(higherTrump);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), trumpLead.code());

        MasGameView secondView = service.view(gameId, second.id());
        assertThat(secondView.canPickupRoundTwo()).isTrue();
        assertThat(secondView.mustPickupRoundTwo()).isFalse();

        service.pickupRoundTwoCard(gameId, second.id());

        MasGameView finishedView = service.view(gameId, second.id());
        assertThat(finishedView.gameFinished()).isTrue();
        assertThat(finishedView.loserName()).isEqualTo("Bo");
        assertThat(finishedView.hand()).extracting(CardView::code)
                .containsExactly(trumpLead.code(), higherTrump.code());
    }

    @Test
    void roundTwoPlayerMayPickupWhenUnableToFollowSuitEvenWithTrumpAvailable() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasGame game = service.game(gameId);

        Card lead = new Card(Rank.THREE, Suit.HEARTS);
        Card trump = new Card(Rank.ACE, Suit.SPADES);
        first.wonCards().add(lead);
        second.wonCards().add(trump);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), lead.code());

        MasGameView secondView = service.view(gameId, second.id());
        assertThat(secondView.canPickupRoundTwo()).isTrue();
        assertThat(secondView.mustPickupRoundTwo()).isFalse();

        service.pickupRoundTwoCard(gameId, second.id());

        MasGameView finishedView = service.view(gameId, second.id());
        assertThat(finishedView.gameFinished()).isTrue();
        assertThat(finishedView.loserName()).isEqualTo("Bo");
        assertThat(finishedView.hand()).extracting(CardView::code)
                .containsExactly(lead.code(), trump.code());
    }

    @Test
    void roundTwoPickupOnlyTakesTopCardAndNextPlayerMustBeatNewTopCard() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasPlayer third = service.join(gameId, "Cia");
        MasPlayer fourth = service.join(gameId, "Dan");
        MasGame game = service.game(gameId);

        Card firstLead = new Card(Rank.THREE, Suit.HEARTS);
        Card secondPlay = new Card(Rank.FOUR, Suit.HEARTS);
        Card thirdLowerSameSuit = new Card(Rank.TWO, Suit.HEARTS);
        Card fourthTooLow = new Card(Rank.THREE, Suit.HEARTS);
        Card fourthPlayable = new Card(Rank.FIVE, Suit.HEARTS);
        first.wonCards().add(firstLead);
        second.wonCards().add(secondPlay);
        third.wonCards().add(thirdLowerSameSuit);
        fourth.wonCards().add(fourthTooLow);
        fourth.wonCards().add(fourthPlayable);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), firstLead.code());
        service.playRoundTwoCard(gameId, second.id(), secondPlay.code());
        service.pickupRoundTwoCard(gameId, third.id());

        MasGameView thirdView = service.view(gameId, third.id());
        assertThat(third.hand()).hasSize(2);
        assertThat(thirdView.hand()).extracting(CardView::code)
                .containsExactly(thirdLowerSameSuit.code(), secondPlay.code());
        assertThat(thirdView.roundTwoTable()).extracting(TableEventView::sentCard)
                .extracting(CardView::code)
                .containsExactly(firstLead.code());
        assertThat(thirdView.events()).singleElement().satisfies(event ->
                assertThat(event.text()).isEqualTo("Cia kunde inte lägga högre och tog upp 4 i Hjärter. 1 kort ligger kvar i högen.")
        );
        assertThat(service.view(gameId, fourth.id()).youAreActive()).isTrue();

        assertThatThrownBy(() -> service.playRoundTwoCard(gameId, fourth.id(), fourthTooLow.code()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Du måste följa färg och lägga högre än föregående kort.");

        service.playRoundTwoCard(gameId, fourth.id(), fourthPlayable.code());

        assertThat(game.status()).isEqualTo(MasGameStatus.ROUND_TWO);
        assertThat(game.activePlayerId()).isEqualTo(third.id());
    }

    @Test
    void roundTwoNextPlayerMayStartAnySuitWhenPickupEmptiesPile() {
        MasGameService service = new MasGameService();
        String gameId = service.createGame();
        MasPlayer first = service.join(gameId, "Anna");
        MasPlayer second = service.join(gameId, "Bo");
        MasPlayer third = service.join(gameId, "Cia");
        MasGame game = service.game(gameId);

        Card lead = new Card(Rank.THREE, Suit.HEARTS);
        Card lowerSameSuit = new Card(Rank.TWO, Suit.HEARTS);
        Card freeSuit = new Card(Rank.FOUR, Suit.CLUBS);
        first.wonCards().add(lead);
        second.wonCards().add(lowerSameSuit);
        third.wonCards().add(freeSuit);
        game.status(MasGameStatus.ROUND_ONE_FINISHED);
        game.trumpSuit(Suit.SPADES);

        service.startRoundTwo(gameId);
        service.playRoundTwoCard(gameId, first.id(), lead.code());
        service.pickupRoundTwoCard(gameId, second.id());

        MasGameView thirdView = service.view(gameId, third.id());
        assertThat(thirdView.youAreActive()).isTrue();
        assertThat(thirdView.roundTwoTable()).isEmpty();

        service.playRoundTwoCard(gameId, third.id(), freeSuit.code());

        MasGameView secondView = service.view(gameId, second.id());
        assertThat(secondView.gameFinished()).isFalse();
        assertThat(secondView.youAreActive()).isTrue();
        assertThat(secondView.roundTwoTable()).extracting(TableEventView::sentCard)
                .extracting(CardView::code)
                .containsExactly(freeSuit.code());

        service.playRoundTwoCard(gameId, second.id(), lowerSameSuit.code());

        MasGameView finishedView = service.view(gameId, second.id());
        assertThat(finishedView.gameFinished()).isTrue();
        assertThat(finishedView.loserName()).isEqualTo("Bo");
    }
}
