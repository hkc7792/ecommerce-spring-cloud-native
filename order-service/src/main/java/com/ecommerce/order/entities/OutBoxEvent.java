package com.ecommerce.order.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "Out_Box")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutBoxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String aggregateType; // Order, Inventory, etc.

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payload; // Store as JSON string

    @Builder.Default
    private int retryCount=0 ;

    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime lastModifiedAt;



    public enum OutboxStatus {
        PENDING,    // Just saved to DB, not yet picked up
        PROCESSING, // Currently being sent to Kafka
        PUBLISH,    // Successfully acknowledged by Kafka
        FAILED      // Exhausted all retries or critical error
    }
}