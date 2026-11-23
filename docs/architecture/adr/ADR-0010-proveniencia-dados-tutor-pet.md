# ADR-0010 — Proveniência das informações de tutor e pet

- **Status:** Aceito, com pendência (P-08, valor provisório na Etapa 5). PT-09 → DA-35 e PT-12 → DA-36 (2026-11-17); implementado na Etapa 5
- **Data:** 2026-11-11
- **Decisão:** DA-14

## Contexto

O briefing exige não fazer o tutor repetir informações e, ao mesmo tempo, não tratar informações de outra conversa como fatos confirmados. A proposta original distinguia apenas "confirmado nesta conversa" e "registrado anteriormente", e chamava de "confirmado" um dado que era só extraído pelo modelo (revisão técnica, 9.2 item 6).

## Decisão

1. Toda informação de tutor e pet apresentada ao modelo carrega um **status de proveniência**:

   | Status | Significado | Uso permitido |
   |---|---|---|
   | **MENCIONADA** | Dita pelo tutor **nesta conversa** e extraída pelo modelo, sem confirmação | Pode ser usada na conversa; deve ser tratada como possivelmente imprecisa |
   | **CONFIRMADA** | Confirmada pelo tutor **nesta conversa**, segundo o critério de P-08 | Pode ser usada e registrada em solicitações |
   | **CADASTRADA** | Existe no cadastro, foi confirmada em atendimento anterior e está dentro do prazo de validade (P-08) | Pode ser usada para reconhecer e perguntar ("É sobre a Mel?"); **não** é confirmação atual |
   | **A_RECONFIRMAR** | Existe no cadastro, mas nunca foi confirmada ou está fora do prazo de validade | Só pode ser mencionada como pergunta de reconfirmação |

2. **Informação antiga nunca vale como confirmação atual.** Ao abrir uma conversa nova (ADR-0009), todo o cadastro entra como `CADASTRADA` ou `A_RECONFIRMAR`. Só passa a `CONFIRMADA` com confirmação do tutor na conversa atual.
3. **O cadastro guarda só dados persistentes** do tutor (nome, telefone/contato) e do pet (nome, espécie, raça, idade ou data de nascimento informada). Motivo de consulta, sintomas e preferências de horário **não** entram no cadastro: pertencem à solicitação ou à conversa.
4. Dados extraídos pelo modelo são **normalizados e validados pelo backend** (tamanho, espécie no enum, formatos) antes de gravar; dado inválido é descartado. O modelo nunca grava diretamente (ADR-0002).
5. O contexto de um tutor nunca contém dados de outro tutor: as consultas são sempre filtradas pelo tutor vinculado à conversa. Esse vínculo vem do identificador do canal, nunca de um telefone digitado.
6. As colunas de proveniência entram no schema na **Etapa 5** (ADR-0005).

## Pendências ligadas

- **P-08:** critério para considerar uma informação confirmada (ex.: o assistente repete e o tutor concorda explicitamente?), prazo de validade de `CADASTRADA` e quais status são exigidos para registrar uma solicitação.
- ~~**PT-09**~~ → **DA-35** (ver "Decisões complementares").
- ~~**PT-12**~~ → **DA-36** (ver "Decisões complementares").

## Alternativas consideradas

- **Dois níveis (confirmado × anterior):** chamava de "confirmado" o que era só extraído; rejeitada.
- **Não reutilizar nada entre conversas:** obriga o tutor a repetir tudo; contraria o objetivo 10.
- **Reutilizar o cadastro como verdade atual:** contraria o briefing (4.3).

## Consequências

- O contexto do tutor/pet é renderizado com o status de cada informação, e o prompt instrui como agir em cada status.
- A detecção de confirmação depende de metadados do modelo e de uma regra do backend, a definir em P-08. É uma fonte de erro a medir na Etapa 6.

## Decisões complementares (2026-11-17)

**DA-35 — Granularidade e correspondência de pets (PT-09, opção A).**
- A proveniência é **por campo**, numa tabela própria: `subject` (`TUTOR`/`PET`), `subject_id`, `field` (nome, telefone, espécie, raça, idade), `status`, `conversation_id`, `confirmed_at`, `updated_at`. Um campo duvidoso não rebaixa o registro inteiro.
- `MENCIONADA` e `CONFIRMADA` valem só para a conversa gravada em `conversation_id`. Numa conversa nova, `CADASTRADA` ou `A_RECONFIRMAR` é **calculado** a partir de `confirmed_at` e do prazo de validade (P-08), sem gravar nada.
- Correspondência de pets: só entre os pets do tutor da conversa, por igualdade do nome normalizado (minúsculas, sem acento, espaços colapsados). Nome sem correspondência, dois pets com o mesmo nome ou espécie divergente → o assistente pergunta, e o pet não é vinculado até a resposta. Sem similaridade aproximada no MVP: um erro de correspondência misturaria dados de animais diferentes.

**DA-36 — Criação do tutor (PT-12, opção A).**
- O `Tutor` só é criado no primeiro turno em que o backend aceita um dado normalizado de tutor ou pet; a conversa é vinculada nesse momento. Contato que só tira dúvidas não gera cadastro (minimização).
- Se o contato já tem cadastro, a conversa nova é vinculada na abertura, pelo identificador do canal.

## Implementação (Etapa 5, 2026-11-17)

- Migration V4: tabela `data_provenance` (único por `subject` + `subject_id` + `field`; status `MENTIONED` ou `CONFIRMED`; `confirmed_at` obrigatório quando `CONFIRMED`). Espécie do pet passou a opcional.
- Domínio: `Provenance` (`mention`, `confirm`, `statusFor`), `ProvenanceStatus`, `DataField`, `DataSubject`, `NameKey`, `PetMatcher`.
- Aplicação: `CustomerDataCollector` (normalização, tutor, pets, proveniência, confirmação, agendamento), `CustomerDataNormalizer`, `AppointmentRequirements`; camada 3 montada pelo `ContextAssembler` só com o tutor vinculado à conversa.
- **P-08 provisório:**
  - confirmação = a mensagem anterior do assistente repetiu o valor gravado e o tutor concordou (o modelo marca e o backend confere; discordância anula);
  - validade de CADASTRADA = 365 dias (`assistant.customer-data.registered-validity`);
  - registrar um agendamento exige nome e espécie do pet confirmados na conversa atual;
  - (2026-11-23, achado A1) para o pet do agendamento em coleta, responder à pergunta de confirmação sem corrigir também confirma: o assistente pergunta no máximo uma vez ([business-rules.md, 10.4](../business-rules.md#104-confirmação-p-08-provisório)).
- O assistente não coleta telefone (o contato vem do canal).
- Detalhes e tabelas de decisão: [business-rules.md, seção 10](../business-rules.md#10-coleta-de-dados-e-pedido-de-agendamento-etapa-5).
- Testes: `ProvenanceAndPetMatcherTest`, `CustomerDataNormalizerTest`, `CustomerDataIT` e `CustomerDataAiIT`.
