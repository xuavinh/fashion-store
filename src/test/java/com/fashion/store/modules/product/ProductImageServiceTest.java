package com.fashion.store.modules.product;

import com.fashion.store.modules.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private ProductImageService imageService;

    private UUID productId;
    private Product sampleProduct;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        sampleProduct = Product.builder()
                .productId(productId)
                .name("Áo Thun")
                .images(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Thêm ảnh đầu tiên vào sản phẩm -> Tự động nhận isPrimary = true và sortOrder = 0")
    void addImage_FirstImage_BecomesPrimaryAndSortOrderZero() {
        ProductImageDTO.AddImageRequest request = new ProductImageDTO.AddImageRequest(
                "https://s3.com/first.jpg", null, null, null
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(imageRepository.countByProductProductId(productId)).thenReturn(0L);
        when(imageRepository.findMaxSortOrderByProductId(productId)).thenReturn(-1);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageDTO.ImageResponse response = imageService.addImage(productId, request);

        assertThat(response).isNotNull();
        assertThat(response.isPrimary()).isTrue();
        assertThat(response.sortOrder()).isEqualTo(0);
        verify(imageRepository, never()).resetPrimaryImageForProduct(any());
        verify(imageRepository).save(any(ProductImage.class));
    }

    @Test
    @DisplayName("Thêm ảnh phụ khi Frontend KHÔNG gửi sortOrder -> Backend tự động tăng sortOrder = MAX + 1")
    void addImage_BackendAutoSortOrder_AssignsMaxPlusOne() {
        ProductImageDTO.AddImageRequest request = new ProductImageDTO.AddImageRequest(
                "https://s3.com/second.jpg", null, null, false
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(imageRepository.countByProductProductId(productId)).thenReturn(1L);
        when(imageRepository.findMaxSortOrderByProductId(productId)).thenReturn(0);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageDTO.ImageResponse response = imageService.addImage(productId, request);

        assertThat(response).isNotNull();
        assertThat(response.isPrimary()).isFalse();
        assertThat(response.sortOrder()).isEqualTo(1); // 0 + 1 = 1
        verify(imageRepository, never()).shiftSortOrderFrom(any(), anyInt());
    }

    @Test
    @DisplayName("Thêm ảnh phụ khi Frontend CHỦ ĐỘNG gửi sortOrder = 1 -> Backend dời các ảnh từ vị trí 1 lùi lại 1 bậc")
    void addImage_FrontendSendsSortOrder_ShiftsExistingImages() {
        ProductImageDTO.AddImageRequest request = new ProductImageDTO.AddImageRequest(
                "https://s3.com/inserted.jpg", null, 1, false
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(sampleProduct));
        when(imageRepository.countByProductProductId(productId)).thenReturn(2L);
        when(imageRepository.findMaxSortOrderByProductId(productId)).thenReturn(1);
        when(imageRepository.save(any(ProductImage.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductImageDTO.ImageResponse response = imageService.addImage(productId, request);

        assertThat(response).isNotNull();
        assertThat(response.sortOrder()).isEqualTo(1);
        verify(imageRepository).shiftSortOrderFrom(productId, 1);
    }

    @Test
    @DisplayName("Đặt một ảnh làm Primary (setPrimaryImage) -> Ảnh đó nhận sortOrder = 0 và isPrimary = true, ảnh cũ nhận 1, 2...")
    void setPrimaryImage_Success() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();

        ProductImage img1 = ProductImage.builder().imageId(img1Id).product(sampleProduct).sortOrder(0).isPrimary(true).build();
        ProductImage img2 = ProductImage.builder().imageId(img2Id).product(sampleProduct).sortOrder(1).isPrimary(false).build();

        List<ProductImage> allImages = new ArrayList<>(List.of(img1, img2));

        when(productRepository.existsById(productId)).thenReturn(true);
        when(imageRepository.findById(img2Id)).thenReturn(Optional.of(img2));
        when(imageRepository.findByProductIdOrdered(productId)).thenReturn(allImages);

        ProductImageDTO.ImageResponse response = imageService.setPrimaryImage(productId, img2Id);

        assertThat(response).isNotNull();
        assertThat(img2.getIsPrimary()).isTrue();
        assertThat(img2.getSortOrder()).isEqualTo(0);
        assertThat(img1.getIsPrimary()).isFalse();
        assertThat(img1.getSortOrder()).isEqualTo(1);
        verify(imageRepository).saveAll(allImages);
    }

    @Test
    @DisplayName("Kéo thả sắp xếp lại thứ tự ảnh (reorderImages) -> Cập nhật sortOrder = 0, 1, 2...")
    void reorderImages_Success() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();

        ProductImage img1 = ProductImage.builder().imageId(img1Id).product(sampleProduct).sortOrder(0).build();
        ProductImage img2 = ProductImage.builder().imageId(img2Id).product(sampleProduct).sortOrder(1).build();

        List<ProductImage> existingImages = new ArrayList<>(List.of(img1, img2));

        when(productRepository.existsById(productId)).thenReturn(true);
        when(imageRepository.findByProductIdOrdered(productId)).thenReturn(existingImages);

        // Đảo ngược thứ tự: img2 lên trước, img1 ra sau
        ProductImageDTO.ReorderImagesRequest reorderReq = new ProductImageDTO.ReorderImagesRequest(List.of(img2Id, img1Id));

        List<ProductImageDTO.ImageResponse> responses = imageService.reorderImages(productId, reorderReq);

        assertThat(responses).isNotNull();
        assertThat(img2.getSortOrder()).isEqualTo(0);
        assertThat(img2.getIsPrimary()).isTrue();
        assertThat(img1.getSortOrder()).isEqualTo(1);
        assertThat(img1.getIsPrimary()).isFalse();
        verify(imageRepository).saveAll(existingImages);
    }

    @Test
    @DisplayName("Xóa ảnh đại diện chính -> Tự động thăng cấp ảnh kế tiếp lên làm Primary và gọi StorageService xóa file")
    void deleteImage_PrimaryImage_PromotesNextImage() {
        UUID img1Id = UUID.randomUUID();
        UUID img2Id = UUID.randomUUID();

        ProductImage primaryImg = ProductImage.builder()
                .imageId(img1Id).product(sampleProduct).url("https://s3.com/primary.jpg").isPrimary(true).sortOrder(0).build();

        ProductImage nextImg = ProductImage.builder()
                .imageId(img2Id).product(sampleProduct).url("https://s3.com/next.jpg").isPrimary(false).sortOrder(1).build();

        when(productRepository.existsById(productId)).thenReturn(true);
        when(imageRepository.findById(img1Id)).thenReturn(Optional.of(primaryImg));
        when(imageRepository.findByProductIdOrdered(productId)).thenReturn(List.of(nextImg));

        imageService.deleteImage(productId, img1Id);

        verify(imageRepository).delete(primaryImg);
        verify(storageService).deleteFile("https://s3.com/primary.jpg");
        assertThat(nextImg.getIsPrimary()).isTrue();
        verify(imageRepository).saveAll(any());
    }
}
