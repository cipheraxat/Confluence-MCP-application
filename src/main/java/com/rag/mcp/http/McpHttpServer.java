package com.rag.mcp.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.mcp.model.QueryRequest;
import com.rag.mcp.service.QueryOrchestrator;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class McpHttpServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final QueryOrchestrator orchestrator;
    private final int port;

    public McpHttpServer(QueryOrchestrator orchestrator, int port) {
        this.orchestrator = orchestrator;
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/query", exchange -> {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                    return;
                }

                QueryRequest request = readRequest(exchange.getRequestBody());
                Map<String, Object> response = orchestrator.process(request);
                sendJson(exchange, 200, response);
            } catch (Exception ex) {
                sendJson(exchange, 400, errorPayload(ex));
            }
        });

        server.createContext("/api/extract", exchange -> {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, Map.of("status", "error", "message", "Method not allowed"));
                    return;
                }

                QueryRequest request = readRequest(exchange.getRequestBody());
                Map<String, Object> response = orchestrator.extractOnly(request);
                sendJson(exchange, 200, response);
            } catch (Exception ex) {
                sendJson(exchange, 400, errorPayload(ex));
            }
        });

        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveFile(exchange, Path.of("ui/index.html"), "text/html; charset=UTF-8");
                return;
            }
            if ("/app.js".equals(path)) {
                serveFile(exchange, Path.of("ui/app.js"), "application/javascript; charset=UTF-8");
                return;
            }
            sendJson(exchange, 404, Map.of("status", "error", "message", "Not found"));
        });

        server.setExecutor(null);
        server.start();
        System.out.println("MCP server running at http://localhost:" + port);
    }

    private QueryRequest readRequest(InputStream inputStream) throws IOException {
        return MAPPER.readValue(inputStream, QueryRequest.class);
    }

    private void sendJson(HttpExchange exchange, int statusCode, Map<String, Object> payload) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(payload);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=UTF-8");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void serveFile(HttpExchange exchange, Path filePath, String contentType) throws IOException {
        if (!Files.exists(filePath)) {
            sendJson(exchange, 404, new LinkedHashMap<>(Map.of("status", "error", "message", "File not found")));
            return;
        }
        byte[] body = Files.readAllBytes(filePath);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private Map<String, Object> errorPayload(Exception ex) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "error");
        String message = ex.getMessage();
        payload.put("message", (message == null || message.isBlank()) ? ex.getClass().getSimpleName() : message);
        return payload;
    }
}
