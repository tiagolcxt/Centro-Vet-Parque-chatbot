package com.clinicavet.assistant.clinicknowledge.infrastructure;

import com.clinicavet.assistant.clinicknowledge.domain.ClinicConfig;
import com.clinicavet.assistant.clinicknowledge.domain.ConfigImports;
import com.clinicavet.assistant.platform.AuditLog;
import com.clinicavet.assistant.shared.exception.DomainValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Importação de serviços por planilha e sugestões de perguntas a partir de conversas exportadas (S3-14, S3-15). */
@Service
class ConfigImportService implements ConfigImports {

    static final int MAX_SERVICES = 300;
    static final int MAX_FAQ = 100;

    private final ClinicConfig config;
    private final AuditLog audit;

    ConfigImportService(ClinicConfig config, AuditLog audit) {
        this.config = config;
        this.audit = audit;
    }

    @Override
    @Transactional
    public ServicesResult importServices(String csv) {
        ServiceSheet.Parsed parsed = ServiceSheet.parse(csv);
        if (parsed.rows().size() > MAX_SERVICES) {
            throw new DomainValidationException("file", "até " + MAX_SERVICES + " serviços por planilha");
        }
        ClinicConfig.Version draft = config.draft();
        Map<String, Object> document = draft.document();
        Map<String, Object> services = collection(document, "services");
        int added = 0;
        int updated = 0;
        for (ServiceSheet.Row row : parsed.rows()) {
            // Mesmo serviço = mesmo nome (sem acento e caixa), qualquer que seja o id já usado.
            String id = services.entrySet().stream()
                    .filter(e -> e.getValue() instanceof Map<?, ?> m && m.get("name") instanceof String n
                            && BusinessTemplateService.slug(n).equals(BusinessTemplateService.slug(row.name())))
                    .map(Map.Entry::getKey).findFirst()
                    .orElse(BusinessTemplateService.slug(row.name()));
            Map<String, Object> entry = existing(services, id);
            boolean isNew = entry == null;
            if (isNew) {
                entry = BusinessTemplateService.emptyEntry("services");
                entry.put("name", row.name());
            }
            row.values().forEach(entry::put);
            services.put(id, entry);
            if (isNew) {
                added++;
            } else {
                updated++;
            }
        }
        ClinicConfig.Version saved = config.saveDraft(document, draft.summary().revision());
        audit.record("config.services-imported", "config_version", saved.summary().id(),
                "Planilha de serviços importada: " + added + " novos, " + updated + " atualizados");
        return new ServicesResult(saved, added, updated, parsed.problems());
    }

    @Override
    public ChatAnalysis analyzeWhatsApp(List<String> files, String clinicSender) {
        return WhatsAppExport.analyze(files, clinicSender);
    }

    @Override
    @Transactional
    public ClinicConfig.Version addFaq(List<FaqItem> items) {
        if (items.isEmpty() || items.size() > MAX_FAQ) {
            throw new DomainValidationException("items", "de 1 a " + MAX_FAQ + " perguntas");
        }
        ClinicConfig.Version draft = config.draft();
        Map<String, Object> document = draft.document();
        Map<String, Object> faq = collection(document, "faq");
        for (FaqItem item : items) {
            if (item.question() == null || item.question().isBlank()) {
                throw new DomainValidationException("question", "não pode ser vazia");
            }
            Map<String, Object> entry = BusinessTemplateService.emptyEntry("faq");
            entry.put("question", item.question().strip());
            entry.put("answer", item.answer() == null || item.answer().isBlank() ? null : item.answer().strip());
            faq.put(BusinessTemplateService.slug(item.question()), entry);
        }
        ClinicConfig.Version saved = config.saveDraft(document, draft.summary().revision());
        audit.record("config.faq-added", "config_version", saved.summary().id(),
                items.size() + (items.size() == 1 ? " pergunta frequente aprovada" : " perguntas frequentes aprovadas"));
        return saved;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> collection(Map<String, Object> document, String name) {
        Object current = document.get(name);
        Map<String, Object> result = current instanceof Map<?, ?> map
                ? new LinkedHashMap<>((Map<String, Object>) map) : new LinkedHashMap<>();
        document.put(name, result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> existing(Map<String, Object> services, String id) {
        Object entry = services.get(id);
        return entry instanceof Map<?, ?> map ? new LinkedHashMap<>((Map<String, Object>) map) : null;
    }

    /** Planilha de serviços: cabeçalho reconhecido por nome, separador detectado, preço em reais. */
    static final class ServiceSheet {

        record Row(String name, Map<String, Object> values) {
        }

        record Parsed(List<Row> rows, List<String> problems) {
        }

        private static final Map<String, String> HEADERS = new LinkedHashMap<>();

        static {
            for (String h : List.of("nome", "servico", "serviço", "name")) {
                HEADERS.put(h, "name");
            }
            for (String h : List.of("preco", "preço", "valor", "price")) {
                HEADERS.put(h, "price-cents");
            }
            for (String h : List.of("setor", "departamento")) {
                HEADERS.put(h, "department");
            }
            for (String h : List.of("especies", "espécies", "especie", "espécie")) {
                HEADERS.put(h, "species");
            }
            for (String h : List.of("duracao", "duração", "tempo")) {
                HEADERS.put(h, "duration");
            }
            for (String h : List.of("descricao", "descrição")) {
                HEADERS.put(h, "description");
            }
            for (String h : List.of("observacao", "observação", "obs", "observacoes", "observações")) {
                HEADERS.put(h, "price-note");
            }
            for (String h : List.of("agendamento", "como agendar")) {
                HEADERS.put(h, "booking");
            }
        }

        static Parsed parse(String csv) {
            if (csv == null || csv.isBlank()) {
                throw new DomainValidationException("file", "planilha vazia");
            }
            List<String> lines = csv.replace("﻿", "").lines().toList();
            String header = lines.getFirst();
            char separator = header.contains(";") ? ';' : header.contains("\t") ? '\t' : ',';
            List<String> columns = split(header, separator).stream()
                    .map(c -> HEADERS.get(c.strip().toLowerCase(java.util.Locale.ROOT)))
                    .toList();
            if (!columns.contains("name")) {
                throw new DomainValidationException("file",
                        "a primeira linha deve ter os nomes das colunas, com pelo menos \"nome\"");
            }
            List<Row> rows = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = split(line, separator);
                Map<String, Object> values = new LinkedHashMap<>();
                String name = null;
                String problem = null;
                for (int c = 0; c < columns.size() && c < cells.size(); c++) {
                    String field = columns.get(c);
                    String cell = cells.get(c).strip();
                    if (field == null || cell.isEmpty()) {
                        continue;
                    }
                    switch (field) {
                        case "name" -> name = cell;
                        case "price-cents" -> {
                            Long cents = cents(cell);
                            if (cents == null) {
                                problem = "preço \"" + cell + "\" não reconhecido (ex.: 150,00)";
                            } else {
                                values.put(field, cents);
                            }
                        }
                        case "department" -> {
                            String kind = department(cell);
                            if (kind == null) {
                                problem = "setor \"" + cell + "\" não reconhecido (recepção, clínica ou pet shop)";
                            } else {
                                values.put(field, kind);
                            }
                        }
                        default -> values.put(field, cell.length() > KnowledgeFileLoader.MAX_TEXT
                                ? cell.substring(0, KnowledgeFileLoader.MAX_TEXT) : cell);
                    }
                }
                if (name == null) {
                    problem = "sem nome do serviço";
                }
                if (problem != null) {
                    problems.add("linha " + (i + 1) + ": " + problem);
                    continue;
                }
                values.putIfAbsent("offered", true);
                rows.add(new Row(name, values));
            }
            return new Parsed(rows, problems);
        }

        /** "R$ 1.500,00", "150,5", "150" ou "150.00" → centavos. */
        static Long cents(String text) {
            String clean = text.replace("R$", "").replace(" ", "").replace(" ", "");
            if (!clean.matches("[0-9.,]+")) {
                return null;
            }
            String normalized;
            if (clean.contains(",")) {
                normalized = clean.replace(".", "").replace(",", ".");
            } else if (clean.matches("\\d+\\.\\d{1,2}")) {
                normalized = clean;
            } else {
                normalized = clean.replace(".", "");
            }
            try {
                return new java.math.BigDecimal(normalized).movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP)
                        .longValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                return null;
            }
        }

        static String department(String text) {
            String t = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                    .toLowerCase(java.util.Locale.ROOT);
            if (t.contains("recep")) {
                return "RECEPTION";
            }
            if (t.contains("clinic") || t.contains("veterin")) {
                return "CLINICAL";
            }
            if (t.contains("pet") || t.contains("banho") || t.contains("tosa") || t.contains("estetic")) {
                return "PETSHOP";
            }
            return null;
        }

        /** Divide uma linha de CSV respeitando aspas ("a;b" fica numa célula, "" é aspas literal). */
        static List<String> split(String line, char separator) {
            List<String> cells = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else if (ch == '"') {
                        quoted = false;
                    } else {
                        cell.append(ch);
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == separator) {
                    cells.add(cell.toString());
                    cell.setLength(0);
                } else {
                    cell.append(ch);
                }
            }
            cells.add(cell.toString());
            return cells;
        }
    }
}
