# ADR-0001 — Arquitetura modular com princípios hexagonais

- **Status:** Aceito, com pendências (PT-02)
- **Data:** 2026-11-11
- **Decisões:** DM-01 (mantida), DA-04, DA-08

## Contexto

O briefing exige separação entre domínio, aplicação e infraestrutura, domínio independente de Spring AI/OpenAI/JPA, e evolução futura para WhatsApp, agenda e sistemas de gestão sem reescrever o núcleo. Também pede para evitar abstrações prematuras. A referência (`budgeting`) mistura `@Tool` nos casos de uso e coloca orquestração no controller.

## Decisão

1. Organização **por módulo de domínio**: `conversation`, `tutor`, `servicerequest`, `clinicknowledge`, `ai`, `shared`. Cada módulo tem `domain` / `application` / `infrastructure` apenas quando houver código para essas camadas.
2. **Domínio em Java puro**: sem anotações de Spring, JPA ou Spring AI. Entidades JPA separadas, com mapeamento explícito.
3. **Interfaces só onde há substituição real:**
   - `AssistantModel` (porta em `conversation/application`), implementada por `SpringAiAssistantModel` (`ai/infrastructure`) e por um *double* nos testes;
   - um repositório por agregado (porta no domínio, adaptador JPA). A motivação é isolar o domínio da JPA, não permitir implementações em memória (ver ADR-0006).
4. **Tutor e Pet no mesmo módulo `tutor`** (DA-04): o pet não existe sem tutor e as regras de privacidade são as mesmas.
5. **Sem interface de canal no MVP**: o caso de uso recebe o comando neutro `IncomingMessage(channel, externalContactId, text, receivedAt)`. O identificador do contato é fornecido pelo canal, nunca extraído do texto.
6. **Sem Lombok** (DA-08): `record` para DTOs e objetos de valor; classes simples para entidades.
7. Pacotes sem underscore (`servicerequest`, `clinicknowledge`).

## Alternativas consideradas

- **Camadas globais** (`controller/service/repository`): mais familiar, mas espalha cada conceito por todo o projeto e não isola o domínio.
- **Tutor e Pet em módulos separados**: segue literalmente a sugestão do briefing; gera dependência cruzada sem ganho no MVP.
- **Interface para cada serviço/caso de uso**: abstração sem segunda implementação; rejeitada.
- **Lombok** (como na referência): reduz código, mas `@Data` em entidade JPA é problemático e `record` cobre a maior parte dos casos.

## Consequências

- Duplicação domínio × JPA: cerca de 4–5 classes por agregado (domínio, entidade, mapeamento, repositório Spring Data, adaptador). É o custo aceito da exigência de domínio sem JPA.
- **Pendente (PT-02):** como o caso de uso do turno (`conversation`) interage com os módulos `tutor` e `servicerequest` dentro de uma mesma transação. A proposta registra duas opções. Precisa ser decidido antes da Etapa 4, quando essa interação começa (ADR-0017).
- Um futuro adaptador WhatsApp entra como novo pacote de infraestrutura, sem alterar `conversation/domain`.
