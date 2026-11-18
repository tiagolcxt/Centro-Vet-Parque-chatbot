# ADR-0008 — Validação de respostas e rastreamento de fontes institucionais

- **Status:** Aceito. A lista do validador foi decidida em 2026-11-16 (PT-11 → DA-29)
- **Data:** 2026-11-11 (lista final e V7: 2026-11-16)
- **Decisões:** DA-12, DA-26 (V6 ampliada), DA-29 (lista do validador e V7)

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
| V2 | **Valores autorizados** | Valores factuais detectados no `reply` (formatos em 3) precisam estar **autorizados**, ou seja: (a) pertencer aos valores dos itens citados em `knowledgeRefs`; (b) aparecer nas mensagens do tutor da conversa atual ou nos campos da solicitação em andamento (eco); ou (c) vir do contexto operacional: data, dia da semana e hora atuais (DA-29). |
| V3 | **Coerência com a lacuna** | Se `knowledgeGap = true`, o `reply` não pode conter valores factuais institucionais (V2-a). Deve dizer que vai confirmar com a equipe. |
| V4 | **Resposta institucional sem fonte** | Intenções `CLINIC_INFORMATION`, `SERVICE_INFORMATION` ou `PRICE_INQUIRY` com `knowledgeRefs` vazio e `knowledgeGap = false` são reprovadas. |
| V5 | **Confirmação de agendamento** | Expressões que afirmam agendamento confirmado são reprovadas (ADR-0011). |
| V6 | **Promessa sem processo** (ampliada pelas Diretrizes, DA-26) | São reprovadas expressões que afirmam: (a) que alguém foi avisado ou notificado; (b) que um funcionário **entrará em contato** ou dará retorno, salvo nos textos aprovados com processo definido (P-14); (c) que a solicitação foi **resolvida** ou "está tudo certo" quando só houve registro (ADR-0007, ADR-0011). |
| V7 | **Dose de medicamento** (DA-29) | Doses detectadas no `reply` são reprovadas: unidades (`mg`, `ml`, `mg/kg`, `gotas`), quantidades de comprimido ("2 comprimidos", "meio comprimido") e intervalos ("de 8 em 8 horas"). O eco de uma dose escrita pelo tutor não é reprovado. Barreira automática para a proibição de prescrever (DA-15), que antes era só instrução ao modelo. |

### 3. Formatos detectados e expressões (DA-29, aprovada em 2026-11-16)

**Valores (V2).** A comparação é feita sobre o valor normalizado, não sobre o texto.

| Tipo | Formatos cobertos | Normalização |
|---|---|---|
| Dinheiro | `R$ 120`, `R$120`, `R$ 120,00`, `R$ 1.200,50`, `120 reais`, `120,00 reais`; faixas (`R$ 90 a R$ 150`, as duas pontas); `a partir de R$ 90` | Centavos |
| Horário | `8h`, `8h30`, `8:30`, `08:00`, `às 8`, `das 8 às 18`, `8 horas`, `meio-dia`, `meia-noite` | HH:MM |
| Atendimento 24 horas | `24h`, `24 horas` | Marcador próprio: precisa estar na base |
| Dias | `segunda` … `domingo`, com ou sem `-feira`; abreviações (`seg`, `ter`, `qua`, `qui`, `sex`, `sáb`, `dom`); `fim de semana`; `feriado` | Nome do dia |
| Datas | `25/12`, `25/12/2026`, `25 de dezembro` | Dia e mês |
| Telefone | 8 a 13 dígitos, com ou sem `+55`, DDD e separadores | Só dígitos; autorizado se coincidir com um número autorizado, com ou sem DDD e `+55` |
| E-mail, site e redes | Formatos usuais; `@perfil` | Sem diferenciar maiúsculas, sem protocolo e sem barra final |
| CEP | `00000-000`, `00000000` | Só dígitos |
| Desconto e parcelamento | `10%`, `10% de desconto`, `3x`, `em até 10 vezes` | Número e tipo |
| Prazos | `em 48 horas`, `em até 2 dias úteis`, `em 30 minutos` | Número e unidade |

**Fora de propósito:** números sem unidade nem moeda ("3 anos", "2 vezes ao dia"), para evitar alarmes falsos. Se a clínica decidir não divulgar preços (P-11), uma configuração passa a reprovar qualquer valor em dinheiro.

**Expressões (V5, V6).** Ficam num arquivo versionado de regras, com testes. O texto é comparado em minúsculas, sem acentos e com espaços normalizados. Lista inicial:

| Verificação | Expressões |
|---|---|
| V5 — confirmação de agendamento | "consulta está/foi/ficou marcada, agendada, confirmada, reservada"; "agendamento confirmado/feito/marcado"; "já marquei", "agendei", "reservei", "confirmei"; "está (tudo) marcado/agendado/confirmado"; "horário/vaga reservado(a), garantido(a)"; "fica combinado", "ficamos combinados"; "esperamos você amanhã / às… / no dia…" |
| V6a — dizer que avisou | "avisei", "notifiquei", "repassei", "encaminhei", "transferi" (para a equipe, o veterinário, a recepção ou alguém); "a equipe já foi avisada", "já está ciente"; "sua mensagem foi encaminhada/transferida" |
| V6b — prometer contato | "vão te ligar", "vão retornar", "vão entrar em contato"; "retornaremos", "entraremos em contato"; "aguarde nosso contato/retorno". Exceção: textos aprovados com processo real (P-14); hoje nenhum |
| V6c — dizer que resolveu | "já está resolvido", "ficou tudo certo", "está tudo certo"; "problema resolvido"; "sua solicitação foi concluída". "Tudo certo?" como pergunta não é reprovado |

**Regra de negação:** uma expressão de V5 ou V6 precedida de "não", "nunca" ou "ainda não" até 3 palavras antes não é reprovada (ex.: "ainda **não** está confirmado"). Limite aceito: negação dupla ou confusa pode escapar.

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

## Implementação (Etapa 3, 2026-11-16)

- `ResponseValidator` roda depois da classificação, dentro de cada tentativa: resposta reprovada é **inconsistente** e vale a única nova tentativa do turno (ADR-0002, DA-28).
- **Formatos efetivamente implementados**, com exemplos detectados e não detectados: [context-management.md, validação por fontes](../context-management.md#validação-por-fontes-adr-0008-da-29). Escolhas de detecção dentro da lista aprovada, para evitar alarmes falsos óbvios:
  - `às 8` só com "às" acentuado; `das 8` só seguido de "às/até";
  - abreviação de dia só com ponto (`qui.`) ou num intervalo (`seg a sex`), porque "ter" também é verbo;
  - prazo só com preposição antes (`em`, `em até`, `dentro de`, `com`, `por`, `após`, `depois de`, `antes de`), porque "até 18h" é horário;
  - "24 horas" vira prazo quando há preposição (`com 24 horas de antecedência`) e afirmação de atendimento 24 h quando não há.
- **Valores implícitos na chave:** citar `hours.<dia>` autoriza o nome do dia (e "fim de semana" para sábado e domingo); citar `hours.holidays` conhecido autoriza "feriado"; citar `hours.twenty-four-hours` conhecido autoriza falar de "24 horas", inclusive para negar.
- **Fonte (c) do V2** (DA-29): dia da semana e data de hoje e de amanhã, e a hora atual.
- **Eco (V2-b):** as mensagens do tutor que foram ao modelo no turno (janela de histórico e mensagem atual). Os campos da solicitação em andamento entram na Etapa 5.
- **V6b:** a lista `allowed` do arquivo de expressões existe para textos aprovados com processo real (P-14) e hoje está vazia.
- **Testes:** `FactExtractorTest` (cada formato, detectado e não detectado), `ResponseValidatorTest` (V1–V7, aprovado e reprovado), `KnowledgeTurnIT` (lacuna, reprovação e resposta corrigida), `KnowledgeTrapsAiIT` (modelo real).
- **Linha de base com o modelo real** (2026-11-16, base fictícia, 5 cenários × 10): **0 reprovações** em 50 respostas; todas corretas. Detalhes em context-management.md.

## Revisão (2026-11-17): DA-41

- **V3 removida.** Com a V2 exigindo fonte citada para todo valor institucional, a V3 ("lacuna com valor da base") só barrava respostas parciais legítimas, como "não funcionamos 24 horas; sobre o plantão não tenho a informação". No teste manual, isso virou resposta segura duas vezes seguidas.
- **Citação de seção inteira:** `knowledgeRefs` pode trazer o nome de uma seção (`hours`, `services`, `payment`) quando a resposta resume várias linhas dela; todos os itens da seção autorizam os valores. Seções internas (`approved-texts`, `review`) continuam fora.
- A resposta segura passou a trazer o próximo passo (ligar para a recepção, DA-40).
