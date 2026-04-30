package com.customer.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LangChain4jConfig {

    @Bean
    public ChatModel chatModel(@Value("${langchain.llm.base-url}") String baseUrl,
                               @Value("${langchain.llm.api-key}") String apiKey,
                               @Value("${langchain.llm.model-name}") String modelName,
                               @Value("${langchain.llm.temperature:0.3}") Double temperature,
                               @Value("${langchain.llm.max-tokens:4096}") Integer maxTokens) {
        return OpenAiOfficialChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxCompletionTokens(maxTokens)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(@Value("${langchain.llm.base-url}") String baseUrl,
                                         @Value("${langchain.llm.api-key}") String apiKey,
                                         @Value("${langchain.llm.embedding-model-name}") String embeddingModelName) {
        return OpenAiOfficialEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModelName)
                .build();
    }
}
