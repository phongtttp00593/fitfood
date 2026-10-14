package com.fitfood.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodCategoryRepository extends JpaRepository<FoodCategory, Long> {
    Optional<FoodCategory> findByNameIgnoreCase(String name);
    List<FoodCategory> findAllByOrderByIdDesc();
    long countByActiveTrue();
    long countByActiveFalse();
}
