package com.rag.mcp.llm;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

public class BedrockProvider implements LlmProvider {
    @Override
    public String generate(String prompt) {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        String modelId = System.getenv().getOrDefault("BEDROCK_MODEL_ID", "anthropic.claude-3-5-sonnet-20240620-v1:0");

        try (BedrockRuntimeClient client = BedrockRuntimeClient.builder().region(Region.of(region)).build()) {
            Message userMessage = Message.builder()
                    .role(ConversationRole.USER)
                    .content(ContentBlock.builder().text(prompt).build())
                    .build();

            ConverseRequest request = ConverseRequest.builder()
                    .modelId(modelId)
                    .messages(userMessage)
                    .inferenceConfig(InferenceConfiguration.builder()
                            .maxTokens(2048)
                            .temperature(0.3f)
                            .build())
                    .build();

            return client.converse(request)
                    .output()
                    .message()
                    .content()
                    .stream()
                    .map(ContentBlock::text)
                    .filter(text -> text != null && !text.isBlank())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Bedrock response had no text output"));
        }
    }

    @Override
    public String name() {
        return "bedrock";
    }
}
