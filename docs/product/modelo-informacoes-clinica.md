# Modelo de informações da clínica (clínica veterinária com petshop)

> **Aprovado e implementado em 2026-11-17 (DA-39, schema v2 da base).** Formulário-base para organizar o que o assistente pode dizer, com qual
> segurança e por quanto tempo. Serve para qualquer clínica com petshop. Os dados reais são preenchidos pela clínica
> e ficam **fora do repositório** (DA-20); este arquivo é só a estrutura.
> Relação com o código: a base é o YAML validado do ADR-0003 (`config/clinic-knowledge.example.yml`, com um
> comentário por campo). A coluna "Hoje" foi escrita antes da implementação: os campos "novo" agora existem, exceto
> os listados em "Não implementado" no fim deste arquivo.

## 1. Princípios

1. **Uma fonte só.** O assistente fala apenas do que está aqui. O que estiver em branco vira "não tenho essa
   informação" e o contato da recepção. Nunca estimativa.
2. **Toda informação tem prazo.** Cada seção tem a data da última revisão e até quando vale. Vencida, ela é tratada
   como em branco: melhor não informar do que informar errado.
3. **Quanto mais a informação muda, menos o assistente afirma.** Ver a classificação abaixo.
4. **Sem integração, nada "ao vivo".** Vagas na agenda, estoque, fila de espera, status de exame ou de internação
   não são informados até existir integração.
5. **Clínico é da equipe veterinária.** Diagnóstico, dose, protocolo, preparo para procedimento e gravidade só
   com texto aprovado por veterinário, e mesmo assim como texto fixo.
6. **Próximo passo sempre claro.** Quando não resolve, o assistente indica ligar para a recepção no horário de
   funcionamento. Nunca promete contato.

## 2. Como cada informação pode ser usada

| Classe | O que é | Exemplos | O assistente… | Revisão sugerida |
|---|---|---|---|---|
| **A — Estável** | Muda raramente | Nome, endereço, telefone da recepção, formas de pagamento | Afirma | A cada 6 meses ou quando mudar |
| **B — Periódica** | Muda de tempos em tempos | Horários, preços, serviços oferecidos, feriados do ano | Afirma **dentro da validade**, com ressalva quando houver ("a partir de", "por dose") | Mensal (preços) / anual (feriados) |
| **C — Volátil** | Muda no dia | Vagas na agenda, estoque de produtos, tempo de espera, promoções | **Não afirma.** Diz que a recepção confirma | — (só com integração futura) |
| **D — Clínica** | Depende de avaliação veterinária | Gravidade, preparo, protocolos, eutanásia, pós-operatório | **Não orienta.** Só texto aprovado pela equipe veterinária (ex.: emergência) e encaminhamento | Revisão pela equipe veterinária |

## 3. Formulário

Legenda: **Obrig.** = necessário para o assistente funcionar bem; **Classe** = tabela acima; **Hoje** = existe no
schema atual (✓), parcial (◐) ou é novo (novo).

### 3.1 Identificação e contato

| Campo | Exemplo (fictício) | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Nome da clínica | Clínica Exemplo | Sim | A | ✓ | |
| Endereço completo | Rua Fictícia, 100, Bairro, Cidade | Sim | A | ✓ | |
| Referência de localização | "Em frente à praça" | Não | A | novo | Ajuda o tutor a chegar |
| CEP | 01000-000 | Não | A | ✓ | |
| **Telefone da recepção** | (11) 3000-1000 | **Sim** | A | ✓ (`phone`) | É o número do próximo passo em todo encaminhamento |
| WhatsApp | (11) 90000-1000 | Não | A | ✓ | |
| E-mail, site, Instagram | | Não | A | ✓ | |
| Estacionamento / acessibilidade | "Estacionamento conveniado" | Não | A | novo | |

### 3.2 Horário de funcionamento

Base para saber se há alguém na recepção (o assistente diz "aberta agora" ou "abre amanhã às 8h").

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Horário da clínica, por dia da semana | Seg–sex 08:00–18:00; sáb 08:00–12:00 | **Sim** | B | ✓ | Faixas por dia; dia sem faixa = fechado |
| Horário da recepção / telefone, se diferente | Seg–sex 08:00–17:30 | Não | B | novo | Se diferente, é este que vale para "ligue agora" |
| **Horário do petshop e do banho e tosa**, se diferente | Seg–sáb 09:00–17:00 | Sim, se houver petshop | B | novo | |
| **Feriados do ano** (datas) e horário neles | 25/12 fechado; 20/11 08:00–12:00 | **Sim** | B | ◐ (texto livre) | Precisa ser lista de datas para o cálculo de "aberta agora" |
| Atendimento 24 horas? | Não | Sim | B | ✓ | |
| Fechamentos temporários | "Fechado 02/01 para manutenção" | Não | B | novo | Com data de início e fim |

### 3.3 Emergência

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| A clínica atende emergência? Em que horário? | Sim, no horário de funcionamento | **Sim** | D | ◐ (`after-hours`) | P-01 |
| **Orientação de emergência aprovada** | Texto da equipe veterinária | **Sim** | D | ✓ (`approved-texts.emergency-policy`) | Hoje aparece um texto provisório |
| **Fora do horário: onde procurar** | Hospital parceiro, endereço e telefone | **Sim** | D | novo | P-01; sem isso, orientação genérica |
| Sinais de alerta (P1/P2) aprovados | "não consegue respirar", "convulsão"… | **Sim** | D | ✓ (`triage`) | P-20; lista atual é ilustrativa |

### 3.4 Serviços veterinários

Um item por serviço.

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Nome | Consulta clínica | Sim | B | ✓ | |
| Oferecido? | Sim | Sim | B | ✓ | Serviço ausente = "não tenho essa informação" |
| Descrição curta aprovada | "Consulta com clínico geral" | Não | B | ✓ | Sem promessa de resultado |
| **Espécies atendidas** | Cães e gatos | Sim | B | novo | Evita marcar exótico onde não atende |
| Precisa agendar? Atende por ordem de chegada? | Agendado | Sim | B | novo | |
| Preço | R$ 150,00 | Não | B | ✓ | Só se "divulgar preços" = sim |
| Tipo de preço | fixo / a partir de / por porte / sob avaliação | Não | B | ◐ (`price-note`) | "Sob avaliação" = recepção informa |
| Preço por porte ou espécie | Pequeno R$ 60; médio R$ 80; grande R$ 100 | Não | B | novo | |
| Duração aproximada | 30 min | Não | B | novo | |
| Preparo do animal | "Jejum de 8 h" | Não | **D** | novo | **Só com texto aprovado por veterinário** |
| Especialidades e profissionais | Dermatologia às terças | Não | B | novo | Sem nomes de pessoas sem autorização |

**Exames, cirurgias, internação e eutanásia:** só informação de existência ("a clínica realiza"), com classe **D**
para todo o resto. Pedidos sobre esses assuntos vão para a equipe (assunto sensível, CT-19).

### 3.5 Vacinas

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Vacinas oferecidas, por espécie | V10 (cães), V4 (gatos), antirrábica | Sim | B | ◐ (como serviço) | |
| Preço por dose | R$ 90,00 | Não | B | ✓ | |
| Precisa agendar? | Não, por ordem de chegada | Sim | B | novo | |
| Calendário vacinal, intervalo entre doses, idade mínima | — | — | **D** | — | **Não informar:** depende do veterinário |

### 3.6 Petshop

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Banho e tosa: tipos | Banho, tosa higiênica, tosa completa | Sim | B | ◐ (como serviço) | |
| Banho e tosa: preço por porte e pelagem | Pequeno pelo curto R$ 50… | Não | B | novo | |
| **Exigências para banho e tosa** | Vacinas em dia; não atende animais agressivos sem aviso | **Sim** | B | novo | Evita o tutor ir e voltar |
| Duração e retirada | 2 h; avisamos pelo telefone | Não | B | novo | Sem prometer horário de entrega |
| Táxi pet / leva e traz | Sim, raio de 5 km | Não | B | novo | |
| Hotel / creche | Não oferecemos | Não | B | novo | |
| Produtos (categorias) | Rações, acessórios, antipulgas | Não | B | novo | **Estoque e preço de produto: classe C** |
| Medicamentos à venda | — | — | **D** | — | Nunca indicar medicamento ou dose |
| Entrega / retirada de compras | Retirada na loja | Não | B | novo | |

### 3.7 Pagamento

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Divulgar preços pelo assistente? | Sim / não | **Sim** | A | ✓ | Não = nunca cita valor |
| Formas de pagamento | Pix, débito, crédito, dinheiro | Sim | A | ✓ | |
| Parcelamento | Até 3x sem juros acima de R$ 300 | Não | B | ✓ | |
| Planos de saúde pet / convênios aceitos | Nenhum | Não | B | novo | |
| Orçamento antes do procedimento | Sim, sempre | Não | B | novo | |

### 3.8 Políticas

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Como funciona o agendamento | Pedido pelo assistente; a recepção confirma | **Sim** | A | ✓ | Base do "pedido ≠ agendamento" (DA-07) |
| Cancelamento e atraso | Aviso com 24 h | Não | B | ✓ | |
| Falta (no-show) | | Não | B | ✓ | |
| Documentos para a consulta | Carteira de vacinação | Não | B | ✓ (FAQ) | |
| Acompanhante, animais agressivos, transporte (caixa, guia) | Gatos em caixa de transporte | Não | B | novo | |

### 3.9 Encaminhamentos (o que é sempre da equipe)

| Campo | Exemplo | Obrig. | Classe | Hoje | Observação |
|---|---|---|---|---|---|
| Situações exclusivas de atendimento humano | Eutanásia, óbito, reclamação, orçamento de cirurgia | **Sim** | D | ◐ (regras no código) | P-12 |
| Encaminhar direto × só oferecer | Sintoma: direto; luto: oferecer | **Sim** | D | ◐ | P-12 |
| Texto de contato direto aprovado | "Ligue para a recepção: …" | Sim | A | ✓ (`approved-texts.direct-contact`) | Sem promessa de retorno |
| Prazo de atendimento por prioridade | P1 imediato; P4 até o fim do dia útil | Sim | B | ✓ (provisório) | P-02; propõe-se contar só no horário aberto |

### 3.10 Perguntas frequentes

Pergunta e resposta **aprovadas**, uma por item (✓). Respostas clínicas (classe D) precisam de aprovação
veterinária.

### 3.11 Controle da informação (novo, em todas as seções)

| Campo | Exemplo | Uso |
|---|---|---|
| Revisado em | 2026-11-17 | Rastreabilidade |
| Revisado por (função) | Gerência | Quem responde pela informação |
| Válido até | 2026-12-31 | Vencido = tratado como em branco |

## 4. O que o assistente nunca informa (sem integração)

- Horário livre na agenda, confirmação de agendamento ou de horário (DA-07).
- Estoque, disponibilidade de produto, promoções do dia.
- Status de exame, internação, cirurgia ou retirada do animal.
- Diagnóstico, gravidade, dose, medicamento, preparo ou protocolo não aprovado.
- Preço não cadastrado, preço vencido ou estimativa.
- Nomes ou dados de outros clientes (teste de privacidade, `PrivacyAiIT`).

## 5. Integração futura

| Hoje (manual) | Com integração |
|---|---|
| Pedido de agendamento + preferência; a recepção confirma | Horários livres reais e reserva com confirmação (revisão da DA-07) |
| Estoque: não informa | Consulta de disponibilidade de produto |
| Cadastro do pet vindo da conversa | Cadastro sincronizado com o sistema da clínica |
| Preços e horários neste formulário | O mesmo formulário passa a ser alimentado pelo sistema, com a mesma validade |

## 6. Como isso entra no sistema

1. A clínica preenche o formulário (seções 3.1 a 3.10) e marca a revisão (3.11).
2. Os campos que já existem (✓) vão para o YAML da base; o restante depende da aprovação deste modelo e de uma
   mudança no schema (`KnowledgeSchema`), com testes de validação.
3. A aplicação valida tudo ao subir: campo faltando, formato errado ou validade vencida aparecem antes de chegar ao
   tutor.

## 7. Situação da implementação (2026-11-17)

Implementado no schema v2: validade por seção (`review.valid-until`), referência de localização e acesso, feriados com data (`hours.holidays`, também para fechamentos temporários), seção de emergência, espécies atendidas, forma de agendamento, duração e preço por porte em cada serviço, horário e informações do petshop, planos de saúde e orçamento, transporte do animal, e o cálculo de "aberta agora".

Não implementado, de propósito:
- **Preparo para procedimento e calendário vacinal** (classe D): só como pergunta frequente com resposta aprovada por veterinário.
- **Estoque, vagas e promoções** (classe C): dependem de integração.
- **Horário da recepção separado e especialidades/profissionais:** acrescentar quando a clínica pedir.
- **Vacinas em seção própria:** cadastradas como serviços, com `species` e `price-note: "por dose"`.
