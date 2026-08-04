package com.ecommerce.order.service.impl;

import com.ecommerce.order.entities.OutBoxEvent;
import com.ecommerce.order.repository.OutBoxEventRepository;
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

    private final OutBoxEventRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 30000) // Runs every 30 seconds
    @Transactional
    public void relayEvents() {
        // 1. Fetch PENDING or FAILED events that haven't hit the max limit
        List<OutBoxEvent> pendingEvents = outboxRepository
                .findAllByEventStatusAndRetryCountLessThan(OutBoxEvent.OutboxStatus.PENDING, 5);

        for (OutBoxEvent event : pendingEvents) {
            try {
                // 2. SET TO PROCESSING IMMEDIATELY
                event.setStatus(OutBoxEvent.OutboxStatus.PROCESSING);
                outboxRepository.saveAndFlush(event);
                // 3. Attempt to send to Kafka
                kafkaTemplate.send("notificationTopic", event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS); // Block briefly to ensure success

                // 4. If successful, update status to PUBLISH
                event.setStatus(OutBoxEvent.OutboxStatus.PUBLISH);

            } catch (Exception ex) {
                // 4. If fails, increment retry count
                event.setRetryCount(event.getRetryCount() + 1);

                // 5. If it hits the limit (e.g., 5), mark as permanently FAILED
                if (event.getRetryCount() >= 5) {
                    event.setStatus(OutBoxEvent.OutboxStatus.FAILED);
                    log.error("Event {} reached max retries and is now FAILED", event.getId());
                }else{
                    event.setStatus(OutBoxEvent.OutboxStatus.PENDING);
                }

            }
            outboxRepository.save(event);
        }
    }
}