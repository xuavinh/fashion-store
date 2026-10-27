package com.fashion.store.modules.category;

import com.fashion.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryDTO.CategoryResponse>>> getCategoryTree() {
        List<CategoryDTO.CategoryResponse> tree = categoryService.getCategoryTree();
        return ResponseEntity.ok(ApiResponse.success("Lấy cây danh mục thành công", tree));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryDTO.CategoryResponse>>> getAllCategories() {
        List<CategoryDTO.CategoryResponse> list = categoryService.getAllCategoriesFlat();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách danh mục thành công", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryDTO.CategoryResponse>> getCategoryById(@PathVariable UUID id) {
        CategoryDTO.CategoryResponse response = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết danh mục thành công", response));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<CategoryDTO.CategoryResponse>> getCategoryBySlug(@PathVariable String slug) {
        CategoryDTO.CategoryResponse response = categoryService.getCategoryBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh mục theo slug thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<CategoryDTO.CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryDTO.CreateCategoryRequest request
    ) {
        CategoryDTO.CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo danh mục mới thành công", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<CategoryDTO.CategoryResponse>> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDTO.UpdateCategoryRequest request
    ) {
        CategoryDTO.CategoryResponse updated = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật danh mục thành công", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa danh mục thành công", null));
    }
}
