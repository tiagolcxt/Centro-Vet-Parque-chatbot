# ADR-0002 — IA: chamada única, saída estruturada, reconciliação e nenhum acesso à persistência

- **Status:** Aceito
- **Data:** 2026-11-11
- **Decisões:** DA-01, DA-02

## Contexto

O assistente precisa gerar um texto natural para o tutor e, ao mesmo tempo, informar à aplicação a intenção, os dados mencionados, a necessidade de encaminhamento e as fontes institucionais usadas. O briefing exige que a aplicação valide e execute todas as operações e que o modelo não acesse repositórios. A revisão técnica (9.2) mostrou que JSON válido não garante conteúdo correto e que texto e metadados podem se contradizer.

## Decisão

1. **Uma chamada principal por turno** ao modelo, com **saída estruturada** (`AssistantTurn`). Ela separa `reply` (texto ao tutor) dos metadados operacionais. Os campos são introduzidos por etapa, conforme o roadmap.
2. **O modelo não acessa repositórios, entidades nem operações de persistência.** No MVP, **nenhuma tool de escrita** é registrada no `ChatClient`. Toda alteração de estado é feita pela aplicação, a partir do `AssistantTurn` validado.
3. **Classificação do resultado** antes de qualquer uso. Cada resposta é classificada como:

   | Resultado | Exemplos | Tratamento |
   |---|---|---|
   | **Válido** | Schema ok, valores dentro do domínio, texto coerente com os metadados | Segue para regras e persistência |
   | **Recusa** | O modelo recusou responder (campo de recusa da API, texto de recusa) | **Falha** |
   | **Truncado** | `finish_reason` de limite de tokens; JSON incompleto | **Falha** |
   | **Inválido** | Schema violado, enum desconhecido sem fallback permitido, campos obrigatórios ausentes | **Falha** |
   | **Inconsistente** | Texto e metadados se contradizem (regras abaixo) | **Falha** |
   | **Erro técnico** | Timeout, 5xx, rede | **Falha** |

   Intenção fora do enum é normalizada para `OTHER` (não é falha). Os demais desvios são falhas.
4. **Reconciliação** entre o texto e as decisões do backend:
   - quando o backend decide encaminhar (ADR-0007) e o `reply` do modelo não comunica isso, o texto final é **composto pelo backend** a partir de um texto padrão de encaminhamento;
   - se o `reply` anuncia transferência para a equipe e a decisão final não é encaminhar, o resultado é **inconsistente**;
   - se o `reply` afirma que alguém foi avisado ou notificado, o resultado é **inconsistente** (não existe notificação no MVP);
   - se o modelo declara lacuna de conhecimento, mas o `reply` afirma fatos institucionais, o resultado é **inconsistente** (ADR-0008);
   - se há indício de possível emergência, o texto final **sempre** inclui a orientação da política aprovada, inserida pelo backend (ADR-0007).
5. **Tratamento de falha:** a mensagem do tutor já está persistida e o estado da conversa não é alterado pelo turno com falha. O tutor recebe uma resposta segura padrão. **Quando a falha envolve encaminhamento** (inconsistência sobre transferência, ou pedido explícito de humano detectado), a resposta segura é a de encaminhamento: o sistema falha em direção ao humano. Novas tentativas e timeout: no máximo 1 nova tentativa por turno (recusa não repete), timeout de 30 s ([ADR-0016](ADR-0016-modelo-parametros-timeout-retry.md), DA-28).

## Alternativas consideradas

- **Tools de escrita** (`registrar_solicitacao`, `solicitar_humano`): o modelo decidiria quando escrever; a validação ficaria espalhada; rejeitada.
- **Duas chamadas** (classificar e depois redigir com a decisão já tomada): metadados mais confiáveis e texto coerente por construção, ao custo de ~2× latência e custo por turno. Fica como alternativa se a Etapa 6 medir inconsistência acima do aceitável.
- **Texto livre + extração posterior por regex:** frágil; rejeitada.
- **Tool somente leitura** (`consultar_informacao_clinica`): desnecessária enquanto a base institucional couber no contexto (ADR-0003).

## Consequências

- Os testes de regra usam um `AssistantTurn` falso, sem depender da OpenAI.
- A robustez do controle do fluxo depende da reconciliação e da validação, não do formato JSON em si.
- Há limitação conhecida: **uma intenção principal por turno** (PT-06).
- É necessário verificar, na Etapa 2, o suporte do Spring AI 2.0.0-M4 a JSON Schema estrito da OpenAI e como a API expõe recusa e `finish_reason`.
