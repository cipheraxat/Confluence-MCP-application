package com.rag.mcp.confluence;

import com.rag.mcp.model.ConfluencePage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfluenceExtractorService {
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private final ConfluenceClient confluenceClient;

    public ConfluenceExtractorService(ConfluenceClient confluenceClient) {
        this.confluenceClient = confluenceClient;
    }

    public List<ConfluencePage> fetchTree(String rootPageId, int maxDepth, int maxPages) throws Exception {
        List<ConfluencePage> pages = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<TraversalNode> queue = new ArrayDeque<>();
        queue.add(new TraversalNode(rootPageId, null, 0));

        while (!queue.isEmpty() && pages.size() < maxPages) {
            TraversalNode current = queue.poll();
            if (current.depth > maxDepth || visited.contains(current.pageId)) {
                continue;
            }
            visited.add(current.pageId);

            ConfluenceClient.PagePayload pagePayload = confluenceClient.fetchPage(current.pageId);
            String plainText = toPlainText(pagePayload.contentHtml());
            pages.add(new ConfluencePage(
                    pagePayload.pageId(),
                    pagePayload.title(),
                    current.parentId,
                    current.depth,
                    pagePayload.sourceUrl(),
                    plainText
            ));

            if (current.depth < maxDepth) {
                List<ConfluenceClient.ChildPageRef> children = confluenceClient.fetchChildren(current.pageId);
                for (ConfluenceClient.ChildPageRef child : children) {
                    if (!visited.contains(child.pageId())) {
                        queue.add(new TraversalNode(child.pageId(), current.pageId, current.depth + 1));
                    }
                }
            }
        }

        return pages;
    }

    private String toPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String text = TAG_PATTERN.matcher(html).replaceAll(" ");
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record TraversalNode(String pageId, String parentId, int depth) {
    }
}
