package se.sprinto.hakan.masonline.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.sprinto.hakan.masonline.mas.MasGameService;

import java.util.Map;

@RestController
public class MasActionController {

    private final MasGameService masGameService;
    private final MasWebSocketHandler webSocketHandler;

    public MasActionController(MasGameService masGameService, MasWebSocketHandler webSocketHandler) {
        this.masGameService = masGameService;
        this.webSocketHandler = webSocketHandler;
    }

    @PostMapping("/api/mas/{gameId}/start")
    public void start(@PathVariable String gameId, @RequestParam String playerId) {
        masGameService.start(gameId, playerId);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/loser-title/suggest")
    public void suggestLoserTitle(@PathVariable String gameId, @RequestParam String playerId, @RequestParam String title) {
        masGameService.suggestLoserTitle(gameId, playerId, title);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/loser-title/select")
    public void selectLoserTitle(@PathVariable String gameId, @RequestParam String playerId, @RequestParam String suggestionId) {
        masGameService.selectLoserTitle(gameId, playerId, suggestionId);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/start-round-two")
    public void startRoundTwo(@PathVariable String gameId) {
        masGameService.startRoundTwo(gameId);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/send")
    public void sendFromHand(@PathVariable String gameId, @RequestParam String playerId, @RequestParam String cardCode) {
        masGameService.sendFromHand(gameId, playerId, cardCode);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/send-from-deck")
    public void sendFromDeck(@PathVariable String gameId, @RequestParam String playerId) {
        masGameService.sendFromDeck(gameId, playerId);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/respond")
    public void respond(@PathVariable String gameId, @RequestParam String playerId, @RequestParam String cardCode) {
        masGameService.receiverRespond(gameId, playerId, cardCode);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/pickup")
    public void pickup(@PathVariable String gameId, @RequestParam String playerId) {
        masGameService.receiverPickup(gameId, playerId);
        webSocketHandler.broadcast(gameId);
    }

    @PostMapping("/api/mas/{gameId}/round-two/play")
    public void playRoundTwoCard(@PathVariable String gameId, @RequestParam String playerId, @RequestParam String cardCode) {
        masGameService.playRoundTwoCard(gameId, playerId, cardCode);
        webSocketHandler.broadcast(gameId);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadMove(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}
