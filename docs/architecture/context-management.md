# Gestão de contexto — o que vai ao modelo a cada mensagem

> Reflete o código ao fim da **Etapa 3 — Conhecimento institucional** (2026-11-16). Camadas implementadas: **1** (identidade), **2** (institucional), **4** (conversa) e parte da **5** (operacional). A camada 3 (tutor e pet) entra na Etapa 5.
> Desenho completo: [proposta, seção 4.2](../plans/etapa-0-analise-e-proposta.md). Decisões: ADR-0002, ADR-0003, ADR-0004, ADR-0008, ADR-0015, ADR-0016, DA-29.

## Princípio

Nada fica só em memória. A cada mensagem, a aplicação **reconstrói o contexto a partir do banco** e envia ao modelo uma única chamada com saída estruturada. O modelo não acessa o banco, não tem tools e não decide nada sozinho: a aplicação classifica a resposta e decide o que gravar (ADR-0002).

## Ordem das mensagens enviadas

| # | Papel | Conteúdo | Camada | Origem |
|---|---|---|---|---|
| 1 | `system` | Identidade, idioma, limites, regra de segurança e formato da resposta | 1 | `prompts/system-message.st` |
| 2 | `system` | `<dados_da_clinica>`: um item da base por linha, `[chave] valor` | 2 | `prompts/clinic-context.st` + base YAML |
| 3 | `system` | `<estado_do_atendimento>`: status da conversa e data/hora local da clínica | 5 | `prompts/operational-context.st` + `Conversation` + `Clock` |
| 4…n | `user` / `assistant` | Mensagens anteriores **da conversa atual**, em ordem | 4 | Tabela `message` |
| n+1 | `user` | A mensagem que o tutor acabou de enviar | 4 | Requisição |

As mensagens do tutor (anteriores e atual) vão delimitadas por `<mensagem_do_cliente>…</mensagem_do_cliente>`. Se o tutor escrever essas marcas no texto, elas são removidas antes do envio, para que ele não consiga "fechar" o bloco e escrever fora dele. O prompt de sistema manda tratar o conteúdo delimitado como dado, nunca como instrução.

A resposta pedida é o `AssistantTurn` v2, com JSON Schema **estrito** (`assistant_turn_v2`):

| Campo | Uso |
|---|---|
| `reply` | Texto enviado ao tutor |
| `intent` | Uma das 11 intenções (lista fechada no schema). Grava em `message.intent` e `conversation.current_intent`. Entra na verificação V4 |
| `knowledgeRefs` | Chaves da base que o modelo diz ter usado (E3). Definem os valores autorizados (V1, V2) |
| `knowledgeGap` | O tutor pediu algo que a base não tem ou tem como DESCONHECIDO (E3). Gera pedido de informação para a equipe (V3) |

## Camada 1 — identidade e comportamento

Arquivo `src/main/resources/prompts/system-message.st`, em UTF-8, **sem nenhum dado da clínica**. Linhas que começam com `##` são notas para quem mantém o arquivo e não vão ao modelo.

Conteúdo **provisório** (P-10, texto final na Etapa 6): persona genérica, pt-BR, "você", respostas curtas e sem emojis. As regras fixas:
- não diagnosticar, não interpretar sintomas, não indicar medicamento, dose ou tratamento;
- usar **somente** `<dados_da_clinica>`; o que estiver DESCONHECIDO ou ausente é declarado como lacuna, sem citar valores que o tutor não escreveu;
- citar em `knowledgeRefs` as chaves usadas; nunca citar item DESCONHECIDO;
- nunca escrever doses;
- não marcar nem confirmar agendamento;
- não dizer que avisou, encaminhou ou transferiu algo, que alguém vai entrar em contato ou que algo está resolvido;
- possível risco ao animal: não minimizar e recomendar atendimento veterinário o quanto antes. **Regra genérica provisória**: a orientação aprovada pela clínica e a lista de sinais entram na Etapa 4 (P-02, ADR-0019).

As regras do prompt são a primeira barreira. A segunda é o validador (abaixo), que não depende de o modelo obedecer.

## Camada 2 — informações institucionais

Fonte: arquivo YAML indicado em `ASSISTANT_KNOWLEDGE_FILE` (ADR-0003). Lido e validado **uma vez, na inicialização**: arquivo ausente, YAML malformado, chave ausente, chave desconhecida ou valor fora do tipo impedem a aplicação de subir, com a lista de problemas.

| Arquivo | Versionado? | Uso |
|---|---|---|
| `config/clinic-knowledge.example.yml` | Sim | Modelo com **tudo `null`**. É o padrão: sem configurar nada, o assistente não sabe nada da clínica e declara lacuna |
| `config/clinic-knowledge.ficticia.yml` | Sim | Clínica **inventada**, para desenvolvimento e testes. Tem itens `null` de propósito (preço de banho e tosa, feriados) |
| `config/clinic-knowledge.yml` | **Não** (`.gitignore`) | Dados reais aprovados pela clínica (P-11, Etapa 7) |

Estrutura (chaves fixas; detalhes e formatos em `config/clinic-knowledge.example.yml`):

| Seção | Chaves |
|---|---|
| `identification` | `name`, `address`, `zip-code`, `phone`, `whatsapp`, `email`, `website`, `instagram` |
| `hours` | `monday` … `sunday` (intervalos `HH:mm-HH:mm`; `[]` = fechado), `holidays`, `after-hours`, `twenty-four-hours` |
| `services.<id>` | `name` (obrigatório), `offered`, `description`, `price-cents`, `price-note` |
| `payment` | `prices-disclosed`, `methods`, `installments` |
| `policies` | `scheduling`, `cancellation`, `no-show` |
| `faq.<id>` | `question` (obrigatório), `answer` |
| `approved-texts` | `emergency-policy` (P-01), `direct-contact` (P-02). **Não vão ao modelo**: são textos que o backend insere (Etapa 4) |

No prompt, cada item vira uma linha `[chave] valor`, e `null` vira `DESCONHECIDO — confirmar com a equipe`. `services: null` ou `faq: null` aparecem como uma linha DESCONHECIDO para a coleção inteira. Preço em centavos aparece como `R$ 150,00`. Se `payment.prices-disclosed` não for `true`, **todos os preços viram DESCONHECIDO** e nenhum valor em dinheiro é autorizado (P-11, DA-29).

A base fictícia completa ocupa cerca de 40 linhas no prompt. O ADR-0003 manda reavaliar o formato se a base passar de 3 a 4 mil tokens.

## Camada 4 — conversa (histórico)

Montada por `ContextAssembler` e `HistoryWindow`, sem `ChatMemory` do Spring AI (ADR-0015).

1. No fim da transação 1 (depois de gravar a mensagem do tutor) são lidas as **20 mensagens mais recentes** da conversa, incluindo a atual.
2. Só entram mensagens com o `conversationId` da conversa ativa. Conversas anteriores do mesmo contato, encerradas ou expiradas, **nunca** entram (ADR-0009).
3. A mensagem atual sai da lista e vai à parte: **nunca é cortada**. O limite de entrada dela é 2.000 caracteres (acima disso, `422`, e nada é gravado).
4. As anteriores (no máximo 19) passam pelo **orçamento de 4.000 tokens estimados**. O corte é da mais recente para a mais antiga: na primeira que não cabe, ela e todas as mais antigas ficam de fora. Vale o limite atingido primeiro, quantidade ou tokens.

**Estimativa de tokens:** tokenizador do Spring AI (JTokkit) com a codificação `o200k_base`, mais 4 tokens por mensagem para papel e separadores. A OpenAI não publica o tokenizador do `gpt-6-luna`, então o valor é uma **estimativa**: serve para limitar o histórico, não para calcular custo. O custo real vem do `usage` devolvido pela API e aparece no log.

| Parâmetro | Valor | Origem |
|---|---|---|
| `assistant.conversation.history-max-messages` | 20 (contando a atual) | DA-10 |
| `assistant.conversation.history-token-budget` | 4.000 | DA-28 |
| `assistant.conversation.max-customer-message-length` | 2.000 caracteres | DA-28 |

**Limite conhecido:** informação livre dita fora da janela se perde, porque não há resumo (ADR-0004). Os dados estruturados de tutor e pet passam a sobreviver à janela a partir da Etapa 5. A Etapa 6 mede quantas conversas passam da janela.

## Camada 5 — operacional (parte da Etapa 2)

Bloco `<estado_do_atendimento>`:
- **status** da conversa, em texto (`em atendimento pelo assistente`, `sinalizada para revisão da equipe`);
- **data e hora local** da clínica, no fuso `America/Sao_Paulo`, com dia da semana em português (ex.: `segunda-feira, 16/11/2026, 09:05`).

Encaminhamento, prioridade, atraso e solicitação em andamento entram nas Etapas 4 e 5.

## Do resultado do modelo ao que é gravado

```
Transação 1: resolve a conversa ativa (expira/abre) → grava a mensagem do tutor → lê a janela
Fora de transação: monta o contexto → chama o modelo (até 2×) → classifica → valida por fontes (V1–V7)
Transação 2: conversa mudou? → 409 | válido → grava intenção + resposta (+ pedido de informação se houver lacuna)
                                  | falha → grava a resposta segura
```

| Resultado (`TurnResultClassifier`) | Nova tentativa? | O que acontece |
|---|---|---|
| Válido | — | Grava a resposta com a intenção; atualiza a intenção corrente e a atividade |
| Intenção fora da lista | — | Vira `OTHER`; não é falha |
| Recusa (`refusal` ou `content_filter`) | Não | Resposta segura |
| Truncado (`finishReason` `length`, em qualquer caixa) | Sim, 1 | Resposta segura se falhar de novo |
| Inválido (não é JSON, sem `reply`, `intent`, `knowledgeRefs` ou `knowledgeGap`, `reply` vazio) | Sim, 1 | Idem |
| Inconsistente (reprovado pelo validador, V1–V7) | Sim, 1 | Idem |
| Timeout (30 s), 5xx, 429, rede | Sim, 1, após 1 s | Idem |
| Outro 4xx (ex.: chave da OpenAI inválida) | Não | Resposta segura |

Na falha, a mensagem do tutor continua gravada e o **estado da conversa não muda**: a intenção corrente fica como estava e a única atividade registrada é a da mensagem do tutor. A resposta segura é gravada como mensagem do assistente sem intenção, porque foi o que o tutor recebeu. O texto dela é provisório (P-10) e fica em `assistant.conversation.fallback-reply`. A resposta da API indica `fallback: true`.

**Concorrência (DA-21):** se outra mensagem chegou ou a conversa foi alterada durante a chamada ao modelo, a transação 2 percebe pela versão da conversa ou pela última mensagem gravada. O turno é descartado com `409` e `messageRecorded: true`. A mensagem do tutor fica gravada, sem resposta, e entra no histórico do turno seguinte.

**Lacuna de conhecimento (ADR-0008, item 4):** num turno válido com `knowledgeGap = true`, a transação 2 cria uma `ServiceRequest(INFORMATION)` em `PENDING`, com a pergunta do tutor no resumo (até 500 caracteres). Se a conversa já tem uma pendente, a nova pergunta é acrescentada ao mesmo resumo. Ninguém é notificado: a equipe verá os pedidos nas listagens da Etapa 4.

## Validação por fontes (ADR-0008, DA-29)

`ResponseValidator`, depois da classificação. Qualquer violação torna o turno **inconsistente**.

| # | Reprova quando |
|---|---|
| V1 | `knowledgeRefs` tem chave inexistente, de texto interno (`approved-texts`) ou só com itens DESCONHECIDO |
| V2 | O `reply` tem um valor (formatos abaixo) que não está autorizado |
| V3 | `knowledgeGap = true` e o `reply` tem valor que só a base autorizaria |
| V4 | Intenção `CLINIC_INFORMATION`, `SERVICE_INFORMATION` ou `PRICE_INQUIRY` sem fonte e sem lacuna |
| V5 | Expressão de confirmação de agendamento |
| V6 | Expressão de aviso à equipe, promessa de contato ou de resolução |
| V7 | Dose de medicamento que o tutor não escreveu |

**Valor autorizado** (V2) é o que vem: (a) dos itens **conhecidos** citados em `knowledgeRefs`, incluindo o implícito na chave (`hours.saturday` autoriza "sábado"; `hours.twenty-four-hours` autoriza falar de "24 horas"); dinheiro só se a clínica divulga preços; (b) do que o tutor escreveu nas mensagens enviadas ao modelo (eco); (c) do contexto operacional: dia da semana e data de hoje e de amanhã, e a hora atual.

**Formatos cobertos, todos com testes em `FactExtractorTest`:**

| Tipo | Exemplos detectados | Não detectados de propósito |
|---|---|---|
| Dinheiro | `R$ 120`, `R$120,00`, `R$ 1.200,50`, `120 reais`, `R$ 90,5`, faixas `R$ 90 a R$ 150` | valor por extenso; `R$ 90 a 150` (só a primeira ponta) |
| Horário | `8h`, `8h30`, `08:00`, `8:30`, `às 8`, `às 18 horas`, `das 8 às 18`, `até 18h`, `meio-dia`, `meia-noite` | `as 3` sem acento; `das 3` sem "às/até" depois |
| 24 horas | `24h`, `24 horas` (fora de prazo) | — |
| Dias | `segunda`…`domingo`, com ou sem `-feira`, plural; `qui.` com ponto; `seg a sex`; `fim de semana`; `feriado(s)` | abreviação solta sem ponto (`ter` também é verbo) |
| Datas | `25/12`, `25/12/2026`, `25 de dezembro` | "dia 25", "Natal" |
| Telefone | 8 a 13 dígitos, com ou sem `+55`, DDD e separadores; vale com ou sem DDD/`+55` | — |
| E-mail, site, perfil | `contato@x.example`, `https://www.x.example/` (comparado sem protocolo, `www` e barra), `@perfil` | domínio com terminação fora de `.com`, `.net`, `.org`, `.vet`, `.br`, `.example` |
| CEP | `01000-000`, `CEP 01000000` | 8 dígitos sem hífen e sem "CEP" (vira telefone) |
| Desconto e parcelamento | `10%`, `3x`, `em até 10 vezes` | `2 vezes ao dia` |
| Prazos | `em 48 horas`, `em até 2 dias úteis`, `em 30 minutos`, `com 24 horas de antecedência`, `em 48h` | prazo sem preposição antes |
| Doses (V7) | `5 mg`, `2,5 ml`, `0,5 mg/kg`, `3 gotas`, `meio comprimido`, `2 comprimidos`, `de 8 em 8 horas` | dose por extenso ("cinco miligramas") |

**Expressões (V5, V6):** `src/main/resources/validation/expression-rules.yml`, com o texto comparado em minúsculas, sem acentos e com espaços normalizados. Com "não" ou "nunca" até 3 palavras antes, a expressão não reprova ("ainda não está confirmado"). "Está tudo certo?" como pergunta não reprova.

**Limites conhecidos** (ADR-0008, não resolvidos): valores por extenso; afirmações sem valor detectável ("fazemos ultrassom"), cobertas só em parte pela V4; valor certo aplicado ao assunto errado dentro da mesma fonte citada; frases reescritas fora da lista; negação dupla ou confusa; eco de valor do tutor apresentado como se fosse da clínica.

**Avaliação com o modelo real** (2026-11-16, base fictícia, `KnowledgeTrapsAiIT`, 10 execuções por cenário):

| Cenário | Acertos | Reprovadas pelo validador | Aprovadas mas erradas |
|---|---|---|---|
| Preço não cadastrado (banho e tosa) | 10 | 0 | 0 |
| Serviço inexistente (acupuntura) | 10 | 0 | 0 |
| Feriado desconhecido (Natal) | 10 | 0 | 0 |
| Pedido de confirmação de consulta | 10 | 0 | 0 |
| Controle: preço cadastrado (consulta) | 10 | 0 | 0 |

O validador não reprovou nenhuma resposta, nem na primeira tentativa. É uma amostra pequena, com uma base pequena e perguntas diretas. A Etapa 6 amplia o conjunto.

## O que não vai ao modelo

- Mensagens de outras conversas, inclusive anteriores do mesmo contato.
- Identificador do contato (`externalContactId`), ids internos e versão.
- Textos internos da base (`approved-texts`).
- Nenhum dado real: até a Etapa 7, só dados fictícios (DA-17).

## O que vai ao log

Só metadados por chamada: tentativa, resultado, `finishReason`, modelo informado pela API, tokens de entrada e saída, códigos das violações do validador (ex.: `V2:MONEY`, `V6:v6a-avisei`, sem o texto) e latência. E por turno: id da conversa, se é nova, resultado e número de tentativas. **Nunca** o conteúdo das mensagens, o identificador do contato, a API Key ou o corpo de erro da OpenAI, que pode trazer parte da chave (DM-12).

Medido com o modelo real (2026-11-16, dados fictícios): turnos curtos de 3 mensagens com 810 a 940 tokens de entrada, 30 a 70 de saída, 1 a 3 s de latência.
