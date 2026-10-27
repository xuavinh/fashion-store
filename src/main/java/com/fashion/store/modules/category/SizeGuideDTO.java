package com.fashion.store.modules.category;

import com.fashion.store.modules.product.Gender;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public class SizeGuideDTO {

    public record CreateSizeGuideRequest(
            @NotNull(message = "Danh mục không được để trống")
            UUID categoryId,

            @NotNull(message = "Giới tính không được để trống (MALE, FEMALE, UNISEX, KIDS, ALL)")
            Gender gender,

            @NotNull(message = "Bảng thông số kích cỡ (JSON) không được để trống")
            JsonNode sizeChart
    ) {}

    public record UpdateSizeGuideRequest(
            Gender gender,
            JsonNode sizeChart
    ) {}

    public record SizeGuideResponse(
            UUID guideId,
            UUID categoryId,
            String categoryName,
            Gender gender,
            JsonNode sizeChart,
            OffsetDateTime createdAt
    ) {}
}
