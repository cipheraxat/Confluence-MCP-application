package com.rag.mcp.model;

import java.util.List;

public class QueryRequest {
    private String query;
    private String provider;
    private String rootPageUrl; // Keep for backward compatibility
    private List<String> rootPageUrls; // New field for multiple URLs
    private Integer maxDepth;
    private Integer maxPages;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getRootPageUrl() {
        return rootPageUrl;
    }

    public void setRootPageUrl(String rootPageUrl) {
        this.rootPageUrl = rootPageUrl;
    }

    public List<String> getRootPageUrls() {
        return rootPageUrls;
    }

    public void setRootPageUrls(List<String> rootPageUrls) {
        this.rootPageUrls = rootPageUrls;
    }

    public Integer getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(Integer maxDepth) {
        this.maxDepth = maxDepth;
    }

    public Integer getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(Integer maxPages) {
        this.maxPages = maxPages;
    }
}
