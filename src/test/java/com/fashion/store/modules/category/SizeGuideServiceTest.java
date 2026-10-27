package com.fashion.store.modules.category;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.modules.product.Gender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SizeGuideServiceTest {

    @Mock
    private SizeGuideRepository sizeGuideRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private SizeGuideService sizeGuideService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID categoryId;
    private UUID guideId;
    private Category sampleCategory;
    private SizeGuide sampleSizeGuide;
    private JsonNode sampleChartNode;

    @BeforeEach
    void setUp()  {
        sizeGuideService = new SizeGuideService(sizeGuideRepository, categoryRepository, objectMapper);

        categoryId = UUID.randomUUID();
        guideId = UUID.randomUUID();

        sampleChartNode = objectMapper.readTree("{\"headers\":[\"Size\",\"Chest\"],\"rows\":[{\"size\":\"M\",\"chest\":\"90\"}]}");

        sampleCategory = Category.builder()
                .name("Áo Thun")
                .slug("ao-thun")
                .level((short) 1)
                .build();
        sampleCategory.setCategoryId(categoryId);

        sampleSizeGuide = SizeGuide.builder()
                .guideId(guideId)
                .category(sampleCategory)
                .gender(Gender.UNISEX)
                .sizeChart(sampleChartNode.toString())
                .build();
        sampleSizeGuide.setCreatedAt(OffsetDateTime.now());
    }

    @Test
    @DisplayName("Tạo bảng size thành công")
    void createSizeGuide_Success() {
        SizeGuideDTO.CreateSizeGuideRequest request = new SizeGuideDTO.CreateSizeGuideRequest(
                categoryId, Gender.UNISEX, sampleChartNode
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.UNISEX))
                .thenReturn(Optional.empty());
        when(sizeGuideRepository.save(any(SizeGuide.class))).thenAnswer(invocation -> {
            SizeGuide saved = invocation.getArgument(0);
            saved.setGuideId(guideId);
            saved.setCreatedAt(OffsetDateTime.now());
            return saved;
        });

        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.createSizeGuide(request);

        assertThat(response).isNotNull();
        assertThat(response.gender()).isEqualTo(Gender.UNISEX);
        assertThat(response.categoryName()).isEqualTo("Áo Thun");
        assertThat(response.sizeChart()).isEqualTo(sampleChartNode);
        verify(sizeGuideRepository).save(any(SizeGuide.class));
    }

    @Test
    @DisplayName("Tạo bảng size thất bại khi giới tính trong danh mục đã tồn tại")
    void createSizeGuide_DuplicateGender_ThrowsBadRequest() {
        SizeGuideDTO.CreateSizeGuideRequest request = new SizeGuideDTO.CreateSizeGuideRequest(
                categoryId, Gender.UNISEX, sampleChartNode
        );

        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(sampleCategory));
        when(sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.UNISEX))
                .thenReturn(Optional.of(sampleSizeGuide));

        assertThatThrownBy(() -> sizeGuideService.createSizeGuide(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("đã tồn tại");

        verify(sizeGuideRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cập nhật bảng size thành công")
    void updateSizeGuide_Success() {
        JsonNode updatedChart = objectMapper.readTree("{\"chart\":\"updated\"}");
        SizeGuideDTO.UpdateSizeGuideRequest request = new SizeGuideDTO.UpdateSizeGuideRequest(
                Gender.MALE, updatedChart
        );

        when(sizeGuideRepository.findWithCategoryByGuideId(guideId)).thenReturn(Optional.of(sampleSizeGuide));
        when(sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.MALE))
                .thenReturn(Optional.empty());
        when(sizeGuideRepository.save(any(SizeGuide.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.updateSizeGuide(guideId, request);

        assertThat(response.gender()).isEqualTo(Gender.MALE);
        assertThat(response.sizeChart()).isEqualTo(updatedChart);
        verify(sizeGuideRepository).save(sampleSizeGuide);
    }

    @Test
    @DisplayName("Xóa bảng size thành công")
    void deleteSizeGuide_Success() {
        when(sizeGuideRepository.existsById(guideId)).thenReturn(true);

        sizeGuideService.deleteSizeGuide(guideId);

        verify(sizeGuideRepository).deleteById(guideId);
    }

    @Test
    @DisplayName("Lấy bảng size theo danh mục và giới tính thành công")
    void getSizeGuideByCategoryAndGender_Success() {
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.FEMALE))
                .thenReturn(Optional.empty());
        when(sizeGuideRepository.findByCategoryCategoryIdAndGender(categoryId, Gender.UNISEX))
                .thenReturn(Optional.of(sampleSizeGuide));

        SizeGuideDTO.SizeGuideResponse response = sizeGuideService.getSizeGuideByCategoryAndGender(categoryId, Gender.FEMALE);

        assertThat(response).isNotNull();
        assertThat(response.gender()).isEqualTo(Gender.UNISEX);
    }
}
