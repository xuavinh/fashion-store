package com.fashion.store.modules.category;

import com.fashion.store.common.domain.BaseEntity;
import com.fashion.store.modules.product.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "categories")
public class Category extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "category_id", nullable = false, updatable = false)
    private UUID categoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @JsonIgnore
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Category> subCategories = new ArrayList<>();

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 200)
    private String slug;

    @Column(name = "level", nullable = false)
    @Builder.Default
    private Short level = 1;

    @Column(name = "gender_filter", nullable = false, length = 20)
    @Builder.Default
    private String genderFilter = "ALL";

    @JsonIgnore
    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Product> products = new ArrayList<>();
}
