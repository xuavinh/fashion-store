package com.fashion.store.modules.product;

import com.fashion.store.common.exception.BadRequestException;
import com.fashion.store.common.exception.ConflictException;
import com.fashion.store.common.exception.ResourceNotFoundException;
import com.fashion.store.modules.brand.Brand;
import com.fashion.store.modules.brand.BrandRepository;
import com.fashion.store.modules.category.Category;
import com.fashion.store.modules.category.CategoryRepository;
import com.fashion.store.modules.storage.StorageService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    @Transactional
    public ProductDTO.ProductResponse createProduct(ProductDTO.CreateProductRequest request) {
        String slug = resolveSlug(request.name(), request.slug(), null);

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với ID: " + request.brandId()));
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.categoryId()));
        }

        Product product = Product.builder()
                .name(request.name().trim())
                .slug(slug)
                .description(request.description())
                .brand(brand)
                .category(category)
                .gender(request.gender() != null ? request.gender() : Gender.UNISEX)
                .material(request.material())
                .careGuide(request.careGuide())
                .isActive(true)
                .tags(request.tags() != null ? new HashSet<>(request.tags()) : new HashSet<>())
                .build();

        // Khởi tạo các biến thể SKU
        List<ProductVariant> variants = new ArrayList<>();
        for (ProductDTO.CreateVariantRequest vReq : request.variants()) {
            String sku = StringUtils.hasText(vReq.skuCode())
                    ? vReq.skuCode().trim().toUpperCase()
                    : generateSkuCode(product.getSlug(), vReq.color(), vReq.size());

            if (variantRepository.existsBySkuCode(sku)) {
                throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại trong hệ thống");
            }

            ProductVariant variant = ProductVariant.builder()
                    .product(product)
                    .color(vReq.color().trim())
                    .colorCode(vReq.colorCode())
                    .size(vReq.size().trim().toUpperCase())
                    .skuCode(sku)
                    .price(vReq.price())
                    .comparePrice(vReq.comparePrice())
                    .stockQty(vReq.stockQty() != null ? vReq.stockQty() : 0)
                    .weightG(vReq.weightG())
                    .build();

            variants.add(variant);
        }
        product.setVariants(variants);

        // Thêm ảnh ban đầu nếu có
        if (request.images() != null && !request.images().isEmpty()) {
            List<ProductImage> images = new ArrayList<>();
            for (int i = 0; i < request.images().size(); i++) {
                ProductDTO.CreateImageRequest imgReq = request.images().get(i);
                boolean isPrimary = (i == 0 && imgReq.isPrimary() == null) || Boolean.TRUE.equals(imgReq.isPrimary());

                ProductImage img = ProductImage.builder()
                        .product(product)
                        .url(imgReq.url())
                        .sortOrder(imgReq.sortOrder() != null ? imgReq.sortOrder() : i)
                        .isPrimary(isPrimary)
                        .build();
                images.add(img);
            }
            product.setImages(images);
        }

        Product savedProduct = productRepository.save(product);
        return toDetailResponse(savedProduct);
    }

    // Cập nhật toàn diện thông tin sản phẩm và đồng bộ biến thể SKU
    @Transactional
    public ProductDTO.ProductResponse updateProduct(UUID productId, ProductDTO.UpdateProductRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

        String slug = resolveSlug(request.name(), request.slug(), productId);

        Brand brand = null;
        if (request.brandId() != null) {
            brand = brandRepository.findById(request.brandId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thương hiệu với ID: " + request.brandId()));
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục với ID: " + request.categoryId()));
        }

        // Cập nhật thông tin cơ bản
        product.setName(request.name().trim());
        product.setSlug(slug);
        product.setDescription(request.description());
        product.setBrand(brand);
        product.setCategory(category);
        if (request.gender() != null) {
            product.setGender(request.gender());
        }
        product.setMaterial(request.material());
        product.setCareGuide(request.careGuide());
        if (request.isActive() != null) {
            product.setIsActive(request.isActive());
        }
        if (request.tags() != null) {
            product.setTags(new HashSet<>(request.tags()));
        }

        // Đồng bộ danh sách biến thể SKU
        Map<UUID, ProductVariant> existingVariantsMap = new HashMap<>();
        for (ProductVariant existing : product.getVariants()) {
            existingVariantsMap.put(existing.getVariantId(), existing);
        }

        List<ProductVariant> updatedVariants = new ArrayList<>();
        for (ProductDTO.UpdateVariantRequest vReq : request.variants()) {
            if (vReq.variantId() != null && existingVariantsMap.containsKey(vReq.variantId())) {
                // Cập nhật SKU đã có
                ProductVariant variant = existingVariantsMap.remove(vReq.variantId());
                variant.setColor(vReq.color().trim());
                variant.setColorCode(vReq.colorCode());
                variant.setSize(vReq.size().trim().toUpperCase());
                variant.setPrice(vReq.price());
                variant.setComparePrice(vReq.comparePrice());
                if (vReq.stockQty() != null) {
                    variant.setStockQty(vReq.stockQty());
                }
                variant.setWeightG(vReq.weightG());

                if (StringUtils.hasText(vReq.skuCode()) && !vReq.skuCode().equalsIgnoreCase(variant.getSkuCode())) {
                    String newSku = vReq.skuCode().trim().toUpperCase();
                    if (variantRepository.existsBySkuCode(newSku)) {
                        throw new ConflictException("Mã SKU '" + newSku + "' đã tồn tại");
                    }
                    variant.setSkuCode(newSku);
                }
                updatedVariants.add(variant);
            } else {
                // Thêm mới SKU
                String sku = StringUtils.hasText(vReq.skuCode())
                        ? vReq.skuCode().trim().toUpperCase()
                        : generateSkuCode(product.getSlug(), vReq.color(), vReq.size());

                if (variantRepository.existsBySkuCode(sku)) {
                    throw new ConflictException("Mã SKU '" + sku + "' đã tồn tại");
                }

                ProductVariant newVariant = ProductVariant.builder()
                        .product(product)
                        .color(vReq.color().trim())
                        .colorCode(vReq.colorCode())
                        .size(vReq.size().trim().toUpperCase())
                        .skuCode(sku)
                        .price(vReq.price())
                        .comparePrice(vReq.comparePrice())
                        .stockQty(vReq.stockQty() != null ? vReq.stockQty() : 0)
                        .weightG(vReq.weightG())
                        .build();

                updatedVariants.add(newVariant);
            }
        }

        // Thay thế danh sách biến thể
        product.getVariants().clear();
        product.getVariants().addAll(updatedVariants);

        Product saved = productRepository.save(product);
        return toDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProductDTO.ProductResponse getProductById(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        return toDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductDTO.ProductResponse getProductBySlug(String slug) {
        Product product = productRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với slug: " + slug));
        return toDetailResponse(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductDTO.ProductSummaryResponse> searchProducts(
            String search,
            UUID categoryId,
            UUID brandId,
            Gender gender,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean isActive,
            Pageable pageable
    ) {
        String genderStr = (gender != null && gender != Gender.UNISEX) ? gender.name() : null;
        String cleanSearch = StringUtils.hasText(search) ? search.trim() : null;

        if (cleanSearch != null) {
            Pageable unpagedSort = org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize()
            );

            return productRepository.searchFullTextWithRelevance(
                    cleanSearch,
                    categoryId,
                    brandId,
                    genderStr,
                    minPrice,
                    maxPrice,
                    isActive,
                    unpagedSort
            ).map(this::toSummaryResponse);
        } else {
            Pageable dbSortPageable = mapToDbSortPageable(pageable);

            return productRepository.findAllFiltered(
                    categoryId,
                    brandId,
                    genderStr,
                    minPrice,
                    maxPrice,
                    isActive,
                    dbSortPageable
            ).map(this::toSummaryResponse);
        }
    }

    private Pageable mapToDbSortPageable(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return org.springframework.data.domain.PageRequest.of(
                    pageable.getPageNumber(),
                    pageable.getPageSize(),
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "created_at")
            );
        }

        List<org.springframework.data.domain.Sort.Order> dbOrders = new ArrayList<>();
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            String dbProperty = switch (order.getProperty()) {
                case "createdAt" -> "created_at";
                case "totalSold" -> "total_sold";
                case "avgRating" -> "avg_rating";
                case "name" -> "name";
                case "created_at", "total_sold", "avg_rating" -> order.getProperty();
                default -> "created_at";
            };
            dbOrders.add(new org.springframework.data.domain.Sort.Order(order.getDirection(), dbProperty));
        }

        return org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(dbOrders)
        );
    }

    @Transactional
    public void updateStatus(UUID productId, boolean isActive) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));
        product.setIsActive(isActive);
        productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy sản phẩm với ID: " + productId));

        // Thu thập URL ảnh để xóa trên S3 sau khi xóa DB
        List<String> imageUrls = product.getImages().stream().map(ProductImage::getUrl).toList();

        productRepository.delete(product);

        for (String url : imageUrls) {
            storageService.deleteFile(url);
        }
    }

    private String resolveSlug(String name, String customSlug, UUID currentProductId) {
        String baseSlug = StringUtils.hasText(customSlug) ? toSlug(customSlug) : toSlug(name);
        String finalSlug = baseSlug;
        int counter = 1;

        while (true) {
            Optional<Product> existing = productRepository.findBySlug(finalSlug);
            if (existing.isEmpty() || (currentProductId != null && existing.get().getProductId().equals(currentProductId))) {
                break;
            }
            finalSlug = baseSlug + "-" + counter++;
        }
        return finalSlug;
    }

    private String generateSkuCode(String productSlug, String color, String size) {
        String cleanSlug = productSlug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (cleanSlug.length() > 6) {
            cleanSlug = cleanSlug.substring(0, 6);
        }
        String cleanColor = color.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (cleanColor.length() > 3) {
            cleanColor = cleanColor.substring(0, 3);
        }
        String cleanSize = size.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        return cleanSlug + "-" + cleanColor + "-" + cleanSize + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }

    private String toSlug(String input) {
        if (!StringUtils.hasText(input)) return "san-pham";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String slug = pattern.matcher(normalized).replaceAll("")
                .toLowerCase()
                .replaceAll("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        return StringUtils.hasText(slug) ? slug : "san-pham";
    }

    private ProductDTO.ProductResponse toDetailResponse(Product product) {
        List<ProductDTO.VariantResponse> variantResponses = product.getVariants().stream()
                .map(v -> ProductDTO.VariantResponse.builder()
                        .variantId(v.getVariantId())
                        .productId(product.getProductId())
                        .color(v.getColor())
                        .colorCode(v.getColorCode())
                        .size(v.getSize())
                        .skuCode(v.getSkuCode())
                        .price(v.getPrice())
                        .comparePrice(v.getComparePrice())
                        .stockQty(v.getStockQty())
                        .weightG(v.getWeightG())
                        .createdAt(v.getCreatedAt())
                        .build())
                .toList();

        List<ProductImageDTO.ImageResponse> imageResponses = product.getImages().stream()
                .sorted(Comparator.comparing(ProductImage::getIsPrimary).reversed()
                        .thenComparing(ProductImage::getSortOrder))
                .map(img -> ProductImageDTO.ImageResponse.builder()
                        .imageId(img.getImageId())
                        .productId(product.getProductId())
                        .variantId(img.getVariant() != null ? img.getVariant().getVariantId() : null)
                        .url(img.getUrl())
                        .sortOrder(img.getSortOrder())
                        .isPrimary(img.getIsPrimary())
                        .createdAt(img.getCreatedAt())
                        .build())
                .toList();

        BigDecimal priceMin = product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal priceMax = product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return ProductDTO.ProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .slug(product.getSlug())
                .description(product.getDescription())
                .brandId(product.getBrand() != null ? product.getBrand().getBrandId() : null)
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .categoryId(product.getCategory() != null ? product.getCategory().getCategoryId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .gender(product.getGender())
                .material(product.getMaterial())
                .careGuide(product.getCareGuide())
                .isActive(product.getIsActive())
                .avgRating(product.getAvgRating())
                .totalSold(product.getTotalSold())
                .priceMin(priceMin)
                .priceMax(priceMax)
                .tags(product.getTags())
                .variants(variantResponses)
                .images(imageResponses)
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt() : OffsetDateTime.now())
                .build();
    }

    private ProductDTO.ProductSummaryResponse toSummaryResponse(Product product) {
        BigDecimal priceMin = product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal priceMax = product.getVariants().stream()
                .map(ProductVariant::getPrice)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        String primaryImageUrl = product.getImages().stream()
                .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                .findFirst()
                .map(ProductImage::getUrl)
                .orElseGet(() -> product.getImages().isEmpty() ? null : product.getImages().get(0).getUrl());

        return ProductDTO.ProductSummaryResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .slug(product.getSlug())
                .brandName(product.getBrand() != null ? product.getBrand().getName() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getSlug() : null)
                .gender(product.getGender())
                .isActive(product.getIsActive())
                .avgRating(product.getAvgRating())
                .totalSold(product.getTotalSold())
                .priceMin(priceMin)
                .priceMax(priceMax)
                .primaryImageUrl(primaryImageUrl)
                .createdAt(product.getCreatedAt() != null ? product.getCreatedAt(): OffsetDateTime.now())
                .build();
    }
}
