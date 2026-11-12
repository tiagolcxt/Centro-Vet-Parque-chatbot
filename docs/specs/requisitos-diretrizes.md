# Requisitos derivados das Diretrizes de Atendimento

> Versão 1 — 2026-11-11. Origem: [Diretrizes](../product/diretrizes-de-atendimento.md) e [Hierarquia de prioridades](../product/hierarquia-de-prioridades.md).
> **RF** = requisito funcional · **RN** = regra de negócio · **CT** = cenário de teste ([cenarios-diretrizes.md](cenarios-diretrizes.md)).
> "Garantido por" indica quem garante o requisito:
> - **backend**: determinístico, testável sem IA;
> - **modelo**: instrução e sinal, verificado por avaliação;
> - **backend + modelo**: o modelo sinaliza e o backend aplica pisos e regras que não dependem do modelo.

## 1. Requisitos funcionais

| ID | Requisito | Diretriz | Garantido por | Etapa | ADR | CT |
|---|---|---|---|---|---|---|
| RF-D01 | Classificar **cada** mensagem em P1–P6, sem usar dados comerciais | Hier. H1, H2 | backend + modelo | E4 | 0018 | CT-01, CT-06 |
| RF-D02 | Reavaliar a prioridade a cada mensagem, **inclusive em `WAITING_HUMAN`** (modo restrito) | Hier. H2; Dir. 6.2 do prompt | backend | E4 | 0007, 0018 | CT-07 |
| RF-D03 | Detectar possível emergência em qualquer estado e responder com o protocolo aprovado **antes de qualquer outro conteúdo** | Dir. 2 (P1) | backend + modelo | E4 | 0007, 0018 | CT-01, CT-07 |
| RF-D04 | Detectar **deterministicamente** pedido explícito de atendimento humano, antes do modelo | Dir. 3.1, 3.3 | backend | E4 | 0007 | CT-02 |
| RF-D05 | Persistir o **motivo do encaminhamento** e os sinais que o motivaram | Prompt 6.5 | backend | E4 | 0007 | todos com encaminhamento |
| RF-D06 | Manter **prioridade clínica** e **estado operacional** como campos independentes | Hier. H9 | backend | E4 | 0018 | CT-07, CT-11 |
| RF-D07 | Ordenar a fila da equipe por prioridade e, dentro do nível, por tempo de espera; indicar atraso **por nível** | Hier. H10, H11 | backend | E4 | 0018 | CT-11 |
| RF-D08 | Permitir que um funcionário **reavalie e rebaixe** a prioridade, registrando quem, quando e o nível anterior | Hier. H4 | backend | E4 (quem: E7) | 0018 | CT-16 |
| RF-D09 | Detectar conversa **improdutiva** (limiares: PT-14) e oferecer ou encaminhar para humano | Dir. 3.1 | backend | E4 | 0007 | CT-14 |
| RF-D10 | Registrar sinais não clínicos (sofrimento, insatisfação, confusão, vulnerabilidade financeira, luto, decisão de funcionário) e aplicar o tratamento da hierarquia (seção 4) | Dir. 3.1, 3.2 | backend + modelo | E4 | 0007 | CT-03, CT-05, CT-10, CT-13 |
| RF-D11 | Compor no backend os textos de encaminhamento, emergência, prazo vencido e indisponibilidade a partir de **textos aprovados** (YAML) | Dir. 1.4; Hier. H8 | backend | E4 | 0003, 0007 | CT-07, CT-11 |
| RF-D12 | Toda resposta indica o **próximo passo** (o que foi registrado e o que acontece a seguir) | Dir. 1.4, 4 | backend (textos compostos) + modelo (demais) | E4, E6 | 0002 | todos |
| RF-D13 | Preservar o histórico relevante para que o tutor não repita informação (camadas 3–5 + proveniência) | Dir. 6.7 do prompt; Hier. P3 | backend | E2, E5 | 0004, 0010 | CT-06, CT-18 |
| RF-D14 | Disponibilizar ao modelo os critérios aprovados de P1/P2 (P-20) como dados institucionais, nunca como texto fixo do prompt | Hier. H12 | backend | E4 | 0003, 0019 | CT-01 |
| RF-D15 | Fail-safe: em falha da IA, manter a prioridade e incluir a orientação curta de emergência quando a conversa está em P1/P2 | Hier. H8 | backend | E4 | 0002, 0018 | CT-15 |
| RF-D16 | Comunicar com transparência o que **foi e o que não foi** feito: registrado para a equipe ≠ resolvido ≠ alguém avisado | Dir. 1.3, 1.4 | backend (validação V6) + modelo | E3, E4 | 0008 | CT-09, CT-11 |

## 2. Regras de negócio

| ID | Regra | Diretriz | Garantido por | Etapa | CT |
|---|---|---|---|---|---|
| RN-D01 | Em **P1**, a resposta é a orientação do protocolo aprovado, sem perguntas administrativas, cadastro, coleta ou outros assuntos; o fluxo em andamento é interrompido (fica `COLLECTING`); a conversa é encaminhada | Hier. P1; Dir. 0 | backend (`ReplyComposer`) | E4 | CT-01, CT-06, CT-07 |
| RN-D02 | Em **P2**, encaminhamento obrigatório dentro do prazo de P2; nunca orientar o tutor a aguardar indefinidamente; incluir quando procurar atendimento imediato (texto aprovado) | Hier. P2 | backend + modelo | E4 | CT-03, CT-04, CT-12 |
| RN-D03 | **Pisos de prioridade** H5 aplicados pelo backend; vale o nível mais urgente (H3) | Hier. H3, H5 | backend | E4 | CT-01, CT-08, CT-12 |
| RN-D04 | **Rebaixamento automático proibido** (H4). Mensagens como "já melhorou, pode esquecer" não baixam a prioridade; só um funcionário | Hier. H4; Dir. 1.1 | backend | E4 | CT-16 |
| RN-D05 | **Incerteza não é segurança** (H7): piso P2, orientação primeiro e no máximo uma pergunta objetiva | Hier. H7; Dir. 1.1 | backend + modelo | E4 | CT-12 |
| RN-D06 | Pedido explícito de humano: encaminhar **sem pedir justificativa** e **sem tentar convencer** o tutor a continuar com a IA; não retomar o fluxo automatizado depois | Dir. 3.3 | backend (detector, estado) + validação de texto | E4 | CT-02 |
| RN-D07 | Reclamação: encaminhar; acolher sem postura defensiva; não prometer compensação, reembolso ou exceção | Dir. 3.1; 1.3 | backend + modelo | E4 | CT-05 |
| RN-D08 | Sofrimento, luto ou vulnerabilidade financeira: **nenhum conteúdo comercial** (oferta de serviço, pacote ou procedimento não solicitado) e oferta clara de atendimento humano | Dir. 3.2 | modelo (instrução) + avaliação | E4, E6 | CT-10, CT-13 |
| RN-D09 | Medicamentos, dosagens, suspensão ou alteração de tratamento, interpretação de exame: não responder o conteúdo clínico; encaminhar ao profissional; prioridade **no mínimo P3**, e P2 se houver sintoma | Dir. 5 | backend (piso) + modelo + avaliação | E4 | CT-08 |
| RN-D10 | Acompanhamento (P3): não alterar nem reinterpretar orientação de profissional; condição nova é avaliada como nova (não como continuidade) e pode elevar a prioridade | Hier. P3 | modelo + piso do backend | E4, E5 | CT-18 |
| RN-D11 | Nenhuma resposta afirma: agendamento confirmado (V5); alguém avisado ou notificado; contato ou retorno de funcionário **sem processo aprovado**; solicitação **resolvida** (V6 ampliada) | Dir. 1.3, 1.4 | backend (validação) | E3 (V5/V6), E4 (textos) | CT-09, CT-11 |
| RN-D12 | Indisponibilidade da equipe e prazo vencido: o texto segue o protocolo aprovado (P-02) e diz **o que o tutor pode fazer agora**; nunca promete retorno sem processo | Dir. 1.4; Hier. H11 | backend (`ReplyComposer`) | E4 | CT-11 |
| RN-D13 | Elogio, agradecimento ou sugestão: cordialidade, sem oferta comercial; feedback relevante registrado para a equipe | Hier. P6 | modelo + backend (registro) | E4 | CT-17 |
| RN-D14 | O assistente não simula sentimentos humanos ("também estou triste") nem afirma ações que o backend não executou | Dir. 3.2 | modelo + avaliação | E6 | CT-03, CT-13 |
| RN-D15 | Mensagem mista: o nível mais urgente define a resposta; a parte administrativa só entra depois da orientação clínica, e em P1 não entra | Hier. H6 | backend (`ReplyComposer`) | E4 | CT-01 |

## 3. Requisitos não funcionais

| ID | Requisito | Etapa |
|---|---|---|
| RNF-D01 | Nenhuma regra crítica (pisos de prioridade, detector, proibição de rebaixamento, composição de textos de emergência e encaminhamento, validação V5/V6) depende só do modelo; todas têm teste **sem IA** | E3, E4 |
| RNF-D02 | Os critérios clínicos, prazos e textos de protocolo ficam em configuração aprovada (YAML), versionada à parte do prompt; o ambiente real recusa iniciar com *placeholder* (ADR-0014) | E4, E7 |
| RNF-D03 | Métricas por turno incluem a prioridade atribuída, a origem (modelo ou piso do backend) e os sinais, **sem conteúdo** das mensagens | E6 |
| RNF-D04 | Os sinais não clínicos (seção 4 da hierarquia) não são usados para finalidade comercial nem expostos fora da fila de atendimento | E4, E7 |
