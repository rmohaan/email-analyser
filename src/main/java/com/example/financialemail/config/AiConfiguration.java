package com.example.financialemail.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@Configuration
public class AiConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ChatClient chatClient(OllamaChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    String financialEmailSystemPrompt(Resource financialEmailSystemPromptResource) throws IOException {
        return financialEmailSystemPromptResource.getContentAsString(StandardCharsets.UTF_8);
    }

    @Bean
    Resource financialEmailSystemPromptResource(
            org.springframework.core.io.ResourceLoader resourceLoader) {
        return resourceLoader.getResource("classpath:prompts/financial-email-system.st");
    }
}
