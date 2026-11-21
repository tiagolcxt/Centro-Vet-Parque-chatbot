# Product Blueprint — plataforma de atendimento para clínicas veterinárias

> Versão 1 — 2026-11-18. Aprovado pelo responsável pelo projeto (decisões DA-42 a DA-51).
> Origem: briefing "SaaS – Atendimento e Operação para Clínicas Veterinárias" e o planejamento aprovado em 2026-11-18.
> Documentos ligados: [PRD](prd.md), [arquitetura](../../architecture/saas-modulos-e-arquitetura.md), [roadmap](../../plans/saas-roadmap.md).

## 1. Visão

Cada contato de um tutor vira uma demanda identificada, com setor, responsável e próximo passo, até ser resolvida. Nada se perde no celular da recepção.

## 2. Posicionamento

A plataforma organiza o atendimento e a coordenação do trabalho em clínicas veterinárias pequenas e médias, com ou sem pet shop e banho e tosa. Ela convive com o sistema de gestão que a clínica já usa.

**O que não é:** ERP, prontuário eletrônico, agenda completa, PDV, e-commerce nem chatbot genérico.

**Princípio de produto:** a IA é uma camada sobre a operação, não o lugar dela. Se a IA falhar, a equipe continua trabalhando pela caixa de entrada e pelas filas.

## 3. Público

### Piloto

Uma clínica, um número de WhatsApp e uma unidade. Os dados são fictícios até a barreira de dados reais ([ADR-0014](../../architecture/adr/ADR-0014-barreira-dados-reais-lgpd.md)).

### Públicos atendidos

Os problemas da segunda coluna são hipóteses, a confirmar na Etapa S0.

| Público | Problema hoje (hipótese) | O que o produto entrega |
|---|---|---|
| Tutor | Espera sem saber se foi lido; repete dados; recebe informação desatualizada; não sabe com quem fala | Resposta imediata com informação validada; dados pedidos uma vez; honestidade sobre o que acontece em seguida; continuidade quando outra pessoa assume |
| Recepção | Um celular para tudo; pedidos sem dono; perguntas repetidas; urgência misturada ao resto | Caixa única; perguntas frequentes respondidas pela IA; pedidos já organizados; urgência no topo |
| Veterinário | Recados soltos, sem histórico | Fila própria só com o que exige julgamento clínico, com o histórico do pet e da conversa |
| Pet shop e tosa | Pedidos de banho anotados em papel ou perdidos no chat | Fila própria com pet, serviço, período e estado |
| Administrador | Mudar um horário ou um preço depende de alguém técnico; não enxerga a operação | Configuração pela web com rascunho, simulador e publicação; visão geral das pendências |

## 4. Proposta de valor

Transformar contatos desorganizados em solicitações identificadas, direcionadas, acompanhadas e resolvidas, sem que a clínica troque o sistema que já usa.

## 5. Diferenciais

São hipóteses, a validar no piloto. A pesquisa de mercado de 2026-11-18 não encontrou nenhum produto que reúna os cinco.

1. **Domínio veterinário:**
   - prioridade clínica separada da operacional;
   - urgência reconhecida mesmo com a conversa em atendimento humano;
   - assuntos sensíveis tratados com cuidado;
   - dados do pet com proveniência.
2. **Só informação validada e dentro do prazo:**
   - cada seção da configuração tem "válido até";
   - o que está vencido é tratado como desconhecido;
   - o validador barra fatos que não estão na base.
3. **Honestidade operacional:**
   - nunca diz que alguém foi avisado sem que tenha sido;
   - diferencia "encaminhado para a recepção" de "assumido por Ana";
   - fora do horário, orienta a ligar para a recepção em vez de prometer contato.
4. **Configuração sem técnica:** formulários em vez de prompts, YAML ou JSON, e simulador antes de publicar.
5. **Simplicidade proporcional:** setores se ativam por configuração. Uma clínica pequena usa só recepção e clínica.

## 6. Problemas resolvidos

As medidas de cada problema estão no [plano de validação](../../plans/saas-plano-de-validacao.md).

| Problema | Como o produto resolve |
|---|---|
| Mensagens sem resposta ou respondidas tarde | Resposta automática dentro do escopo; indicadores de "sem resposta" e "aguardando funcionário" |
| Demanda sem dono | Solicitação com setor e responsável; assumir e transferir registrados |
| Perda de informação na troca de turno ou de pessoa | Conversa, notas internas e histórico no mesmo lugar |
| Urgência perdida no meio do volume | Prioridade clínica reavaliada a cada mensagem, inclusive em atendimento humano |
| Informação institucional errada ou vencida | Configuração versionada com validade por seção |
| Trabalho repetitivo | Perguntas frequentes respondidas e dados coletados pela IA |

## 7. Escopo do MVP

O detalhe está no [PRD](prd.md). Em resumo:

- plataforma multi-clínica com login individual, perfis editáveis e auditoria;
- configuração pela web: dados da clínica, horários, setores, serviços e preços, perguntas frequentes, equipe e regras de encaminhamento, com rascunho, simulador e publicação;
- caixa de entrada: assumir, devolver ao assistente, transferir, responder manualmente e anotar;
- solicitações em abas por setor;
- cadastro de clientes e pets com proveniência;
- WhatsApp oficial (Cloud API), com um número no piloto;
- o assistente atual, que passa a escolher o setor.

**Fora do MVP:**

- tudo o que está na seção 10.7 do briefing, menos áudio e transcrição, que entram na S5 (DA-53);
- unidades;
- tarefas separadas;
- escalas e turnos;
- aviso à equipe fora da aplicação;
- Embedded Signup;
- mensagens ativas por modelo aprovado;
- cobrança do SaaS;
- autenticação em dois fatores, recomendada logo depois do piloto.

## 8. Evolução futura

Documentada, não antecipada.

| Versão | Conteúdo | Observação |
|---|---|---|
| A. Operação integrada | Agenda; sincronização com o sistema de gestão | Começar pelo sistema que a clínica piloto usa |
| B. Relacionamento | Lembretes de vacina e retorno por modelo aprovado; pesquisa de satisfação | Tende a ser a primeira pedida: o mercado brasileiro já oferece |
| C. Pet shop | Catálogo e pedidos | Sem estoque nem PDV próprios enquanto houver um sistema de gestão |
| D. Portal do tutor | Acesso pelo navegador | Só depois que o WhatsApp estiver estável |
| E. Expansão | Várias unidades; relatórios; planos e cobrança do SaaS; Embedded Signup | Pré-requisito para a segunda clínica: Embedded Signup |

## 9. Riscos de produto

Os riscos estão na [matriz](../../plans/matriz-de-riscos.md), de R-30 a R-39. Os principais:

- prazo da verificação na Meta;
- política do WhatsApp contra IA de uso geral;
- preço por mensagem em mudança;
- adoção pela equipe;
- LGPD antes de dados reais.
