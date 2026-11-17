# ADR-0016 — Modelo, parâmetros, timeout e retry

- **Status:** Aceito (2026-11-16). As 4 verificações técnicas passaram no mesmo dia (resultado abaixo)
- **Data:** 2026-11-16
- **Decisão:** DA-28 (resolve P-09)

## Contexto

A Etapa 2 conecta o assistente ao modelo via Spring AI 2.0.0-M4 (`ChatClient`, API de Chat Completions da OpenAI) com saída estruturada (ADR-0002). É preciso fixar o modelo, os parâmetros de geração, os limites de tamanho, o timeout e a política de novas tentativas.

**Fatos verificados em 2026-11-16** (fontes no fim):
- O `gpt-4o-mini`, usado no projeto de referência, está **depreciado**, com desligamento da API em **14/01/2027**. O substituto indicado pela OpenAI é o `gpt-6-luna`.
- `gpt-6-luna`:
  - suporta Chat Completions e Responses, saída estruturada (JSON Schema), streaming e cache de prompt;
  - custa US$ 0,10 por milhão de tokens de entrada (US$ 0,01 em cache) e US$ 0,50 por milhão de saída;
  - tem janela de 1,05 milhão de tokens e conhecimento até 18/05/2026;
  - usa raciocínio com `reasoning_effort` `none`, `low`, `medium` (padrão), `high`, `xhigh` e `max`;
  - **não tem snapshot datado**: só o identificador `gpt-6-luna`.
- A página do modelo **não informa** se `temperature` é aceita. Em modelos de raciocínio anteriores (família GPT-5), valores diferentes do padrão retornavam erro 400.

## Decisão

| Parâmetro | Valor proposto | Justificativa |
|---|---|---|
| Modelo | `gpt-6-luna` (configurável em `assistant.ai.model`) | O modelo da referência sai do ar em menos de 2 meses; o Luna é o substituto oficial, mais barato e com saída estruturada |
| `reasoning_effort`, chamada principal | `low` | Um pouco de raciocínio ajuda na classificação de intenção e prioridade, com latência menor que o padrão (`medium`). Alternativa: `none` (mais rápido, a medir na Etapa 6) |
| `reasoning_effort`, modo restrito (`WAITING_HUMAN`) | `low` | Detecta possível emergência; a lista determinística (DA-27) e o fail-safe cobrem erros |
| `temperature` | **Não enviada** (padrão do modelo) | Não há confirmação de suporte; enviar um valor não aceito quebraria toda chamada. O Spring AI pode enviar um valor padrão por conta própria: **verificar e desligar na Etapa 2** |
| Limite de tokens de saída (`max_completion_tokens`), principal | 2.000 | Os tokens de raciocínio contam nesse limite; resposta truncada é falha (ADR-0002), então o limite precisa sobrar |
| Limite de tokens de saída, modo restrito | 500 | A saída é só de sinais |
| Orçamento de tokens do histórico (ADR-0004) | 4.000 tokens estimados (além do limite de 20 mensagens) | Cobre conversas típicas com folga; custo previsível |
| Tamanho máximo da mensagem do tutor | 2.000 caracteres (acima disso, `422` na API) | Mensagens de atendimento costumam ser curtas; limita custo e abuso. O WhatsApp aceita até 4.096 caracteres, então o adaptador futuro vai precisar de outra estratégia |
| Timeout, chamada principal | 30 s | Raciocínio `low` + saída estruturada |
| Timeout, modo restrito | 15 s | Em falha, entra o fail-safe |
| Novas tentativas, chamada principal | **No máximo 1 por turno**, para erro técnico (timeout, 5xx, 429), resposta truncada, inválida ou inconsistente. **Recusa não é repetida.** Espera de 1 s antes de repetir | Limita latência (pior caso ≈ 60 s) e custo (no máximo 2 chamadas por turno) |
| Novas tentativas, modo restrito | Nenhuma | O fail-safe responde na hora, com orientação de emergência |
| Testes com IA real (`@Tag("ai")`) | Cada cenário 10 vezes; aprovação ≥ 9/10; comportamentos proibidos 0/10 | Já previsto no roadmap e nos cenários CT |

### Estimativa de custo (ordem de grandeza, sem cache)

Um turno típico tem cerca de 8 mil tokens de entrada (prompt, base institucional, histórico e contexto) e uns 600 de saída (incluindo raciocínio). Isso custa cerca de **US$ 0,0011 por turno**, ou **≈ US$ 1,10 a cada mil mensagens**. O cache de prompt reduz bastante a parte fixa. Os valores reais serão medidos na Etapa 6 (métricas por turno).

## Verificações obrigatórias no início da Etapa 2

1. O Spring AI 2.0.0-M4 envia `reasoning_effort` para o `gpt-6-luna` pela Chat Completions e aceita o valor `low`.
2. O Spring AI **não** envia `temperature` por padrão. Se enviar, desligar explicitamente.
3. A saída estruturada em modo estrito funciona com o `AssistantTurn` (sem array no topo do schema).
4. A recusa e o `finish_reason` de truncamento chegam até a aplicação.

Se algum item falhar, a Etapa 2 para e o ponto volta como decisão, sem troca de modelo por conta própria.

### Resultado (2026-11-16): as 4 verificações passaram

Teste `OpenAiCompatibilityProbeIT` (`./gradlew aiTest`), contra a API real, com dados fictícios. O corpo da requisição HTTP enviada foi capturado e conferido.

| # | Verificação | Resultado observado |
|---|---|---|
| 1 | `reasoning_effort` enviado e aceito | Requisição com `"model":"gpt-6-luna"` e `"reasoning_effort":"low"`; resposta normal (`finish_reason` = `STOP`). Modelo informado na resposta: `gpt-6-luna` |
| 2 | Sem `temperature` | O campo **não** aparece na requisição: o autoconfigure do M4 só define o modelo |
| 3 | Saída estruturada estrita | `response_format` = `json_schema` com `strict: true`; resposta em JSON válido conforme o schema, com `intent` dentro dos valores permitidos |
| 4 | Truncamento e recusa visíveis | Com `max_completion_tokens` = 16, `finishReason` = `LENGTH`. Os metadados da `AssistantMessage` incluem a chave `refusal` (além de `finishReason`, `id`, `role` etc.) |

Uso medido numa resposta curta: 95 tokens de entrada e 86 de saída, já incluindo o raciocínio `low`.

**Achados para a implementação:**
- O `finishReason` vem com o **nome do enum em maiúsculas** (`STOP`, `LENGTH`), não com o valor da API (`stop`, `length`). A comparação deve ignorar caixa.
- O Spring AI tem **retry próprio**: por padrão, 10 tentativas com espera inicial de 2 s multiplicada por 5. Isso contraria a DA-28, por isso foi desligado. A única nova tentativa por turno é responsabilidade da aplicação.
- A M4 usa cliente HTTP próprio (não o SDK oficial da OpenAI) e aceita `RestClientCustomizer`.

### Complemento (Etapa 2, 2026-11-16): timeout e retry verificados contra um servidor local

O `SpringAiAssistantModelIT` aponta o adaptador real para um servidor HTTP local que imita a API da OpenAI (nenhuma chamada sai da máquina). Resultados:
- **`spring.ai.retry.max-attempts` não é o total de chamadas.** Na M4 o valor vira `RetryPolicy.maxRetries` (novas tentativas além da primeira). Com `1`, um erro 500 gerou **2** chamadas. Valor corrigido para **`0`**: uma chamada só, verificada para 500 e 429.
- O Spring AI trata **todo 4xx como não transitório**, inclusive o 429. A aplicação reclassifica o 429 como repetível, conforme a tabela acima. Os outros 4xx (ex.: chave inválida) não são repetidos.
- O cliente HTTP efetivo é o **Reactor Netty** (está no classpath via Spring AI). O prazo vem de `spring.http.clients.read-timeout: 30s` (e `connect-timeout: 5s`). O timeout chega como `io.netty.handler.timeout.ReadTimeoutException` e é reconhecido como timeout. Com 2 s configurados no teste, a chamada foi cortada entre 2 e 4 s.
- O corpo de erro da OpenAI pode trazer parte da chave (ex.: 401). O adaptador só repassa o código HTTP.
- O timeout de 15 s do modo restrito (Etapa 4) vai exigir um cliente separado, porque a propriedade vale para todo o `RestClient` da aplicação.

## Riscos e consequências

- **Sem snapshot datado:** a OpenAI pode atualizar o `gpt-6-luna` sem mudar o nome, e o comportamento pode mudar. Mitigação: registrar o modelo informado em cada resposta da API nas métricas (sem conteúdo) e rodar a bateria de cenários com IA real a cada mudança percebida.
- **Modelo novo (lançado em 2026):** há pouca experiência pública. A bateria da Etapa 6 é o critério de permanência.
- **Transferência internacional (P-04):** a página do modelo oferece residência de dados na União Europeia, mas não no Brasil. Isso entra na avaliação jurídica da barreira G3.
- Trocar de modelo é só configuração (`assistant.ai.model`) e uma nova rodada da bateria de cenários. O domínio não muda.

## Alternativas consideradas

- **Manter `gpt-4o-mini`:** sai da API em 14/01/2027, antes do fim do MVP. Rejeitada.
- **`gpt-6-luna` com `reasoning_effort` `none`:** menor latência e custo, mas menos raciocínio na classificação. Fica como alternativa a medir.
- **`gpt-6-luna` com `medium` (padrão):** mais raciocínio, porém mais latência e mais tokens de saída para um atendimento conversacional.
- **Modelo maior da família:** custo maior sem necessidade demonstrada; reavaliar só se a Etapa 6 mostrar qualidade insuficiente.

## Fontes

- [OpenAI — Deprecations](https://developers.openai.com/api/docs/deprecations)
- [OpenAI — GPT-6 Luna (modelo)](https://developers.openai.com/api/docs/models/gpt-6-luna)
- [OpenAI — Pricing](https://developers.openai.com/api/docs/pricing)
- [OpenAI — Introducing GPT-6 Sol and Luna](https://openai.com/index/introducing-gpt-6-sol-and-luna/)
- [Spring AI — Provider-Native Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html)
- [Spring AI 2.0.0-M4 — anúncio](https://spring.io/blog/2026/03/26/spring-ai-2-0-0-M4-and-1-1-4-and-1-0-5-available/)
- [OpenAI Community — Temperature in GPT-5 models](https://community.openai.com/t/temperature-in-gpt-5-models/1337133)

### Complemento (Etapa 4, 2026-11-17): modo restrito

- O prazo de 15 s do modo restrito é aplicado à **resposta**, com um `Future` e `get(15 s)`: passado o prazo, entra o fail-safe. A requisição HTTP segue até o read-timeout geral (30 s) e é descartada. Um cliente HTTP próprio evitaria isso; não foi necessário no MVP.
- Saída de até 500 tokens (`maxCompletionTokens` só nas opções do modo restrito) e nenhuma nova tentativa.
- O schema principal passou a `assistant_turn_v3`. Com o enum de intenção usado em dois campos, o gerador põe a definição em `$defs` e referencia por `$ref`; o modo estrito da OpenAI aceitou (testes com IA real da Etapa 4 e repetição dos das Etapas 2 e 3).
