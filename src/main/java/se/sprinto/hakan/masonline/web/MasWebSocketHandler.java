package se.sprinto.hakan.masonline.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import se.sprinto.hakan.masonline.mas.MasGameService;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MasWebSocketHandler extends TextWebSocketHandler {

    private final MasGameService masGameService;
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessionsByGame = new ConcurrentHashMap<>();

    public MasWebSocketHandler(MasGameService masGameService, ObjectMapper objectMapper) {
        this.masGameService = masGameService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String gameId = gameId(session);
        sessionsByGame.computeIfAbsent(gameId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        sendPersonalState(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String gameId = gameId(session);
        Set<WebSocketSession> sessions = sessionsByGame.get(gameId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }

    public void broadcast(String gameId) {
        Set<WebSocketSession> sessions = sessionsByGame.getOrDefault(gameId, Set.of());
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    sendPersonalState(session);
                } catch (IOException ignored) {
                    // A browser refresh can close the socket while a game event is being broadcast.
                }
            }
        }
    }

    private void sendPersonalState(WebSocketSession session) throws IOException {
        String gameId = gameId(session);
        String playerId = playerId(session.getUri());
        String payload = objectMapper.writeValueAsString(masGameService.view(gameId, playerId));
        session.sendMessage(new TextMessage(payload));
    }

    private String gameId(WebSocketSession session) {
        Object value = session.getAttributes().get("gameId");
        if (value != null) {
            return value.toString();
        }
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        String gameId = path.substring(path.lastIndexOf('/') + 1);
        session.getAttributes().put("gameId", gameId);
        return gameId;
    }

    private String playerId(URI uri) {
        if (uri == null) {
            return null;
        }
        return UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("playerId");
    }
}
