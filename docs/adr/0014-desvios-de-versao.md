# ADR-0014: Desvios conscientes da regra "versão estável mais recente"

- Status: Aceito · Data: 2026-10-22

| Item | Mais recente | Adotado | Motivo |
|---|---|---|---|
| TypeScript | 7.0.2 | 6.0.3 | `typescript-eslint` 8.72 suporta só TS < 6.1. Migrar quando houver suporte |
| @hookform/resolvers | 6.0.0 | 5.9.2 | a 6.0.0 traz conflito de peer deps opcionais (effect/@typeschema) que quebra `npm ci` sem `--legacy-peer-deps` |
| Spring Boot | 4.2.0-RC1 | 4.1.2 | só GA |
| Spring AI / Modulith / MapStruct | RC/Beta | última GA | só GA |
| Node | 26 (LTS em 28/10/2026) | 24 LTS | atualizar após a promoção a LTS |
| Java | 27 | 25 LTS | exigência do projeto (LTS) |

Revisar a cada fase. O Renovate (pendência) abrirá PRs automaticamente.
