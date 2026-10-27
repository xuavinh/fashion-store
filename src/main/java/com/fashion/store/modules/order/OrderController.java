package com.fashion.store.modules.order;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.common.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // Đặt hàng (Thanh toán COD từ giỏ hàng hoặc Mua ngay)
    @PostMapping("/checkout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDTO.OrderResponse>> checkout(
            @Valid @RequestBody OrderDTO.CheckoutRequest request
    ) {
        UUID userId = getCurrentUserId();
        OrderDTO.OrderResponse response = orderService.checkout(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt hàng thành công", response));
    }

    // Lịch sử đơn hàng của người dùng hiện tại

    @GetMapping("/my-orders")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OrderDTO.OrderSummaryResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        UUID userId = getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<OrderDTO.OrderSummaryResponse> response = orderService.getMyOrders(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng thành công", response));
    }

    //Chi tiết đơn hàng
    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDTO.OrderResponse>> getOrderDetails(@PathVariable UUID orderId) {
        UUID userId = getCurrentUserId();
        boolean isAdminOrStaff = isUserAdminOrStaff();
        OrderDTO.OrderResponse response = orderService.getOrderDetails(userId, orderId, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", response));
    }

    // Chi tiết đơn hàng theo mã hiển thị
    @GetMapping("/code/{orderCode}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDTO.OrderResponse>> getOrderByCode(@PathVariable String orderCode) {
        UUID userId = getCurrentUserId();
        boolean isAdminOrStaff = isUserAdminOrStaff();
        OrderDTO.OrderResponse response = orderService.getOrderByOrderCode(userId, orderCode, isAdminOrStaff);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết đơn hàng thành công", response));
    }

    // Khách hàng tự hủy đơn hàng (khi đơn còn ở trạng thái PENDING hoặc CONFIRMED)
    @RequestMapping(value = "/{orderId}/cancel", method = {RequestMethod.POST, RequestMethod.PATCH})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderDTO.OrderResponse>> cancelOrder(
            @PathVariable UUID orderId,
            @RequestBody(required = false) OrderDTO.CancelOrderRequest request
    ) {
        UUID userId = getCurrentUserId();
        String reason = (request != null) ? request.reason() : null;
        OrderDTO.OrderResponse response = orderService.cancelOrder(userId, orderId, reason);
        return ResponseEntity.ok(ApiResponse.success("Đã hủy đơn hàng thành công", response));
    }

    // Danh sách tất cả đơn hàng kèm phân trang, lọc theo trạng thái và tìm kiếm
    @GetMapping({"", "/admin"})
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderDTO.OrderSummaryResponse>>> getAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = "ASC".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<OrderDTO.OrderSummaryResponse> response = orderService.getAllOrders(status, search, pageable);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách đơn hàng quản trị thành công", response));
    }

    // Cập nhật trạng thái đơn hàng
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderDTO.OrderResponse>> updateOrderStatus(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderDTO.UpdateOrderStatusRequest request
    ) {
        OrderDTO.OrderResponse response = orderService.updateOrderStatus(orderId, request.status());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật trạng thái đơn hàng", response));
    }

    private UUID getCurrentUserId() {
        return SecurityUtils.getCurrentUserId()
                .orElseThrow(() -> new BadRequestException("Chưa đăng nhập hoặc phiên đăng nhập không hợp lệ"));
    }

    private boolean isUserAdminOrStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_STAFF"));
    }
}
