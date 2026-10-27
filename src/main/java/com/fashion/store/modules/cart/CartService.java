package com.fashion.store.modules.cart;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.product.Product;
import com.fashion.store.modules.product.ProductImage;
import com.fashion.store.modules.product.ProductVariant;
import com.fashion.store.modules.product.ProductVariantRepository;
import com.fashion.store.modules.user.User;
import com.fashion.store.modules.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;

    @Transactional
    public Cart getOrCreateCartEntity(UUID userId) {
        return cartRepository.findWithItemsByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại: " + userId));

                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    @Transactional(readOnly = true)
    public CartDTO.CartResponse getCart(UUID userId) {
        Cart cart = cartRepository.findWithItemsByUserId(userId)
                .orElseGet(() -> {
                    // Nếu chưa có trong DB, trả về giỏ hàng rỗng
                    return Cart.builder().items(new ArrayList<>()).build();
                });

        return toCartResponse(cart);
    }

    @Transactional
    public CartDTO.CartResponse addItemToCart(UUID userId, CartDTO.AddToCartRequest request) {
        ProductVariant variant = variantRepository.findById(request.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể sản phẩm: " + request.variantId()));

        Product product = variant.getProduct();
        if (product != null && Boolean.FALSE.equals(product.getIsActive())) {
            throw new BadRequestException("Sản phẩm '" + product.getName() + "' hiện đang ngừng kinh doanh");
        }

        if (variant.getStockQty() <= 0) {
            throw new BadRequestException("Biến thể này hiện đã hết hàng");
        }

        Cart cart = getOrCreateCartEntity(userId);

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getVariant().getVariantId().equals(variant.getVariantId()))
                .findFirst()
                .orElse(null);

        int targetQuantity = (existingItem != null ? existingItem.getQuantity() : 0) + request.quantity();

        if (targetQuantity > variant.getStockQty()) {
            throw new BadRequestException(
                    "Số lượng yêu cầu (" + targetQuantity + ") vượt quá tồn kho hiện có (" + variant.getStockQty() + ")"
            );
        }

        if (existingItem != null) {
            existingItem.setQuantity(targetQuantity);
            existingItem.setUpdatedAt(OffsetDateTime.now());
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.quantity())
                    .build();
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        cart.setUpdatedAt(OffsetDateTime.now());
        cartRepository.save(cart);

        return toCartResponse(cart);
    }

    @Transactional
    public CartDTO.CartResponse updateItemQuantity(UUID userId, UUID itemId, int quantity) {
        Cart cart = getOrCreateCartEntity(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại trong giỏ hàng: " + itemId));

        if (quantity <= 0) {
            cart.getItems().remove(item);
            cartItemRepository.delete(item);
        } else {
            ProductVariant variant = item.getVariant();
            if (quantity > variant.getStockQty()) {
                throw new BadRequestException(
                        "Số lượng yêu cầu (" + quantity + ") vượt quá tồn kho hiện có (" + variant.getStockQty() + ")"
                );
            }
            item.setQuantity(quantity);
            item.setUpdatedAt(OffsetDateTime.now());
            cartItemRepository.save(item);
        }

        cart.setUpdatedAt(OffsetDateTime.now());
        cartRepository.save(cart);

        return toCartResponse(cart);
    }

    @Transactional
    public CartDTO.CartResponse removeItemFromCart(UUID userId, UUID itemId) {
        Cart cart = getOrCreateCartEntity(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getItemId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Sản phẩm không tồn tại trong giỏ hàng: " + itemId));

        cart.getItems().remove(item);
        cartItemRepository.delete(item);

        cart.setUpdatedAt(OffsetDateTime.now());
        cartRepository.save(cart);

        return toCartResponse(cart);
    }

    @Transactional
    public CartDTO.CartResponse clearCart(UUID userId) {
        Cart cart = getOrCreateCartEntity(userId);
        cartItemRepository.deleteByCartCartId(cart.getCartId());
        cart.getItems().clear();
        cart.setUpdatedAt(OffsetDateTime.now());
        cartRepository.save(cart);

        return toCartResponse(cart);
    }

    private CartDTO.CartResponse toCartResponse(Cart cart) {
        List<CartDTO.CartItemResponse> itemResponses = new ArrayList<>();
        int totalItems = 0;
        BigDecimal totalPrice = BigDecimal.ZERO;

        if (cart.getItems() != null) {
            for (CartItem item : cart.getItems()) {
                ProductVariant v = item.getVariant();
                Product p = v.getProduct();

                BigDecimal subtotal = v.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
                totalPrice = totalPrice.add(subtotal);
                totalItems += item.getQuantity();

                String imageUrl = resolveImageUrl(p, v);
                boolean inStock = v.getStockQty() >= item.getQuantity();

                itemResponses.add(CartDTO.CartItemResponse.builder()
                        .itemId(item.getItemId())
                        .variantId(v.getVariantId())
                        .productId(p != null ? p.getProductId() : null)
                        .productName(p != null ? p.getName() : null)
                        .productSlug(p != null ? p.getSlug() : null)
                        .color(v.getColor())
                        .colorCode(v.getColorCode())
                        .size(v.getSize())
                        .skuCode(v.getSkuCode())
                        .price(v.getPrice())
                        .comparePrice(v.getComparePrice())
                        .quantity(item.getQuantity())
                        .stockQty(v.getStockQty())
                        .subtotal(subtotal)
                        .imageUrl(imageUrl)
                        .inStock(inStock)
                        .build());
            }
        }

        return CartDTO.CartResponse.builder()
                .cartId(cart.getCartId())
                .items(itemResponses)
                .totalItems(totalItems)
                .totalPrice(totalPrice)
                .build();
    }

    private String resolveImageUrl(Product product, ProductVariant variant) {
        if (product == null || product.getImages() == null || product.getImages().isEmpty()) {
            return null;
        }

        // Ưu tiên 1: Ảnh gán riêng cho biến thể này
        for (ProductImage img : product.getImages()) {
            if (img.getVariant() != null && img.getVariant().getVariantId().equals(variant.getVariantId())) {
                return img.getUrl();
            }
        }

        // Ưu tiên 2: Ảnh chính (Primary) của sản phẩm
        for (ProductImage img : product.getImages()) {
            if (Boolean.TRUE.equals(img.getIsPrimary())) {
                return img.getUrl();
            }
        }

        // Ưu tiên 3: Ảnh đầu tiên bất kỳ
        return product.getImages().getFirst().getUrl();
    }
}
