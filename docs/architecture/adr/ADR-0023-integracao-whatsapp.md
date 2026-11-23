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
- Áudio e transcrição continuam fora (DM-03). Mídia recebe uma resposta aprovada pedindo texto. *(Mudou na implementação; ver abaixo.)*

## Implementação (Etapa S4, 2026-11-22)

Módulo `channel`. A conversa não depende dele: publica `OutgoingMessageRecorded` ao gravar resposta do assistente ou da equipe e lê a situação da entrega pela porta `MessageChannelInfo`.

- **Webhook** em `/webhooks/whatsapp` (público). O GET devolve o `hub.challenge` só com o token de verificação certo. O POST confere `X-Hub-Signature-256` sobre o corpo bruto com o App Secret, em tempo constante; sem assinatura válida, 401.
- **Clínica pelo número.** O `phone_number_id` do corpo leva à clínica por uma consulta global em `channel_account` (único na plataforma). Número sem clínica ativa é ignorado com 200.
- **Entrada.** `INSERT IGNORE` em `inbound_message` por `(clinic_id, wamid)` descarta o reenvio da Meta. O processamento roda fora da requisição, em série por contato (trava por contato e fila na ordem de envio do tutor). O texto sai de `inbound_message` assim que entra na conversa. Uma varredura retoma o que ficou parado há mais de 1 minuto, até 5 tentativas.
- **Contato.** `wa_id` (número); sem ele, o identificador de usuário da Meta (BSUID).
- **Mídia.** Muda o item das Consequências: em vez de uma resposta pedindo texto, o assistente recebe uma descrição curta ("[Imagem enviada sem texto]", ou "[Imagem anexada]" mais a legenda) e responde normalmente. A equipe abre o arquivo pela aplicação, que o busca na Meta na hora; nada é guardado no servidor. O áudio aparece como anexo; a transcrição continua na S5.
- **Saída.** A linha da *outbox* entra na transação da resposta, então o simulador (transação desfeita) não envia nada. O despachante roda a cada 2 segundos.
  - Falha que passa com o tempo (rede, 429, 5xx, limites da Meta) espera 10 s, 30 s, 2 min, 10 min e 30 min.
  - Na 5ª falha, ou numa falha que não passa (número inválido, fora da janela), a resposta fica "não enviada" na conversa, com o código do erro e o botão "Tentar enviar de novo".
  - Entrega pelo menos uma vez: se o servidor cair entre enviar e gravar, a mensagem pode sair duas vezes.
- **Situação** (enviada, entregue, lida, falhou) chega pelo webhook e só avança: a Meta pode mandar "lida" antes de "entregue".
- **Janela de 24 horas.** Conta da última mensagem do tutor. A tela trava a caixa de resposta e o servidor recusa com 409 (`StaffConversationService.reply`). A API de testes não tem janela.
- **Coexistência.** `smb_message_echoes` entra como mensagem `BUSINESS_APP` ("Celular da clínica"). Não roda o assistente e não é reenviada. Edição e apagamento pelo celular são ignorados.
- **Credenciais.** O token fica cifrado com AES-256-GCM (IV aleatório) e a chave `WHATSAPP_TOKEN_KEY` fica fora do banco. A tela Configuração > Canal WhatsApp nunca devolve o token. Ligar ou trocar o número exige `config:publish` e fica na auditoria, sem o token.
- **Segredos do servidor:** `WHATSAPP_APP_SECRET`, `WHATSAPP_VERIFY_TOKEN` e `WHATSAPP_TOKEN_KEY`, só no `.env` ou em variável de ambiente. Sem eles, a aplicação sobe com o canal desligado.
- **Testes.** Cloud API falsa (`FakeWhatsAppApi`), corpos no formato da documentação da Meta e só números fictícios (prefixo 55009).

### O que o onboarding real mostrou (2026-11-22)

- **Número de teste da Meta.** O número americano não consegue enviar para celulares do Brasil: erro 130497, conta nova restrita a mensagens entre países. O ensaio precisa de uma conta do WhatsApp Business com número brasileiro.
- **Coexistência** (mesmo número no app do celular e na API) só sai pelo Embedded Signup, que exige ser *Tech Provider* ou parceiro da Meta. Não dá para configurar à mão no painel.
- **Cadastro trabalhoso** para quem não é técnico: dados da empresa com site obrigatório, contas duplicadas criadas por telas que travaram, e erro de "número já em uso" sem dizer onde.

Decisão pendente: antes do piloto, comparar a Cloud API direta (A) com um provedor intermediário (B) quanto a preço, suporte no Brasil e conexão da clínica pelo próprio provedor. A troca afeta só a implementação de `WhatsAppApi` e o formato do webhook.
