package com.fashion.store.modules.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public class CartDTO {

    public record AddToCartRequest(
            @NotNull(message = "ID biến thể sản phẩm (variantId) không được để trống")
            UUID variantId,

            @NotNull(message = "Số lượng không được để trống")
            @Min(value = 1, message = "Số lượng phải lớn hơn hoặc bằng 1")
            Integer quantity
    ) {}

    public record UpdateQuantityRequest(
            @NotNull(message = "Số lượng không được để trống")
            @Min(value = 1, message = "Số lượng cập nhật phải lớn hơn hoặc bằng 1")
            Integer quantity
    ) {}

    @Builder
    public record CartItemResponse(
            UUID itemId,
            UUID variantId,
            UUID productId,
            String productName,
            String productSlug,
            String color,
            String colorCode,
            String size,
            String skuCode,
            BigDecimal price,
            BigDecimal comparePrice,
            Integer quantity,
            Integer stockQty,
            BigDecimal subtotal,
            String imageUrl,
            boolean inStock
    ) {}

    @Builder
    public record CartResponse(
            UUID cartId,
            List<CartItemResponse> items,
            int totalItems,
            BigDecimal totalPrice
    ) {}
}
