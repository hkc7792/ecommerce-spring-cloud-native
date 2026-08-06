package com.ecommerce.order.realtime;

import com.ecommerce.commons.events.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Real-time push channel for order status updates.
 *
 * Clients (Angular frontend) subscribe with an EventSource to
 * GET /api/order/events/{customerId} and receive an "order-status"
 * event on every state transition. A map keyed by customerId holds
 * the open emitters (one per browser tab).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderStatusSseService {

    private final Map<Long, List<SseEmitter>> emittersByCustomer = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE connection for a customer and returns the emitter
     * that will receive order-status pushes. Emitters live until the client
     * disconnects, times out, or errors.
     */
    public SseEmitter subscribe(Long customerId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = never time out; client controls the connection
        emitter.onCompletion(() -> remove(customerId, emitter));
        emitter.onTimeout(() -> remove(customerId, emitter));
        emitter.onError(e -> remove(customerId, emitter));

        emittersByCustomer.computeIfAbsent(customerId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        // Fire a heartbeat immediately so the client knows the channel is live.
        try {
            emitter.send(SseEmitter.event().name("connected").data("subscribed"));
        } catch (IOException e) {
            log.warn("Failed to send connect event to customer {}", customerId);
            remove(customerId, emitter);
        }
        log.info("Customer {} subscribed to order status SSE ({} open connections)",
                customerId, emittersByCustomer.getOrDefault(customerId, List.of()).size());
        return emitter;
    }

    /**
     * Pushes a status change to every open connection for the order's customer.
     * A failed send (client gone) removes that emitter.
     */
    public void publishStatus(OrderStatusChangedEvent event) {
        List<SseEmitter> emitters = emittersByCustomer.get(event.customerId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("order-status")
                        .data(event));
            } catch (IOException | IllegalStateException e) {
                log.warn("Removing stale SSE connection for customer {}", event.customerId());
                remove(event.customerId(), emitter);
            }
        }
    }

    private void remove(Long customerId, SseEmitter emitter) {
        emittersByCustomer.computeIfPresent(customerId, (id, list) -> {
            list.remove(emitter);
            return list.isEmpty() ? null : list;
        });
    }
}
