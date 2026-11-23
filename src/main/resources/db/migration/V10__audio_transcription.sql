-- V10 — Etapa S5: transcrição de áudio (DA-53). A duração entra no custo por minuto (indicadores); o texto
-- transcrito vai para a mensagem do tutor, não para cá.
ALTER TABLE inbound_message ADD COLUMN audio_seconds DECIMAL(8, 1) NULL;
