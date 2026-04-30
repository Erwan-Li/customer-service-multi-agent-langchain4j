package com.customer.agent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageRequest {

    private String conversationId;

    @NotBlank
    private String userId;

    @NotBlank
    private String channel;

    @NotBlank
    private String message;
}
