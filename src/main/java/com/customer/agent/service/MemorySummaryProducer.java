package com.customer.agent.service;

import com.customer.agent.config.RabbitConfig;
import com.customer.agent.dto.MemorySummaryTask;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemorySummaryProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(MemorySummaryTask task) {
        rabbitTemplate.convertAndSend(RabbitConfig.MEMORY_EXCHANGE, RabbitConfig.MEMORY_ROUTING_KEY, task);
    }
}
