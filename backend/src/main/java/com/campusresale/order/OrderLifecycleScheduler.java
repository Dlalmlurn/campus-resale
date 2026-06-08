package com.campusresale.order;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单生命周期后台任务，负责自动推进无需人工参与的支付超时取消和到期结算。
 * 调度间隔使用配置占位符，NAS 部署时可通过环境变量或配置文件按机器性能调整。
 */
@Component
public class OrderLifecycleScheduler {

    private final OrderService orderService;

    public OrderLifecycleScheduler(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 批量处理待付款超时订单，并释放商品占用，避免演示或公网部署时订单长期卡住库存。
     */
    @Scheduled(
            initialDelayString = "${campus-resale.orders.lifecycle-initial-delay-ms:30000}",
            fixedDelayString = "${campus-resale.orders.lifecycle-scan-delay-ms:60000}"
    )
    public void runOrderLifecycleScan() {
        orderService.cancelExpiredPendingPayments();
        orderService.advanceDueSettlementsAutomatically();
    }
}
