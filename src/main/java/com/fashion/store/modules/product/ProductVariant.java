package com.fashion.store.modules.product;

import com.fashion.store.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "variant_id", nullable = false, updatable = false)
    private UUID variantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @Column(name = "color", nullable = false, length = 50)
    private String color;

    @Column(name = "color_code", length = 10)
    private String colorCode;

    @Column(name = "size", nullable = false, length = 20)
    private String size;

    @Column(name = "sku_code", nullable = false, unique = true, length = 100)
    private String skuCode;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "compare_price", precision = 12, scale = 2)
    private BigDecimal comparePrice;

    @Column(name = "stock_qty", nullable = false)
    @Builder.Default
    private Integer stockQty = 0;

    @Column(name = "weight_g")
    private Integer weightG;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();
}
