package com.fashion.store.modules.category;

import com.fashion.store.common.domain.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "size_guides")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SizeGuide extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @JsonIgnore
    private Category category;

    @Column(name = "gender", nullable = false, length = 20)
    @Builder.Default
    private String gender = "UNISEX";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "size_chart", columnDefinition = "jsonb", nullable = false)
    private String sizeChart;
    
}
