package org.example.payments.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity "
            + "WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
