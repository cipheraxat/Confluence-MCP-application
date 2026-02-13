package com.rag.mcp.service;

import com.rag.mcp.confluence.ConfluenceExtractorService;
import com.rag.mcp.llm.LlmProvider;
import com.rag.mcp.llm.LlmProviderFactory;
import com.rag.mcp.model.ConfluencePage;
import com.rag.mcp.model.ProviderType;
import com.rag.mcp.model.QueryRequest;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QueryOrchestrator {
    public static final String DEFAULT_ROOT_URL = "https://akshatanand.atlassian.net/wiki/spaces/~5e80e683cb85aa0c1448bd0f/pages/327681/Software+architecture+review";
    private static final Pattern PAGE_ID_PATTERN = Pattern.compile("/pages/(\\d+)");

    private final ConfluenceExtractorService extractorService;
    private final LlmProviderFactory llmProviderFactory;

    public QueryOrchestrator(ConfluenceExtractorService extractorService, LlmProviderFactory llmProviderFactory) {
        this.extractorService = extractorService;
        this.llmProviderFactory = llmProviderFactory;
    }

    public Map<String, Object> process(QueryRequest request) throws Exception {
        validate(request);

        ProviderType providerType = ProviderType.from(request.getProvider());
        String rootUrl = request.getRootPageUrl() == null || request.getRootPageUrl().isBlank()
                ? DEFAULT_ROOT_URL
                : request.getRootPageUrl().trim();

        int maxDepth = request.getMaxDepth() == null ? 5 : Math.max(0, request.getMaxDepth());
        int maxPages = request.getMaxPages() == null ? 200 : Math.max(1, request.getMaxPages());

        String rootPageId = extractPageId(rootUrl);
        List<ConfluencePage> pages = extractorService.fetchTree(rootPageId, maxDepth, maxPages);

        String prompt = buildPrompt(request.getQuery(), rootUrl, pages);
        LlmProvider provider = llmProviderFactory.getProvider(providerType);
        String answer = provider.generate(prompt);

        // Parse referenced sources from the answer
        List<Map<String, Object>> referencedSources = extractReferencedSources(answer, pages);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("provider", provider.name());
        response.put("rootPageUrl", rootUrl);
        response.put("retrievedPageCount", pages.size());
        response.put("sources", referencedSources);
        response.put("answer", answer);
        return response;
    }

    public Map<String, Object> extractOnly(QueryRequest request) throws Exception {
        validateExtractionRequest(request);

        String rootUrl = request.getRootPageUrl() == null || request.getRootPageUrl().isBlank()
                ? DEFAULT_ROOT_URL
                : request.getRootPageUrl().trim();

        int maxDepth = request.getMaxDepth() == null ? 5 : Math.max(0, request.getMaxDepth());
        int maxPages = request.getMaxPages() == null ? 200 : Math.max(1, request.getMaxPages());

        String rootPageId = extractPageId(rootUrl);
        List<ConfluencePage> pages = extractorService.fetchTree(rootPageId, maxDepth, maxPages);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("mode", "extract-only");
        response.put("rootPageUrl", rootUrl);
        response.put("retrievedPageCount", pages.size());
        response.put("pages", pages.stream().map(page -> {
            Map<String, Object> pageData = new LinkedHashMap<>();
            pageData.put("pageId", page.getPageId());
            pageData.put("title", page.getTitle());
            pageData.put("parentId", page.getParentId());
            pageData.put("depth", page.getDepth());
            pageData.put("sourceUrl", page.getSourceUrl());
            pageData.put("content", page.getContent());
            return pageData;
        }).toList());
        return response;
    }

    private void validate(QueryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
        if (request.getQuery() == null || request.getQuery().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
    }

    private void validateExtractionRequest(QueryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }
    }

    private String extractPageId(String pageUrl) {
        URI uri = URI.create(pageUrl);
        Matcher matcher = PAGE_ID_PATTERN.matcher(uri.getPath());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not extract Confluence pageId from URL: " + pageUrl);
        }
        return matcher.group(1);
    }

    private String buildPrompt(String userQuestion, String rootUrl, List<ConfluencePage> pages) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            ConfluencePage page = pages.get(i);
            context.append("\n--- Source ").append(i + 1).append(" ---\n")
                    .append("Page ID  : ").append(page.getPageId()).append("\n")
                    .append("Title    : ").append(page.getTitle()).append("\n")
                    .append("Depth    : ").append(page.getDepth()).append("\n")
                    .append("URL      : ").append(page.getSourceUrl()).append("\n")
                    .append("Content  :\n").append(trim(page.getContent(), 4000)).append("\n");
        }

        return """
                You are an expert technical analyst specializing in Confluence knowledge base analysis.
                Your role is to provide comprehensive, well-structured, and actionable answers.

                INSTRUCTIONS:
                1. Use ONLY the Confluence context provided below. Do not infer or fabricate information.
                2. If information is insufficient, explicitly state what is missing.
                3. Reference specific source pages by title when citing information.
                4. Structure your response using the format below.

                RESPONSE FORMAT:
                ## Summary
                A concise 2-3 sentence overview answering the core question.

                ## Key Findings
                - Bullet points covering the main facts, decisions, or details found.
                - Group related points together logically.

                ## Details
                Expand on the key findings with relevant context, explanations, and relationships
                between different pieces of information. Use sub-headings if multiple topics are covered.

                ## Sources Referenced
                List each Confluence page title used in this answer.

                ## Gaps & Limitations
                Note any areas where the available documentation is incomplete or unclear.
                If no gaps exist, write "None identified."

                ---
                Root URL: """ + rootUrl + "\n" +
                "Total pages retrieved: " + pages.size() + "\n" +
                "User question: " + userQuestion + "\n" +
                "\nConfluence context:" + context;
    }

    private String trim(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "...";
    }

    private List<Map<String, Object>> extractReferencedSources(String answer, List<ConfluencePage> allPages) {
        List<Map<String, Object>> referencedSources = new java.util.ArrayList<>();

        // Find the "Sources Referenced" section in the answer
        Pattern sourcesPattern = Pattern.compile("## Sources Referenced\\s*(.*?)(?=\\n##|\\n---|\\n*$)", Pattern.DOTALL);
        Matcher sourcesMatcher = sourcesPattern.matcher(answer);

        if (sourcesMatcher.find()) {
            String sourcesSection = sourcesMatcher.group(1);
            // Extract page titles from the sources section
            Pattern titlePattern = Pattern.compile("(.+?)(?=\\n|$)");
            Matcher titleMatcher = titlePattern.matcher(sourcesSection);

            while (titleMatcher.find()) {
                String title = titleMatcher.group(1).trim();
                if (!title.isEmpty() && !title.equals("## Sources Referenced")) {
                    // Find the matching page by title
                    for (ConfluencePage page : allPages) {
                        if (page.getTitle() != null && page.getTitle().trim().equalsIgnoreCase(title)) {
                            Map<String, Object> source = new LinkedHashMap<>();
                            source.put("pageId", page.getPageId());
                            source.put("title", page.getTitle());
                            source.put("parentId", page.getParentId());
                            source.put("depth", page.getDepth());
                            source.put("sourceUrl", page.getSourceUrl());
                            referencedSources.add(source);
                            break; // Found the matching page, no need to continue searching
                        }
                    }
                }
            }
        }

        // If no sources were referenced or parsing failed, return all sources as fallback
        if (referencedSources.isEmpty()) {
            for (ConfluencePage page : allPages) {
                Map<String, Object> source = new LinkedHashMap<>();
                source.put("pageId", page.getPageId());
                source.put("title", page.getTitle());
                source.put("parentId", page.getParentId());
                source.put("depth", page.getDepth());
                source.put("sourceUrl", page.getSourceUrl());
                referencedSources.add(source);
            }
        }

        return referencedSources;
    }
}
