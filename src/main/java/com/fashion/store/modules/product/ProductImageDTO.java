package com.fashion.store.modules.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class ProductImageDTO {

    public record AddImageRequest(
            @NotBlank(message = "Đường dẫn ảnh không được để trống")
            String url,

            UUID variantId,

            Integer sortOrder,

            Boolean isPrimary
    ) {}

    public record ReorderImagesRequest(
            @NotEmpty(message = "Danh sách ID ảnh không được để trống")
            List<UUID> imageIdsInOrder
    ) {}

    @Builder
    public record ImageResponse(
            UUID imageId,
            UUID productId,
            UUID variantId,
            String url,
            Integer sortOrder,
            Boolean isPrimary,
            OffsetDateTime createdAt
    ) {}
}
