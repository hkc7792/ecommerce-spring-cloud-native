package com.ecommerce.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaRetryTopic;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Kafka configuration for payment-service.
 *
 * The consumer factory / listener container factory are auto-configured by Spring Boot
 * from the properties in application.yml ({@code spring.kafka.consumer.*}), including the
 * JsonDeserializer trusted-packages / default-type settings
 * ({@code spring.kafka.consumer.properties.spring.json.*}).
 *
 * {@link EnableKafkaRetryTopic} enables the non-blocking, topic-based retry + DLT
 * machinery used by the {@code @RetryableTopic} listener in
 * {@code com.ecommerce.payment.consumer.OrderEventConsumer}. A {@link TaskScheduler}
 * bean is required by that machinery to schedule retry backoffs.
 */
@Configuration
@EnableKafkaRetryTopic
public class KafkaConfig {

    /**
     * Scheduler used by the retry-topic machinery to pause consumer partitions while
     * retry backoff delays elapse (see {@code @RetryableTopic(backoff = ...)} on the
     * consumer). Required because {@code @RetryableTopic} needs a {@link TaskScheduler}.
     */
    @Bean
    public TaskScheduler kafkaRetryTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("kafka-retry-");
        return scheduler;
    }
}
