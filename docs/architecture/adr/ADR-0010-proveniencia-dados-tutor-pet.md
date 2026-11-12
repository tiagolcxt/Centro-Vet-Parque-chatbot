# ADR-0010 — Proveniência das informações de tutor e pet

- **Status:** Aceito, com pendências (P-08, PT-09, PT-12)
- **Data:** 2026-11-11
- **Decisão:** DA-14

## Contexto

O briefing exige não fazer o tutor repetir informações e, ao mesmo tempo, não tratar informações de outra conversa como fatos confirmados. A proposta original distinguia apenas "confirmado nesta conversa" e "registrado anteriormente", e chamava de "confirmado" um dado que era só extraído pelo modelo (revisão técnica, 9.2 item 6).

## Decisão

1. Toda informação de tutor e pet apresentada ao modelo carrega um **status de proveniência**:

   | Status | Significado | Uso permitido |
   |---|---|---|
   | **MENCIONADA** | Dita pelo tutor **nesta conversa** e extraída pelo modelo, sem confirmação | Pode ser usada na conversa; deve ser tratada como possivelmente imprecisa |
   | **CONFIRMADA** | Confirmada pelo tutor **nesta conversa**, segundo o critério de P-08 | Pode ser usada e registrada em solicitações |
   | **CADASTRADA** | Existe no cadastro, foi confirmada em atendimento anterior e está dentro do prazo de validade (P-08) | Pode ser usada para reconhecer e perguntar ("É sobre a Mel?"); **não** é confirmação atual |
   | **A_RECONFIRMAR** | Existe no cadastro, mas nunca foi confirmada ou está fora do prazo de validade | Só pode ser mencionada como pergunta de reconfirmação |

2. **Informação antiga nunca vale como confirmação atual.** Ao abrir uma conversa nova (ADR-0009), todo o cadastro entra como `CADASTRADA` ou `A_RECONFIRMAR`. Só passa a `CONFIRMADA` com confirmação do tutor na conversa atual.
3. **O cadastro guarda só dados persistentes** do tutor (nome, telefone/contato) e do pet (nome, espécie, raça, idade ou data de nascimento informada). Motivo de consulta, sintomas e preferências de horário **não** entram no cadastro: pertencem à solicitação ou à conversa.
4. Dados extraídos pelo modelo são **normalizados e validados pelo backend** (tamanho, espécie no enum, formatos) antes de gravar; dado inválido é descartado. O modelo nunca grava diretamente (ADR-0002).
5. O contexto de um tutor nunca contém dados de outro tutor: as consultas são sempre filtradas pelo tutor vinculado à conversa. Esse vínculo vem do identificador do canal, nunca de um telefone digitado.
6. As colunas de proveniência entram no schema na **Etapa 5** (ADR-0005).

## Pendências ligadas

- **P-08:** critério para considerar uma informação confirmada (ex.: o assistente repete e o tutor concorda explicitamente?), prazo de validade de `CADASTRADA` e quais status são exigidos para registrar uma solicitação.
- **PT-09:** granularidade da proveniência (por registro × por campo) e regra de correspondência de pets (variações de nome, dois pets com o mesmo nome). Recomendação da proposta: por registro, por simplicidade. Não aprovada.
- **PT-12:** momento de criação do registro do tutor (no primeiro contato × só quando houver dado a guardar). A segunda opção favorece a minimização de dados.

## Alternativas consideradas

- **Dois níveis (confirmado × anterior):** chamava de "confirmado" o que era só extraído; rejeitada.
- **Não reutilizar nada entre conversas:** obriga o tutor a repetir tudo; contraria o objetivo 10.
- **Reutilizar o cadastro como verdade atual:** contraria o briefing (4.3).

## Consequências

- O contexto do tutor/pet é renderizado com o status de cada informação, e o prompt instrui como agir em cada status.
- A detecção de confirmação depende de metadados do modelo e de uma regra do backend, a definir em P-08. É uma fonte de erro a medir na Etapa 6.
