package com.fashion.store.modules.order;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.cart.Cart;
import com.fashion.store.modules.cart.CartItem;
import com.fashion.store.modules.cart.CartItemRepository;
import com.fashion.store.modules.cart.CartRepository;
import com.fashion.store.modules.product.Product;
import com.fashion.store.modules.product.ProductImage;
import com.fashion.store.modules.product.ProductVariant;
import com.fashion.store.modules.product.ProductVariantRepository;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.fashion.store.modules.user.address.UserAddress;
import com.fashion.store.modules.user.address.UserAddressRepository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    public static final BigDecimal FREESHIP_THRESHOLD = BigDecimal.valueOf(500000); // Đơn >= 500k miễn phí ship
    public static final BigDecimal STANDARD_SHIPPING_FEE = BigDecimal.valueOf(30000); // Phí ship chuẩn 30k

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final UserAddressRepository userAddressRepository;

    private static final String CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public OrderDTO.OrderResponse checkout(UUID userId, OrderDTO.CheckoutRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + userId));

        PaymentMethod paymentMethod = (request.paymentMethod() != null)
                ? request.paymentMethod()
                : PaymentMethod.COD;

        // Khóa COD nếu khách có từ 3 đơn hàng hoàn trả (bom hàng)
        if (paymentMethod == PaymentMethod.COD) {
            long returnedCount = orderRepository.countByUserUserIdAndStatusAndPaymentMethod(
                    userId, OrderStatus.RETURNED, PaymentMethod.COD
            );
            if (returnedCount >= 3) {
                throw new BadRequestException(
                        "Tài khoản của bạn đã có " + returnedCount + " đơn hàng giao không thành công (bom hàng). " +
                                "Phương thức COD đã tạm thời bị khóa đối với tài khoản này. Vui lòng liên hệ CSKH hoặc chọn hình thức thanh toán trực tuyến."
                );
            }
        }

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean fromCart = false;

        // Trường hợp 1: Khách bấm "Mua ngay" 1 sản phẩm trực tiếp
        if (request.buyNowVariantId() != null) {
            int quantity = (request.buyNowQuantity() != null && request.buyNowQuantity() > 0)
                    ? request.buyNowQuantity()
                    : 1;

            ProductVariant variant = lockAndValidateVariant(request.buyNowVariantId(), quantity);
            variant.setStockQty(variant.getStockQty() - quantity);
            variantRepository.save(variant);

            BigDecimal itemSubtotal = variant.getPrice().multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .variant(variant)
                    .productName(variant.getProduct().getName())
                    .color(variant.getColor())
                    .size(variant.getSize())
                    .skuCode(variant.getSkuCode())
                    .price(variant.getPrice())
                    .quantity(quantity)
                    .subtotal(itemSubtotal)
                    .imageUrl(resolveImageUrl(variant.getProduct(), variant))
                    .build();

            orderItems.add(orderItem);
        } else {
            // Trường hợp 2: Thanh toán từ giỏ hàng
            fromCart = true;
            Cart cart = cartRepository.findWithItemsByUserId(userId)
                    .orElseThrow(() -> new BadRequestException("Giỏ hàng của bạn đang trống, không thể thanh toán"));

            if (cart.getItems() == null || cart.getItems().isEmpty()) {
                throw new BadRequestException("Giỏ hàng của bạn đang trống, không thể thanh toán");
            }

            List<CartItem> itemsToCheckout;
            if (request.selectedCartItemIds() != null && !request.selectedCartItemIds().isEmpty()) {
                itemsToCheckout = cart.getItems().stream()
                        .filter(item -> request.selectedCartItemIds().contains(item.getItemId()))
                        .collect(Collectors.toList());

                if (itemsToCheckout.isEmpty()) {
                    throw new BadRequestException("Không tìm thấy các sản phẩm đã chọn trong giỏ hàng");
                }
            } else {
                itemsToCheckout = new ArrayList<>(cart.getItems());
            }

            for (CartItem cartItem : itemsToCheckout) {
                int quantity = cartItem.getQuantity();
                ProductVariant variant = lockAndValidateVariant(cartItem.getVariant().getVariantId(), quantity);

                // Trừ tồn kho an toàn bằng Pessimistic Lock
                variant.setStockQty(variant.getStockQty() - quantity);
                variantRepository.save(variant);

                BigDecimal itemSubtotal = variant.getPrice().multiply(BigDecimal.valueOf(quantity));
                subtotal = subtotal.add(itemSubtotal);

                OrderItem orderItem = OrderItem.builder()
                        .variant(variant)
                        .productName(variant.getProduct().getName())
                        .color(variant.getColor())
                        .size(variant.getSize())
                        .skuCode(variant.getSkuCode())
                        .price(variant.getPrice())
                        .quantity(quantity)
                        .subtotal(itemSubtotal)
                        .imageUrl(resolveImageUrl(variant.getProduct(), variant))
                        .build();

                orderItems.add(orderItem);
            }
        }

        // Tính phí vận chuyển (Miễn phí nếu đơn >= 500,000 VNĐ)
        BigDecimal shippingFee = (subtotal.compareTo(FREESHIP_THRESHOLD) >= 0)
                ? BigDecimal.ZERO
                : STANDARD_SHIPPING_FEE;

        BigDecimal totalAmount = subtotal.add(shippingFee);

        // Xác định thông tin giao hàng
        ShippingInfo shippingInfo = resolveShippingInfo(userId, user, request);

        // Sinh mã đơn hàng dạng
        String orderCode = generateOrderCode();

        Order order = Order.builder()
                .orderCode(orderCode)
                .user(user)
                .status(OrderStatus.PENDING)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.UNPAID)
                .shippingName(shippingInfo.name())
                .shippingPhone(shippingInfo.phone())
                .shippingAddress(shippingInfo.address())
                .shippingProvince(shippingInfo.province())
                .shippingDistrict(shippingInfo.district())
                .shippingWard(shippingInfo.ward())
                .note(request.note())
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discountAmount(BigDecimal.ZERO)
                .totalAmount(totalAmount)
                .items(new ArrayList<>())
                .build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
            order.getItems().add(item);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Đặt hàng thành công: orderCode={}, userId={}, total={}", orderCode, userId, totalAmount);

        // Nếu đặt hàng từ giỏ hàng -> Xóa các sản phẩm đã thanh toán
        if (fromCart) {
            Cart cart = cartRepository.findWithItemsByUserId(userId).orElse(null);
            if (cart != null) {
                if (request.selectedCartItemIds() != null && !request.selectedCartItemIds().isEmpty()) {
                    List<CartItem> itemsToRemove = cart.getItems().stream()
                            .filter(i -> request.selectedCartItemIds().contains(i.getItemId()))
                            .collect(Collectors.toList());

                    for (CartItem item : itemsToRemove) {
                        cart.getItems().remove(item);
                        cartItemRepository.delete(item);
                    }
                } else {
                    cartItemRepository.deleteByCartCartId(cart.getCartId());
                    cart.getItems().clear();
                }
                cart.setUpdatedAt(OffsetDateTime.now());
                cartRepository.save(cart);
            }
        }

        return toOrderResponse(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO.OrderSummaryResponse> getMyOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toOrderSummaryResponse);
    }

    @Transactional(readOnly = true)
    public OrderDTO.OrderResponse getOrderDetails(UUID currentUserId, UUID orderId, boolean isAdminOrStaff) {
        Order order = orderRepository.findWithItemsByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId));

        if (!isAdminOrStaff && !order.getUser().getUserId().equals(currentUserId)) {
            throw new BadRequestException("Bạn không có quyền xem thông tin đơn hàng này");
        }

        return toOrderResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderDTO.OrderResponse getOrderByOrderCode(UUID currentUserId, String orderCode, boolean isAdminOrStaff) {
        Order order = orderRepository.findWithItemsByOrderCode(orderCode)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn hàng với mã: " + orderCode));

        if (!isAdminOrStaff && !order.getUser().getUserId().equals(currentUserId)) {
            throw new BadRequestException("Bạn không có quyền xem thông tin đơn hàng này");
        }

        return toOrderResponse(order);
    }

    @Transactional
    public OrderDTO.OrderResponse cancelOrder(UUID userId, UUID orderId, String reason) {
        Order order = orderRepository.findWithItemsByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId));

        if (!order.getUser().getUserId().equals(userId)) {
            throw new BadRequestException("Bạn không có quyền hủy đơn hàng này");
        }

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Đơn hàng ở trạng thái '" + order.getStatus() + "' không thể tự hủy. Vui lòng liên hệ CSKH.");
        }

        // Hoàn lại số lượng tồn kho
        for (OrderItem item : order.getItems()) {
            ProductVariant variant = variantRepository.findByIdWithLock(item.getVariant().getVariantId())
                    .orElse(null);
            if (variant != null) {
                variant.setStockQty(variant.getStockQty() + item.getQuantity());
                variantRepository.save(variant);
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            String updatedNote = (order.getNote() != null ? order.getNote() + " | " : "") + "Lý do hủy: " + reason.trim();
            order.setNote(updatedNote);
        }
        order.setUpdatedAt(OffsetDateTime.now());

        Order saved = orderRepository.save(order);
        log.info("Khách hàng đã hủy đơn: orderCode={}, reason={}", saved.getOrderCode(), reason);
        return toOrderResponse(saved);
    }

    @Transactional
    public OrderDTO.OrderResponse updateOrderStatus(UUID orderId, OrderStatus newStatus) {
        Order order = orderRepository.findWithItemsByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Đơn hàng không tồn tại: " + orderId));

        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return toOrderResponse(order);
        }

        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new BadRequestException(String.format(
                    "Không thể chuyển trạng thái đơn hàng từ '%s' sang '%s'. Các trạng thái tiếp theo hợp lệ: %s",
                    currentStatus, newStatus, currentStatus.getValidNextStatuses()
            ));
        }

        // Nếu chuyển sang CANCELLED hoặc RETURNED mà trước đó chưa hoàn kho -> hoàn trả tồn kho
        boolean wasTerminated = (currentStatus == OrderStatus.CANCELLED || currentStatus == OrderStatus.RETURNED);
        boolean isTerminating = (newStatus == OrderStatus.CANCELLED || newStatus == OrderStatus.RETURNED);

        if (isTerminating && !wasTerminated) {
            for (OrderItem item : order.getItems()) {
                ProductVariant variant = variantRepository.findByIdWithLock(item.getVariant().getVariantId())
                        .orElse(null);
                if (variant != null) {
                    variant.setStockQty(variant.getStockQty() + item.getQuantity());
                    variantRepository.save(variant);
                }
            }
        }

        // Nếu đơn hàng COD được giao thành công -> Tự động chuyển paymentStatus sang PAID
        if (newStatus == OrderStatus.DELIVERED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        // Nếu hàng hoàn về kho (RETURNED) và khách đã từng thanh toán trước -> Chuyển sang REFUNDED
        if (newStatus == OrderStatus.RETURNED && order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(OffsetDateTime.now());

        Order saved = orderRepository.save(order);
        log.info("Đã cập nhật trạng thái đơn: orderCode={}, from={}, to={}", saved.getOrderCode(), currentStatus, newStatus);
        return toOrderResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderDTO.OrderSummaryResponse> getAllOrders(OrderStatus status, String search, Pageable pageable) {
        String trimmedSearch = (search != null && !search.isBlank()) ? search.trim() : null;
        return orderRepository.findAllFiltered(status, trimmedSearch, pageable)
                .map(this::toOrderSummaryResponse);
    }

    private ProductVariant lockAndValidateVariant(UUID variantId, int requestedQuantity) {
        ProductVariant variant = variantRepository.findByIdWithLock(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Biến thể sản phẩm không tồn tại: " + variantId));

        Product product = variant.getProduct();
        if (product != null && Boolean.FALSE.equals(product.getIsActive())) {
            throw new BadRequestException("Sản phẩm '" + product.getName() + "' hiện đang ngừng kinh doanh");
        }

        if (variant.getStockQty() < requestedQuantity) {
            throw new BadRequestException(
                    "Sản phẩm '" + product.getName() + " (" + variant.getColor() + ", " + variant.getSize() + ")' không đủ tồn kho (chỉ còn " + variant.getStockQty() + " sản phẩm)"
            );
        }

        return variant;
    }

    private String generateOrderCode() {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        StringBuilder sb = new StringBuilder("ORD-").append(datePrefix).append("-");
        for (int i = 0; i < 6; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private OrderDTO.OrderResponse toOrderResponse(Order order) {
        List<OrderDTO.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(i -> OrderDTO.OrderItemResponse.builder()
                        .itemId(i.getItemId())
                        .variantId(i.getVariant() != null ? i.getVariant().getVariantId() : null)
                        .productName(i.getProductName())
                        .color(i.getColor())
                        .size(i.getSize())
                        .skuCode(i.getSkuCode())
                        .price(i.getPrice())
                        .quantity(i.getQuantity())
                        .subtotal(i.getSubtotal())
                        .imageUrl(i.getImageUrl())
                        .build())
                .collect(Collectors.toList());

        return OrderDTO.OrderResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser() != null ? order.getUser().getUserId() : null)
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .shippingName(order.getShippingName())
                .shippingPhone(order.getShippingPhone())
                .shippingAddress(order.getShippingAddress())
                .shippingProvince(order.getShippingProvince())
                .shippingDistrict(order.getShippingDistrict())
                .shippingWard(order.getShippingWard())
                .note(order.getNote())
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderDTO.OrderSummaryResponse toOrderSummaryResponse(Order order) {
        int totalItems = (order.getItems() != null)
                ? order.getItems().stream().mapToInt(OrderItem::getQuantity).sum()
                : 0;

        return OrderDTO.OrderSummaryResponse.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .shippingName(order.getShippingName())
                .shippingPhone(order.getShippingPhone())
                .totalAmount(order.getTotalAmount())
                .totalItems(totalItems)
                .createdAt(order.getCreatedAt())
                .build();
    }

    private String resolveImageUrl(Product product, ProductVariant variant) {
        if (product == null || product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }

        for (ProductImage img : product.getImages()) {
            if (img.getVariant() != null && img.getVariant().getVariantId().equals(variant.getVariantId())) {
                return img.getUrl();
            }
        }

        for (ProductImage img : product.getImages()) {
            if (Boolean.TRUE.equals(img.getIsPrimary())) {
                return img.getUrl();
            }
        }

        return product.getImages().get(0).getUrl();
    }

    private ShippingInfo resolveShippingInfo(UUID userId, User user, OrderDTO.CheckoutRequest request) {
        // Tùy chọn 1: Người dùng truyền ID địa chỉ trong sổ địa chỉ
        if (request.addressId() != null) {
            UserAddress addr = userAddressRepository.findByAddressIdAndUserUserId(request.addressId(), userId)
                    .orElseThrow(() -> new BadRequestException("Không tìm thấy địa chỉ nhận hàng đã chọn trong sổ địa chỉ"));
            return new ShippingInfo(
                    addr.getRecipientName(),
                    addr.getPhoneNumber(),
                    addr.getStreetAddress(),
                    addr.getProvince(),
                    addr.getDistrict(),
                    addr.getWard()
            );
        }

        // Tùy chọn 2: Người dùng nhập địa chỉ trực tiếp
        if (request.shippingAddress() != null && !request.shippingAddress().isBlank()) {
            String name = (request.shippingName() != null && !request.shippingName().isBlank())
                    ? request.shippingName().trim()
                    : (user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : "Khách hàng");

            String phone = (request.shippingPhone() != null && !request.shippingPhone().isBlank())
                    ? request.shippingPhone().trim()
                    : user.getPhone();

            if (phone == null || phone.isBlank()) {
                throw new BadRequestException("Số điện thoại nhận hàng không được để trống");
            }

            return new ShippingInfo(
                    name,
                    phone,
                    request.shippingAddress().trim(),
                    request.shippingProvince(),
                    request.shippingDistrict(),
                    request.shippingWard()
            );
        }

        // Tùy chọn 3: Tự động tìm địa chỉ mặc định trong sổ địa chỉ (Chuẩn Shopee)
        UserAddress defaultAddr = userAddressRepository.findByUserUserIdAndIsDefaultTrue(userId)
                .or(() -> userAddressRepository.findByUserUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream().findFirst())
                .orElseThrow(() -> new BadRequestException("Vui lòng cung cấp địa chỉ nhận hàng hoặc thiết lập địa chỉ trong sổ địa chỉ"));

        return new ShippingInfo(
                defaultAddr.getRecipientName(),
                defaultAddr.getPhoneNumber(),
                defaultAddr.getStreetAddress(),
                defaultAddr.getProvince(),
                defaultAddr.getDistrict(),
                defaultAddr.getWard()
        );
    }

    private record ShippingInfo(
            String name,
            String phone,
            String address,
            String province,
            String district,
            String ward
    ) {}
}
