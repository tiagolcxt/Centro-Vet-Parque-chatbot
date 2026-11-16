package com.clinicavet.assistant.conversation.application;

import com.clinicavet.assistant.conversation.domain.ConversationExpirationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConversationSettings.class)
class ConversationApplicationConfig {

    @Bean
    ConversationExpirationPolicy conversationExpirationPolicy(ConversationSettings settings) {
        return settings.expirationPolicy();
    }
}
