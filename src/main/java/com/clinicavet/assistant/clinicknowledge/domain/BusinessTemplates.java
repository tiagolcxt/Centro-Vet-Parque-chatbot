package com.clinicavet.assistant.clinicknowledge.domain;

import java.util.List;

/**
 * Modelos por tipo de negócio (S3-13, DA-53): o ponto de partida da configuração. Um modelo liga os setores do tipo e
 * sugere serviços e perguntas frequentes. <b>Não traz dados da clínica</b>: preço, horário, endereço e respostas ficam
 * desconhecidos até a clínica preencher. Assim nada inventado chega ao tutor.
 */
public interface BusinessTemplates {

    enum Type {
        SMALL_CLINIC, CLINIC_WITH_PETSHOP, PETSHOP_ONLY, HOSPITAL_24H
    }

    record Template(Type type, String name, String description, List<Departments.Kind> departments,
                    List<ServiceSuggestion> services, List<String> questions) {
    }

    record ServiceSuggestion(String name, Departments.Kind department) {
    }

    List<Template> list();

    /**
     * Aplica o modelo: ativa os setores do tipo (e desativa os outros, menos a Recepção) e acrescenta ao rascunho os
     * serviços e as perguntas que ainda não existem. Não apaga nem sobrescreve o que já foi preenchido.
     */
    ClinicConfig.Version apply(Type type);
}
