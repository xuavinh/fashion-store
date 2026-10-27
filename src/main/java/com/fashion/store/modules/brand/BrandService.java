package com.fashion.store.modules.brand;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<BrandDTO.BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BrandDTO.BrandResponse getBrandById(UUID brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại: " + brandId));
        return toResponse(brand);
    }

    @Transactional(readOnly = true)
    public BrandDTO.BrandResponse getBrandBySlug(String slug) {
        Brand brand = brandRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại với slug: " + slug));
        return toResponse(brand);
    }

    @Transactional
    public BrandDTO.BrandResponse createBrand(BrandDTO.CreateBrandRequest request) {
        String slug = StringUtils.hasText(request.slug())
                ? request.slug().trim().toLowerCase()
                : generateSlug(request.name());

        if (brandRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug thương hiệu '" + slug + "' đã tồn tại");
        }

        Brand brand = Brand.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description())
                .build();

        Brand saved = brandRepository.save(brand);
        log.info("Tạo thương hiệu thành công: id={}, name={}", saved.getBrandId(), saved.getName());
        return toResponse(saved);
    }

    @Transactional
    public BrandDTO.BrandResponse updateBrand(UUID brandId, BrandDTO.UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại: " + brandId));

        if (StringUtils.hasText(request.slug())) {
            String newSlug = request.slug().trim().toLowerCase();
            if (!newSlug.equals(brand.getSlug()) && brandRepository.existsBySlug(newSlug)) {
                throw new ConflictException("Slug thương hiệu '" + newSlug + "' đã tồn tại");
            }
            brand.setSlug(newSlug);
        }

        brand.setName(request.name().trim());
        brand.setDescription(request.description());

        Brand updated = brandRepository.save(brand);
        log.info("Cập nhật thương hiệu thành công: id={}", updated.getBrandId());
        return toResponse(updated);
    }

    @Transactional
    public void deleteBrand(UUID brandId) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("Thương hiệu không tồn tại: " + brandId));

        if (!brand.getProducts().isEmpty()) {
            throw new BadRequestException("Không thể xóa thương hiệu đang gắn với " + brand.getProducts().size() + " sản phẩm");
        }

        brandRepository.delete(brand);
        log.info("Đã xóa thương hiệu: id={}", brandId);
    }

    private BrandDTO.BrandResponse toResponse(Brand b) {
        return BrandDTO.BrandResponse.builder()
                .brandId(b.getBrandId())
                .name(b.getName())
                .slug(b.getSlug())
                .description(b.getDescription())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private String generateSlug(String input) {
        String nowhitespace = Pattern.compile("\\s").matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = Pattern.compile("[^\\w-]").matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
