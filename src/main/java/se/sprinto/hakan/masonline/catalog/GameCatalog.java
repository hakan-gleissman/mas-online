package se.sprinto.hakan.masonline.catalog;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameCatalog {

    private final List<GameDefinition> games = List.of(
            new GameDefinition("mas", "MAS", "Omgång 1 är spelbar. Omgång 2 kommer senare.", true),
            new GameDefinition("coming-soon", "Fler kortspel", "Plats för nästa spelmodul.", false)
    );

    public List<GameDefinition> allGames() {
        return games;
    }
}
