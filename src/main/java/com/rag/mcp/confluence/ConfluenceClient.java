package com.rag.mcp.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ConfluenceClient {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String baseUrl;
    private final String authHeader;

    public ConfluenceClient(String baseUrl, String email, String apiToken) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        String raw = email + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public PagePayload fetchPage(String pageId) throws Exception {
        String url = baseUrl + "/wiki/rest/api/content/" + encode(pageId) + "?expand=body.storage,version";
        JsonNode root = get(url);

        String id = text(root, "id", pageId);
        String title = text(root, "title", "Untitled");
        String body = root.path("body").path("storage").path("value").asText("");
        String webUi = root.path("_links").path("webui").asText("");
        String sourceUrl = webUi.isBlank() ? baseUrl : baseUrl + webUi;

        return new PagePayload(id, title, body, sourceUrl);
    }

    public List<ChildPageRef> fetchChildren(String parentId) throws Exception {
        List<ChildPageRef> children = new ArrayList<>();
        int start = 0;
        int limit = 100;

        while (true) {
            String url = baseUrl + "/wiki/rest/api/content/" + encode(parentId) + "/child/page?limit=" + limit + "&start=" + start;
            JsonNode root = get(url);
            JsonNode results = root.path("results");
            if (!results.isArray() || results.isEmpty()) {
                break;
            }

            for (JsonNode result : results) {
                String id = text(result, "id", null);
                if (id == null || id.isBlank()) {
                    continue;
                }
                String title = text(result, "title", "Untitled");
                children.add(new ChildPageRef(id, title, parentId));
            }

            if (results.size() < limit) {
                break;
            }
            start += limit;
        }

        return children;
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Confluence request failed: " + status + " - " + response.body());
        }
        return MAPPER.readTree(response.body());
    }

    private static String normalizeBaseUrl(String input) {
        String normalized = input == null ? "" : input.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/wiki")) {
            normalized = normalized.substring(0, normalized.length() - 5);
        }
        return normalized;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? fallback : value.asText(fallback);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record PagePayload(String pageId, String title, String contentHtml, String sourceUrl) {
    }

    public record ChildPageRef(String pageId, String title, String parentId) {
    }
}
