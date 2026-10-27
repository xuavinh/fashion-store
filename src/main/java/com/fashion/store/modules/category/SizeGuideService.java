package com.fashion.store.modules.category;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.product.Gender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SizeGuideService {

    private final SizeGuideRepository sizeGuideRepository;
    private final CategoryRepository categoryRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public SizeGuideDTO.SizeGuideResponse createSizeGuide(SizeGuideDTO.CreateSizeGuideRequest request) {
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục: " + request.categoryId()));

        Gender gender = request.gender() != null ? request.gender() : Gender.UNISEX;

        if (sizeGuideRepository.findByCategoryCategoryIdAndGender(request.categoryId(), gender).isPresent()) {
            throw new BadRequestException("Bảng size cho danh mục '" + category.getName() + "' với giới tính '" + gender + "' đã tồn tại");
        }

        SizeGuide sizeGuide = SizeGuide.builder()
                .category(category)
                .gender(gender)
                .sizeChart(request.sizeChart().toString())
                .build();

        SizeGuide saved = sizeGuideRepository.save(sizeGuide);
        return toResponse(saved);
    }

    @Transactional
    public SizeGuideDTO.SizeGuideResponse updateSizeGuide(UUID guideId, SizeGuideDTO.UpdateSizeGuideRequest request) {
        SizeGuide sizeGuide = sizeGuideRepository.findWithCategoryByGuideId(guideId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng size: " + guideId));

        if (request.gender() != null && request.gender() != sizeGuide.getGender()) {
            sizeGuideRepository.findByCategoryCategoryIdAndGender(sizeGuide.getCategory().getCategoryId(), request.gender())
                    .ifPresent(existing -> {
                        throw new BadRequestException("Bảng size cho danh mục với giới tính '" + request.gender() + "' đã tồn tại");
                    });
            sizeGuide.setGender(request.gender());
        }

        if (request.sizeChart() != null && !request.sizeChart().isNull()) {
            sizeGuide.setSizeChart(request.sizeChart().toString());
        }

        SizeGuide updated = sizeGuideRepository.save(sizeGuide);
        return toResponse(updated);
    }

    @Transactional
    public void deleteSizeGuide(UUID guideId) {
        if (!sizeGuideRepository.existsById(guideId)) {
            throw new ResourceNotFoundException("Không tìm thấy bảng size: " + guideId);
        }
        sizeGuideRepository.deleteById(guideId);
    }

    @Transactional(readOnly = true)
    public SizeGuideDTO.SizeGuideResponse getSizeGuideById(UUID guideId) {
        SizeGuide sizeGuide = sizeGuideRepository.findWithCategoryByGuideId(guideId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng size: " + guideId));
        return toResponse(sizeGuide);
    }

    @Transactional(readOnly = true)
    public List<SizeGuideDTO.SizeGuideResponse> getSizeGuidesByCategory(UUID categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục: " + categoryId);
        }
        return sizeGuideRepository.findByCategoryCategoryId(categoryId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SizeGuideDTO.SizeGuideResponse getSizeGuideByCategoryAndGender(UUID categoryId, Gender gender) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ResourceNotFoundException("Không tìm thấy danh mục: " + categoryId);
        }

        if (gender != null) {
            Optional<SizeGuide> guideOpt = sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, gender);
            if (guideOpt.isEmpty() && gender != Gender.UNISEX) {
                guideOpt = sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.UNISEX);
            }
            return guideOpt.map(this::toResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bảng size phù hợp cho danh mục"));
        }

        List<SizeGuide> list = sizeGuideRepository.findByCategoryCategoryId(categoryId);
        if (list.isEmpty()) {
            throw new ResourceNotFoundException("Không có bảng size nào cho danh mục này");
        }
        return toResponse(list.getFirst());
    }

    public SizeGuideDTO.SizeGuideResponse toResponse(SizeGuide guide) {
        JsonNode chartNode;
        try {
            chartNode = objectMapper.readTree(guide.getSizeChart());
        } catch (Exception e) {
            chartNode = objectMapper.getNodeFactory().stringNode(guide.getSizeChart());
        }

        return new SizeGuideDTO.SizeGuideResponse(
                guide.getGuideId(),
                guide.getCategory().getCategoryId(),
                guide.getCategory().getName(),
                guide.getGender(),
                chartNode,
                guide.getCreatedAt()
        );
    }
}
