# ADR-0007: Notificações com porta NotificationSender; WhatsApp Cloud API direto

- Status: Proposto · Data: 2026-10-22

## Decisão
- A porta `NotificationSender` tem as implementações `WhatsAppCloudSender` (Meta, sem BSP),
  `EmailSmtpSender`, `SmsSender` (provedor a definir na Fase 3) e `FakeMessageSender` (demo: grava no
  banco e aparece na "Caixa de saída", com os botões funcionando).
- O roteamento por canal preferido e o fallback ficam em `RoteadorNotificacao`. Os retries usam
  backoff exponencial via db-scheduler. O status de entrega vem dos webhooks.
- Fora da janela de 24h, só templates `utility` aprovados. Dentro da janela, mensagem livre. Cada
  envio registra o custo estimado (tabela de preços configurável).
- O webhook da Meta valida `X-Hub-Signature-256` (HMAC do corpo bruto com o app secret, comparação
  em tempo constante) e é idempotente por `message.id`.
