package com.ecommerce.order.service.impl;

import com.ecommerce.order.dto.OrderRequest;
import com.ecommerce.order.entities.OutBoxEvent;
import com.ecommerce.order.repository.OutBoxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OutboxService {
    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveFailedEvent(OrderRequest orderRequest, String orderNumber) {
        try {
            String jsonPayload = objectMapper.writeValueAsString(orderRequest);

            OutBoxEvent event = OutBoxEvent.builder()
                    .aggregateType("ORDER")
                    .aggregateId(orderNumber)
                    .eventType("ORDER_PLACED")
                    .payload(jsonPayload)
                    .status(OutBoxEvent.OutboxStatus.PENDING) // Use Enum here
                    .retryCount(0)
                    .build();

            outBoxEventRepository.save(event);
        } catch (Exception ex) {
            // Log error
        }
    }
}
