# ADR-0019 — Lista de sinais de alerta aprovada como verificação determinística

- **Status:** Aceito (opção A, 2026-11-11), com pendência de conteúdo (P-20)
- **Decisão:** DA-27 (resolve PT-13)
- **Data:** 2026-11-11

## Contexto

Pelo ADR-0018, um sinal de possível emergência hoje depende do modelo (`possibleEmergency`) e dos pisos ligados à intenção. As diretrizes pedem que as regras críticas de segurança não dependam só do modelo. O briefing original proíbe "tabela própria de diagnóstico ou classificação médica de gravidade". Há, portanto, tensão entre:
- **segurança:** detectar "não está respirando" mesmo que o modelo erre;
- **a proibição do briefing.**

## Decisão

1. A equipe veterinária aprova uma lista de **expressões de alerta** (ex.: "não respira", "convulsionando", "comeu veneno"), associada a P1 ou P2. A lista fica no YAML institucional (`triage.alert-expressions`).
2. Um detector de domínio compara o texto normalizado com a lista, da mesma forma que o detector de pedido de humano (ADR-0007). Uma correspondência impõe o **piso** P1 ou P2. Ele **só eleva** a prioridade, nunca rebaixa, e **não diagnostica**.
3. Por que isso não seria "classificação médica própria":
   - a lista não é do sistema, é da equipe veterinária;
   - só ordena e encaminha, nunca descarta;
   - a ausência de correspondência não significa que a situação é leve.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Adotar a lista determinística | Pega erros do modelo nos casos mais graves; testável sem IA | Mantém lista de expressões; falsos positivos (ex.: "meu gato não respira bem quando dorme? é normal?") elevam a prioridade; pode ser lida como contrária ao briefing |
| (B) Só o modelo, com os critérios aprovados como contexto (situação do ADR-0018) | Sem lista de expressões; respeita literalmente o briefing | Uma possível emergência depende do modelo; mitigado por pisos de intenção e fail-safe |
| (C) Lista usada só na avaliação (testes), não em produção | Mede o modelo contra a lista sem afetar o fluxo | Não protege em tempo real |

**Escolhida: (A)**, pelo responsável pelo projeto. Falsos positivos só elevam a prioridade e têm custo baixo, enquanto um falso negativo em P1 tem custo alto. O conteúdo da lista é da equipe veterinária (P-20). Até a aprovação, os testes usam expressões fictícias derivadas da lista ilustrativa das diretrizes, e o ambiente real recusa iniciar sem a lista aprovada.

## Consequências

- Entra na Etapa 4, junto com a `PriorityPolicy`, com testes unitários de cada expressão e frases de controle.
- O ambiente real recusa iniciar sem a lista aprovada.

## Implementação (Etapa 4, 2026-11-17)

- A lista fica na base institucional em `triage.alert-expressions-p1` e `triage.alert-expressions-p2` (tipo novo `TEXT_LIST`), em vez de `triage.alert-expressions` único: o nível de cada expressão fica explícito na chave.
- `AlertExpressionDetector`: texto normalizado (minúsculas, sem acentos e pontuação), correspondência por **palavras inteiras**. P1 vence P2. Roda no modo principal e no restrito, e também quando o modelo falha.
- **Sem regra de negação**, de propósito: "não está respirando" é a própria expressão, e um falso positivo só eleva a prioridade.
- A lista vai ao modelo como dado institucional, na camada 2 (RF-D14), e nunca como texto fixo do prompt.
- **Lista atual: ilustrativa**, tirada dos exemplos da hierarquia de prioridades, só na base fictícia (`config/clinic-knowledge.ficticia.yml`). O modelo versionado (`config/clinic-knowledge.example.yml`) tem `null`: sem lista aprovada, o detector nunca dispara. A recusa de iniciar o ambiente real sem a lista aprovada fica para a Etapa 7 (ADR-0014), junto com a noção de ambiente.
- Testes: `AlertAndUnproductiveDetectorsTest` (P1, P2, frases de controle, lista ausente), CT-01, CT-07, CT-16.
