# ADR-0023 — Integração com o WhatsApp pela Cloud API oficial

- **Status:** Aceito (opção A, 2026-11-18)
- **Decisão:** DA-46
- **Data:** 2026-11-18

## Contexto

O WhatsApp é o canal inicial com os tutores (briefing 10.2). A pesquisa de 2026-11-18 (planejamento, seção 3) encontrou quatro pontos que condicionam o desenho:

- **Onboarding.** A Meta oferece o Embedded Signup para provedores de tecnologia (*Tech Provider*). Isso exige verificação do negócio e acesso avançado, que podem levar semanas.
- **Janela de 24 horas.** Fora dela, só se enviam modelos aprovados.
- **Preço.** A cobrança é por mensagem desde julho de 2025, e houve mudanças em outubro de 2026.
- **Política de IA.** Desde janeiro de 2026, a Meta proíbe assistentes de IA de uso geral. Bots com função de negócio definida continuam permitidos.

## Decisão

1. **Só a API oficial (Cloud API).** Nenhuma biblioteca não oficial.
2. **Piloto.** A clínica usa a própria conta do WhatsApp Business, compartilhada com o nosso app, com configuração manual por nós.
3. **Verificação.** O pedido de verificação como *Tech Provider* é feito na **Etapa S1**, por causa do prazo. O Embedded Signup entra antes da segunda clínica.
4. **Entrada.**
   - O webhook verifica a assinatura e grava `inbound_message`, deduplicada pelo `wamid`.
   - O webhook responde 200 e só depois o turno roda.
   - Mensagens do mesmo contato são processadas em série.
5. **Saída.** Por *outbox* gravada na transação do turno, com nova tentativa e espera crescente. O status (enviada, entregue, lida, falhou) é atualizado pelo webhook.
6. **Janela de 24 horas.** Fora dela, a tela bloqueia a resposta livre. Modelos aprovados ficam para a versão futura B.
7. **Escopo do assistente.** Fica restrito aos assuntos da clínica, como já é, e isso é documentado para a revisão da Meta.
8. **Credenciais.** O token de acesso fica cifrado em `channel_account`, com a chave vinda de variável de ambiente.
9. **Preço.** Os valores por mensagem são conferidos na página oficial da Meta antes de definir o preço do SaaS. Nenhum valor é fixado neste ADR.

## Alternativas

| Opção | Vantagem | Desvantagem |
|---|---|---|
| (A) Cloud API direta, nós como *Tech Provider* | Sem intermediário; menor custo por mensagem; controle do fluxo | Verificação pode atrasar; nós respondemos pela conformidade |
| (B) Provedor intermediário (BSP) | Onboarding e verificação mais rápidos | Custo por mensagem ou mensalidade; dependência de terceiro |

## Consequências

- Se a verificação travar a Etapa S4, reavaliar a opção B só para o piloto.
- O teste de contrato do webhook usa cargas de exemplo da documentação da Meta, sem dados reais.
- Áudio e transcrição continuam fora (DM-03). Mídia recebe uma resposta aprovada pedindo texto.
