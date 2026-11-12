# Hierarquia de Prioridades do Atendimento

> **Versão 1 — 2026-11-11.** Estrutura adotada (DA-23). **Os critérios clínicos de P1 e P2 e os prazos por nível dependem de aprovação da equipe veterinária e da clínica (P-20, P-02).** Os exemplos abaixo são **ilustrativos**, não um protocolo clínico.
> Arquitetura: [ADR-0018](../architecture/adr/ADR-0018-prioridade-clinica.md), [ADR-0019](../architecture/adr/ADR-0019-sinais-de-alerta-aprovados.md).

## 1. Finalidade e limites

A hierarquia **ordena a atenção e decide o encaminhamento**. Ela não avalia o animal, não diagnostica e não classifica gravidade médica. Sua única função é garantir que uma mensagem com possível risco ao animal:
- não fique atrás de mensagens administrativas;
- não seja respondida como se fosse trivial;
- chegue à equipe adequada.

Isso compatibiliza a hierarquia com a regra do briefing de não criar classificação médica própria: **os critérios clínicos não são do sistema, são da equipe veterinária**.

## 2. Níveis

| Nível | Código | Nome | Exemplos ilustrativos (a validar: P-20) | Encaminhamento humano | Comportamento do assistente |
|---|---|---|---|---|---|
| **P1** | `POSSIBLE_EMERGENCY` | Possível emergência veterinária | Dificuldade respiratória; convulsões; perda de consciência ou colapso; hemorragia intensa; suspeita de intoxicação; trauma grave; incapacidade súbita de se movimentar; dor intensa ou sofrimento evidente | **Imediato, obrigatório** | Orientação do protocolo de emergência aprovado (P-01) **primeiro e sozinha**. Sem perguntas administrativas, cadastro ou outros assuntos antes dela. Se a clínica não puder receber, diz isso claramente e segue o encaminhamento definido |
| **P2** | `URGENT` | Urgente ou com possível agravamento | Sintomas persistentes ou em piora; dor ou desconforto relevante; alteração importante de comportamento; recusa persistente de comida ou água; alteração relevante após procedimento; dúvida sobre complicações | **Obrigatório**, dentro do prazo de P2 (P-02) | Acolhe; diz que a situação precisa de avaliação da equipe; não orienta a esperar indefinidamente; inclui orientação curta sobre quando procurar atendimento imediato (texto aprovado, P-01). Se surgirem sinais de P1, eleva |
| **P3** | `CLINICAL_FOLLOW_UP` | Acompanhamento clínico e continuidade | Evolução após consulta ou procedimento; retorno solicitado pela equipe; dúvida sobre orientação já dada por veterinário; efeitos observados num tratamento | **Obrigatório** quando exige julgamento profissional; registro para a equipe nos demais casos | Não modifica nem reinterpreta orientações clínicas; não trata condição nova como mera continuidade; não faz o tutor repetir o que está registrado e válido |
| **P4** | `SERVICE_REQUEST` | Solicitação de atendimento e agendamento | Marcar consulta; retorno; disponibilidade; procedimento; especialidades | Registro para a equipe (`ServiceRequest`) | Coleta o necessário de forma organizada; deixa claro o que está **pendente de confirmação**; nunca afirma agendamento confirmado |
| **P5** | `ADMINISTRATIVE` | Informações administrativas e comerciais | Preços; horários; endereço; formas de pagamento; serviços | Só quando a informação não existe ou exige decisão de funcionário | Responde só com informação verificada e com fonte (ADR-0008); diz que vai confirmar quando não sabe |
| **P6** | `RELATIONSHIP` | Relacionamento e demandas não urgentes | Agradecimentos; elogios; sugestões; dúvidas gerais; reclamações sem componente clínico | Reclamação: obrigatório; feedback relevante: registro para a equipe | Cordial e atento; não transforma a interação em oportunidade comercial; reconhece elogios e críticas |

**P1 é mais urgente que P6.** "Elevar" significa ir para um número menor.

## 3. Regras de classificação

| # | Regra | Implementação |
|---|---|---|
| H1 | **A prioridade não considera dados comerciais** (valor, histórico de compras, tipo de cliente) | Backend: nenhuma dessas informações entra no cálculo nem no contexto de prioridade |
| H2 | **Reavaliação a cada mensagem**, inclusive em conversas `WAITING_HUMAN` | Backend: toda mensagem passa pelo cálculo (modo principal ou restrito) |
| H3 | **Vale o nível mais urgente** entre: sinal do modelo, pisos determinísticos do backend (H5) e prioridade atual da conversa | `PriorityPolicy` (domínio) |
| H4 | **Rebaixamento automático é proibido.** Só um funcionário pode baixar a prioridade de uma conversa, com registro de quem e quando. Conversa nova (ADR-0009) começa em P6 | Domínio + endpoint da equipe |
| H5 | **Pisos determinísticos** (o backend não aceita nível menos urgente que estes): possível emergência → P1; preocupação médica (`MEDICAL_CONCERN`), agravamento, alteração após procedimento ou **incerteza clínica** → no máximo P2; acompanhamento de tratamento → no máximo P3; correspondência na **lista aprovada de expressões de alerta** (DA-27, ADR-0019) → P1 ou P2 | `PriorityPolicy` |
| H6 | **Mensagem mista** (ex.: preço + sintoma): vale o nível mais urgente. O conteúdo administrativo **nunca** atrasa nem substitui a orientação clínica | `ReplyComposer` |
| H7 | **Incerteza não é segurança.** Se não é possível determinar a gravidade, a mensagem não é tratada como leve: piso P2, orientação curta sobre quando procurar atendimento imediato, encaminhamento. No máximo **uma** pergunta objetiva, e só depois da orientação | `PriorityPolicy` + prompt |
| H8 | **Falha da IA não rebaixa.** Em falha, a prioridade se mantém. Se a conversa já está em P1 ou P2, a resposta segura inclui a orientação curta de emergência | `TurnResultClassifier` + `ReplyComposer` |
| H9 | **Prioridade clínica ≠ estado operacional.** `WAITING_HUMAN` diz que a conversa espera a equipe; a prioridade diz quão urgente é. Os dois campos são independentes | Domínio (ADR-0018) |
| H10 | **Fila da equipe ordenada por prioridade** e, dentro do nível, por tempo de espera. Nunca só por ordem de chegada | Listagem `GET /api/conversations` |
| H11 | **Prazo por nível**: cada nível tem um prazo de atendimento humano (P-02). O atraso é calculado por nível | Domínio + listagem |
| H12 | **Critérios clínicos não são definidos pela IA.** Os sinais de P1 e P2 entregues ao modelo vêm de lista aprovada pela equipe veterinária (P-20), mantida no conhecimento institucional. Enquanto não houver aprovação, só a lista ilustrativa desta página é usada, e apenas com dados fictícios | YAML institucional + barreira E7 |

## 4. Sinais que não alteram a prioridade clínica, mas afetam o atendimento

| Sinal | Origem | Efeito |
|---|---|---|
| `EMOTIONAL_DISTRESS` (sofrimento, medo, ansiedade) | Modelo | Tom acolhedor; oferta de atendimento humano (ou encaminhamento direto, P-12); sem conteúdo comercial |
| `DISSATISFACTION` (frustração, perda de confiança) | Modelo | Encaminhamento |
| `CONFUSION` (dificuldade de usar o atendimento) | Modelo | Oferta de atendimento humano |
| `FINANCIAL_VULNERABILITY` | Modelo | Nenhuma pressão comercial; sem julgamento; condições de pagamento só se aprovadas no conhecimento institucional (P-11); oferta de atendimento humano para exceções e negociação |
| `BEREAVEMENT` (perda do animal) | Modelo | Linguagem respeitosa; nenhum conteúdo comercial; oferta de atendimento humano |
| `NEEDS_STAFF_DECISION` (negociação, exceção) | Modelo | Encaminhamento |
| `UNPRODUCTIVE_CONVERSATION` | **Backend** (limiares: PT-14) | Oferta de atendimento humano; automático ao atingir o limite |

Esses sinais ficam registrados no turno e o motivo de encaminhamento é persistido (RF-D05). Eles **não** são usados para nenhuma finalidade comercial.

## 5. O que depende de aprovação antes de produção

- **P-20:** sinais e critérios de P1 e P2, e o que mais a equipe considera emergencial; se P3 exige sempre profissional.
- **P-01:** protocolo de emergência, incluindo o que dizer quando a clínica não pode receber o animal.
- **P-02:** prazo de atendimento humano **por nível** e protocolo de indisponibilidade.
- **P-12:** quando oferecer e quando encaminhar diretamente em sofrimento emocional, sintomas sem sinal de alerta e vulnerabilidade financeira.

Detalhes: [aprovacoes-equipe-veterinaria.md](aprovacoes-equipe-veterinaria.md).
