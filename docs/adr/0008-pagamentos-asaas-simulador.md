# ADR-0008: Pagamentos via porta GatewayPagamento (Asaas + simulador)

- Status: Proposto · Data: 2026-10-22

## Decisão
- A porta `GatewayPagamento` tem as implementações `AsaasGateway` (sandbox/prod) e `SimuladorGateway`
  (demo). O simulador **emite webhooks no mesmo formato do Asaas** para o mesmo endpoint, o que
  exercita o caminho real.
- Webhook: valida o header `asaas-access-token` (comparação em tempo constante) e faz
  `INSERT … ON CONFLICT DO NOTHING` em `webhook_recebido(origem, id_evento)`. Se já existia,
  retorna 200 sem reprocessar.
- O hold do horário dura `minutos_hold_pagamento` (padrão 15). O pagamento confirma o agendamento.
  Se o hold expirar, a cobrança é cancelada no gateway.
- Cancelamento dentro da regra gera reembolso integral automático (sem penalidade).
- NFS-e: a porta `EmissorNotaFiscal` usa o Asaas ou o simulador. A emissão é disparada por
  `PagamentoConfirmado`, e o estorno cancela a nota.
