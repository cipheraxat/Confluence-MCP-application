package com.rag.mcp.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class GitLabDuoProvider implements LlmProvider {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    @Override
    public String generate(String prompt) throws Exception {
        String token = System.getenv("GITLAB_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GITLAB_TOKEN is not configured");
        }

        String baseUrl = System.getenv().getOrDefault("GITLAB_BASE_URL", "https://gitlab.com");
        String model = System.getenv().getOrDefault("GITLAB_DUO_MODEL", "claude-3-5-sonnet-latest");
        String endpoint = baseUrl + "/api/v4/chat/completions";

        String payload = MAPPER.writeValueAsString(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "You are an expert technical analyst."),
                        Map.of("role", "user", "content", prompt)
                ),
                "max_tokens", 2048,
                "temperature", 0.3,
                "stream", false
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(90))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitLab Duo request failed: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = MAPPER.readTree(response.body());
        JsonNode textNode = root.path("choices").path(0).path("message").path("content");
        if (textNode.isMissingNode() || textNode.isNull()) {
            throw new IllegalStateException("GitLab Duo response had no text output");
        }
        return textNode.asText();
    }

    @Override
    public String name() {
        return "gitlab_duo";
    }
}
