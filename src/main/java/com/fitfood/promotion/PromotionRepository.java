/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitfood.promotion;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromotionRepository
        extends JpaRepository<Promotion, Long> {

    boolean existsByCodeIgnoreCase(String code);

    List<Promotion> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByIdDesc(
            String code, String name
    );

    List<Promotion> findAllByOrderByIdDesc();
}
