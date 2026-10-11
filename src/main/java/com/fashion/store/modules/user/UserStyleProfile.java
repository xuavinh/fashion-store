package com.fashion.store.modules.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_style_profiles")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserStyleProfile {
    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JsonIgnore
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_styles", columnDefinition = "jsonb")
    private List<String> preferredStyles;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_colors", columnDefinition = "jsonb")
    private List<String> preferredColors;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "body_measurements", columnDefinition = "jsonb")
    private Map<String, Object> bodyMeasurements; // { "height": 170, "weight": 65, "chest": 92, "waist": 78, "hip": 95 }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_size", columnDefinition = "jsonb")
    private Map<String, String> defaultSize; // { "top": "M", "bottom": "L", "shoes": "40" }

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", length = 50)
    private AgeGroup ageGroup;

    @Column(name = "gender_pref", length = 20)
    private String genderPref;
}
