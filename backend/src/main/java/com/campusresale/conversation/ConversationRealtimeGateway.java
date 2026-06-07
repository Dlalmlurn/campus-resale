package com.campusresale.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class ConversationRealtimeGateway {

    private final ObjectMapper objectMapper;
    private final Map<Long, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public ConversationRealtimeGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(long userId, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUser.remove(userId, sessions);
        }
    }

    public void publishAfterCommit(ConversationRealtimeEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish(event);
                }
            });
            return;
        }
        publish(event);
    }

    public void publish(ConversationRealtimeEvent event) {
        if (event == null || event.targetUserIds().isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot serialize realtime event", exception);
        }
        for (Long userId : event.targetUserIds()) {
            Set<WebSocketSession> sessions = sessionsByUser.get(userId);
            if (sessions == null) {
                continue;
            }
            for (WebSocketSession session : sessions) {
                if (!isSubscribed(session, event.conversationId())) {
                    continue;
                }
                send(session, payload);
            }
        }
    }

    private boolean isSubscribed(WebSocketSession session, long conversationId) {
        Object value = session.getAttributes().get("subscribedConversationIds");
        if (value instanceof Set<?> subscriptions) {
            return subscriptions.contains(conversationId);
        }
        return false;
    }

    private void send(WebSocketSession session, String payload) {
        if (!session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (IOException ignored) {
                try {
                    session.close();
                } catch (IOException closeIgnored) {
                    // The session is already unusable.
                }
            }
        }
    }
}
