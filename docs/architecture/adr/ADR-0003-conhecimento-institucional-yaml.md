# ADR-0003 — Conhecimento institucional em YAML tipado e validado

- **Status:** Aceito, com pendências (P-11: conteúdo real). Implementado na Etapa 3, com o desvio descrito no fim
- **Data:** 2026-11-11
- **Decisão:** DA-03

## Contexto

O assistente precisa de informações confiáveis da clínica (identificação, funcionamento, serviços, informações comerciais, dúvidas frequentes, políticas). Ainda não há nenhum dado real (P-11). O briefing proíbe inventar informações e desaconselha RAG sem necessidade comprovada. Não há interface administrativa no MVP. A validação de respostas (ADR-0008) precisa de fontes estruturadas e endereçáveis.

## Decisão

1. As informações ficam em um arquivo **YAML externo ao artefato** (`spring.config.import`), mapeado para **estruturas tipadas** (`record`s com `@ConfigurationProperties`), por categoria.
2. **Cada item tem uma chave estável** (ex.: `hours.saturday`, `services.vaccination.price`), usada como fonte nas respostas (ADR-0008).
3. **Desconhecido é explícito.** Todo item esperado deve estar presente no arquivo. O valor `null` significa **DESCONHECIDO** e é renderizado no prompt como `DESCONHECIDO — confirmar com a equipe`. Item ausente ou chave não reconhecida é **erro de validação**, não "desconhecido".
4. **Validação na inicialização (fail-fast):** sintaxe, chaves obrigatórias presentes, chaves desconhecidas rejeitadas, tipos corretos (preço numérico em centavos, horários em formato definido, dias da semana válidos). Se o arquivo for inválido, a aplicação não sobe, com mensagem indicando a chave e o problema.
5. **Formato dos valores sensíveis à validação:** preço como valor numérico (centavos) + `note` textual opcional ("a partir de", "varia conforme porte"); horários como intervalos `HH:mm–HH:mm` por dia. O conteúdo de cada campo depende da clínica (P-11). O formato é técnico e faz parte desta decisão.
6. **Também ficam neste arquivo** os textos institucionais aprovados pela clínica que o backend insere nas respostas: política de emergência (P-01), contato direto para quando não houver atendimento no prazo (P-02). Enquanto não aprovados, ficam `null`, e o comportamento correspondente está descrito em ADR-0007 e ADR-0014.
7. **Sem RAG ou banco vetorial.** A base inteira entra no contexto (camada 2).
8. O arquivo de exemplo versionado contém **apenas `null`** (nenhum dado inventado). Os testes usam um *fixture* com dados **fictícios** claramente identificados.

## Alternativas consideradas

- **Tabelas no MySQL:** preparam o painel administrativo futuro, mas sem tela a edição seria por SQL.
- **Markdown/texto livre no prompt:** flexível, mas impede validar fontes e valores.
- **RAG:** desproporcional ao volume previsto; vetado pelo briefing sem necessidade comprovada.

## Consequências

- Alterar informações exige editar o YAML e reiniciar a aplicação. A edição é feita por quem mantém o sistema, não pela clínica.
- Critério para reavaliar (não é decisão agora): base institucional acima de ~3–4 mil tokens, ou necessidade de edição pela própria clínica.
- O arquivo com dados reais da clínica **não é versionado** (ADR-0013).

## Implementação (Etapa 3, 2026-11-16)

- **Desvio do item 1:** o arquivo não é lido por `@ConfigurationProperties`. O binding do Spring não distingue chave ausente de chave `null`, e o item 3 exige tratar as duas de forma diferente. O arquivo é lido com SnakeYAML (`SafeConstructor`, sem chaves duplicadas) e conferido contra um esquema próprio (`KnowledgeSchema`), que gera os itens tipados. O resultado pedido pelo ADR se mantém: estrutura tipada, chaves estáveis e fail-fast. A importação via `spring.config.import` também foi trocada pela propriedade `assistant.knowledge.location` (`ASSISTANT_KNOWLEDGE_FILE`).
- **Arquivos:** `config/clinic-knowledge.example.yml` (versionado, tudo `null`, padrão), `config/clinic-knowledge.ficticia.yml` (versionado, clínica inventada para testes) e `config/clinic-knowledge.yml` (dados reais, fora do Git).
- **Esquema:** seções fixas `identification`, `hours`, `payment`, `policies`, `approved-texts` e coleções `services.<id>` e `faq.<id>`, com ids escolhidos pela clínica. Chaves e tipos em [context-management.md](../context-management.md#camada-2--informações-institucionais).
- **Formatos (item 5):** preço inteiro em centavos (`price-cents`) com observação separada (`price-note`); horários como lista de intervalos `HH:mm-HH:mm`, `[]` = fechado; telefone, e-mail, site, `@perfil` e CEP validados.
- **Preços divulgáveis (P-11):** `payment.prices-disclosed`. Se não for `true`, os preços são tratados como DESCONHECIDO e nenhum valor em dinheiro é autorizado (DA-29).
- **Coleção inteira desconhecida:** `services: null` ou `faq: null` é aceito e aparece no prompt como uma linha DESCONHECIDO.
- **Erros cobertos por teste** (`KnowledgeFileLoaderTest`): YAML malformado, chave duplicada, chave ausente (item e seção), chave desconhecida, preço não numérico ou decimal, horário fora do formato ou invertido, telefone, CEP, perfil e booleano inválidos, id de entrada inválido, nome de serviço `null`. Todos os problemas são listados de uma vez.
