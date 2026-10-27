package com.fashion.store.modules.brand;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.UUID;

public class BrandDTO {

    public record CreateBrandRequest(
            @NotBlank(message = "Tên thương hiệu không được để trống")
            @Size(min = 2, max = 200, message = "Tên thương hiệu phải từ 2 đến 200 ký tự")
            String name,

            String slug,

            String description
    ) {}

    public record UpdateBrandRequest(
            @NotBlank(message = "Tên thương hiệu không được để trống")
            @Size(min = 2, max = 200, message = "Tên thương hiệu phải từ 2 đến 200 ký tự")
            String name,

            String slug,

            String description
    ) {}

    @Builder
    public record BrandResponse(
            UUID brandId,
            String name,
            String slug,
            String description,
            OffsetDateTime createdAt
    ) {}
}
