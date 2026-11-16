# ADR-0008 — Validação de respostas e rastreamento de fontes institucionais

- **Status:** Aceito, com pendências (PT-11)
- **Data:** 2026-11-11
- **Decisões:** DA-12, DA-26 (V6 ampliada)

## Contexto

O assistente não pode inventar preços, horários, serviços ou políticas. A guarda proposta originalmente detectava só valores em `R$`, horários numéricos e frases fixas de confirmação. A revisão técnica (9.1) mostrou dois problemas:
- **falsos negativos:** valor certo atribuído ao serviço errado, horário em linguagem natural, serviços e feriados inventados, confirmação parafraseada;
- **falsos positivos:** eco da preferência informada pelo tutor.

## Decisão

### 1. Fontes declaradas pelo modelo
O `AssistantTurn` passa a conter:
- `knowledgeRefs[]`: chaves dos itens institucionais (ADR-0003) usados na resposta;
- `knowledgeGap`: o modelo não encontrou a informação na base ou ela está `DESCONHECIDO`.

### 2. Verificações, em ordem (qualquer reprovação = resultado **inconsistente**, ADR-0002)

| # | Verificação | Regra |
|---|---|---|
| V1 | **Referências válidas** | Toda chave em `knowledgeRefs` existe na base e **não** está `DESCONHECIDO`. Citar item desconhecido é reprovado. |
| V2 | **Valores autorizados** | Valores factuais detectados no `reply` (formatos em 3) precisam estar **autorizados**, ou seja: (a) pertencer aos valores dos itens citados em `knowledgeRefs`, ou (b) aparecer nas mensagens do tutor da conversa atual ou nos campos da solicitação em andamento (eco). |
| V3 | **Coerência com a lacuna** | Se `knowledgeGap = true`, o `reply` não pode conter valores factuais institucionais (V2-a). Deve dizer que vai confirmar com a equipe. |
| V4 | **Resposta institucional sem fonte** | Intenções `CLINIC_INFORMATION`, `SERVICE_INFORMATION` ou `PRICE_INQUIRY` com `knowledgeRefs` vazio e `knowledgeGap = false` são reprovadas. |
| V5 | **Confirmação de agendamento** | Expressões que afirmam agendamento confirmado são reprovadas (ADR-0011). |
| V6 | **Promessa sem processo** (ampliada pelas Diretrizes, DA-26) | São reprovadas expressões que afirmam: (a) que alguém foi avisado ou notificado; (b) que um funcionário **entrará em contato** ou dará retorno, salvo nos textos aprovados com processo definido (P-14); (c) que a solicitação foi **resolvida** ou "está tudo certo" quando só houve registro (ADR-0007, ADR-0011). |

### 3. Formatos detectados (lista inicial pendente de aprovação, PT-11)

| Tipo | Formatos cobertos |
|---|---|
| Valores monetários | `R$ 120`, `R$120`, `R$ 120,00`, `120 reais`, `120,00 reais` |
| Horários | `8h`, `8h30`, `08:00`, `8:30`, `às 8` |
| Dias da semana | `segunda` … `domingo`, com ou sem `-feira` |
| Telefones | Sequências com 8 a 13 dígitos, com ou sem DDD e separadores |
| E-mails e URLs | Formatos usuais |
| Confirmação (V5) e notificação (V6) | Lista configurável de expressões; lista inicial em PT-11 |

### 4. Lacuna de conhecimento
`knowledgeGap = true` gera (ou atualiza) uma `ServiceRequest(INFORMATION)`, para a equipe responder a dúvida.

### 5. Tratamento da reprovação
Conforme ADR-0002 (falha). No máximo 1 nova tentativa por turno (DA-28).

### 6. Estratégia de teste desde a introdução (Etapa 3)
- **Testes unitários** do validador com um conjunto versionado de respostas aprovadas e reprovadas. Para cada formato e verificação acima, ao menos um caso positivo e um negativo, incluindo os falsos positivos conhecidos (eco da preferência).
- **Conjunto inicial de avaliação com IA real** (`@Tag("ai")`), com perguntas-armadilha e base fictícia:
  - preço não cadastrado;
  - serviço inexistente;
  - horário de feriado desconhecido;
  - pedido de confirmação de agendamento.

  O conjunto é ampliado na Etapa 6.
- A documentação de gestão de contexto lista **exatamente os formatos cobertos pelos testes**.

## Limites conhecidos (documentados, não resolvidos)

**Não há garantia absoluta de ausência de informação inventada.** A estratégia **reduz** o risco e torna os erros mensuráveis. Não detecta:
- valores por extenso ("cento e vinte reais") e formas não listadas em 3;
- afirmações factuais sem valor detectável ("fazemos ultrassom", "abrimos no feriado", "aceitamos cartão"), quando o modelo não cita fonte e não declara lacuna. V4 cobre parcialmente, conforme a intenção classificada;
- valor correto de um item citado aplicado a outro assunto no texto (ex.: cita o preço da vacina e o texto diz que é da consulta);
- confirmação ou notificação parafraseada fora da lista de expressões;
- eco de valor do tutor reapresentado como se fosse informação da clínica.

Esses limites são cobertos por instruções no prompt, pela avaliação contínua (Etapa 6) e, se a clínica optar por não divulgar preços (P-11), por uma regra mais restritiva (nenhum valor monetário permitido).

## Alternativas consideradas

- **Só detectar padrões no texto** (proposta original): estreita demais; rejeitada.
- **Respostas institucionais montadas por template no backend:** proteção mais forte, menos natural; possível evolução para horário e endereço, se a avaliação indicar.
- **Segundo modelo como verificador:** adiciona custo e é ele próprio probabilístico; não adotado no MVP.

## Consequências

- O `AssistantTurn` cresce (`knowledgeRefs`, `knowledgeGap`), e o prompt precisa instruir o modelo a citar as chaves.
- Reprovações custam uma resposta segura padrão e pioram a experiência naquele turno. A taxa de reprovação é medida a partir da Etapa 3.
