package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.List;

/**
 * Importação do que a clínica já tem (S3-14, S3-15, DA-53). Tudo vai para o rascunho; nada é publicado sem revisão.
 */
public interface ConfigImports {

    /**
     * @param added   serviços novos
     * @param updated serviços que já existiam (mesmo nome) e tiveram campos atualizados
     * @param problems linhas recusadas, no formato "linha 3: motivo"; as outras linhas entram
     */
    record ServicesResult(ClinicConfig.Version draft, int added, int updated, List<String> problems) {
    }

    /**
     * Planilha de serviços em CSV (separada por ponto e vírgula, vírgula ou tabulação), com cabeçalho. Colunas
     * reconhecidas: nome (obrigatória), preço, setor, espécies, duração, descrição, observação. No Excel: "Salvar como
     * CSV".
     */
    ServicesResult importServices(String csv);

    /**
     * Uma pergunta frequente sugerida a partir de conversas exportadas do WhatsApp.
     *
     * @param count           quantas perguntas do assunto apareceram
     * @param examples        até três perguntas como os tutores escreveram (telefones e e-mails mascarados)
     * @param suggestedAnswer a resposta que a clínica deu à pergunta mais recente do assunto, para revisar
     */
    record FaqSuggestion(String topic, String question, int count, List<String> examples, String suggestedAnswer) {
    }

    /**
     * @param clinicSender quem foi considerado a clínica (as respostas vêm dele)
     * @param senders      todos os nomes encontrados, para a tela permitir trocar
     */
    record ChatAnalysis(String clinicSender, List<String> senders, int messages, int questions,
                        List<FaqSuggestion> suggestions) {
    }

    /**
     * Analisa arquivos "Exportar conversa" do WhatsApp (sem mídia), em memória. <b>Nada é gravado</b>: nem os arquivos,
     * nem as mensagens, nem o resultado; a clínica aprova as sugestões uma a uma, e só o que ela aprovar vai ao rascunho.
     *
     * @param clinicSender nome da clínica nos arquivos; {@code null} = deduzir (quem aparece em mais conversas)
     */
    ChatAnalysis analyzeWhatsApp(List<String> files, String clinicSender);

    record FaqItem(String question, String answer) {
    }

    /** Acrescenta ao rascunho as perguntas aprovadas (mesmo id = substitui). */
    ClinicConfig.Version addFaq(List<FaqItem> items);
}
