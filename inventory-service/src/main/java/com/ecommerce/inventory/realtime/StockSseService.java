package com.ecommerce.inventory.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time push channel for stock changes.
 *
 * Clients (Angular frontend) subscribe with an EventSource to
 * GET /api/inventory/events and receive a "stock-update" event on every
 * stock mutation. A ConcurrentHashMap holds the open emitters (one per
 * browser tab); unlike the order service the channel is global because
 * inventory events are not scoped to a single customer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockSseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE connection and returns the emitter that will
     * receive stock-update pushes. Emitters live until the client
     * disconnects, times out, or errors.
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 0 = never time out; client controls the connection
        String id = UUID.randomUUID().toString();
        emitters.put(id, emitter);
        emitter.onCompletion(() -> remove(id));
        emitter.onTimeout(() -> remove(id));
        emitter.onError(e -> remove(id));

        // Fire a heartbeat immediately so the client knows the channel is live.
        try {
            emitter.send(SseEmitter.event().name("connected").data("subscribed"));
        } catch (IOException e) {
            log.warn("Failed to send connect event to stock subscriber");
            remove(id);
        }
        log.info("Client subscribed to stock SSE ({} open connections)", emitters.size());
        return emitter;
    }

    /**
     * Broadcasts a stock event to every open connection. A failed send
     * (client gone) removes that emitter.
     */
    public void publish(Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name("stock-update")
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                log.warn("Removing stale SSE connection for stock subscriber");
                remove(entry.getKey());
            }
        }
    }

    private void remove(String id) {
        emitters.remove(id);
    }
}
