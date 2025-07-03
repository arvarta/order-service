package org.ezon.msa.repository;

import java.util.List;

import org.ezon.msa.entity.OrderItem;
import org.ezon.msa.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
	List<OrderItem> findByOrderedNum(String orderedNum);
	List<OrderItem> findByUserId(Long sellerId);
	List<OrderItem> findByUserIdAndStatus(Long userId, OrderStatus status);
	List<OrderItem> findByProductId(Long productId);
}


