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

## Implementação (Etapa S1, 2026-11-18)

**Rotas:**

| Prefixo | Quem | Como |
|---|---|---|
| `/api/**` | Canal de teste e integrações | Chave de serviço, sem sessão nem CSRF. As rotas da equipe que ainda estão aqui (fila, encerrar, solicitações) passam para `/app/api` na S2 |
| `/app/api/**` | Funcionários | Sessão por cookie e CSRF no padrão de SPA (cookie `XSRF-TOKEN`, cabeçalho `X-XSRF-TOKEN`). Erros em JSON, nunca redirecionamento |

**Sessão:**

- A sessão guarda só a conta e a clínica.
- A cada requisição, o `StaffSessionFilter` recarrega a conta e o perfil: mudança de permissão vale sem novo login, e conta desativada perde a sessão na requisição seguinte.
- Cada login cria uma sessão nova (proteção contra fixação de sessão).

**Rotas da equipe:**

- **Login e senha:** `auth/login`, `auth/logout`, `auth/session`, `auth/csrf`, `auth/accept-invite` e `auth/change-password`.
- **Perfis:** `roles` e `permissions`, com `team:manage`.
- **Equipe:** `employees` (convidar, alterar, desativar, novo link), com `team:manage`.
- **O próprio funcionário:** `me` e `me/availability`.
- **Setores:** `departments`. Listar exige só login; alterar exige `config:edit`.

**Primeiro administrador:** `POST /api/clinic/first-admin`, com a chave de serviço da clínica. Só funciona enquanto a clínica não tem nenhuma conta. A clínica em si é criada pela equipe da plataforma (RF-S01), por SQL, na S1.

**Convite e redefinição de senha:**

- A resposta ao administrador traz um **token** de uso único; o banco guarda só o SHA-256 dele.
- O administrador entrega o link à pessoa. O **envio por e-mail ficou para depois** (backlog S1-7).
- "Esqueci a senha" é resolvido pelo administrador, que gera um novo link.

**Senha:**

- bcrypt, pelo codificador delegante do Spring Security (o prefixo do algoritmo fica no hash).
- Mensagem de erro única no login: não revela se o e-mail existe nem se a conta está bloqueada.

**Padrões ao iniciar:** cada clínica ativa ganha os setores Recepção (padrão), Clínica e Pet shop, e os seis perfis-modelo (`ClinicDefaults`). Isso é idempotente.

**Auditoria** (`audit_event`): login, login recusado, convite, aceite, troca de senha, mudança e desativação de conta, perfis e setores.

**Valores provisórios** (sem decisão da clínica):

- 5 senhas erradas bloqueiam a conta por 15 minutos;
- o convite vale 72 horas;
- a senha tem de 10 a 72 caracteres.

**Ainda não feito na S1:**

- escopo de setor aplicado a conversas e solicitações (S2, com as filas);
- devolução das atribuições de quem é desativado (RN-S07, S2);
- tela de auditoria (S3);
- rotação do token CSRF a cada login.

**Testes:**

- `StaffAccessIT`: perfis e setores padrão; primeiro administrador; bloqueio; 401 e 403; permissão alterada na hora; desativação; isolamento; convite de uso único; e-mail único.
- `ApiKeyFilterTest`.
