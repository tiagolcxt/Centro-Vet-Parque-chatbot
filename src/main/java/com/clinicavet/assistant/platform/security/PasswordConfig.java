package com.clinicavet.assistant.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/** Vale também sem servidor web (testes e tarefas): o {@code AccountService} precisa do codificador. */
@Configuration
class PasswordConfig {

    /** bcrypt, com o prefixo do algoritmo no hash (troca futura sem migrar senhas). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Onde o login guarda a sessão (usado pelo AuthController e pela cadeia da equipe). */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Impede o usuário em memória que o Spring Boot criaria; o login é feito pelo {@code AuthController}. */
    @Bean
    UserDetailsService noUserDetails() {
        return username -> {
            throw new UsernameNotFoundException("Login só por /app/api/auth/login");
        };
    }
}
