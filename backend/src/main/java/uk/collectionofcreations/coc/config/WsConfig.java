package uk.collectionofcreations.coc.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import uk.collectionofcreations.coc.ws.CocWsHandler;

/**
 * WebSocket 注册：/ws?token=xxx
 */
@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {

    private final CocWsHandler cocWsHandler;

    public WsConfig(CocWsHandler cocWsHandler) {
        this.cocWsHandler = cocWsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cocWsHandler, "/ws").setAllowedOrigins("*");
    }
}
