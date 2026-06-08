package com.campusresale.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campusresale.platform.api.ApiException;
import com.campusresale.platform.api.PageResponse;
import com.campusresale.platform.security.CurrentPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final NotificationRepository notificationRepository = org.mockito.Mockito.mock(NotificationRepository.class);
    private final NotificationService notificationService = new NotificationService(notificationRepository);

    @Test
    void listsCurrentUsersNotifications() {
        when(notificationRepository.findByReceiver(1L, true, 10, 0)).thenReturn(List.of(record(10L, 1L, null)));
        when(notificationRepository.countByReceiver(1L, true)).thenReturn(1L);

        PageResponse<NotificationResponse> response = notificationService.list(principal(1L), true, 1, 10);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(10L);
        verify(notificationRepository).findByReceiver(1L, true, 10, 0);
    }

    @Test
    void markReadRejectsNotificationOwnedByOtherUser() {
        when(notificationRepository.findByIdAndReceiver(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(10L, principal(1L)))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.code()).isEqualTo("NOT_FOUND"));
    }

    @Test
    void markReadReturnsUpdatedNotification() {
        when(notificationRepository.findByIdAndReceiver(10L, 1L))
                .thenReturn(Optional.of(record(10L, 1L, null)))
                .thenReturn(Optional.of(record(10L, 1L, Instant.parse("2026-06-03T08:00:00Z"))));

        NotificationResponse response = notificationService.markRead(10L, principal(1L));

        assertThat(response.read()).isTrue();
        verify(notificationRepository).markRead(eq(10L), eq(1L), any());
    }

    @Test
    void createsPaymentNotificationsForBothParticipantsWithStableDedupeKeys() {
        when(notificationRepository.create(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> record(
                        100L + invocation.getArgument(0, Long.class),
                        invocation.getArgument(0, Long.class),
                        null
                ));

        List<NotificationRecord> records = notificationService.notifyPaymentSucceeded(1L, 2L, 20L);

        assertThat(records).extracting(NotificationRecord::receiverUserId).containsExactly(1L, 2L);
        verify(notificationRepository).create(
                eq(1L),
                eq(NotificationType.PAYMENT_ESCROWED),
                eq("支付已完成"),
                eq("模拟支付已完成，订单进入待面交状态。"),
                eq("ORDER"),
                eq(20L),
                eq("order:20:payment-escrowed:buyer:1")
        );
        verify(notificationRepository).create(
                eq(2L),
                eq(NotificationType.PAYMENT_ESCROWED),
                eq("买家已完成支付"),
                eq("买家已完成模拟支付，请按约定准备面交。"),
                eq("ORDER"),
                eq(20L),
                eq("order:20:payment-escrowed:seller:2")
        );
    }

    @Test
    void rejectsIncompleteRelatedObject() {
        assertThatThrownBy(() -> notificationService.create(
                1L,
                NotificationType.ORDER_CREATED,
                "标题",
                "内容",
                " ",
                20L,
                null
        )).isInstanceOfSatisfying(ApiException.class,
                exception -> assertThat(exception.code()).isEqualTo("VALIDATION_FAILED"));
    }

    private NotificationRecord record(long id, long receiverUserId, Instant readAt) {
        return new NotificationRecord(
                id,
                receiverUserId,
                NotificationType.ORDER_CREATED,
                "收到新的订单",
                "买家已发起下单",
                "ORDER",
                20L,
                "dedupe-" + id,
                readAt,
                Instant.parse("2026-06-03T07:00:00Z")
        );
    }

    private CurrentPrincipal principal(long id) {
        return new CurrentPrincipal(
                id,
                "user" + id,
                "User " + id,
                "ACTIVE",
                Set.of("REGISTERED_USER"),
                10L,
                Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(120)
        );
    }
}
