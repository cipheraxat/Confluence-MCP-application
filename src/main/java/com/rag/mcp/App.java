package com.rag.mcp;

import com.rag.mcp.confluence.ConfluenceClient;
import com.rag.mcp.confluence.ConfluenceExtractorService;
import com.rag.mcp.http.McpHttpServer;
import com.rag.mcp.llm.LlmProviderFactory;
import com.rag.mcp.service.QueryOrchestrator;

public class App {
    public static void main(String[] args) throws Exception {
        String baseUrl = requiredEnv("CONFLUENCE_BASE_URL");
        String email = requiredEnv("CONFLUENCE_EMAIL");
        String token = requiredEnv("CONFLUENCE_API_TOKEN");
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        ConfluenceClient confluenceClient = new ConfluenceClient(baseUrl, email, token);
        ConfluenceExtractorService extractorService = new ConfluenceExtractorService(confluenceClient);
        QueryOrchestrator orchestrator = new QueryOrchestrator(extractorService, new LlmProviderFactory());
        new McpHttpServer(orchestrator, port).start();
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }
}
