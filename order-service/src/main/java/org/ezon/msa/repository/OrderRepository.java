package org.ezon.msa.repository;

import java.util.List;
import java.util.Set;

import org.ezon.msa.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByUserId(Long userId);
    List<Order> findByOrderedNumIn(Set<String> orderedNums);
    boolean existsByPaymentId(Long paymentId);
}


