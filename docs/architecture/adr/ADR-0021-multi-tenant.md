# ADR-0021 — Multi-tenant: banco e schema únicos com `clinic_id`

- **Status:** Aceito (opção A, 2026-11-18)
- **Decisão:** DA-43
- **Data:** 2026-11-18

## Contexto

O produto vai atender várias clínicas, mesmo começando com uma (briefing 12.2). É preciso definir como identificar a clínica, isolar os dados, impedir acesso cruzado e testar o isolamento, sem infraestrutura desproporcional.

## Decisão

1. **Um banco e um schema.** Toda tabela de dados da clínica tem `clinic_id NOT NULL` com FK para `clinic`.
2. **Identificação da clínica:**
   - web: a clínica do usuário logado;
   - webhook: o `phone_number_id` de destino (`channel_account`);
   - tarefas agendadas: passada explicitamente.
   - Nunca por parâmetro ou cabeçalho controlado pelo cliente.
3. **Filtro.** O Hibernate `@TenantId` filtra as consultas JPA. As consultas nativas recebem `clinic_id` explicitamente, e um teste falha se alguma não o usar.
4. **Chaves.** As chaves únicas de negócio incluem `clinic_id`. As únicas exceções globais são `user_account.email` e `channel_account.phone_number_id`.
5. **Acesso cruzado** devolve 404.
6. **Testes.**
   - Todo teste de API usa duas clínicas fictícias.
   - Teste de arquitetura: toda entidade de dados da clínica tem `@TenantId`.
7. **Migração V5.** Cria `clinic`, insere a clínica fictícia e preenche `clinic_id` nas tabelas atuais.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Schema único com `clinic_id` | Uma migração para todos; operação simples; suficiente para clínicas pequenas | Um erro de filtro expõe dados; mitigado por `@TenantId`, teste de consultas nativas e testes com duas clínicas |
| (B) Um schema por clínica | Isolamento físico | Migrações multiplicadas, pool de conexões por clínica, operação mais cara, sem ganho para o público-alvo |

## Consequências

- O `TenantContext` passa a ser obrigatório em todo caso de uso. Faltar a clínica é erro de programação, não um valor padrão.
- **Confirmar no início da Etapa S1** se o `@TenantId` do Hibernate da versão usada pelo Boot 4.0.5 cobre as consultas derivadas e as `@Query` JPQL do projeto. Se não cobrir, usar um filtro do Hibernate (`@Filter`) ativado por requisição.

## Implementação (Etapa S1, 2026-11-18)

**Prova de conceito no Hibernate 7.2.7** (a versão do Boot 4.0.5). Um registro gravado pela clínica A e lido como clínica B:

| Forma de consulta | B viu o registro? |
|---|---|
| `findAll`, `findById`, `existsById`, `em.find`, JPQL, consulta derivada | Não |
| `deleteById` feito por B | Não apagou |
| Consulta nativa | **Sim** |

O `@Filter` não foi necessário.

**V5:**

- cria `clinic` e a clínica fictícia de id fixo `00000000-0000-4000-8000-000000000001`;
- acrescenta `clinic_id` (NOT NULL + FK) às sete tabelas existentes;
- refaz as chaves únicas de contato e os índices de listagem começando por `clinic_id`.

**Clínica da execução** (`platform.CurrentClinic`):

- é guardada por thread;
- o Hibernate a lê por um `CurrentTenantIdentifierResolver<UUID>`;
- sem clínica definida, o Hibernate recebe um valor que não existe (`00000000-0000-0000-0000-000000000000`): consultas não acham nada e gravações falham pela FK. Nada vaza, e o erro aparece.

**Quando a clínica é fixada:** o Hibernate fixa a clínica **quando a sessão abre**. Por isso, ela tem de ser definida antes de abrir a transação. O login e o aceite de convite leem a conta global primeiro e só então abrem a transação na clínica da conta.

**Quem define a clínica:**

- **Chave de serviço** (`/api/**`): cada chave pertence a uma clínica (`assistant.security.api-key-clinic`; nos testes, `additional-api-keys`). A requisição roda com essa clínica.
- **Sessão de funcionário** (`/app/api/**`): a clínica do usuário.

**Entidades globais, sem `@TenantId`:**

- `clinic`;
- `user_account`: o login acha a conta pelo e-mail antes de existir clínica. Toda outra consulta filtra `clinic_id` explicitamente.

**Testes:**

- `TenancyRulesTest`: toda entidade, exceto as globais, tem `@TenantId`, e toda consulta nativa tem `clinic_id = :clinic`.
- `ClinicIsolationIT`: 404 entre clínicas; filas e solicitações separadas; o mesmo contato em duas clínicas; consulta nativa filtrada.
- `StaffAccessIT`: funcionários e setores de outra clínica.
- Todo teste roda na clínica fictícia (`FictitiousClinicExtension`).
