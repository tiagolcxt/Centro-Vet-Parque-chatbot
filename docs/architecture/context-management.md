# Gestão de contexto — o que vai ao modelo a cada mensagem

> Reflete o código ao fim da **Etapa 2 — Conversação** (2026-11-16). Camadas implementadas: **1** (identidade), **4** (conversa) e parte da **5** (operacional). As camadas 2 (institucional, E3) e 3 (tutor e pet, E5) ainda não existem.
> Desenho completo: [proposta, seção 4.2](../plans/etapa-0-analise-e-proposta.md). Decisões: ADR-0002, ADR-0004, ADR-0015, ADR-0016.

## Princípio

Nada fica só em memória. A cada mensagem, a aplicação **reconstrói o contexto a partir do banco** e envia ao modelo uma única chamada com saída estruturada. O modelo não acessa o banco, não tem tools e não decide nada sozinho: a aplicação classifica a resposta e decide o que gravar (ADR-0002).

## Ordem das mensagens enviadas

| # | Papel | Conteúdo | Camada | Origem |
|---|---|---|---|---|
| 1 | `system` | Identidade, idioma, limites, regra de segurança e formato da resposta | 1 | `prompts/system-message.st` |
| 2 | `system` | `<estado_do_atendimento>`: status da conversa e data/hora local da clínica | 5 | `prompts/operational-context.st` + `Conversation` + `Clock` |
| 3…n | `user` / `assistant` | Mensagens anteriores **da conversa atual**, em ordem | 4 | Tabela `message` |
| n+1 | `user` | A mensagem que o tutor acabou de enviar | 4 | Requisição |

As mensagens do tutor (anteriores e atual) vão delimitadas por `<mensagem_do_cliente>…</mensagem_do_cliente>`. Se o tutor escrever essas marcas no texto, elas são removidas antes do envio, para que ele não consiga "fechar" o bloco e escrever fora dele. O prompt de sistema manda tratar o conteúdo delimitado como dado, nunca como instrução.

A resposta pedida é o `AssistantTurn` v1, com JSON Schema **estrito** (`assistant_turn_v1`):

| Campo | Uso na Etapa 2 |
|---|---|
| `reply` | Texto enviado ao tutor |
| `intent` | Uma das 11 intenções (lista fechada no schema). **Só registro**: grava em `message.intent` e `conversation.current_intent`, sem regra associada |

## Camada 1 — identidade e comportamento

Arquivo `src/main/resources/prompts/system-message.st`, em UTF-8, **sem nenhum dado da clínica**. Linhas que começam com `##` são notas para quem mantém o arquivo e não vão ao modelo.

Conteúdo **provisório** (P-10, texto final na Etapa 6): persona genérica, pt-BR, "você", respostas curtas e sem emojis. As regras fixas:
- não diagnosticar, não interpretar sintomas, não indicar medicamento, dose ou tratamento;
- **não inventar** horário, endereço, telefone, preço, serviço, convênio ou política. Na Etapa 2 o assistente não conhece nada da clínica e deve dizer isso;
- não marcar nem confirmar agendamento;
- não dizer que avisou ou transferiu alguém, nem que alguém vai entrar em contato;
- possível risco ao animal: não minimizar e recomendar atendimento veterinário o quanto antes. **Regra genérica provisória**: a orientação aprovada pela clínica e a lista de sinais entram na Etapa 4 (P-02, ADR-0019).

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
Fora de transação: monta o contexto → chama o modelo (até 2×) → classifica
Transação 2: conversa mudou? → 409 | válido → grava intenção + resposta | falha → grava a resposta segura
```

| Resultado (`TurnResultClassifier`) | Nova tentativa? | O que acontece |
|---|---|---|
| Válido | — | Grava a resposta com a intenção; atualiza a intenção corrente e a atividade |
| Intenção fora da lista | — | Vira `OTHER`; não é falha |
| Recusa (`refusal` ou `content_filter`) | Não | Resposta segura |
| Truncado (`finishReason` `length`, em qualquer caixa) | Sim, 1 | Resposta segura se falhar de novo |
| Inválido (não é JSON, sem `reply` ou `intent`, `reply` vazio) | Sim, 1 | Idem |
| Timeout (30 s), 5xx, 429, rede | Sim, 1, após 1 s | Idem |
| Outro 4xx (ex.: chave da OpenAI inválida) | Não | Resposta segura |

Na falha, a mensagem do tutor continua gravada e o **estado da conversa não muda**: a intenção corrente fica como estava e a única atividade registrada é a da mensagem do tutor. A resposta segura é gravada como mensagem do assistente sem intenção, porque foi o que o tutor recebeu. O texto dela é provisório (P-10) e fica em `assistant.conversation.fallback-reply`. A resposta da API indica `fallback: true`.

**Concorrência (DA-21):** se outra mensagem chegou ou a conversa foi alterada durante a chamada ao modelo, a transação 2 percebe pela versão da conversa ou pela última mensagem gravada. O turno é descartado com `409` e `messageRecorded: true`. A mensagem do tutor fica gravada, sem resposta, e entra no histórico do turno seguinte.

## O que não vai ao modelo

- Mensagens de outras conversas, inclusive anteriores do mesmo contato.
- Identificador do contato (`externalContactId`), ids internos e versão.
- Dados da clínica (ainda não existem no sistema).
- Nenhum dado real: até a Etapa 7, só dados fictícios (DA-17).

## O que vai ao log

Só metadados por chamada: tentativa, resultado, `finishReason`, modelo informado pela API, tokens de entrada e saída e latência. E por turno: id da conversa, se é nova, resultado e número de tentativas. **Nunca** o conteúdo das mensagens, o identificador do contato, a API Key ou o corpo de erro da OpenAI, que pode trazer parte da chave (DM-12).

Medido com o modelo real (2026-11-16, dados fictícios): turnos curtos de 3 mensagens com 810 a 940 tokens de entrada, 30 a 70 de saída, 1 a 3 s de latência.
