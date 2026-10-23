# ADR-0001: Monólito modular com Spring Modulith

- Status: Proposto · Data: 2026-10-22

## Contexto
É uma única clínica, que vai rodar num único VPS dividido com outro projeto. O sistema deve poder
evoluir para SaaS. O RabbitMQ foi vetado nesta fase.

## Decisão
Um único deployable Spring Boot com 14 módulos (pacotes de topo) verificados pelo Spring Modulith.
Os módulos se comunicam via eventos de domínio, com o Event Publication Registry (JDBC) como outbox
transacional. Os listeners usam `@ApplicationModuleListener` (assíncrono, pós-commit, reprocessável).

## Consequências
+ Um processo e um banco: menor RAM e operação simples, com transações locais.
+ A fronteira entre módulos é testada (`verify()`), o que permite extrair um serviço no futuro.
− Eventos são "at-least-once": todo listener precisa ser idempotente.
− Sem broker, não há fan-out entre processos. Com 2 réplicas, o registro de eventos é
  compartilhado via banco (suportado).

## Alternativas
Microsserviços (custo de RAM/operação incompatível com o VPS) e monólito sem fronteiras (degrada com o tempo).
