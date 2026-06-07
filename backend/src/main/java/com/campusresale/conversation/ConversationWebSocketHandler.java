package com.campusresale.conversation;

import com.campusresale.platform.security.CurrentPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ConversationWebSocketHandler extends TextWebSocketHandler {

    public static final String PRINCIPAL_ATTRIBUTE = "currentPrincipal";

    private final ConversationRepository conversationRepository;
    private final ConversationRealtimeGateway realtimeGateway;
    private final ObjectMapper objectMapper;

    public ConversationWebSocketHandler(
            ConversationRepository conversationRepository,
            ConversationRealtimeGateway realtimeGateway,
            ObjectMapper objectMapper
    ) {
        this.conversationRepository = conversationRepository;
        this.realtimeGateway = realtimeGateway;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        CurrentPrincipal principal = principal(session);
        realtimeGateway.register(principal.id(), session);
        session.getAttributes().put("subscribedConversationIds", ConcurrentHashMap.<Long>newKeySet());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode root = objectMapper.readTree(message.getPayload());
        String type = root.path("type").asText("");
        if (!"SUBSCRIBE_CONVERSATION".equals(type) && !"UNSUBSCRIBE_CONVERSATION".equals(type)) {
            sendControl(session, "ERROR", "UNSUPPORTED_EVENT", "不支持的实时事件");
            return;
        }
        long conversationId = root.path("conversationId").asLong(0);
        if (conversationId <= 0) {
            sendControl(session, "ERROR", "VALIDATION_FAILED", "conversationId 不能为空");
            return;
        }
        CurrentPrincipal principal = principal(session);
        if ("UNSUBSCRIBE_CONVERSATION".equals(type)) {
            subscriptions(session).remove(conversationId);
            sendControl(session, "UNSUBSCRIBED", null, "已取消订阅");
            return;
        }
        boolean participant = conversationRepository.findById(conversationId)
                .filter(conversation -> conversation.buyerId() == principal.id() || conversation.sellerId() == principal.id())
                .isPresent();
        if (!participant) {
            sendControl(session, "ERROR", "NOT_FOUND", "会话不存在或不可见");
            return;
        }
        subscriptions(session).add(conversationId);
        sendControl(session, "SUBSCRIBED", null, "已订阅会话");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        CurrentPrincipal principal = principal(session);
        realtimeGateway.unregister(principal.id(), session);
    }

    @SuppressWarnings("unchecked")
    private Set<Long> subscriptions(WebSocketSession session) {
        Object value = session.getAttributes().get("subscribedConversationIds");
        if (value instanceof Set<?> set) {
            return (Set<Long>) set;
        }
        Set<Long> set = ConcurrentHashMap.newKeySet();
        session.getAttributes().put("subscribedConversationIds", set);
        return set;
    }

    private CurrentPrincipal principal(WebSocketSession session) {
        Object principal = session.getAttributes().get(PRINCIPAL_ATTRIBUTE);
        if (principal instanceof CurrentPrincipal currentPrincipal) {
            return currentPrincipal;
        }
        throw new IllegalStateException("Authenticated principal missing from websocket session");
    }

    private void sendControl(WebSocketSession session, String type, String code, String message) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(new ControlMessage(type, code, message))));
    }

    private record ControlMessage(String type, String code, String message) {
    }
}
