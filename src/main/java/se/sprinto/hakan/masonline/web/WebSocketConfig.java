package se.sprinto.hakan.masonline.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MasWebSocketHandler masWebSocketHandler;

    public WebSocketConfig(MasWebSocketHandler masWebSocketHandler) {
        this.masWebSocketHandler = masWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(masWebSocketHandler, "/ws/mas/{gameId}").setAllowedOrigins("*");
    }
}
