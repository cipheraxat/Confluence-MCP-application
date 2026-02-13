package com.rag.mcp.llm;

public interface LlmProvider {
    String generate(String prompt) throws Exception;

    String name();
}
