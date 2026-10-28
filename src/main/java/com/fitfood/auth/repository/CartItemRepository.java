
package com.fitfood.cart.repository;

import com.fitfood.cart.model.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository
        extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCart_IdOrderByIdDesc(Long cartId);

    Optional<CartItem> findByCart_IdAndProduct_Id(
            Long cartId, Long productId);

    Optional<CartItem> findByIdAndCart_Id(
            Long itemId, Long cartId);
}
