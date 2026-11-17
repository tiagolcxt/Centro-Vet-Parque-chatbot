# ADR-0017 — Orquestração do turno entre módulos

- **Status:** Aceito
- **Data:** 2026-11-17
- **Decisão:** DA-32 (resolve PT-02)

## Contexto

A partir da Etapa 4, um único turno de conversa pode exigir alterações em mais de um módulo ao mesmo tempo:
- encaminhar a conversa (`WAITING_HUMAN`, motivo e momento);
- criar uma `ServiceRequest(HUMAN_CONTACT)`;
- na Etapa 5, registrar ou atualizar dados de tutor e pet.

Pela ADR-0007, o encaminhamento e a solicitação nascem **na mesma transação**: ou tudo muda, ou nada muda. A revisão técnica (9.6) apontou o risco de casos de uso chamarem casos de uso de outros módulos: transações aninhadas, acoplamento e ordem de efeitos difícil de testar. A Etapa 3 já criou `ServiceRequest(INFORMATION)` a partir do turno, usando o repositório direto.

## Decisão

1. O caso de uso do turno (`HandleIncomingMessageUseCase`, módulo `conversation`) **usa diretamente as portas de repositório e os objetos de domínio** de `servicerequest` e `tutor`, dentro da sua transação 2 (a que grava a resposta).
2. As **regras de cada agregado continuam dentro dele**. O turno só chama métodos de domínio (ex.: `ServiceRequest.open`, `describe`, `submit`). Ele nunca altera estado por fora nem reimplementa uma invariante.
3. **Casos de uso dos outros módulos existem só para entradas externas**: endpoints da equipe (`PATCH /api/service-requests/{id}`, listagens). O turno não os chama.
4. **Nenhum módulo acessa a infraestrutura de outro**: entidades JPA, repositórios Spring Data e adaptadores continuam *package-private*. A dependência entre módulos é sempre por porta de domínio.
5. **Direção das dependências:** `conversation.application` pode depender de `servicerequest.domain`, `tutor.domain` e `clinicknowledge.domain`. O contrário não é permitido. Os módulos `servicerequest` e `tutor` não conhecem `conversation.application`.

## Alternativas consideradas

- **O turno chama casos de uso de outros módulos** (opção B da PT-02): isola mais os módulos, mas cria transações aninhadas (ou exige propagar a transação por convenção) e acopla casos de uso entre si. Rejeitada.
- **Eventos de domínio entre módulos:** desacopla, mas a consistência na mesma transação passaria a depender de ouvintes síncronos, mais difíceis de testar. Desproporcional ao MVP.

## Consequências

- Tudo o que um turno altera fica numa transação só, e o teste de integração verifica o resultado conjunto.
- O caso de uso do turno conhece o domínio de `servicerequest` e `tutor`. Para não virar um "faz-tudo", a lógica de cada decisão fica em componentes próprios do módulo `conversation` (`HandoffPolicy`, `ReplyComposer`, detectores), e o caso de uso só aplica o resultado.
- Se um dia o adaptador WhatsApp exigir processamento assíncrono, esta decisão é revista junto com a serialização de mensagens (DA-21).
