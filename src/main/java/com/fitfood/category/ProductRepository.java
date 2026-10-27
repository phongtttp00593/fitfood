package com.fitfood.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    boolean existsByNameIgnoreCase(String name);
}