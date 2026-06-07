package com.campusresale.conversation;

import com.campusresale.identity.application.SessionLookupService;
import com.campusresale.platform.config.CampusResaleProperties;
import com.campusresale.platform.security.SecurityProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@EnableWebSocket
public class ConversationWebSocketConfig implements WebSocketConfigurer {

    private final ConversationWebSocketHandler handler;
    private final SessionLookupService sessionLookupService;
    private final CampusResaleProperties properties;

    public ConversationWebSocketConfig(
            ConversationWebSocketHandler handler,
            SessionLookupService sessionLookupService,
            CampusResaleProperties properties
    ) {
        this.handler = handler;
        this.sessionLookupService = sessionLookupService;
        this.properties = properties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> allowedOrigins = properties.cors().allowedOrigins();
        registry.addHandler(handler, "/ws/conversations")
                .addInterceptors(new SessionHandshakeInterceptor(sessionLookupService))
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    private static class SessionHandshakeInterceptor implements HandshakeInterceptor {

        private final SessionLookupService sessionLookupService;

        private SessionHandshakeInterceptor(SessionLookupService sessionLookupService) {
            this.sessionLookupService = sessionLookupService;
        }

        @Override
        public boolean beforeHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Map<String, Object> attributes
        ) {
            if (!(request instanceof ServletServerHttpRequest servletRequest)) {
                return false;
            }
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            return sessionLookupService.loadByRawToken(findSessionCookie(httpRequest))
                    .map(principal -> {
                        attributes.put(ConversationWebSocketHandler.PRINCIPAL_ATTRIBUTE, principal);
                        return true;
                    })
                    .orElse(false);
        }

        @Override
        public void afterHandshake(
                ServerHttpRequest request,
                ServerHttpResponse response,
                WebSocketHandler wsHandler,
                Exception exception
        ) {
            // No-op.
        }

        private String findSessionCookie(HttpServletRequest request) {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) {
                return null;
            }
            for (Cookie cookie : cookies) {
                if (SecurityProperties.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
            return null;
        }
    }
}
