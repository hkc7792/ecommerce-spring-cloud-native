package com.ecommerce.order.service.impl;

import com.ecommerce.commons.events.OrderPlaceEvent;
import com.ecommerce.order.entities.OutBoxEvent;
import com.ecommerce.order.repository.OutBoxEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxRelayWorker {

    private static final String NOTIFICATION_TOPIC = "notificationTopic";

    private final OutBoxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 2000) // Poll every 2 seconds for near-realtime delivery
    @Transactional
    public void relayEvents() {
        // 1. Fetch PENDING events that haven't hit the max limit
        List<OutBoxEvent> pendingEvents = outboxRepository
                .findAllByStatusAndRetryCountLessThan(OutBoxEvent.OutboxStatus.PENDING, 5);

        for (OutBoxEvent event : pendingEvents) {
            try {
                // 2. SET TO PROCESSING IMMEDIATELY
                event.setStatus(OutBoxEvent.OutboxStatus.PROCESSING);
                outboxRepository.saveAndFlush(event);

                // 3. Rebuild the typed event and send — JsonSerializer adds the
                //    type header so consumers deserialize to OrderPlaceEvent.
                OrderPlaceEvent orderPlaceEvent =
                        objectMapper.readValue(event.getPayload(), OrderPlaceEvent.class);
                kafkaTemplate.send(NOTIFICATION_TOPIC, event.getAggregateId(), orderPlaceEvent)
                        .get(5, TimeUnit.SECONDS); // Block briefly to ensure success

                // 4. If successful, update status to PUBLISH
                event.setStatus(OutBoxEvent.OutboxStatus.PUBLISH);

            } catch (Exception ex) {
                // 5. If it fails, increment retry count
                event.setRetryCount(event.getRetryCount() + 1);

                // 6. If it hits the limit (e.g., 5), mark as permanently FAILED
                if (event.getRetryCount() >= 5) {
                    event.setStatus(OutBoxEvent.OutboxStatus.FAILED);
                    log.error("Event {} reached max retries and is now FAILED", event.getId());
                } else {
                    event.setStatus(OutBoxEvent.OutboxStatus.PENDING);
                }
            }
            outboxRepository.save(event);
        }
    }
}
