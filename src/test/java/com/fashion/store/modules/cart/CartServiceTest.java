package com.fashion.store.modules.cart;

import com.fashion.store.common.exception.BadRequestException;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;

    private UUID userId;
    private User sampleUser;
    private Product sampleProduct;
    private ProductVariant sampleVariant;
    private Cart sampleCart;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, variantRepository, userRepository);

        userId = UUID.randomUUID();
        sampleUser = User.builder().userId(userId).email("user@fashion.vn").build();

        sampleProduct = Product.builder()
                .productId(UUID.randomUUID())
                .name("Áo Sơ Mi Oxford")
                .slug("ao-so-mi-oxford")
                .isActive(true)
                .images(new ArrayList<>())
                .build();

        sampleVariant = ProductVariant.builder()
                .variantId(UUID.randomUUID())
                .product(sampleProduct)
                .color("Trắng")
                .size("L")
                .skuCode("OXFORD-WHT-L")
                .price(BigDecimal.valueOf(350000))
                .stockQty(10)
                .build();

        sampleCart = Cart.builder()
                .cartId(UUID.randomUUID())
                .user(sampleUser)
                .items(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Lấy giỏ hàng khi người dùng chưa có sản phẩm nào -> Trả về giỏ rỗng totalItems = 0")
    void getCart_Empty() {
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.empty());

        CartDTO.CartResponse response = cartService.getCart(userId);

        assertThat(response).isNotNull();
        assertThat(response.totalItems()).isEqualTo(0);
        assertThat(response.totalPrice()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.items()).isEmpty();
    }

    @Test
    @DisplayName("Thêm sản phẩm mới vào giỏ hàng thành công")
    void addItemToCart_SuccessNewItem() {
        CartDTO.AddToCartRequest request = new CartDTO.AddToCartRequest(sampleVariant.getVariantId(), 2);

        when(variantRepository.findById(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartDTO.CartResponse response = cartService.addItemToCart(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.totalItems()).isEqualTo(2);
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(700000));
        assertThat(response.items()).hasSize(1);
        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("Thêm sản phẩm đã có trong giỏ -> Tự động cộng dồn số lượng")
    void addItemToCart_IncrementQuantityWhenAlreadyInCart() {
        CartItem existingItem = CartItem.builder()
                .itemId(UUID.randomUUID())
                .cart(sampleCart)
                .variant(sampleVariant)
                .quantity(1)
                .build();
        sampleCart.getItems().add(existingItem);

        CartDTO.AddToCartRequest request = new CartDTO.AddToCartRequest(sampleVariant.getVariantId(), 2);

        when(variantRepository.findById(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartDTO.CartResponse response = cartService.addItemToCart(userId, request);

        assertThat(response).isNotNull();
        assertThat(response.totalItems()).isEqualTo(3); // 1 + 2 = 3
        assertThat(response.totalPrice()).isEqualByComparingTo(BigDecimal.valueOf(1050000));
    }

    @Test
    @DisplayName("Thêm sản phẩm thất bại khi số lượng yêu cầu vượt quá tồn kho -> Ném BadRequestException")
    void addItemToCart_StockExceeded_ThrowsBadRequestException() {
        sampleVariant.setStockQty(3);
        CartDTO.AddToCartRequest request = new CartDTO.AddToCartRequest(sampleVariant.getVariantId(), 5);

        when(variantRepository.findById(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));

        assertThatThrownBy(() -> cartService.addItemToCart(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("vượt quá tồn kho");
    }

    @Test
    @DisplayName("Thêm sản phẩm thất bại khi sản phẩm đang ngừng kinh doanh -> Ném BadRequestException")
    void addItemToCart_ProductInactive_ThrowsBadRequestException() {
        sampleProduct.setIsActive(false);
        CartDTO.AddToCartRequest request = new CartDTO.AddToCartRequest(sampleVariant.getVariantId(), 1);

        when(variantRepository.findById(sampleVariant.getVariantId())).thenReturn(Optional.of(sampleVariant));

        assertThatThrownBy(() -> cartService.addItemToCart(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ngừng kinh doanh");
    }

    @Test
    @DisplayName("Cập nhật số lượng sản phẩm trong giỏ thành công")
    void updateItemQuantity_Success() {
        UUID itemId = UUID.randomUUID();
        CartItem item = CartItem.builder()
                .itemId(itemId)
                .cart(sampleCart)
                .variant(sampleVariant)
                .quantity(1)
                .build();
        sampleCart.getItems().add(item);

        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));

        CartDTO.CartResponse response = cartService.updateItemQuantity(userId, itemId, 4);

        assertThat(response).isNotNull();
        assertThat(response.totalItems()).isEqualTo(4);
        assertThat(item.getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("Cập nhật số lượng về 0 -> Tự động xóa sản phẩm khỏi giỏ hàng")
    void updateItemQuantity_Zero_DeletesItem() {
        UUID itemId = UUID.randomUUID();
        CartItem item = CartItem.builder()
                .itemId(itemId)
                .cart(sampleCart)
                .variant(sampleVariant)
                .quantity(2)
                .build();
        sampleCart.getItems().add(item);

        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));

        CartDTO.CartResponse response = cartService.updateItemQuantity(userId, itemId, 0);

        assertThat(response).isNotNull();
        assertThat(response.totalItems()).isEqualTo(0);
        assertThat(sampleCart.getItems()).isEmpty();
        verify(cartItemRepository).delete(item);
    }

    @Test
    @DisplayName("Xóa sản phẩm khỏi giỏ hàng thành công")
    void removeItemFromCart_Success() {
        UUID itemId = UUID.randomUUID();
        CartItem item = CartItem.builder()
                .itemId(itemId)
                .cart(sampleCart)
                .variant(sampleVariant)
                .quantity(2)
                .build();
        sampleCart.getItems().add(item);

        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));

        CartDTO.CartResponse response = cartService.removeItemFromCart(userId, itemId);

        assertThat(response).isNotNull();
        assertThat(sampleCart.getItems()).isEmpty();
        verify(cartItemRepository).delete(item);
    }

    @Test
    @DisplayName("Làm trống toàn bộ giỏ hàng thành công")
    void clearCart_Success() {
        when(cartRepository.findWithItemsByUserId(userId)).thenReturn(Optional.of(sampleCart));

        CartDTO.CartResponse response = cartService.clearCart(userId);

        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
        verify(cartItemRepository).deleteByCartCartId(sampleCart.getCartId());
    }
}
