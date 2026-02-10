package com.example.transcriptionai.network.model;

import java.util.List;

public class ChatCompletionRequest {
    private final String model;
    private final List<ChatMessage> messages;
    private final double temperature;

    public ChatCompletionRequest(String model, List<ChatMessage> messages, double temperature) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
    }

    public String getModel() {
        return model;
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public double getTemperature() {
        return temperature;
    }
}
