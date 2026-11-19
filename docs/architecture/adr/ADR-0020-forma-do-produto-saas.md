# ADR-0020 — Forma do produto SaaS: setores como configuração e mapa de módulos

- **Status:** Aceito (opção A, 2026-11-18). A ferramenta de verificação está pendente (PT-16)
- **Decisão:** DA-42
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
5. Um módulo usa outro só pela API pública. Um teste de arquitetura verifica isso, com a ferramenta definida na PT-16.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Setores como configuração, filas em abas, sem Unidade e sem Tarefa | Menos código e menos telas; uma clínica pequena desliga o que não usa | Uma regra realmente exclusiva de um setor exige revisitar esta decisão |
| (B) Módulo por setor, menu por setor, Unidade e Tarefa desde já | Espaço pronto para regras exclusivas | Código e telas triplicados; Unidade e Tarefa sem uso no piloto complicam toda consulta |

## Consequências

- Uma regra que só um setor tenha vira, primeiro, uma configuração. Só vira módulo quando houver um domínio próprio (ex.: agenda de banho e tosa, versão futura C).
- O escopo de permissão por setor (DA-49) é o que separa o que cada setor vê.
