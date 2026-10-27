package com.fashion.store.modules.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class OrderDTO {

    public record CheckoutRequest(
            // Tùy chọn 1: Chọn từ sổ địa chỉ (Chuẩn Shopee)
            UUID addressId,

            // Tùy chọn 2: Nhập địa chỉ mới (nếu không chọn từ sổ địa chỉ)
            @Size(max = 100, message = "Họ tên người nhận không được quá 100 ký tự")
            String shippingName,

            @Pattern(
                    regexp = "^(|(0|\\+84)[35789]\\d{8})$",
                    message = "Số điện thoại nhận hàng không đúng định dạng"
            )
            String shippingPhone,

            String shippingAddress,
            String shippingProvince,
            String shippingDistrict,
            String shippingWard,
            String note,

            PaymentMethod paymentMethod,

            // Dùng khi khách bấm "Mua ngay" không qua giỏ hàng
            UUID buyNowVariantId,
            Integer buyNowQuantity,

            // Dùng khi khách chọn các sản phẩm cụ thể trong giỏ hàng
            List<UUID> selectedCartItemIds
    ) {}

    public record UpdateOrderStatusRequest(
            @NotNull(message = "Trạng thái đơn hàng không được để trống")
            OrderStatus status
    ) {}

    public record CancelOrderRequest(
            String reason
    ) {}

    @Builder
    public record OrderItemResponse(
            UUID itemId,
            UUID variantId,
            String productName,
            String color,
            String size,
            String skuCode,
            BigDecimal price,
            Integer quantity,
            BigDecimal subtotal,
            String imageUrl
    ) {}

    @Builder
    public record OrderResponse(
            UUID orderId,
            String orderCode,
            UUID userId,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            String shippingName,
            String shippingPhone,
            String shippingAddress,
            String shippingProvince,
            String shippingDistrict,
            String shippingWard,
            String note,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal totalAmount,
            List<OrderItemResponse> items,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    @Builder
    public record OrderSummaryResponse(
            UUID orderId,
            String orderCode,
            OrderStatus status,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            String shippingName,
            String shippingPhone,
            BigDecimal totalAmount,
            int totalItems,
            OffsetDateTime createdAt
    ) {}
}
