# ADR-0024 — Identidade, perfis editáveis e permissões

- **Status:** Aceito (opção A, 2026-11-18). Os perfis definitivos da clínica estão pendentes (P-17)
- **Decisão:** DA-49. Também resolve a parte técnica da P-17 e revê o ADR-0012
- **Data:** 2026-11-18

## Contexto

O briefing (seção 7) pede:

- contas individuais;
- os conceitos de identidade, cargo, função, setor, qualificação, permissão e disponibilidade separados;
- permissões por módulo, ação, setor e escopo;
- perfis iniciais editáveis;
- desativação que preserve a autoria;
- auditoria.

O ADR-0012 já exigia autenticação individual antes de dados reais.

## Decisão

1. **Uma conta por pessoa.**
   - `user_account` (identidade) pertence a uma clínica.
   - O e-mail é único na plataforma.
   - Login por e-mail e senha, com sessão Spring Security, cookie seguro, CSRF e bloqueio após tentativas.
2. **`employee`** guarda cargo, registro profissional, setores e disponibilidade.
3. **Permissões.** O catálogo é fixo no código, no formato `modulo:acao`. O **perfil** (`role`) é um conjunto de permissões mais o escopo de setor ("meus setores" ou "todos"). A clínica edita perfis e cria outros.
4. **Perfis iniciais.** Os seis do briefing são criados em cada clínica nova como modelos.
5. **Desativação.** Bloqueia o acesso e derruba as sessões. O histórico continua apontando para a conta, que nunca é apagada.
6. **Chave de API.** A `X-API-Key` atual deixa de dar acesso às rotas da equipe. Fica para testes automatizados e integrações de serviço.
7. **Unidade.** Não há permissão por unidade no MVP (DA-42).

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Usuário de uma clínica, catálogo fixo, perfis editáveis | Simples; atende aos seis perfis | Quem trabalha em duas clínicas precisa de duas contas |
| (B) Pessoa em várias clínicas, permissões por unidade | Pronto para redes | Complexidade sem demanda no piloto |

## Consequências

- O `CurrentUser` traz a clínica, as permissões e os setores. Os casos de uso checam o escopo de setor.
- A autenticação em dois fatores é recomendada logo após o piloto e já fica registrada no backlog.
