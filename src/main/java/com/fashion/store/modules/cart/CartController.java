package com.fashion.store.modules.cart;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartDTO.CartResponse>> getCart() {
        UUID userId = getCurrentUserId();
        CartDTO.CartResponse response = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin giỏ hàng thành công", response));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartDTO.CartResponse>> addItem(
            @Valid @RequestBody CartDTO.AddToCartRequest request
    ) {
        UUID userId = getCurrentUserId();
        CartDTO.CartResponse response = cartService.addItemToCart(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã thêm sản phẩm vào giỏ hàng", response));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartDTO.CartResponse>> updateItemQuantity(
            @PathVariable UUID itemId,
            @Valid @RequestBody CartDTO.UpdateQuantityRequest request
    ) {
        UUID userId = getCurrentUserId();
        CartDTO.CartResponse response = cartService.updateItemQuantity(userId, itemId, request.quantity());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật số lượng sản phẩm", response));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartDTO.CartResponse>> removeItem(
            @PathVariable UUID itemId
    ) {
        UUID userId = getCurrentUserId();
        CartDTO.CartResponse response = cartService.removeItemFromCart(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa sản phẩm khỏi giỏ hàng", response));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartDTO.CartResponse>> clearCart() {
        UUID userId = getCurrentUserId();
        CartDTO.CartResponse response = cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Đã làm trống giỏ hàng", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));
    }
}
