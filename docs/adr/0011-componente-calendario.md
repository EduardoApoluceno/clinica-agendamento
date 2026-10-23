# ADR-0011: Grade própria para a Agenda da recepção

- Status: **Aceito** · Data: 2026-10-22

## Contexto
A visão "uma coluna por profissional" é recurso pago no FullCalendar (Premium) e no Schedule-X
(Premium). O protótipo também exige estilos específicos (tracejado/preenchido) e um painel lateral.

## Decisão
Grade própria em CSS Grid + React, com rolagem horizontal no mobile, navegação por teclado e
`role="grid"`. Para as visões de semana/mês do profissional, usar o Schedule-X core (MIT), sem
plugins premium.
