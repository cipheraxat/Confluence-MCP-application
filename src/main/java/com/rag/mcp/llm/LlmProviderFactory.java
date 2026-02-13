package com.rag.mcp.llm;

import com.rag.mcp.model.ProviderType;

public class LlmProviderFactory {
    public LlmProvider getProvider(ProviderType providerType) {
        return switch (providerType) {
            case BEDROCK -> new BedrockProvider();
            case GEMINI -> new GeminiProvider();
            case GITLAB_DUO -> new GitLabDuoProvider();
        };
    }
}
