package com.fashion.store.modules.category;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<CategoryDTO.CategoryResponse> getCategoryTree() {
        List<Category> rootCategories = categoryRepository.findByParentIsNull();
        return rootCategories.stream()
                .map(this::toResponseWithChildren)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryDTO.CategoryResponse> getAllCategoriesFlat() {
        return categoryRepository.findAll().stream()
                .map(this::toSimpleResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CategoryDTO.CategoryResponse getCategoryById(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại: " + categoryId));
        return toResponseWithChildren(category);
    }

    @Transactional(readOnly = true)
    public CategoryDTO.CategoryResponse getCategoryBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại với slug: " + slug));
        return toResponseWithChildren(category);
    }

    @Transactional
    public CategoryDTO.CategoryResponse createCategory(CategoryDTO.CreateCategoryRequest request) {
        String slug = StringUtils.hasText(request.slug())
                ? request.slug().trim().toLowerCase()
                : generateSlug(request.name());

        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Slug danh mục '" + slug + "' đã tồn tại");
        }

        Category parent = null;
        short level = 1;

        if (request.parentId() != null) {
            parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Danh mục cha không tồn tại: " + request.parentId()));
            level = (short) (parent.getLevel() + 1);
        } else if (request.level() != null && request.level() > 0) {
            level = request.level();
        }

        Category category = Category.builder()
                .name(request.name().trim())
                .slug(slug)
                .parent(parent)
                .level(level)
                .genderFilter(request.genderFilter() != null ? request.genderFilter() : "ALL")
                .build();

        Category saved = categoryRepository.save(category);
        log.info("Tạo danh mục thành công: id={}, name={}", saved.getCategoryId(), saved.getName());
        return toSimpleResponse(saved);
    }

    @Transactional
    public CategoryDTO.CategoryResponse updateCategory(UUID categoryId, CategoryDTO.UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại: " + categoryId));

        if (StringUtils.hasText(request.slug())) {
            String newSlug = request.slug().trim().toLowerCase();
            if (!newSlug.equals(category.getSlug()) && categoryRepository.existsBySlug(newSlug)) {
                throw new ConflictException("Slug danh mục '" + newSlug + "' đã tồn tại");
            }
            category.setSlug(newSlug);
        }

        category.setName(request.name().trim());

        if (request.parentId() != null) {
            if (request.parentId().equals(categoryId)) {
                throw new BadRequestException("Danh mục không thể làm cha của chính nó");
            }
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Danh mục cha không tồn tại: " + request.parentId()));
            category.setParent(parent);
            category.setLevel((short) (parent.getLevel() + 1));
        } else if (request.parentId() == null && request.level() != null) {
            category.setParent(null);
            category.setLevel(request.level());
        }

        if (request.genderFilter() != null) {
            category.setGenderFilter(request.genderFilter());
        }

        Category updated = categoryRepository.save(category);
        log.info("Cập nhật danh mục thành công: id={}", updated.getCategoryId());
        return toSimpleResponse(updated);
    }

    @Transactional
    public void deleteCategory(UUID categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Danh mục không tồn tại: " + categoryId));

        if (!category.getSubCategories().isEmpty()) {
            throw new BadRequestException("Không thể xóa danh mục đang có " + category.getSubCategories().size() + " danh mục con");
        }

        if (!category.getProducts().isEmpty()) {
            throw new BadRequestException("Không thể xóa danh mục đang gắn với " + category.getProducts().size() + " sản phẩm");
        }

        categoryRepository.delete(category);
        log.info("Đã xóa danh mục: id={}", categoryId);
    }

    private CategoryDTO.CategoryResponse toSimpleResponse(Category c) {
        return CategoryDTO.CategoryResponse.builder()
                .categoryId(c.getCategoryId())
                .parentId(c.getParent() != null ? c.getParent().getCategoryId() : null)
                .parentName(c.getParent() != null ? c.getParent().getName() : null)
                .name(c.getName())
                .slug(c.getSlug())
                .level(c.getLevel())
                .genderFilter(c.getGenderFilter())
                .subCategories(Collections.emptyList())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private CategoryDTO.CategoryResponse toResponseWithChildren(Category c) {
        List<CategoryDTO.CategoryResponse> children = c.getSubCategories() != null
                ? c.getSubCategories().stream().map(this::toResponseWithChildren).collect(Collectors.toList())
                : Collections.emptyList();

        return CategoryDTO.CategoryResponse.builder()
                .categoryId(c.getCategoryId())
                .parentId(c.getParent() != null ? c.getParent().getCategoryId() : null)
                .parentName(c.getParent() != null ? c.getParent().getName() : null)
                .name(c.getName())
                .slug(c.getSlug())
                .level(c.getLevel())
                .genderFilter(c.getGenderFilter())
                .subCategories(children)
                .createdAt(c.getCreatedAt())
                .build();
    }

    private String generateSlug(String input) {
        String nowhitespace = Pattern.compile("[\\s]").matcher(input).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = Pattern.compile("[^\\w-]").matcher(normalized).replaceAll("");
        return slug.toLowerCase(Locale.ENGLISH);
    }
}
