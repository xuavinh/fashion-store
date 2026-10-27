package com.fashion.store.modules.category;

import com.fashion.store.common.response.ApiResponse;
import com.fashion.store.modules.product.Gender;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/size-guides")
@RequiredArgsConstructor
public class SizeGuideController {

    private final SizeGuideService sizeGuideService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SizeGuideDTO.SizeGuideResponse>> createSizeGuide(
            @Valid @RequestBody SizeGuideDTO.CreateSizeGuideRequest request
    ) {
        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.createSizeGuide(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo bảng kích cỡ thành công", response));
    }

    @PutMapping("/{guideId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SizeGuideDTO.SizeGuideResponse>> updateSizeGuide(
            @PathVariable UUID guideId,
            @Valid @RequestBody SizeGuideDTO.UpdateSizeGuideRequest request
    ) {
        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.updateSizeGuide(guideId, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật bảng kích cỡ thành công", response));
    }

    @DeleteMapping("/{guideId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteSizeGuide(@PathVariable UUID guideId) {
        sizeGuideService.deleteSizeGuide(guideId);
        return ResponseEntity.ok(ApiResponse.success("Xóa bảng kích cỡ thành công", null));
    }

    @GetMapping("/{guideId}")
    public ResponseEntity<ApiResponse<SizeGuideDTO.SizeGuideResponse>> getSizeGuideById(@PathVariable UUID guideId) {
        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.getSizeGuideById(guideId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bảng kích cỡ thành công", response));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<ApiResponse<SizeGuideDTO.SizeGuideResponse>> getSizeGuideByCategory(
            @PathVariable UUID categoryId,
            @RequestParam(required = false)  Gender gender
    ) {
        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.getSizeGuideByCategoryAndGender(categoryId, gender);
        return ResponseEntity.ok(ApiResponse.success("Lấy bảng kích cỡ thành công", response));
    }

    @GetMapping("/category/{categoryId}/all")
    public ResponseEntity<ApiResponse<List<SizeGuideDTO.SizeGuideResponse>>> getAllSizeGuidesByCategory(
            @PathVariable UUID categoryId
    ) {
        List<SizeGuideDTO.SizeGuideResponse> response = sizeGuideService.getSizeGuidesByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bảng kích cỡ thành công", response));
    }
}
