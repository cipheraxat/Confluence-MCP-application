package com.rag.mcp.model;

public enum ProviderType {
    BEDROCK,
    GEMINI,
    GITLAB_DUO;

    public static ProviderType from(String value) {
        if (value == null || value.isBlank()) {
            return BEDROCK;
        }
        return ProviderType.valueOf(value.trim().toUpperCase());
    }
}
