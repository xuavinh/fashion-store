package com.fashion.store.modules.brand;

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
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandDTO.BrandResponse>>> getAllBrands() {
        List<BrandDTO.BrandResponse> list = brandService.getAllBrands();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách thương hiệu thành công", list));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BrandDTO.BrandResponse>> getBrandById(@PathVariable UUID id) {
        BrandDTO.BrandResponse response = brandService.getBrandById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy chi tiết thương hiệu thành công", response));
    }

    @GetMapping("/slug/{slug}")
    public ResponseEntity<ApiResponse<BrandDTO.BrandResponse>> getBrandBySlug(@PathVariable String slug) {
        BrandDTO.BrandResponse response = brandService.getBrandBySlug(slug);
        return ResponseEntity.ok(ApiResponse.success("Lấy thương hiệu theo slug thành công", response));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<BrandDTO.BrandResponse>> createBrand(
            @Valid @RequestBody BrandDTO.CreateBrandRequest request
    ) {
        BrandDTO.BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo thương hiệu mới thành công", created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    public ResponseEntity<ApiResponse<BrandDTO.BrandResponse>> updateBrand(
            @PathVariable UUID id,
            @Valid @RequestBody BrandDTO.UpdateBrandRequest request
    ) {
        BrandDTO.BrandResponse updated = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thương hiệu thành công", updated));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteBrand(@PathVariable UUID id) {
        brandService.deleteBrand(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa thương hiệu thành công", null));
    }
}
