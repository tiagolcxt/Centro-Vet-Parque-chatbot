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
