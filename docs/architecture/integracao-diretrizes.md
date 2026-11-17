# Integração das Diretrizes com a arquitetura

> Versão 1 — 2026-11-11. Mostra **onde** cada diretriz vive e o que muda nos componentes já decididos. Requisitos: [requisitos-diretrizes.md](../specs/requisitos-diretrizes.md).

## 1. Distribuição das diretrizes por camada

Princípio: **o que protege o animal e o tutor fica no backend**. O modelo recebe as instruções de comunicação e produz sinais, mas não é a única barreira.

| Camada | O que fica nela | Exemplos |
|---|---|---|
| **Diretrizes institucionais** (`docs/product/`) | Princípios, precedência em conflito, conduta da equipe humana | Diretrizes de atendimento; hierarquia de prioridades |
| **Conhecimento institucional** (YAML, ADR-0003) | Conteúdo **aprovado pela clínica e pela equipe veterinária** que o sistema usa | Protocolo de emergência (P-01); sinais e critérios de P1/P2 (P-20); prazos por nível e protocolo de indisponibilidade (P-02); textos de encaminhamento e de prazo vencido; condições de pagamento (P-11) |
| **Regras determinísticas do backend** (domínio) | Tudo que não pode depender da interpretação do modelo | `PriorityPolicy` (pisos, nível mais urgente, rebaixamento proibido); `ExplicitHumanRequestDetector`; detector de conversa improdutiva; `HandoffPolicy`; `ReplyComposer` (protocolo primeiro em P1, textos aprovados); validação V5/V6; fail-safe |
| **Instruções do modelo** (`.st`) | Comunicação, empatia, limites, quando sinalizar | `system-message.st`: princípios das seções 1, 3, 4 e 5 das diretrizes; instrução para sinalizar `priority`, `signals[]`, `possibleEmergency`, `clinicalUncertainty`; `waiting-human.st`: só sinais |
| **Estados e prioridades** (modelo de domínio) | Estado operacional e prioridade clínica independentes | `Conversation.status` (`OPEN`/`WAITING_HUMAN`/`CLOSED`) × `Conversation.clinicalPriority` (P1–P6) |
| **Encaminhamento humano** (ADR-0007) | Condições ampliadas, motivo persistido, prazo por nível | Novas condições: sofrimento, insatisfação, confusão, conversa improdutiva, decisão de funcionário, prioridade P1–P3 |
| **Testes automatizados** | Cada regra crítica com teste sem IA; comportamento do modelo com avaliação | [cenarios-diretrizes.md](../specs/cenarios-diretrizes.md) |

## 2. Mudanças nos componentes existentes

| Componente | Mudança | Documento |
|---|---|---|
| `Conversation` | Novos campos `clinicalPriority`, `priorityUpdatedAt`, `priorityLoweredBy?`/`priorityLoweredAt?`; regra de rebaixamento só por funcionário | ADR-0018; proposta 3.1 |
| `Message` (do assistente) | Registra a prioridade atribuída, a origem (modelo ou piso) e os sinais do turno | ADR-0018 |
| `AssistantTurn` | Novos campos `priority`, `signals[]`, `clinicalUncertainty` (Etapa 4) | Proposta 4.1 |
| Modo restrito `WAITING_HUMAN` | Passa a devolver também `priority` e `signals[]`, além de `possibleEmergency` | ADR-0007 |
| `PriorityPolicy` (novo, domínio) | Calcula o nível efetivo: mais urgente entre modelo, pisos H5 e nível atual; nunca rebaixa | ADR-0018 |
| `HandoffPolicy` | Inclui as condições da diretriz 3.1 e encaminha sempre em P1–P2 (P3 conforme P-20) | ADR-0007 |
| `ReplyComposer` | P1: só protocolo; P2: encaminhamento + quando procurar atendimento imediato; mensagem mista: clínico antes do administrativo; prazo vencido e indisponibilidade com texto aprovado | ADR-0007 |
| Validador (ADR-0008) | **V6 ampliada**: reprova notificação, promessa de contato ou retorno sem processo aprovado e afirmação de solicitação resolvida | ADR-0008 |
| Detector de conversa improdutiva (novo, domínio) | Sinal `UNPRODUCTIVE_CONVERSATION` a partir de: falhas consecutivas, mesma informação pedida de novo sem progresso, mensagens repetidas do tutor (limiares: PT-14) | ADR-0007 |
| API | `GET /api/conversations?status=` ordena por prioridade e tempo de espera e expõe atraso por nível; novo `PATCH /api/conversations/{id}/priority` (reavaliação pela equipe) | Proposta 6 |
| YAML institucional | Novas seções: `triage.emergency-signs`, `triage.urgent-criteria` (P-20), `policies.response-deadlines` por nível (P-02), `policies.unavailability` (P-02), `texts.*` aprovados | ADR-0003 |
| Roadmap | Etapa 4 recebe prioridade clínica e as novas condições; Etapa 6 recebe os cenários CT-01 a CT-18; Etapa 7 exige P-19 e P-20 | Roadmap |

## 3. Pontos de atenção e conflitos com decisões anteriores

| # | Ponto | Tratamento proposto | Situação |
|---|---|---|---|
| A1 | O briefing original proíbe "tabela própria de diagnóstico ou classificação médica de gravidade" | A hierarquia **não** é classificação médica: ordena e encaminha. Os critérios clínicos são da equipe veterinária (P-20), não do sistema. Usar a lista aprovada também como **verificação determinística** de texto (piso P1/P2) foi **adotado** (DA-27, ADR-0019): a lista só eleva a prioridade e é conteúdo da equipe veterinária | Decidido |
| A2 | P3 pede "preservar o contexto do atendimento anterior", mas o ADR-0009 não leva solicitações nem mensagens de uma conversa para a outra, e não existe prontuário no MVP | Hoje: o assistente pergunta o necessário, sem presumir continuidade, e encaminha. Mostrar ao modelo uma referência às solicitações anteriores do mesmo contato (tipo, data, status) foi decidido na **DA-37** (implementação na Etapa 5) | Decidido |
| A3 | "Não prometer contato sem processo real" | V6 ampliada reprova promessas de contato; os textos aprovados só podem prometer o que P-14 definir | Incorporado (DA-26) |
| A4 | Rebaixamento só por funcionário | Exige endpoint de reavaliação na E4; a identificação individual de quem rebaixou só existe na E7 (até lá, registra a API Key) | Incorporado (DA-23), limitação até a E7 |
| A5 | Em P1, "se a clínica não puder receber o animal" | Depende de saber se a clínica pode receber naquele momento. No MVP não há essa informação em tempo real, então o protocolo aprovado (P-01) precisa cobrir esse caso por horário (ex.: fora do expediente) | Pendente em P-01 |
| A6 | Custo: o modo restrito em `WAITING_HUMAN` passa a classificar prioridade | Chamada curta, sem histórico completo; já prevista no ADR-0007 | Aceito |
