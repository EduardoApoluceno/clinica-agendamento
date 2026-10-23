# ADR-0003: Clean Architecture por módulo, com domínio sem JPA

- Status: Proposto · Data: 2026-10-22

## Decisão
Camadas `domain` / `application` / `adapters` / `infrastructure` em cada módulo. Entidades de
domínio são classes Java puras (sem `null` nas APIs, com `Optional` nas consultas). O JPA fica em
`adapters/out/persistence`, com mapeamento explícito.

**Exceção permitida**: módulos de cadastro simples (clinica, templates de mensagem, modelos de
prontuário) podem anotar o domínio com JPA, desde que o ArchUnit continue barrando dependência de `adapters`.

## Consequências
+ Domínio e casos de uso testáveis com JUnit puro (meta de 80% de cobertura).
− Mais classes e mapeamento duplicado nos agregados complexos (agenda, prontuário, pagamentos).
