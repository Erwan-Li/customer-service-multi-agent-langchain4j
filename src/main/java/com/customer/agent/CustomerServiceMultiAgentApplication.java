package com.customer.agent;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableRabbit
@SpringBootApplication
public class CustomerServiceMultiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceMultiAgentApplication.class, args);
    }
}
