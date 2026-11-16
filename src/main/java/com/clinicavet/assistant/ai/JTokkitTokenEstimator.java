package com.clinicavet.assistant.ai;

import com.clinicavet.assistant.conversation.application.TokenEstimator;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.stereotype.Component;

/**
 * Estimativa de tokens para o orçamento do histórico (ADR-0004), com o tokenizador do Spring AI
 * (JTokkit). Usa a codificação {@code o200k_base}, das famílias GPT-4o/GPT-5. A OpenAI não publica o
 * tokenizador do {@code gpt-6-luna}: o número é uma <b>estimativa</b>, suficiente para limitar o
 * histórico, não para cobrança.
 */
@Component
class JTokkitTokenEstimator implements TokenEstimator {

    private final JTokkitTokenCountEstimator delegate = new JTokkitTokenCountEstimator(EncodingType.O200K_BASE);

    @Override
    public int estimate(String text) {
        return text == null || text.isEmpty() ? 0 : delegate.estimate(text);
    }
}
