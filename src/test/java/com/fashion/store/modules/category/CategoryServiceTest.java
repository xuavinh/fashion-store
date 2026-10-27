package com.fashion.store.modules.category;

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
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private UUID catId;
    private Category sampleCategory;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository);

        catId = UUID.randomUUID();
        sampleCategory = Category.builder()
                .categoryId(catId)
                .name("Áo Nam")
                .slug("ao-nam")
                .level((short) 1)
                .genderFilter("MEN")
                .subCategories(new ArrayList<>())
                .products(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Lấy danh mục theo ID thành công")
    void getCategoryById_Success() {
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(sampleCategory));

        CategoryDTO.CategoryResponse response = categoryService.getCategoryById(catId);

        assertThat(response).isNotNull();
        assertThat(response.categoryId()).isEqualTo(catId);
        assertThat(response.name()).isEqualTo("Áo Nam");
    }

    @Test
    @DisplayName("Lấy danh mục theo ID thất bại khi không tìm thấy -> Ném ResourceNotFoundException")
    void getCategoryById_NotFound_ThrowsException() {
        UUID unknownId = UUID.randomUUID();
        when(categoryRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getCategoryById(unknownId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Tạo danh mục mới thành công và tự sinh slug chuẩn")
    void createCategory_Success() {
        CategoryDTO.CreateCategoryRequest request = new CategoryDTO.CreateCategoryRequest(
                "Quần Jean Nữ", null, null, (short) 1, "WOMEN"
        );

        when(categoryRepository.existsBySlug("quan-jean-nu")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category c = inv.getArgument(0);
            c.setCategoryId(UUID.randomUUID());
            return c;
        });

        CategoryDTO.CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Quần Jean Nữ");
        assertThat(response.slug()).isEqualTo("quan-jean-nu");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("Tạo danh mục thất bại khi slug đã tồn tại -> Ném ConflictException")
    void createCategory_DuplicateSlug_ThrowsConflictException() {
        CategoryDTO.CreateCategoryRequest request = new CategoryDTO.CreateCategoryRequest(
                "Áo Nam", "ao-nam", null, (short) 1, "MEN"
        );

        when(categoryRepository.existsBySlug("ao-nam")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ao-nam");
    }

    @Test
    @DisplayName("Xóa danh mục thành công khi không có danh mục con và không có sản phẩm")
    void deleteCategory_Success() {
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(sampleCategory));

        categoryService.deleteCategory(catId);

        verify(categoryRepository).delete(sampleCategory);
    }

    @Test
    @DisplayName("Xóa danh mục thất bại khi có sản phẩm đang liên kết -> Ném BadRequestException")
    void deleteCategory_HasProducts_ThrowsBadRequestException() {
        sampleCategory.setProducts(List.of(new Product()));
        when(categoryRepository.findById(catId)).thenReturn(Optional.of(sampleCategory));

        assertThatThrownBy(() -> categoryService.deleteCategory(catId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("sản phẩm");

        verify(categoryRepository, never()).delete(any());
    }
}
