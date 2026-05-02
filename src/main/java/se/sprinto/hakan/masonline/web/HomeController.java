package se.sprinto.hakan.masonline.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import se.sprinto.hakan.masonline.catalog.GameCatalog;

@Controller
public class HomeController {

    private final GameCatalog gameCatalog;

    public HomeController(GameCatalog gameCatalog) {
        this.gameCatalog = gameCatalog;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("games", gameCatalog.allGames());
        return "index";
    }
}
