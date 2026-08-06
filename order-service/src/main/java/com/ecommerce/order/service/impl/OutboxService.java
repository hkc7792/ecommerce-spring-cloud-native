package com.ecommerce.order.service.impl;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.order.entities.OutBoxEvent;
import com.ecommerce.order.repository.OutBoxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Persists an OrderPlaceEvent that could not be published to Kafka.
     * The OutboxRelayWorker polls PENDING rows and retries publishing.
     */
    @Transactional
    public void saveFailedEvent(OrderPlaceEvent event) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);

            OutBoxEvent outboxEvent = OutBoxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(event.orderNumber())
                    .eventType("ORDER_PLACED")
                    .payload(jsonPayload)
                    .status(OutBoxEvent.OutboxStatus.PENDING)
                    .retryCount(0)
                    .build();

            outBoxEventRepository.save(outboxEvent);
        } catch (Exception ex) {
            log.error("Failed to persist outbox event for order {}", event.orderNumber(), ex);
        }
    }
}
