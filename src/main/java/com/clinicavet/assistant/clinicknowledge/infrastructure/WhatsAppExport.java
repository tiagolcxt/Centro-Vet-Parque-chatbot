package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ConfigImports;
import com.clinicavet.assistant.shared.exception.DomainValidationException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Leitura de "Exportar conversa" do WhatsApp (S3-15). Tudo em memória, sem log de conteúdo e sem gravar nada: o
 * resultado volta para a tela e some. A análise é determinística (palavras por assunto), sem IA: nenhuma conversa de
 * tutor sai da aplicação.
 */
final class WhatsAppExport {

    static final int MAX_FILES = 20;
    static final int MAX_FILE_CHARS = 3_000_000;
    static final int MAX_EXAMPLES = 3;
    static final int MAX_TEXT = 500;

    // Android: "21/11/2026 14:05 - Nome: texto"; iPhone: "[21/11/2026, 14:05:33] Nome: texto"
    private static final Pattern ANDROID = Pattern.compile(
            "^(\\d{1,2}/\\d{1,2}/\\d{2,4}),? (\\d{1,2}:\\d{2})(?:\\s?[ap]\\.?m\\.?)?\\s[-–]\\s([^:]{1,60}): (.*)$");
    private static final Pattern IOS = Pattern.compile(
            "^\\u200E?\\[(\\d{1,2}/\\d{1,2}/\\d{2,4}),? (\\d{1,2}:\\d{2}(?::\\d{2})?)\\] ([^:]{1,60}): (.*)$");
    private static final Pattern PHONE = Pattern.compile("\\+?\\d[\\d\\s().-]{7,}\\d");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern MEDIA = Pattern.compile(
            "^(<.*(m[ií]dia|arquivo|media).*>|.*\\(arquivo anexado\\)|imagem ocultada|v[ií]deo omitido|"
                    + "áudio ocultado|figurinha omitida|.*mensagem apagada.*|null)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUESTION_START = Pattern.compile(
            "^(quanto|qual|quais|que horas|que dia|onde|como|voces|vcs|tem |tem$|aceita|atende|faz |fazem|funciona|"
                    + "abre|fecha|precisa|pode|posso|da pra|consigo|e possivel|gostaria de saber|queria saber)");

    /** Assuntos, na ordem de prioridade: o primeiro que casar decide. Palavras já sem acento e em minúsculas. */
    private static final Map<String, List<String>> TOPICS = new LinkedHashMap<>();

    static {
        TOPICS.put("Emergência e plantão", List.of("emergencia", "urgencia", "urgente", "plantao", "24 horas", "24h",
                "madrugada"));
        TOPICS.put("Vacinas", List.of("vacina", "v10", "v8", "v4", "v5", "antirrabica", "raiva", "vermifug"));
        TOPICS.put("Banho e tosa", List.of("banho", "tosa", "hidratacao", "escovacao"));
        TOPICS.put("Exames", List.of("exame", "ultrassom", "ultrasson", "raio x", "raio-x", "hemograma", "radiografia"));
        TOPICS.put("Busca e entrega do animal", List.of("buscar", "busca", "leva e traz", "taxi dog", "taxi pet",
                "levar ele", "pegar ele"));
        TOPICS.put("Agendamento", List.of("agendar", "agenda", "marcar", "horario disponivel", "vaga", "encaixe"));
        TOPICS.put("Pagamento", List.of("cartao", "pix", "parcel", "pagamento", "dinheiro", "convenio", "plano de saude"));
        TOPICS.put("Endereço e como chegar", List.of("endereco", "onde fica", "onde voces", "localizacao",
                "como chegar", "estacionamento", "ponto de referencia"));
        TOPICS.put("Horário de funcionamento", List.of("horario", "abre", "fecha", "funciona", "aberto", "aberta",
                "sabado", "domingo", "feriado"));
        TOPICS.put("Preços", List.of("quanto", "valor", "preco", "custa", "cobram", "orcamento"));
        TOPICS.put("Consulta", List.of("consulta", "veterinari", "atendimento", "castra"));
    }

    record Message(int file, int index, String sender, String text) {
    }

    private WhatsAppExport() {
    }

    static ConfigImports.ChatAnalysis analyze(List<String> files, String clinicSender) {
        if (files == null || files.isEmpty() || files.size() > MAX_FILES) {
            throw new DomainValidationException("files", "de 1 a " + MAX_FILES + " arquivos");
        }
        List<List<Message>> conversations = new ArrayList<>();
        for (int f = 0; f < files.size(); f++) {
            String content = files.get(f);
            if (content == null || content.length() > MAX_FILE_CHARS) {
                throw new DomainValidationException("files", "arquivo vazio ou maior que 3 MB");
            }
            conversations.add(read(f, content));
        }
        List<String> senders = senders(conversations);
        if (senders.isEmpty()) {
            throw new DomainValidationException("files",
                    "nenhuma mensagem reconhecida. Use \"Exportar conversa\" > \"Sem mídia\" no WhatsApp");
        }
        String clinic = clinicSender != null && senders.contains(clinicSender.strip()) ? clinicSender.strip()
                : senders.getFirst();

        record Asked(String question, String normalized, String answer) {
        }
        Map<String, List<Asked>> byTopic = new LinkedHashMap<>();
        int total = 0;
        int questions = 0;
        for (List<Message> conversation : conversations) {
            total += conversation.size();
            for (int i = 0; i < conversation.size(); i++) {
                Message message = conversation.get(i);
                if (message.sender().equals(clinic) || !isQuestion(message.text())) {
                    continue;
                }
                questions++;
                String normalized = normalize(message.text());
                String topic = topic(normalized);
                String answer = null;
                for (int j = i + 1; j < conversation.size() && j <= i + 6; j++) {
                    if (conversation.get(j).sender().equals(clinic)) {
                        answer = clip(conversation.get(j).text());
                        break;
                    }
                }
                byTopic.computeIfAbsent(topic, t -> new ArrayList<>())
                        .add(new Asked(clip(mask(message.text())), normalized, answer));
            }
        }

        List<ConfigImports.FaqSuggestion> suggestions = new ArrayList<>();
        byTopic.forEach((topic, asked) -> {
            Map<String, Integer> frequency = new HashMap<>();
            asked.forEach(a -> frequency.merge(a.normalized(), 1, Integer::sum));
            Asked representative = asked.stream()
                    .max(Comparator.comparingInt((Asked a) -> frequency.get(a.normalized())))
                    .orElseThrow();
            List<String> examples = asked.stream().map(Asked::question).distinct().limit(MAX_EXAMPLES).toList();
            String answer = null;
            for (int k = asked.size() - 1; k >= 0 && answer == null; k--) {
                answer = asked.get(k).answer();
            }
            suggestions.add(new ConfigImports.FaqSuggestion(topic, representative.question(), asked.size(), examples,
                    answer));
        });
        suggestions.sort(Comparator.comparingInt(ConfigImports.FaqSuggestion::count).reversed());
        return new ConfigImports.ChatAnalysis(clinic, senders, total, questions, suggestions);
    }

    static List<Message> read(int file, String content) {
        List<Message> messages = new ArrayList<>();
        String sender = null;
        StringBuilder text = null;
        for (String raw : content.replace("﻿", "").lines().toList()) {
            String line = raw.replace("‎", "").replace(" ", " ");
            Matcher m = ANDROID.matcher(line);
            if (!m.matches()) {
                m = IOS.matcher(line);
            }
            if (m.matches()) {
                if (sender != null) {
                    add(messages, file, sender, text.toString());
                }
                sender = m.group(3).strip();
                text = new StringBuilder(m.group(4));
            } else if (text != null && !line.isBlank()) {
                text.append('\n').append(line);
            }
        }
        if (sender != null) {
            add(messages, file, sender, text.toString());
        }
        return messages;
    }

    private static void add(List<Message> messages, int file, String sender, String text) {
        String clean = text.strip();
        if (!clean.isEmpty() && !MEDIA.matcher(clean).matches()) {
            messages.add(new Message(file, messages.size(), sender, clean));
        }
    }

    /**
     * Quem aparece em mais conversas vem primeiro (provavelmente a clínica). Empate: quem pergunta menos (a clínica
     * responde), depois quem mais escreveu.
     */
    private static List<String> senders(List<List<Message>> conversations) {
        Map<String, int[]> stats = new LinkedHashMap<>();
        for (List<Message> conversation : conversations) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (Message m : conversation) {
                int[] s = stats.computeIfAbsent(m.sender(), k -> new int[3]);
                s[1]++;
                if (isQuestion(m.text())) {
                    s[2]++;
                }
                if (seen.add(m.sender())) {
                    s[0]++;
                }
            }
        }
        return stats.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, int[]> e) -> -e.getValue()[0])
                        .thenComparingInt(e -> e.getValue()[2])
                        .thenComparingInt(e -> -e.getValue()[1]))
                .map(Map.Entry::getKey)
                .toList();
    }

    static boolean isQuestion(String text) {
        String normalized = normalize(text);
        return text.contains("?") || QUESTION_START.matcher(normalized).find();
    }

    static String topic(String normalized) {
        for (Map.Entry<String, List<String>> topic : TOPICS.entrySet()) {
            if (topic.getValue().stream().anyMatch(normalized::contains)) {
                return topic.getKey();
            }
        }
        return "Outras perguntas";
    }

    static String normalize(String text) {
        String ascii = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").strip();
    }

    /** Telefones e e-mails dos tutores não aparecem nos exemplos. */
    static String mask(String text) {
        return EMAIL.matcher(PHONE.matcher(text).replaceAll("•••")).replaceAll("•••");
    }

    private static String clip(String text) {
        String single = text.replace('\n', ' ').strip();
        return single.length() > MAX_TEXT ? single.substring(0, MAX_TEXT) + "…" : single;
    }
}
