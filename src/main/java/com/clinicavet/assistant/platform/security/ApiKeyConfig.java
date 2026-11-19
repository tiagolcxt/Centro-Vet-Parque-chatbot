package com.clinicavet.assistant.platform.security;

import com.clinicavet.assistant.platform.ClinicId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registra o {@link ApiKeyFilter} em {@code /api/*} (todas as rotas abaixo de {@code /api}).
 * <ul>
 *   <li>A chave principal vem de {@code ASSISTANT_API_KEY} (variável de ambiente ou {@code .env}, nunca no
 *       repositório) e pertence à clínica {@code assistant.security.api-key-clinic} (padrão: a clínica fictícia
 *       criada na V5);</li>
 *   <li>{@code assistant.security.additional-api-keys} aceita outras chaves no formato {@code idDaClinica=chave},
 *       separadas por vírgula. Hoje só os testes de isolamento entre clínicas usam (ADR-0021).</li>
 * </ul>
 * Sem chave, ou com chave curta, a aplicação <b>não sobe</b>: é melhor do que subir com a API aberta.
 */
@Configuration
class ApiKeyConfig {

    static final int MIN_KEY_LENGTH = 32;

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(
            @Value("${assistant.security.api-key:}") String apiKey,
            @Value("${assistant.security.api-key-clinic}") String apiKeyClinic,
            @Value("${assistant.security.additional-api-keys:}") List<String> additional) {
        Map<String, ClinicId> keys = new LinkedHashMap<>();
        keys.put(validated(apiKey, "ASSISTANT_API_KEY"), ClinicId.of(apiKeyClinic.strip()));
        for (String entry : additional) {
            if (entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator < 0) {
                throw new IllegalStateException("assistant.security.additional-api-keys: use idDaClinica=chave");
            }
            String key = validated(entry.substring(separator + 1), "assistant.security.additional-api-keys");
            if (keys.putIfAbsent(key, ClinicId.of(entry.substring(0, separator).strip())) != null) {
                throw new IllegalStateException("A mesma chave não pode pertencer a duas clínicas.");
            }
        }
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(new ApiKeyFilter(keys));
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setName("apiKeyFilter");
        return registration;
    }

    private static String validated(String key, String name) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    name + " não definida. Defina no .env (desenvolvimento) ou como variável de ambiente.");
        }
        if (key.strip().length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    name + " curta demais: use pelo menos " + MIN_KEY_LENGTH + " caracteres aleatórios.");
        }
        return key.strip();
    }
}
