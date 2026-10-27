package com.fashion.store.modules.order;

import java.util.Arrays;
import java.util.List;

public enum OrderStatus {
    PENDING,    // Chờ xử lý / Chờ xác nhận
    CONFIRMED,  // Đã xác nhận đơn hàng
    PROCESSING, // Đang chuẩn bị / đóng gói hàng tại kho
    SHIPPING,   // Đang giao hàng
    DELIVERED,  // Đã giao hàng thành công
    CANCELLED,  // Đã hủy đơn hàng
    RETURNED;   // Đã hoàn hàng về kho

    public boolean canTransitionTo(OrderStatus next) {
        if (this == next) return true;
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SHIPPING || next == CANCELLED;
            case SHIPPING -> next == DELIVERED || next == RETURNED;
            case DELIVERED -> next == RETURNED;
            case CANCELLED, RETURNED -> false;
        };
    }

    public List<OrderStatus> getValidNextStatuses() {
        return Arrays.stream(OrderStatus.values())
                .filter(next -> next != this && this.canTransitionTo(next))
                .toList();
    }
}
