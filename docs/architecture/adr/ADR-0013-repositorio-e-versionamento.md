# ADR-0013 — Repositório Git próprio e política de não versionamento

- **Status:** Aceito
- **Data:** 2026-11-11
- **Decisões:** DA-09, DA-20

## Contexto

A pasta do projeto está hoje dentro de um repositório Git cuja raiz é a pasta do usuário do sistema operacional. Esse repositório enxerga `Desktop`, `Documents`, `.ssh` etc. O projeto terá questionários respondidos pela clínica, conteúdo institucional real e credenciais. Nada disso deve ir para o histórico do Git.

## Decisão

1. O projeto tem **repositório Git próprio**, com raiz na subpasta `assistant-backend/` (ver organização abaixo), criado na Etapa 1.
2. **Nunca versionar:**
   - questionários **preenchidos** e qualquer resposta da clínica;
   - o arquivo de conhecimento institucional com dados reais (versiona-se só o exemplo, todo `null`, ADR-0003);
   - credenciais e chaves (`OPENAI_API_KEY`, API Key, senhas do banco), `.env` e equivalentes;
   - dumps de banco, exportações de conversas, logs;
   - qualquer dado pessoal real.
3. O modelo do questionário **em branco** pode ser versionado.
4. O `.gitignore` é criado **antes do primeiro commit**, com essas regras. Arquivos de configuração locais seguem o padrão `*.example` versionado + arquivo real ignorado.
5. Credenciais chegam à aplicação só por variáveis de ambiente.

## Organização de pastas (DA-20, decidida em 2026-11-11)

```
Chatbot Clinica Veterinaria/        ← NÃO versionado (material da clínica, questionários)
├── Questionario_*.docx / .pdf
├── build.js                        ← gerador do questionário, fora do repositório
└── assistant-backend/              ← repositório Git do projeto
    ├── docs/                       ← movido para cá na Etapa 1
    ├── src/
    └── build.gradle …
```

Alternativa descartada: código na raiz da pasta, com `docs/levantamento/` no `.gitignore`. Seria mais simples, mas a proteção dos dados da clínica dependeria só do `.gitignore`.

## Alternativas consideradas

- **Usar o repositório existente da pasta do usuário:** risco de versionar `.ssh` e dados pessoais por acidente; rejeitada.
- **Versionar questionários preenchidos em repositório privado:** ainda expõe dados de terceiros a quem tiver acesso ao repositório e ao histórico; rejeitada.

## Consequências

- O repositório Git na pasta do usuário continua existindo. Tratá-lo está fora do escopo deste projeto, mas é recomendável.
- Revisar o `git status` antes de cada commit continua necessário; o `.gitignore` não cobre arquivos com nomes inesperados.
