package se.sprinto.hakan.masonline.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import se.sprinto.hakan.masonline.mas.MasGameService;
import se.sprinto.hakan.masonline.mas.MasPlayer;

@Controller
public class MasPageController {

    private final MasGameService masGameService;
    private final MasWebSocketHandler webSocketHandler;

    public MasPageController(MasGameService masGameService, MasWebSocketHandler webSocketHandler) {
        this.masGameService = masGameService;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping("/games/mas")
    public String lobby(Model model) {
        model.addAttribute("matches", masGameService.lobby());
        return "mas-lobby";
    }

    @PostMapping("/games/mas")
    public String createAndJoin(@RequestParam String playerName) {
        String gameId = masGameService.createGame();
        MasPlayer player = masGameService.join(gameId, playerName);
        return "redirect:/games/mas/" + gameId + "?playerId=" + player.id();
    }

    @PostMapping("/games/mas/{gameId}/join")
    public String join(@PathVariable String gameId, @RequestParam String playerName) {
        MasPlayer player = masGameService.join(gameId, playerName);
        webSocketHandler.broadcast(gameId);
        return "redirect:/games/mas/" + gameId + "?playerId=" + player.id();
    }

    @GetMapping("/games/mas/{gameId}")
    public String table(@PathVariable String gameId, @RequestParam String playerId, Model model) {
        model.addAttribute("gameId", gameId);
        model.addAttribute("playerId", playerId);
        return "mas-table";
    }
}
