package com.ecommerce.order.repository;

import com.ecommerce.commons.enums.OrderStatus;
import com.ecommerce.order.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByCustomerId(Long customerId, Pageable pageable);

    Page<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status, Pageable pageable);
}
