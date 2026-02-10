package com.example.transcriptionai.network.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class ChatCompletionResponse {
    private List<Choice> choices;

    public List<Choice> getChoices() {
        return choices;
    }

    public static class Choice {
        @SerializedName("message")
        private ChatMessageResponse message;

        public ChatMessageResponse getMessage() {
            return message;
        }
    }

    public static class ChatMessageResponse {
        private String content;

        public String getContent() {
            return content;
        }
    }
}
