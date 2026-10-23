# Clínica: agendamento e prontuário eletrônico

Monólito modular (Java 25 + Spring Boot 4.1 + Spring Modulith) com frontend React 19, Keycloak (BFF
OAuth2/OIDC), PostgreSQL 18 e Redis. Roda 100% local com Docker, sem nenhuma conta externa.

> **Estado atual: Fase 1 concluída (cadastros e acesso).** Login com MFA para a equipe, permissões por
> papel, clínica, profissionais (serviços, expediente, ausências), pacientes com dados cifrados e
> consentimentos LGPD, auditoria com hash encadeado e testes de ponta a ponta. Próxima: Fase 2 (agenda).
> Veja [`docs/arquitetura/00-proposta-fase0.md`](docs/arquitetura/00-proposta-fase0.md) e
> [`docs/pendencias.md`](docs/pendencias.md).

## Pré-requisitos

| Sistema | O que instalar |
|---|---|
| **Windows 10/11** | 1) WSL2: abra o PowerShell **como administrador**, rode `wsl --install` e **reinicie**. 2) [Docker Desktop](https://www.docker.com/products/docker-desktop/) com o backend WSL2 (padrão). |
| **macOS** | Docker Desktop (ou OrbStack). |
| **Linux** | Docker Engine 24+ com o plugin `docker compose`. |

Memória: o perfil `demo` usa ~2,8 GB (recomendado 8 GB+ de RAM no total) e o `demo-leve` ~1,5 GB.
Não é preciso instalar Java nem Node: tudo é compilado dentro dos containers.

## Subir a demonstração

```bash
docker compose --profile demo up --build
```

O primeiro build baixa dependências e leva alguns minutos. Quando tudo estiver saudável:

| Serviço | Endereço |
|---|---|
| Aplicação | http://localhost:8080 |
| Keycloak (login) | http://localhost:8180 (console admin: `admin` / `admin`) |
| Swagger / OpenAPI | http://localhost:8080/swagger-ui/index.html |
| Mailpit (e-mails) | http://localhost:8025 |
| Grafana | http://localhost:3000 (`admin` / `admin`) |
| Prometheus | http://localhost:9090 |

Todos os serviços escutam só em `127.0.0.1`. Nada fica exposto na rede.

### Usuários de teste (senha: `Demo@2026!`)

| Usuário | Papel |
|---|---|
| `admin@clinica.local` | ADMIN |
| `recepcao@clinica.local` | RECEPCAO |
| `dra.ana@clinica.local` | PROFISSIONAL |
| `dr.bruno@clinica.local` | PROFISSIONAL |
| `carla@exemplo.com` · `diego@exemplo.com` · `elisa@exemplo.com` | PACIENTE |

### Máquinas com 8 GB: perfil `demo-leve`

```bash
docker compose --profile demo-leve up --build
```

Este perfil roda sem Keycloak e sem Grafana/Prometheus, com login simplificado **apenas para
visualização**. Usuários: `admin`, `recepcao`, `dra.ana`, `dr.bruno`, `paciente1`, todos com a senha
`demo`. O backend se recusa a iniciar esse modo no perfil `prod`.

### Parar e limpar

```bash
docker compose --profile demo down        # para
docker compose --profile demo down -v     # para e APAGA os dados (banco, realm importado etc.)
```

## Desenvolvimento

```bash
# backend (JDK 25 + Maven 3.9)
cd backend && mvn verify            # testes unitários, ArchUnit, Modulith, Testcontainers, Checkstyle, SpotBugs, JaCoCo

# frontend (Node 24)
cd frontend && npm ci && npm run dev   # http://localhost:5173 (proxy para o compose em :8080)
npm test && npm run lint && npm run typecheck
npm run api:gerar                       # gera cliente TanStack Query + Zod via Orval (compose de pé)

# ponta a ponta (Playwright) contra o perfil demo; cria contas e cadastra TOTP do dr.bruno
npx playwright install chromium && npm run e2e
# autocadastro, "Esqueceu sua senha?" e MFA da equipe
```

Rode o E2E num banco descartável (`docker compose --profile demo down -v` antes): o teste de MFA espera
que o `dr.bruno@clinica.local` ainda não tenha TOTP. No CI, o job **E2E Playwright** sobe a stack do zero.

Os testes de integração do backend usam Testcontainers, então o Docker precisa estar em execução.

## Estrutura

```
backend/    Spring Boot — br.com.clinica.<modulo>/{domain,application,adapters,infrastructure}
frontend/   React + Vite + Tailwind (tokens em src/styles/index.css); imagem final = Caddy
infra/      postgres (roles), keycloak (realm + tema), seaweedfs, observabilidade, edge (Caddy do VPS)
docs/       arquitetura, DDL proposto, ADRs, pendências
compose.yaml  perfis demo | demo-leve | prod
```

## Produção (fase futura)

Use o mesmo `compose.yaml` com `--profile prod` e um `.env` baseado em [`.env.example`](.env.example).
O Caddy de borda compartilhado fica em [`infra/edge`](infra/edge). Hardening, backup (WAL-G/PITR) e
o runbook serão documentados na fase de produção (ADR-0009).
