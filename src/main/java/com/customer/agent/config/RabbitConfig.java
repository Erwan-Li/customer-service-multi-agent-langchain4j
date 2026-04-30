package com.customer.agent.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String MEMORY_EXCHANGE = "customer.agent.memory.exchange";
    public static final String MEMORY_QUEUE = "customer.agent.memory.queue";
    public static final String MEMORY_ROUTING_KEY = "customer.agent.memory.summary";

    @Bean
    public DirectExchange memoryExchange() {
        return new DirectExchange(MEMORY_EXCHANGE, true, false);
    }

    @Bean
    public Queue memoryQueue() {
        return new Queue(MEMORY_QUEUE, true);
    }

    @Bean
    public Binding memoryBinding(Queue memoryQueue, DirectExchange memoryExchange) {
        return BindingBuilder.bind(memoryQueue).to(memoryExchange).with(MEMORY_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jacksonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jacksonMessageConverter);
        return rabbitTemplate;
    }
}
