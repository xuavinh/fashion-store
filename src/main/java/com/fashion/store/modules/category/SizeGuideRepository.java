package com.fashion.store.modules.category;

import com.fashion.store.modules.product.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SizeGuideRepository extends JpaRepository<SizeGuide, UUID> {

    @Query("SELECT sg FROM SizeGuide sg JOIN FETCH sg.category WHERE sg.category.categoryId = :categoryId AND sg.gender = :gender")
    Optional<SizeGuide> findByCategoryCategoryIdAndGender(@Param("categoryId") UUID categoryId, @Param("gender") Gender gender);

    @Query("SELECT sg FROM SizeGuide sg JOIN FETCH sg.category WHERE sg.category.categoryId = :categoryId")
    List<SizeGuide> findByCategoryCategoryId(@Param("categoryId") UUID categoryId);

    @Query("SELECT sg FROM SizeGuide sg JOIN FETCH sg.category WHERE sg.guideId = :guideId")
    Optional<SizeGuide> findWithCategoryByGuideId(@Param("guideId") UUID guideId);
}
