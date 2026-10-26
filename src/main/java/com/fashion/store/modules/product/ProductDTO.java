package com.fashion.store.modules.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ProductDTO {

    public record CreateProductRequest(
            @NotBlank(message = "Tên sản phẩm không được để trống")
            String name,

            String slug,

            String description,

            UUID brandId,

            UUID categoryId,

            Gender gender,

            String material,

            String careGuide,

            Set<String> tags,

            @NotEmpty(message = "Sản phẩm thời trang phải có ít nhất 1 biến thể (SKU)")
            @Valid
            List<CreateVariantRequest> variants,

            List<CreateImageRequest> images
    ) {}

    public record UpdateProductRequest(
            @NotBlank(message = "Tên sản phẩm không được để trống")
            String name,

            String slug,

            String description,

            UUID brandId,

            UUID categoryId,

            Gender gender,

            String material,

            String careGuide,

            Boolean isActive,

            Set<String> tags,

            @NotEmpty(message = "Sản phẩm thời trang phải có ít nhất 1 biến thể (SKU)")
            @Valid
            List<UpdateVariantRequest> variants
    ) {}

    public record CreateVariantRequest(
            @NotBlank(message = "Màu sắc không được để trống")
            String color,

            String colorCode,

            @NotBlank(message = "Kích cỡ không được để trống")
            String size,

            String skuCode,

            @NotNull(message = "Giá bán không được để trống")
            @DecimalMin(value = "0.0", message = "Giá bán phải >= 0")
            BigDecimal price,

            BigDecimal comparePrice,

            Integer stockQty,

            Integer weightG
    ) {}

    public record UpdateVariantRequest(
            UUID variantId,

            @NotBlank(message = "Màu sắc không được để trống")
            String color,

            String colorCode,

            @NotBlank(message = "Kích cỡ không được để trống")
            String size,

            String skuCode,

            @NotNull(message = "Giá bán không được để trống")
            @DecimalMin(value = "0.0", message = "Giá bán phải >= 0")
            BigDecimal price,

            BigDecimal comparePrice,

            Integer stockQty,

            Integer weightG
    ) {}

    public record CreateImageRequest(
            @NotBlank(message = "URL ảnh không được để trống")
            String url,

            Integer sortOrder,

            Boolean isPrimary
    ) {}

    @Builder
    public record ProductResponse(
            UUID productId,
            String name,
            String slug,
            String description,
            UUID brandId,
            String brandName,
            UUID categoryId,
            String categoryName,
            Gender gender,
            String material,
            String careGuide,
            Boolean isActive,
            BigDecimal avgRating,
            Integer totalSold,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Set<String> tags,
            List<VariantResponse> variants,
            List<ProductImageDTO.ImageResponse> images,
            OffsetDateTime createdAt
    ) {}

    @Builder
    public record ProductSummaryResponse(
            UUID productId,
            String name,
            String slug,
            String brandName,
            String categoryName,
            Gender gender,
            Boolean isActive,
            BigDecimal avgRating,
            Integer totalSold,
            BigDecimal priceMin,
            BigDecimal priceMax,
            String primaryImageUrl,
            OffsetDateTime createdAt
    ) {}

    @Builder
    public record VariantResponse(
            UUID variantId,
            UUID productId,
            String color,
            String colorCode,
            String size,
            String skuCode,
            BigDecimal price,
            BigDecimal comparePrice,
            Integer stockQty,
            Integer weightG,
            OffsetDateTime createdAt
    ) {}
}
