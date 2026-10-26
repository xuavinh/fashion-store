package com.fashion.store.modules.product;

import com.fashion.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService imageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ProductImageDTO.ImageResponse>>> getImages(
            @PathVariable UUID productId
    ) {
        List<ProductImageDTO.ImageResponse> images = imageService.getProductImages(productId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ảnh sản phẩm thành công", images));
    }

    @GetMapping("/variant/{variantId}")
    public ResponseEntity<ApiResponse<List<ProductImageDTO.ImageResponse>>> getImagesByVariant(
            @PathVariable UUID productId,
            @PathVariable UUID variantId
    ) {
        List<ProductImageDTO.ImageResponse> images = imageService.getProductImagesByVariant(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách ảnh theo biến thể thành công", images));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductImageDTO.ImageResponse>> addImage(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductImageDTO.AddImageRequest request
    ) {
        ProductImageDTO.ImageResponse response = imageService.addImage(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm ảnh sản phẩm thành công", response));
    }

    @PatchMapping("/{imageId}/set-primary")
    public ResponseEntity<ApiResponse<ProductImageDTO.ImageResponse>> setPrimaryImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId
    ) {
        ProductImageDTO.ImageResponse response = imageService.setPrimaryImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success("Đã đặt làm ảnh đại diện chính", response));
    }

    @PutMapping("/reorder")
    public ResponseEntity<ApiResponse<List<ProductImageDTO.ImageResponse>>> reorderImages(
            @PathVariable UUID productId,
            @Valid @RequestBody ProductImageDTO.ReorderImagesRequest request
    ) {
        List<ProductImageDTO.ImageResponse> response = imageService.reorderImages(productId, request);
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật thứ tự hiển thị ảnh", response));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<ApiResponse<String>> deleteImage(
            @PathVariable UUID productId,
            @PathVariable UUID imageId
    ) {
        imageService.deleteImage(productId, imageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xóa ảnh sản phẩm"));
    }
}
