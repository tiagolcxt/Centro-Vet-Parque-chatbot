# ADR-0012 — Autenticação: API Key em testes controlados, autenticação individual para dados reais

- **Status:** Aceito, com pendências (P-17, PT-07)
- **Data:** 2026-11-11
- **Decisão:** DA-06

## Contexto

A API expõe conversas e dados pessoais de tutores. No canal API, o chamador informa o identificador do contato: quem tem acesso à API pode se passar por qualquer contato. A revisão técnica (9.1-bis) listou as limitações de uma chave estática.

## Decisão

### Fase de desenvolvimento e testes controlados (Etapas 2 a 6)
- **Uma API Key** no cabeçalho `X-Api-Key`, lida de variável de ambiente, exigida em **todas** as rotas `/api/**` desde a **Etapa 2** (primeira etapa com endpoints).
- Comparação em tempo constante; a chave nunca é logada.
- Fora de `localhost`, somente sobre HTTPS.
- **Permitida exclusivamente com dados fictícios** (ADR-0014).

### Requisito de entrada em produção / uso com dados reais (Etapa 7, obrigatório)
- **Autenticação individual** de cada funcionário: nenhuma credencial compartilhada.
- **Autorização** por perfil, separando no mínimo as operações de canal (enviar mensagens) das operações da equipe (listar e atualizar solicitações e conversas). Os perfis são **pendentes (P-17)**.
- **Auditoria** de acesso a dados pessoais: quem acessou qual conversa ou tutor, quando e qual operação. O registro de auditoria não contém o conteúdo das mensagens.
- O mecanismo concreto (ex.: Spring Security com usuários locais × provedor OIDC) é escolhido na Etapa 7, com os perfis de P-17.

## Limitações da API Key (aceitas só na fase de testes)

- Sem identidade individual e sem auditoria por pessoa.
- Sem separação entre rotas de canal e de equipe.
- Permite personificação de qualquer contato no canal API.
- Vazamento dá acesso total; rotação manual com restart.
- Não limita o volume de chamadas (custo da OpenAI): **rate limiting é pendente (PT-07)**.
- Não se aplica ao futuro webhook do WhatsApp, que usa assinatura própria do provedor.

## Alternativas consideradas

- **Sem autenticação, só `localhost`:** suficiente para a Etapa 1, insuficiente a partir de endpoints com dados.
- **Duas chaves (canal × equipe):** separa rotas, mas continua sem identidade individual; não substitui o requisito de produção.
- **Autenticação individual desde a Etapa 2:** mais segura, mas antecipa cadastro de usuários e perfis ainda indefinidos (P-17).

## Consequências

- Nenhuma demonstração ou piloto com dados reais é possível antes da Etapa 7, por construção.
- A Etapa 7 não é opcional: é condição de entrada em produção (ADR-0014).
