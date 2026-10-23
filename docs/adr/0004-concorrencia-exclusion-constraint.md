# ADR-0004: Overbooking impedido por EXCLUDE constraint no PostgreSQL

- Status: **Aceito** (implementado na Fase 2, ver ADR-0019) · Data: 2026-10-22

## Decisão
- A coluna `agendamento.periodo tstzrange '[)'` tem uma constraint `EXCLUDE USING gist (clinica_id =,
  profissional_id =, periodo &&) WHERE status ocupa horário`. O hold (`RESERVADO`) também ocupa.
- O caso de uso valida as regras (expediente, ausência, antecedência) e grava na mesma transação.
  O banco é a autoridade final contra a corrida.
- `SQLSTATE 23P01` vira `HorarioIndisponivelException`, que vira 409 Problem Details
  (`type: /problemas/horario-indisponivel`).
- `@Version` protege edições concorrentes do mesmo agendamento (remarcação × cancelamento), com
  retorno 409 e outro `type`.
- Ausências/bloqueios não estão na constraint. Para evitar a corrida "bloqueio × agendamento", as
  duas operações pegam `pg_advisory_xact_lock` pelo profissional (barato, local à transação).
- A expiração de holds é um job do db-scheduler (a cada 1 min), que muda o status para `EXPIRADO` e
  publica `ReservaExpirada`. As consultas de disponibilidade também ignoram holds vencidos.

## Teste obrigatório
Testcontainers com PostgreSQL real: 20 threads tentam o mesmo slot ao mesmo tempo. Esperado:
exatamente 1 retorno 201 e 19 retornos 409.
