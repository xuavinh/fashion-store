package com.fashion.store.modules.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public class CategoryDTO {

    public record CreateCategoryRequest(
            @NotBlank(message = "Tên danh mục không được để trống")
            @Size(min = 2, max = 200, message = "Tên danh mục phải từ 2 đến 200 ký tự")
            String name,

            String slug,

            UUID parentId,

            Short level,

            @Pattern(regexp = "^(MEN|WOMEN|UNISEX|ALL)$", message = "Giới tính lọc phải là MEN, WOMEN, UNISEX hoặc ALL")
            String genderFilter
    ) {}

    public record UpdateCategoryRequest(
            @NotBlank(message = "Tên danh mục không được để trống")
            @Size(min = 2, max = 200, message = "Tên danh mục phải từ 2 đến 200 ký tự")
            String name,

            String slug,

            UUID parentId,

            Short level,

            @Pattern(regexp = "^(MEN|WOMEN|UNISEX|ALL)$", message = "Giới tính lọc phải là MEN, WOMEN, UNISEX hoặc ALL")
            String genderFilter
    ) {}

    @Builder
    public record CategoryResponse(
            UUID categoryId,
            UUID parentId,
            String parentName,
            String name,
            String slug,
            Short level,
            String genderFilter,
            List<CategoryResponse> subCategories,
            OffsetDateTime createdAt
    ) {}
}
