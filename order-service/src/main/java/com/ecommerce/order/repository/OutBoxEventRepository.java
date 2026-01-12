package com.ecommerce.order.repository;

import com.ecommerce.order.entities.OutBoxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutBoxEventRepository extends JpaRepository<OutBoxEvent, UUID> {
    //// Find events that still have a chance to be published
    List<OutBoxEvent> findAllByEventStatusAndRetryCountLessThan(OutBoxEvent.OutboxStatus outboxStatus, int maxRetries);
}
