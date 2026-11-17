## Camada 5 — contexto operacional (proposta 4.2). Status, data/hora local, solicitações desta conversa
## (Etapa 5) e referência mínima às solicitações em aberto de conversas anteriores do mesmo contato (DA-37).
## Variáveis (StringTemplate, delimitadores { }): status, agora, funcionamento, solicitacoes, anteriores.
## funcionamento: calculado pelo backend a partir do horário da base (OpeningHours), nunca pelo modelo.
<estado_do_atendimento>
Status da conversa: {status}
Data e hora local da clínica: {agora}
Clínica: {funcionamento}
Solicitações desta conversa:
{solicitacoes}
Atendimentos anteriores em aberto (não reutilize dados deles; você não sabe o andamento):
{anteriores}
</estado_do_atendimento>
