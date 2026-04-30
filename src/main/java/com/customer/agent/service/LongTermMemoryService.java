package com.customer.agent.service;

import java.util.List;

public interface LongTermMemoryService {

    String loadUserProfile(String userId);

    List<String> recallHistoricalTickets(String userId, String query, int topK);

    void rememberConversation(String conversationId, String userId, String content, String summary, List<String> tags);

    void rememberQaFeedback(String conversationId, String userId, String content, List<String> issues);
}
