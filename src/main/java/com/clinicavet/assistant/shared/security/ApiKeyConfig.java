package com.clinicavet.assistant.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registra o {@link ApiKeyFilter} em {@code /api/*} (todas as rotas abaixo de {@code /api}).
 * A chave vem de {@code ASSISTANT_API_KEY} (variável de ambiente ou {@code .env}, nunca no
 * repositório). Sem chave, ou com chave curta, a aplicação <b>não sobe</b>: é melhor do que subir
 * com a API aberta.
 */
@Configuration
class ApiKeyConfig {

    static final int MIN_KEY_LENGTH = 32;

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${assistant.security.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ASSISTANT_API_KEY não definida. Defina no .env (desenvolvimento) ou como variável de ambiente.");
        }
        if (apiKey.strip().length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "ASSISTANT_API_KEY curta demais: use pelo menos " + MIN_KEY_LENGTH + " caracteres aleatórios.");
        }
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(new ApiKeyFilter(apiKey.strip()));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("apiKeyFilter");
        return registration;
    }
}
