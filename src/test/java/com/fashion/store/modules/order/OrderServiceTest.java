package com.fashion.store.modules.order;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.cart.Cart;
import com.fashion.store.modules.cart.CartItem;
import com.fashion.store.modules.cart.CartItemRepository;
import com.fashion.store.modules.cart.CartRepository;
import com.fashion.store.modules.product.Product;
import com.fashion.store.modules.product.ProductVariant;
import com.fashion.store.modules.product.ProductVariantRepository;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.fashion.store.modules.user.address.UserAddressRepository userAddressRepository;

    private OrderService orderService;

    private UUID userId;
    private User sampleUser;
    private Product sampleProduct;
    private ProductVariant sampleVariant;
    private Cart sampleCart;
    private CartItem sampleCartItem;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository,
                orderItemRepository,
                cartRepository,
                cartItemRepository,
                variantRepository,
                userRepository,
                userAddressRepository
        );

        userId = UUID.randomUUID();
        sampleUser = User.builder().userId(userId).email("customer@fashion.vn").fullName("Nguyen Van A").build();

        sampleProduct = Product.builder()
                .productId(UUID.randomUUID())
                .name("Áo Polo Cotton")
                .isActive(true)
                .images(new ArrayList<>())
                .build();

        sampleVariant = ProductVariant.builder()
                .variantId(UUID.randomUUID())
                .product(sampleProduct)
                .color("Đen")
                .size("L")
                .skuCode("POLO-BLK-L")
                .price(BigDecimal.valueOf(300000))
                .stockQty(20)
                .build();

        sampleCart = Cart.builder()
                .cartId(UUID.randomUUID())
                .user(sampleUser)
                .items(new ArrayList<>())
                .build();

        sampleCartItem = CartItem.builder()
                .itemId(UUID.randomUUID())
                .cart(sampleCart)
                .variant(sampleVariant)
                .quantity(2)
                .build();
        sampleCart.getItems().add(sampleCartItem);
    }

    @Test
    @DisplayName("Đặt hàng COD từ giỏ hàng thành công -> Trừ kho và làm trống giỏ hàng")
    void checkout_FromCart_Success() {
        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                null, "Nguyen Van A", "0912345678", "123 Le Loi, Q1", "TP.HCM", "Quận 1", "Bến Nghé",
                "Giao giờ hành chính", PaymentMethod.COD, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            return o;
        });

        OrderDTO.OrderResponse response = orderService.checkout(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.orderCode()).startsWith("ORD-");
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.COD);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(response.items()).hasSize(1);
        assertThat(response.subtotal()).isEqualByComparingTo(BigDecimal.valueOf(600000));
        // Subtotal 600k >= 500k -> Miễn phí ship
        assertThat(response.shippingFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(600000));

        // Kiểm tra trừ kho: 20 - 2 = 18
        assertThat(sampleVariant.getStockQty()).isEqualTo(18);
        verify(variantRepository).save(sampleVariant);
        verify(cartItemRepository).deleteByCartCartId(sampleCart.getCartId());
    }

    @Test
    @DisplayName("Đặt hàng bằng addressId từ sổ địa chỉ thành công")
    void checkout_WithAddressId_Success() {
        UUID addressId = UUID.randomUUID();
        com.fashion.store.modules.user.address.UserAddress savedAddress = com.fashion.store.modules.user.address.UserAddress.builder()
                .addressId(addressId)
                .user(sampleUser)
                .recipientName("Tran Thi B")
                .phoneNumber("0987654321")
                .streetAddress("456 Nguyen Trai")
                .province("Ha Noi")
                .district("Dong Da")
                .ward("Lang Thuong")
                .isDefault(true)
                .build();

        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                addressId, null, null, null, null, null, null,
                "Giao nhanh", PaymentMethod.COD, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(userAddressRepository.findByAddressIdAndUserUserId(addressId, userId)).thenReturn(Optional.of(savedAddress));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            return o;
        });

        OrderDTO.OrderResponse response = orderService.checkout(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.shippingName()).isEqualTo("Tran Thi B");
        assertThat(response.shippingPhone()).isEqualTo("0987654321");
        assertThat(response.shippingAddress()).isEqualTo("456 Nguyen Trai");
    }

    @Test
    @DisplayName("Đặt hàng khi giỏ hàng rỗng -> Ném BadRequestException")
    void checkout_EmptyCart_ThrowsBadRequestException() {
        sampleCart.getItems().clear();
        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                null, "Nguyen Van A", "0912345678", "123 Le Loi", null, null, null, null, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));

        assertThatThrownBy(() -> orderService.checkout(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Giỏ hàng của bạn đang trống");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đặt hàng thất bại khi số lượng yêu cầu vượt quá tồn kho -> Ném BadRequestException")
    void checkout_StockExceeded_ThrowsBadRequestException() {
        sampleVariant.setStockQty(1); // Chỉ còn 1 sản phẩm mà giỏ đòi 2
        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                null, "Nguyen Van A", "0912345678", "123 Le Loi", null, null, null, null, null, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));

        assertThatThrownBy(() -> orderService.checkout(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không đủ tồn kho");
    }

    @Test
    @DisplayName("Khách hàng hủy đơn hàng PENDING thành công -> Hoàn trả tồn kho và đổi status CANCELLED")
    void cancelOrder_Success_RestoresStock() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.builder()
                .variant(sampleVariant)
                .quantity(3)
                .price(BigDecimal.valueOf(300000))
                .subtotal(BigDecimal.valueOf(900000))
                .build();

        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-20261027-123456")
                .user(sampleUser)
                .status(OrderStatus.PENDING)
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setOrder(order);

        sampleVariant.setStockQty(10);

        when(orderRepository.findWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO.OrderResponse response = orderService.cancelOrder(userId, orderId, "Đổi ý không mua nữa");

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        // Tồn kho được hoàn lại: 10 + 3 = 13
        assertThat(sampleVariant.getStockQty()).isEqualTo(13);
        verify(variantRepository).save(sampleVariant);
    }

    @Test
    @DisplayName("Hủy đơn hàng thất bại khi đơn đang giao -> Ném BadRequestException")
    void cancelOrder_Shipping_ThrowsBadRequestException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .user(sampleUser)
                .status(OrderStatus.SHIPPING)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(userId, orderId, "Hủy gấp"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không thể tự hủy");
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng theo orderCode thành công")
    void getOrderByOrderCode_Success() {
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .orderCode("ORD-20261027-TEST01")
                .user(sampleUser)
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findWithItemsByOrderCode("ORD-20261027-TEST01")).thenReturn(Optional.of(order));

        OrderDTO.OrderResponse response = orderService.getOrderByOrderCode(userId, "ORD-20261027-TEST01", false);

        assertThat(response).isNotNull();
        assertThat(response.orderCode()).isEqualTo("ORD-20261027-TEST01");
    }

    @Test
    @DisplayName("Lấy chi tiết đơn hàng theo orderCode của người khác khi không phải admin -> Ném BadRequestException")
    void getOrderByOrderCode_Unauthorized_ThrowsBadRequest() {
        UUID otherUserId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(UUID.randomUUID())
                .orderCode("ORD-20261027-TEST02")
                .user(sampleUser) // thuộc về sampleUser
                .status(OrderStatus.CONFIRMED)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findWithItemsByOrderCode("ORD-20261027-TEST02")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderByOrderCode(otherUserId, "ORD-20261027-TEST02", false))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Bạn không có quyền xem thông tin đơn hàng này");
    }

    @Test
    @DisplayName("Admin cập nhật trạng thái đơn COD sang DELIVERED -> Tự động chuyển paymentStatus thành PAID")
    void updateOrderStatus_Delivered_AutoSetsPaidForCod() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-20261027-999999")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO.OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("Thanh toán chọn lọc theo sản phẩm cụ thể -> Chỉ thanh toán món đã chọn, giữ lại món chưa chọn")
    void checkout_SelectedCartItems_ShopeeStyle_Success() {
        // Tạo thêm món thứ 2 trong giỏ
        ProductVariant variant2 = ProductVariant.builder()
                .variantId(UUID.randomUUID())
                .product(sampleProduct)
                .color("Trắng")
                .size("M")
                .skuCode("POLO-WHT-M")
                .price(BigDecimal.valueOf(250000))
                .stockQty(15)
                .build();

        CartItem item2 = CartItem.builder()
                .itemId(UUID.randomUUID())
                .cart(sampleCart)
                .variant(variant2)
                .quantity(1)
                .build();
        sampleCart.getItems().add(item2);

        // Khách chỉ tick chọn sampleCartItem, KHÔNG chọn item2
        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                null, "Nguyen Van A", "0912345678", "123 Le Loi", "TP.HCM", "Q1", "Ben Nghe",
                "Chi giao mon da chon", PaymentMethod.COD, null, null,
                List.of(sampleCartItem.getItemId())
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setOrderId(UUID.randomUUID());
            return o;
        });

        OrderDTO.OrderResponse response = orderService.checkout(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).skuCode()).isEqualTo("POLO-BLK-L");

        // Đảm bảo item2 vẫn còn lại trong giỏ hàng (không bị xóa)
        assertThat(sampleCart.getItems()).contains(item2);
        assertThat(sampleCart.getItems()).doesNotContain(sampleCartItem);
        verify(cartItemRepository).delete(sampleCartItem);
        verify(cartItemRepository, never()).delete(item2);
    }

    @Test
    @DisplayName("Khách có từ 3 đơn hoàn hàng COD -> Khóa phương thức COD và ném BadRequestException")
    void checkout_CodBlockedWhenReturnedCountExceedsLimit() {
        OrderDTO.CheckoutRequest request = new OrderDTO.CheckoutRequest(
                null, "Nguyen Van A", "0912345678", "123 Le Loi", null, null, null, null,
                PaymentMethod.COD, null, null, null
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(sampleUser));
        when(orderRepository.countByUserUserIdAndStatusAndPaymentMethod(userId, OrderStatus.RETURNED, PaymentMethod.COD))
                .thenReturn(3L);

        assertThatThrownBy(() -> orderService.checkout(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Phương thức COD đã tạm thời bị khóa");
    }

    @Test
    @DisplayName("Cập nhật trạng thái RETURNED -> Tự động hoàn lại tồn kho và cập nhật REFUNDED nếu đã thanh toán")
    void updateOrderStatus_Returned_RestoresInventoryAndRefunds() {
        UUID orderId = UUID.randomUUID();
        OrderItem item = OrderItem.builder()
                .variant(sampleVariant)
                .quantity(2)
                .price(BigDecimal.valueOf(300000))
                .subtotal(BigDecimal.valueOf(600000))
                .build();

        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-20261027-888888")
                .status(OrderStatus.SHIPPING)
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(PaymentStatus.PAID)
                .items(new ArrayList<>(List.of(item)))
                .build();
        item.setOrder(order);

        sampleVariant.setStockQty(10);

        when(orderRepository.findWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));
        when(variantRepository.findByIdWithLock(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDTO.OrderResponse response = orderService.updateOrderStatus(orderId, OrderStatus.RETURNED);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(OrderStatus.RETURNED);
        assertThat(response.paymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // Tồn kho được hoàn lại: 10 + 2 = 12
        assertThat(sampleVariant.getStockQty()).isEqualTo(12);
        verify(variantRepository).save(sampleVariant);
    }

    @Test
    @DisplayName("Chuyển trạng thái không hợp lệ -> Ném BadRequestException")
    void updateOrderStatus_InvalidTransition_ThrowsBadRequestException() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode("ORD-20261027-111111")
                .status(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(PaymentStatus.UNPAID)
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findWithItemsByOrderId(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Không thể chuyển trạng thái đơn hàng từ 'PENDING' sang 'DELIVERED'");

        verify(orderRepository, never()).save(any());
    }
}
