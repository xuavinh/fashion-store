package com.fashion.store.modules.product;

import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.brand.Brand;
import com.fashion.store.modules.brand.BrandRepository;
import com.fashion.store.modules.category.Category;
import com.fashion.store.modules.category.CategoryRepository;
import com.fashion.store.modules.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductService productService;

    private UUID productId;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();

        ProductVariant variant1 = ProductVariant.builder()
                .variantId(UUID.randomUUID())
                .color("Trắng")
                .size("M")
                .skuCode("TSHIRT-WHT-M")
                .price(BigDecimal.valueOf(250000))
                .stockQty(50)
                .build();

        sampleProduct = Product.builder()
                .productId(productId)
                .name("Áo Thun Cotton Heavyweight")
                .slug("ao-thun-cotton-heavyweight")
                .description("Chất vải cotton 100% thoáng mát")
                .gender(Gender.UNISEX)
                .isActive(true)
                .variants(new ArrayList<>(List.of(variant1)))
                .images(new ArrayList<>())
                .tags(new HashSet<>(Set.of("streetwear", "cotton")))
                .build();

        variant1.setProduct(sampleProduct);
    }

    @Test
    @DisplayName("Tạo sản phẩm mới thành công kèm SKU và ảnh")
    void createProduct_Success() {
        ProductDTO.CreateVariantRequest vReq = new ProductDTO.CreateVariantRequest(
                "Trắng", "#FFFFFF", "M", "TSHIRT-WHT-M", BigDecimal.valueOf(250000), null, 50, 250
        );

        ProductDTO.CreateProductRequest request = new ProductDTO.CreateProductRequest(
                "Áo Thun Cotton Heavyweight",
                null, // slug tự sinh
                "Mô tả sản phẩm",
                null,
                null,
                Gender.UNISEX,
                "Cotton 100%",
                "Giặt máy",
                Set.of("streetwear"),
                List.of(vReq),
                null
        );

        when(productRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(variantRepository.existsBySkuCode("TSHIRT-WHT-M")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setProductId(productId);
            return p;
        });

        ProductDTO.ProductResponse response = productService.createProduct(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Áo Thun Cotton Heavyweight");
        assertThat(response.slug()).isEqualTo("ao-thun-cotton-heavyweight");
        assertThat(response.variants()).hasSize(1);
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("Tạo sản phẩm thất bại khi mã SKU bị trùng")
    void createProduct_DuplicateSku_ThrowsConflictException() {
        ProductDTO.CreateVariantRequest vReq = new ProductDTO.CreateVariantRequest(
                "Trắng", "#FFFFFF", "M", "DUPLICATE-SKU", BigDecimal.valueOf(250000), null, 50, 250
        );

        ProductDTO.CreateProductRequest request = new ProductDTO.CreateProductRequest(
                "Áo Thun Mới", null, "Mô tả", null, null, Gender.UNISEX, "Cotton", "Giặt", null, List.of(vReq), null
        );

        when(productRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(variantRepository.existsBySkuCode("DUPLICATE-SKU")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DUPLICATE-SKU");
    }

    @Test
    @DisplayName("Cập nhật sản phẩm: đồng bộ SKU hiện có và thêm SKU mới")
    void updateProduct_Success() {
        UUID existingVariantId = sampleProduct.getVariants().get(0).getVariantId();

        ProductDTO.UpdateVariantRequest updateExistingVariant = new ProductDTO.UpdateVariantRequest(
                existingVariantId, "Trắng Sữa", "#FFFFFA", "M", "TSHIRT-WHT-M", BigDecimal.valueOf(260000), null, 60, 250
        );

        ProductDTO.UpdateVariantRequest addNewVariant = new ProductDTO.UpdateVariantRequest(
                null, "Đen", "#000000", "L", "TSHIRT-BLK-L", BigDecimal.valueOf(270000), null, 40, 260
        );

        ProductDTO.UpdateProductRequest updateReq = new ProductDTO.UpdateProductRequest(
                "Áo Thun Cotton Heavyweight (V2)",
                "ao-thun-cotton-heavyweight-v2",
                "Mô tả mới",
                null,
                null,
                Gender.UNISEX,
                "100% Cotton",
                "Bảo quản mới",
                true,
                Set.of("cotton", "boxy"),
                List.of(updateExistingVariant, addNewVariant)
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(productRepository.findBySlug("ao-thun-cotton-heavyweight-v2")).thenReturn(Optional.empty());
        when(variantRepository.existsBySkuCode("TSHIRT-BLK-L")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenReturn(sampleProduct);

        ProductDTO.ProductResponse response = productService.updateProduct(productId, updateReq);

        assertThat(response).isNotNull();
        assertThat(sampleProduct.getName()).isEqualTo("Áo Thun Cotton Heavyweight (V2)");
        assertThat(sampleProduct.getVariants()).hasSize(2);
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("Lấy chi tiết sản phẩm theo ID thành công")
    void getProductById_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));

        ProductDTO.ProductResponse response = productService.getProductById(productId);

        assertThat(response).isNotNull();
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.name()).isEqualTo(sampleProduct.getName());
    }

    @Test
    @DisplayName("Lấy chi tiết sản phẩm thất bại khi không tìm thấy ID")
    void getProductById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(productRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    @DisplayName("Bật / tắt trạng thái kinh doanh của sản phẩm")
    void updateStatus_Success() {
        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));

        productService.updateStatus(productId, false);

        assertThat(sampleProduct.getIsActive()).isFalse();
        verify(productRepository).save(sampleProduct);
    }

    @Test
    @DisplayName("Xóa sản phẩm: Xóa trong DB và gọi StorageService xóa các ảnh S3/R2")
    void deleteProduct_Success() {
        ProductImage img1 = ProductImage.builder().url("https://s3.com/p1.jpg").build();
        ProductImage img2 = ProductImage.builder().url("https://s3.com/p2.jpg").build();
        sampleProduct.getImages().addAll(List.of(img1, img2));

        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));

        productService.deleteProduct(productId);

        verify(productRepository).delete(sampleProduct);
        verify(storageService).deleteFile("https://s3.com/p1.jpg");
        verify(storageService).deleteFile("https://s3.com/p2.jpg");
    }

    @Test
    @DisplayName("Tìm kiếm sản phẩm khi có từ khóa search (gọi searchFullTextWithRelevance)")
    void searchProducts_WithKeyword_CallsRelevanceQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> productPage = new PageImpl<>(List.of(sampleProduct));

        when(productRepository.searchFullTextWithRelevance(
                eq("cotton"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(productPage);

        Page<ProductDTO.ProductSummaryResponse> result = productService.searchProducts(
                "cotton", null, null, null, null, null, null, pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).searchFullTextWithRelevance(
                eq("cotton"), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        );
        verify(productRepository, never()).findAllFiltered(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Lọc sản phẩm khi KHÔNG có từ khóa search (gọi findAllFiltered)")
    void searchProducts_WithoutKeyword_CallsFilteredQuery() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> productPage = new PageImpl<>(List.of(sampleProduct));

        when(productRepository.findAllFiltered(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        )).thenReturn(productPage);

        Page<ProductDTO.ProductSummaryResponse> result = productService.searchProducts(
                null, null, null, null, null, null, null, pageable
        );

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(productRepository).findAllFiltered(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any(Pageable.class)
        );
        verify(productRepository, never()).searchFullTextWithRelevance(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
