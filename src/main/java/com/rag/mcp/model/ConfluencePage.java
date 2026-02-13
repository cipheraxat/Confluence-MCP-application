package com.rag.mcp.model;

public class ConfluencePage {
    private final String pageId;
    private final String title;
    private final String parentId;
    private final int depth;
    private final String sourceUrl;
    private final String content;

    public ConfluencePage(String pageId, String title, String parentId, int depth, String sourceUrl, String content) {
        this.pageId = pageId;
        this.title = title;
        this.parentId = parentId;
        this.depth = depth;
        this.sourceUrl = sourceUrl;
        this.content = content;
    }

    public String getPageId() {
        return pageId;
    }

    public String getTitle() {
        return title;
    }

    public String getParentId() {
        return parentId;
    }

    public int getDepth() {
        return depth;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContent() {
        return content;
    }
}
