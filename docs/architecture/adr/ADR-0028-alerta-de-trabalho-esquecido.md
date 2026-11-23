# ADR-0028 — Alerta de trabalho esquecido

- **Status:** Aceito, provisório até D4 (2026-11-23)
- **Origem:** achado A10 da [simulação de 1 mês](../../plans/avaliacao-simulacao-1-mes.md) e E3 da [revisão pré-piloto](../../plans/revisao-pre-piloto.md) (seção 3.1)
- **Relacionados:** ADR-0007 (encaminhamento), ADR-0011 (solicitação separada da conversa), ADR-0027 (prioridade da fila)

## Contexto

Na simulação, um tutor pediu atendimento e ficou 16 dias sem responsável, e conversas ficaram "aguardando
atendimento" por semanas. A fila só pintava "passou do prazo" de vermelho: ninguém era avisado.

A revisão pré-piloto prevê o fluxo completo de casos críticos: escala de plantão, alerta externo por WhatsApp e
e-mail, "sem cobertura" com texto ao tutor. Esse fluxo depende de decisões da clínica (D3 e D4) e do WhatsApp real.

## Decisão

1. **Módulo `alert`**, que não conhece conversa nem solicitação. Cada módulo dono do trabalho implementa a porta
   `ForgottenWork` e diz o que está pendente, desde quando, com que prazo e de que setor:
   - **atendimento:** conversa aguardando a equipe. Prazo = prazo do nível (`assistant.handoff.deadlines`, P-02);
   - **solicitações:** solicitação pendente sem responsável. Prazo = `assistant.alerts.request-unassigned-after`
     (24 h). "Atendimento humano" de conversa que está aguardando fica de fora, porque é o mesmo trabalho e a
     conversa já alerta. Se a conversa voltou ao assistente ou foi encerrada e ninguém pegou a solicitação, ela alerta.
2. **Varredura a cada minuto**, por clínica (`AlertJobs`):
   - pendência além do prazo ganha **um** alerta por episódio (`pending_since`);
   - sem "Recebi" por mais um prazo (mínimo 15 min), o alerta **escala**;
   - um "Recebi" vale por um prazo; vencido, o alerta volta sem ele;
   - pendência que acabou fecha o alerta. Na tela, ele some na hora, porque a lista cruza com a pendência atual.
3. **Quem vê:** quem pode abrir o que o alerta aponta (`conversation:view` ou `request:view`).
   - Antes de escalar: o setor da pendência.
   - Escalado: toda a equipe. Esse é o substituto provisório da escala de plantão (D4).
4. **Na tela:** faixa em qualquer página, com bipe para alerta novo ou escalado. O som pode ser silenciado no
   navegador. Para cada alerta, "Recebi" e "Abrir".
5. **"Recebi" não resolve.** Quem resolve é assumir a conversa ou pegar a solicitação.
6. **Ao tutor, nada muda.** O alerta é interno, e o tutor nunca ouve "avisamos a equipe" por causa dele.
7. **Tudo no registro de atividades:** `alert.raised`, `alert.escalated`, `alert.acknowledged` e `alert.closed`,
   com a conversa ou a solicitação como alvo.

## Consequências

- O esquecimento passa a ser visível em qualquer tela, sem depender de terceiros.
- Fora do app não há aviso. Se ninguém está com o sistema aberto, ninguém é alertado. Isso continua limitando o
  piloto ao horário com a recepção presente (revisão pré-piloto, seção 3.1).
- Prazos e escalonamento são provisórios. Antes de dados reais, a clínica precisa decidir em D4 os prazos por nível,
  a escala de plantão e a coordenação.

## Fica para depois (E3)

- Escala de plantão e coordenação (E3.3).
- Alerta por e-mail e por WhatsApp (E3.6, E3.7), que dependem de D3.
- "Sem cobertura" com texto aprovado ao tutor (E3.5, P-01).
- Indicador "alertas sem Recebi" e tempo até o Recebi (E3.8).
