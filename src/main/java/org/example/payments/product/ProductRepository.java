package org.example.payments.product;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // 재고가 충분할 때만 원자적으로 차감한다. 갱신된 row 수를 반환한다(0이면 재고 부족)
    // 차감 후 같은 트랜잭션에서 Product를 다시 읽는 곳이 없어 clearAutomatically는 불필요
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity "
            + "WHERE p.id = :id AND p.stockQuantity >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);

    // 재고를 원자적으로 복구한다
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :quantity WHERE p.id = :id")
    int increaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
