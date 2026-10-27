package com.fashion.store.modules.brand;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.product.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    private BrandService brandService;

    private UUID brandId;
    private Brand sampleBrand;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(brandRepository);

        brandId = UUID.randomUUID();
        sampleBrand = Brand.builder()
                .brandId(brandId)
                .name("Gucci")
                .slug("gucci")
                .description("Thương hiệu cao cấp")
                .products(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Lấy thương hiệu theo ID thành công")
    void getBrandById_Success() {
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));

        BrandDTO.BrandResponse response = brandService.getBrandById(brandId);

        assertThat(response).isNotNull();
        assertThat(response.brandId()).isEqualTo(brandId);
        assertThat(response.name()).isEqualTo("Gucci");
    }

    @Test
    @DisplayName("Lấy thương hiệu thất bại khi không tìm thấy -> Ném ResourceNotFoundException")
    void getBrandById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(brandRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> brandService.getBrandById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tạo thương hiệu mới thành công và tự sinh slug chuẩn")
    void createBrand_Success() {
        BrandDTO.CreateBrandRequest request = new BrandDTO.CreateBrandRequest(
                "Louis Vuitton", null, "Thương hiệu Pháp"
        );

        when(brandRepository.existsBySlug("louis-vuitton")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(inv -> {
            Brand b = inv.getArgument(0);
            b.setBrandId(UUID.randomUUID());
            return b;
        });

        BrandDTO.BrandResponse response = brandService.createBrand(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Louis Vuitton");
        assertThat(response.slug()).isEqualTo("louis-vuitton");
        verify(brandRepository).save(any(Brand.class));
    }

    @Test
    @DisplayName("Tạo thương hiệu thất bại khi slug đã tồn tại -> Ném ConflictException")
    void createBrand_DuplicateSlug_ThrowsConflictException() {
        BrandDTO.CreateBrandRequest request = new BrandDTO.CreateBrandRequest(
                "Gucci", "gucci", "Mô tả"
        );

        when(brandRepository.existsBySlug("gucci")).thenReturn(true);

        assertThatThrownBy(() -> brandService.createBrand(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("gucci");
    }

    @Test
    @DisplayName("Xóa thương hiệu thành công khi không có sản phẩm gắn kèm")
    void deleteBrand_Success() {
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));

        brandService.deleteBrand(brandId);

        verify(brandRepository).delete(sampleBrand);
    }

    @Test
    @DisplayName("Xóa thương hiệu thất bại khi đang có sản phẩm gắn kèm -> Ném BadRequestException")
    void deleteBrand_HasProducts_ThrowsBadRequestException() {
        sampleBrand.setProducts(List.of(new Product()));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(sampleBrand));

        assertThatThrownBy(() -> brandService.deleteBrand(brandId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sản phẩm");

        verify(brandRepository, never()).delete(any());
    }
}
