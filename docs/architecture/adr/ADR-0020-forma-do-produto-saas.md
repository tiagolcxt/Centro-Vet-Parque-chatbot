# ADR-0020 — Forma do produto SaaS: setores como configuração e mapa de módulos

- **Status:** Aceito (opção A, 2026-11-18). Ferramenta de verificação: Spring Modulith (DA-52)
- **Decisão:** DA-42, DA-52
- **Data:** 2026-11-18

## Contexto

O briefing do SaaS lista setores (Recepção, Clínica, Pet shop, Banho e tosa) e entidades (Unit, Task/Assignment). Ele pede para avaliar o que precisa ser módulo e o que pode ser configuração (seções 4, 10.6 e 12.3), e se as filas devem ficar dentro de Solicitações (seção 11).

As três filas pedidas têm o mesmo comportamento: ver, filtrar, atribuir, transferir, anotar, mudar estado e ver histórico. O que muda entre elas é quem vê e o que vê.

## Decisão

1. **Setores são configuração** (`department`), não módulos. Pet shop e Banho e tosa são setores ou tipos de solicitação configurados pela clínica.
2. **Filas são abas da tela de Solicitações**, uma por setor ativo. Não há menu por setor.
3. **Sem Unidade e sem Tarefa no MVP.**
   - O responsável é campo da solicitação, e o histórico fica em `service_request_event`.
   - A unidade entra depois, por migração (`unit_id`).
4. **Mapa de módulos:** `platform`, `clinicknowledge` (configuração), `team`, `tutor` (customer), `conversation`, `ai` (assistant), `servicerequest` e `channel`. As dependências permitidas estão em [saas-modulos-e-arquitetura.md](../saas-modulos-e-arquitetura.md). Os pacotes atuais não são renomeados.
5. Um módulo usa outro só pela API pública. Um teste de arquitetura verifica isso, com o Spring Modulith 2.0.x em escopo de teste (DA-52).

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Setores como configuração, filas em abas, sem Unidade e sem Tarefa | Menos código e menos telas; uma clínica pequena desliga o que não usa | Uma regra realmente exclusiva de um setor exige revisitar esta decisão |
| (B) Módulo por setor, menu por setor, Unidade e Tarefa desde já | Espaço pronto para regras exclusivas | Código e telas triplicados; Unidade e Tarefa sem uso no piloto complicam toda consulta |

## Consequências

- Uma regra que só um setor tenha vira, primeiro, uma configuração. Só vira módulo quando houver um domínio próprio (ex.: agenda de banho e tosa, versão futura C).
- O escopo de permissão por setor (DA-49) é o que separa o que cada setor vê.

## Implementação (Etapa S1, 2026-11-18)

**Verificação.** A verificação das fronteiras usa o Spring Modulith 2.0.9 em escopo de teste: `ModularityTest` chama `ApplicationModules.verify()`. As anotações (`spring-modulith-api`) só entram na compilação.

**Ciclos desfeitos.** `tutor` e `servicerequest` usavam o `ConversationId` do `conversation`, que por sua vez os usa. O `ConversationId` foi para `shared.domain`.

**API pública de cada módulo:**

| Módulo | O que é público |
|---|---|
| `shared` | É módulo aberto: tudo é público |
| `platform` | O pacote-base: `CurrentClinic`, `ClinicId`, `StaffUser`, `Accounts`, `Roles`, `AuditLog`, `Permission`, `Clinics` |
| `clinicknowledge`, `tutor`, `servicerequest`, `conversation` | O pacote `domain`, declarado com `@NamedInterface` |
| `conversation` | O pacote `application`, chamado `assistant-port`: o módulo `ai` implementa o `AssistantModel` |

**Dívida registrada.** O `conversation` ainda usa os repositórios de domínio do `tutor` e do `servicerequest`, como o ADR-0017 decidiu. As APIs por caso de uso entram quando cada módulo for mexido: `servicerequest` na S2.

**Módulos novos:**

- `platform`: clínica, contas, perfis, auditoria e segurança. O antigo `shared.security` mudou para cá.
- `team`: funcionários e padrões da clínica.

**Setores.** Ficam no `clinicknowledge`: `Departments`, `department`.
