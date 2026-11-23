package com.clinicavet.assistant.channel;

import java.util.Optional;

/**
 * Transcrição do áudio do tutor (Etapa S5, DA-53). O módulo de IA implementa. O provedor recebe o áudio, que pode
 * conter dado de saúde: precisa estar na lista de suboperadores (DA-50). Sem transcrição, o áudio continua visível
 * para a equipe como anexo.
 */
public interface AudioTranscriber {

    /** @param seconds duração do áudio, quando o provedor informa (custo por minuto) */
    record Transcription(String text, Double seconds) {
    }

    /** Vazio quando desligado ou quando o provedor falha: quem chama segue sem a transcrição. */
    Optional<Transcription> transcribe(byte[] audio, String mimeType);
}
