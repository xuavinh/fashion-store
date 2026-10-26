package com.fashion.store.modules.product;

import com.fashion.store.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // Tìm kiếm, lọc và phân trang danh sách sản phẩm
    @GetMapping
    public ResponseEntity<ApiResponse<Page<ProductDTO.ProductSummaryResponse>>> searchProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) Gender gender,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = "ASC".equalsIgnoreCase(sortDir) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<ProductDTO.ProductSummaryResponse> result = productService.searchProducts(
                search, categoryId, brandId, gender, minPrice, maxPrice, isActive, pageable
        );
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách sản phẩm thành công", result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductDTO.ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductDTO.ProductResponse product = productService.getProductById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm thành công", product));
    }

    // Lấy chi tiết sản phẩm theo Slug
    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<ProductDTO.ProductResponse>> getProductBySlug(@PathVariable String slug) {
        ProductDTO.ProductResponse product = productService.getProductBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết sản phẩm theo slug thành công", product));
    }

    // Tạo sản phẩm mới kèm danh sách biến thể SKU và ảnh ban đầu
    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO.ProductResponse>> createProduct(
            @Valid @RequestBody ProductDTO.CreateProductRequest request
    ) {
        ProductDTO.ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo sản phẩm mới thành công", created));
    }

    // Cập nhật toàn diện thông tin sản phẩm và đồng bộ danh sách biến thể SKU
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductDTO.ProductResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductDTO.UpdateProductRequest request
    ) {
        ProductDTO.ProductResponse updated = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật sản phẩm thành công", updated));
    }

    // Bật/tắt trạng thái kinh doanh của sản phẩm (Staff, Admin)
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateStatus(
            @PathVariable UUID id,
            @RequestParam boolean isActive
    ) {
        productService.updateStatus(id, isActive);
        return ResponseEntity.ok(ApiResponse.success( "Đã cập nhật trạng thái sản phẩm", null));
    }

    // Xóa sản phẩm và xóa toàn bộ file ảnh liên quan trên S3
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success( "Đã xóa sản phẩm thành công", null));
    }
}
