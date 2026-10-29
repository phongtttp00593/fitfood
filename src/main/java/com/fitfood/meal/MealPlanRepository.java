package com.fitfood.meal;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MealPlanRepository extends JpaRepository<MealPlan, Long> {
    List<MealPlan> findByActiveTrueOrderByNameAsc();
    Optional<MealPlan> findByIdAndActiveTrue(Long id);
}
