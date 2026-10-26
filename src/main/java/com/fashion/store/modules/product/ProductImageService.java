package com.fashion.store.modules.product;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final StorageService storageService;

    @Transactional
    public List<ProductImageDTO.ImageResponse> getProductImages(UUID productId) {
        ensureProductExists(productId);
        List<ProductImage> images = imageRepository.findByProductIdOrdered(productId);

        // Tự động chuẩn hóa lại thứ tự (0, 1, 2...) nếu phát hiện dữ liệu cũ bị trùng sortOrder
        if (hasDuplicateSortOrders(images)) {
            images = autoNormalizeSortOrders(images);
        }

        return images.stream().map(this::toResponse).toList();
    }

    @Transactional
    public List<ProductImageDTO.ImageResponse> getProductImagesByVariant(UUID productId, UUID variantId) {
        ensureProductExists(productId);
        List<ProductImage> images = imageRepository.findByProductIdAndVariantIdOrdered(productId, variantId);

        if (hasDuplicateSortOrders(images)) {
            images = autoNormalizeSortOrders(images);
        }

        return images.stream().map(this::toResponse).toList();
    }

    @Transactional
    public ProductImageDTO.ImageResponse addImage(UUID productId, ProductImageDTO.AddImageRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

        ProductVariant variant = null;
        if (request.variantId() != null) {
            variant = variantRepository.findById(request.variantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy biến thể với ID: " + request.variantId()));

            if (!variant.getProduct().getProductId().equals(productId)) {
                throw new BadRequestException("Biến thể không thuộc về sản phẩm này");
            }
        }

        long existingCount = imageRepository.countByProductProductId(productId);
        int maxOrder = imageRepository.findMaxSortOrderByProductId(productId);

        // Nếu là ảnh đầu tiên hoặc request yêu cầu isPrimary = true
        boolean shouldBePrimary = existingCount == 0 || Boolean.TRUE.equals(request.isPrimary());

        int finalSortOrder;

        if (shouldBePrimary) {
            finalSortOrder = 0;
            if (existingCount > 0) {
                imageRepository.resetPrimaryImageForProduct(productId);
                // Dời toàn bộ ảnh cũ từ vị trí 0 lùi lại 1 nấc
                imageRepository.shiftSortOrderFrom(productId, 0);
            }
        } else {
            if (request.sortOrder() != null && request.sortOrder() > 0 && request.sortOrder() <= maxOrder) {
                // Frontend chủ động gửi thứ tự và muốn chèn vào giữa danh sách
                finalSortOrder = request.sortOrder();
                // Dời các ảnh từ targetOrder trở đi lùi lại 1 nấc để nhường chỗ
                imageRepository.shiftSortOrderFrom(productId, finalSortOrder);
            } else {
                // Backend tự động xác định thứ tự: Gán vào cuối danh sách (MAX + 1)
                finalSortOrder = Math.max(1, maxOrder + 1);
            }
        }

        ProductImage image = ProductImage.builder()
                .product(product)
                .variant(variant)
                .url(request.url())
                .sortOrder(finalSortOrder)
                .isPrimary(shouldBePrimary)
                .build();

        ProductImage savedImage = imageRepository.save(image);
        return toResponse(savedImage);
    }

    @Transactional
    public ProductImageDTO.ImageResponse setPrimaryImage(UUID productId, UUID imageId) {
        ensureProductExists(productId);

        ProductImage targetImage = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh với ID: " + imageId));

        if (!targetImage.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Ảnh này không thuộc về sản phẩm được chỉ định");
        }

        List<ProductImage> allImages = imageRepository.findByProductIdOrdered(productId);

        // Bỏ primary của tất cả các ảnh
        for (ProductImage img : allImages) {
            img.setIsPrimary(false);
        }

        targetImage.setIsPrimary(true);
        targetImage.setSortOrder(0);

        int nextOrder = 1;
        for (ProductImage img : allImages) {
            if (!img.getImageId().equals(imageId)) {
                img.setSortOrder(nextOrder++);
            }
        }

        imageRepository.saveAll(allImages);
        return toResponse(targetImage);
    }

    @Transactional
    public List<ProductImageDTO.ImageResponse> reorderImages(UUID productId, ProductImageDTO.ReorderImagesRequest request) {
        ensureProductExists(productId);

        List<UUID> orderedIds = request.imageIdsInOrder();
        List<ProductImage> productImages = imageRepository.findByProductIdOrdered(productId);

        if (orderedIds.size() != productImages.size()) {
            throw new BadRequestException("Số lượng ID trong yêu cầu sắp xếp không khớp với tổng số ảnh hiện có của sản phẩm");
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            UUID id = orderedIds.get(i);
            int newOrder = i;

            ProductImage matchingImage = productImages.stream()
                    .filter(img -> img.getImageId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("ID ảnh " + id + " không thuộc về sản phẩm này"));

            matchingImage.setSortOrder(newOrder);
            // Ảnh ở vị trí đầu tiên (index 0) tự động được gán làm Primary
            matchingImage.setIsPrimary(i == 0);
        }

        imageRepository.saveAll(productImages);

        return imageRepository.findByProductIdOrdered(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        ensureProductExists(productId);

        ProductImage image = imageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ảnh với ID: " + imageId));

        if (!image.getProduct().getProductId().equals(productId)) {
            throw new BadRequestException("Ảnh không thuộc về sản phẩm này");
        }

        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        String fileUrl = image.getUrl();

        imageRepository.delete(image);

        // Xóa file trên S3 / Cloudflare R2
        storageService.deleteFile(fileUrl);

        // tự động thăng cấp ảnh kế tiếp lên làm Primary
        List<ProductImage> remainingImages = imageRepository.findByProductIdOrdered(productId);
        if (!remainingImages.isEmpty()) {
            if (wasPrimary) {
                ProductImage nextPrimary = remainingImages.get(0);
                nextPrimary.setIsPrimary(true);
            }
            // Chuẩn hóa lại sortOrder 0, 1, 2... sau khi xóa
            autoNormalizeSortOrders(remainingImages);
        }
    }

    private boolean hasDuplicateSortOrders(List<ProductImage> images) {
        if (images == null || images.size() <= 1) return false;
        Set<Integer> seen = new HashSet<>();
        for (ProductImage img : images) {
            if (img.getSortOrder() == null || !seen.add(img.getSortOrder())) {
                return true; // Có trùng sortOrder
            }
        }
        return false;
    }

    private List<ProductImage> autoNormalizeSortOrders(List<ProductImage> images) {
        for (int i = 0; i < images.size(); i++) {
            ProductImage img = images.get(i);
            img.setSortOrder(i);
            // Phần tử đầu tiên luôn là Primary
            img.setIsPrimary(i == 0);
        }
        return imageRepository.saveAll(images);
    }

    private void ensureProductExists(UUID productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId);
        }
    }

    private ProductImageDTO.ImageResponse toResponse(ProductImage image) {
        return ProductImageDTO.ImageResponse.builder()
                .imageId(image.getImageId())
                .productId(image.getProduct().getProductId())
                .variantId(image.getVariant() != null ? image.getVariant().getVariantId() : null)
                .url(image.getUrl())
                .sortOrder(image.getSortOrder())
                .isPrimary(image.getIsPrimary())
                .createdAt(image.getCreatedAt())
                .build();
    }
}
