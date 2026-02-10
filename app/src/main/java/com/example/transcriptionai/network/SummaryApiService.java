package com.example.transcriptionai.network;

import com.example.transcriptionai.network.model.ChatCompletionRequest;
import com.example.transcriptionai.network.model.ChatCompletionResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface SummaryApiService {
    @POST("chat/completions")
    Call<ChatCompletionResponse> summarize(@Body ChatCompletionRequest request);
}
