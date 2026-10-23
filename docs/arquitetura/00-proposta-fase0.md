# Proposta de Arquitetura — Fase 0 (aguardando aprovação)

> Documento de entrada exigido pela Regra de Trabalho 1. Nenhum código de produção foi escrito.
> Data: 2026-10-22. Status: **PROPOSTA**.

Sumário: 1. Versões · 2. Arquitetura · 3. Módulos · 4. Modelo de domínio · 5. Modelo de dados ·
6. Endpoints · 7. Segurança (fluxos) · 8. Memória por container · 9. Plano de entregas ·
10. Decisões pendentes

---

## 1. Versões (verificadas em 2026-10-22 no Maven Central / npm / endoflife.date)

Regra: usar a última versão **GA**; RC/Milestone/Beta não entram.

| Componente | Versão | Observação |
|---|---|---|
| Java | 25 LTS (Eclipse Temurin) | 27 já existe, mas não é LTS. O prompt pede 25 |
| Spring Boot | 4.1.2 | 4.2.0 ainda está em RC1 |
| Spring Modulith | 2.1.2 | linha compatível com Boot 4.1 |
| Spring AI | 2.0.2 | 2.1 ainda está em RC |
| springdoc-openapi | 3.1.1 | |
| MapStruct | 1.6.3 | 1.7 ainda está em Beta |
| Flyway | 13.11.1 | |
| db-scheduler | 16.12.0 | |
| Bucket4j | 8.21.0 | módulo `bucket4j_jdk17-*` + `lettuce` |
| Testcontainers | 2.0.5 | + testcontainers-keycloak 4.4.0 |
| ArchUnit | 1.5.1 | |
| PostgreSQL | 18.6 | btree_gist, pgcrypto |
| Redis | 8.10 | |
| Keycloak | 26.8.1 | passkeys, TOTP, brute force nativos |
| Caddy | 2.11.7 | |
| Node (build do front) | 24 LTS | Node 26 vira LTS em 28/10/2026; atualizar depois |
| React | 19.3 | |
| Vite | 8.3 | |
| TypeScript | 6.0.3 | 7.0 adiado: sem suporte no typescript-eslint (ADR-0014) |
| TanStack Query | 5.104 | |
| React Router | 8.5 | modo "declarative/data" (SPA, sem SSR) |
| Tailwind CSS | 4.3 | shadcn/ui compatível com v4 |
| Zod | 4.6 | |
| Orval | 8.44 | |
| date-fns-tz | 3.2 | |
| Armazenamento de objetos | SeaweedFS (local) · B2/R2 (prod) | ADR-0010; MinIO CE descontinuado |

As versões ficam fixadas no `pom.xml` (via BOMs) e no `package-lock.json`. O Renovate abre PRs de atualização.

---

## 2. Arquitetura

### 2.1 Visão de containers (perfil `demo`)

```
                    navegador (React SPA, só cookie de sessão)
                               │ https://localhost (Caddy, certificado interno)
                     ┌─────────▼─────────┐
                     │      Caddy        │  serve o /  (estático do Vite)
                     │  reverse proxy    │  /api, /oauth2, /login, /logout → backend
                     └───┬───────────┬───┘  auth.localhost → keycloak
                         │           │
              ┌──────────▼──┐   ┌────▼──────┐
              │  backend    │──▶│ Keycloak  │ (realm "clinica", tema pt-BR)
              │ Spring Boot │   └────┬──────┘
              │  (BFF+API)  │        │
              └─┬──┬──┬──┬──┘        │
                │  │  │  └──────────────────▶ Ollama (opcional, perfil "ia")
                │  │  └──▶ Armazenamento S3 (anexos cifrados)
                │  └─────▶ Redis (sessões, rate limit, cache)
                └────────▶ PostgreSQL ◀───────┘ (bancos separados: app e keycloak)
                           Mailpit (SMTP fake + UI)   Prometheus → Grafana
```

- **Mesma origem**: o Caddy entrega a SPA e o backend sob o mesmo host. Assim o cookie
  `SameSite=Strict` funciona e não é preciso CORS no fluxo normal. O CORS fica restrito
  e serve apenas como defesa extra.
- **Um único processo de backend** (monólito modular). Pode ter 2 réplicas no futuro:
  sessão no Redis, jobs no db-scheduler, outbox no Modulith. Tudo isso já é seguro com múltiplas instâncias.
- **Perfil `prod`**: mesmo compose, sem Mailpit e sem Ollama. Os adaptadores reais são
  selecionados por variável de ambiente. O Caddy fica externo e é compartilhado entre os projetos (ver ADR-0009).

### 2.2 Estilo interno

Monólito modular com **Spring Modulith**. Cada módulo é um pacote de topo `br.com.clinica.<modulo>`
com quatro camadas:

```
agenda/
  domain/          entidades, value objects, eventos, regras puras (sem Spring, sem JPA*)
  application/     casos de uso (1 classe por caso de uso), portas de entrada e saída
  adapters/
    in/web/        controllers REST, DTOs, mappers MapStruct
    in/events/     listeners de eventos de outros módulos
    out/persistence/  entidades JPA, repositórios Spring Data, adaptadores de porta
    out/<externo>/    clientes HTTP (Meta, Asaas, Google…)
  infrastructure/  @Configuration, propriedades, seleção de adaptadores por profile
```

\* **Decisão (ADR-0003)**: o domínio fica puro e o JPA mora em `adapters/out/persistence`, com
mapeamento explícito. É mais código, mas mantém os testes de domínio sem Spring e respeita a Clean
Architecture pedida. Para módulos CRUD simples (clínica, templates), um ADR permite mapear JPA
direto no domínio. Isso reduz o código repetitivo sem violar a dependência para dentro.

Regras verificadas automaticamente:
- `ApplicationModules.of(App.class).verify()`: sem ciclos, e cada módulo só expõe o pacote `api`
  e os eventos.
- ArchUnit: `domain` não depende de `org.springframework..`, `jakarta.persistence..` nem `adapters..`.
  `application` não depende de `adapters..`. Controllers só chamam casos de uso.

### 2.3 Comunicação entre módulos

- Chamadas síncronas apenas via **porta pública** (interface em `<modulo>.api`). Exemplo: `agenda`
  consulta `profissionais.api.ExpedienteConsulta`.
- Efeitos colaterais via **eventos de domínio** publicados na transação. O Event Publication
  Registry (tabela `event_publication`) funciona como outbox: o listener `@ApplicationModuleListener`
  roda após o commit e é reprocessado se falhar.

Eventos principais:

| Evento | Publicado por | Consumido por |
|---|---|---|
| `AgendamentoReservado` (hold criado) | agenda | pagamentos (gera cobrança) |
| `AgendamentoCriado` | agenda | notificacoes, integracoes-calendario, relatorios, auditoria |
| `AgendamentoConfirmado` | agenda | notificacoes |
| `AgendamentoRemarcado` | agenda | notificacoes, integracoes-calendario, lista-espera (slot antigo) |
| `AgendamentoCancelado` | agenda | pagamentos (reembolso), notificacoes, lista-espera, integracoes-calendario |
| `VagaLiberada` | agenda | lista-espera |
| `ReservaExpirada` | agenda (job) | pagamentos (cancela cobrança), lista-espera |
| `PagamentoConfirmado` | pagamentos | agenda (efetiva o hold), fiscal (emite NFS-e), notificacoes |
| `PagamentoEstornado` | pagamentos | fiscal (cancela NFS-e), notificacoes |
| `NotaFiscalEmitida` | fiscal | notificacoes |
| `OfertaVagaAceita` | lista-espera | agenda (cria agendamento) |
| `RegistroClinicoFinalizado` | prontuario | auditoria |
| `EventoExternoSincronizado` | integracoes-calendario | agenda (bloqueio) |
| `PacienteCadastrado`, `ContatoVerificado` | pacientes | notificacoes, identidade-acesso |

---

## 3. Módulos

| Módulo | Responsabilidade | Depende (porta síncrona) |
|---|---|---|
| `compartilhado` (shared kernel) | `ClinicaId`, `Dinheiro`, `Periodo`, `Cpf`, `Telefone`, correlation-id, criptografia de campo, Problem Details base | — |
| `identidade-acesso` | BFF/OAuth2 client, mapeamento de papéis, permissões finas, verificação de telefone, Keycloak Admin API | clinica |
| `clinica` | dados da clínica, configurações (antecedência, hold, lembretes, canais), feriados | — |
| `profissionais` | profissional, especialidades, serviços, expediente, intervalos, ausências/bloqueios | clinica |
| `pacientes` | cadastro, contatos, preferências, consentimentos, direitos do titular | clinica |
| `agenda` | disponibilidade, agendamento, hold, remarcação, cancelamento, presença/falta, SSE | profissionais, pacientes, clinica |
| `prontuario` | registros clínicos, adendos, modelos, anexos, assinatura (porta), prescrição digital (porta) | pacientes, profissionais, agenda |
| `notificacoes` | `NotificationSender` (WhatsApp/e-mail/SMS/Fake), templates, lembretes, entrega, webhook Meta | pacientes, clinica |
| `pagamentos` | cobranças, simulador, Asaas, webhooks idempotentes, reembolso | agenda (consulta), clinica |
| `fiscal` | NFS-e via Asaas / simulador | pagamentos (consulta) |
| `lista-espera` | entradas, ofertas com prazo, fila por prioridade | agenda (disponibilidade) |
| `integracoes-calendario` | Google OAuth2, canais push, sync bidirecional (feature flag) | profissionais, agenda |
| `assistente-ia` | Spring AI, tools que chamam casos de uso, limites de custo, registro de conversas | agenda, pacientes, clinica, notificacoes |
| `relatorios` | read models (projeções a partir de eventos e views), export CSV/PDF | todos via eventos/views |
| `auditoria` | trilha append-only com encadeamento por hash; consulta | — (recebe de todos) |

---

## 4. Modelo de domínio (agregados principais)

```
Clinica ─┬─ ConfiguracaoAgenda (antecedenciaMinimaCancelamento, minutosHold, lembretes[24h,2h], fuso)
         └─ Feriado
Profissional ─┬─ RegistroConselho (CRM/UF)    TipoServico (duracao, preco, PoliticaPagamento:
              ├─ Expediente[] (diaSemana,       NENHUM | SINAL(valor) | INTEGRAL, preparo)
              │   faixas[], intervalos[])     ProfissionalServico (n:n, preço/duração override)
              └─ Ausencia[] (FERIAS | BLOQUEIO | EXTERNO_GOOGLE, periodo)
Paciente ─┬─ Contato[] (EMAIL | CELULAR, verificadoEm)   ← CPF cifrado + hash para busca
          ├─ Preferencias (canal preferido, horários)
          └─ Consentimento[] (finalidade, versaoTermo, concedidoEm, revogadoEm, origem)
Agendamento (raiz) — periodo, status, origem, holdExpiraEm, version
          ├─ Recurso[] (reservado p/ futuro: SALA | EQUIPAMENTO | LINK_VIDEO)
          └─ HistoricoAgendamento[] (transições, quem, quando, motivo)
RegistroClinico (raiz) — tipo, status RASCUNHO→FINALIZADO, conteúdo cifrado, hashConteudo,
          ├─ Adendo[] (versão n, conteúdo, hash, autor)   assinatura (porta ICP-Brasil)
          └─ Anexo[] (chaveObjeto, sha256, mime real, tamanho)
ModeloProntuario (especialidade, tipo, esquema JSON de campos)
Cobranca (agendamento, tipo SINAL|INTEGRAL, valor, metodo PIX|CARTAO|BOLETO, status, idExterno)
          └─ Reembolso[]
NotaFiscal (cobranca, status, numero, linkPdf, idExterno)
EntradaListaEspera (paciente, servico, profissional?, janelaPreferida, prioridade, status)
          └─ OfertaVaga[] (slot, expiraEm, status OFERTADA|ACEITA|RECUSADA|EXPIRADA)
Notificacao (canal, template, destino mascarado, status, tentativas, custoEstimado, idExterno)
ConversaIA ─ MensagemIA[] (papel, conteudo minimizado, tokens, custo)
EventoAuditoria (append-only, hash encadeado)
```

**Máquina de estados do Agendamento**

```
               pagamento exigido                         pagamento confirmado
  [novo] ──────────────────────────▶ RESERVADO ─────────────────────────────▶ CONFIRMADO
     │                                  │ job: hold expirou                       ▲  │
     │ sem pagamento                    ▼                                         │  │
     └──────────────▶ AGUARDANDO_CONFIRMACAO ── paciente confirma (lembrete) ────┘  │
                          │        │                                              │
                          │        └──────────── cancelar (antecedência ok) ──────┼──▶ CANCELADO
                          └────────────────────────── check-in ───────▶ PRESENTE ─┴──▶ REALIZADO
                                                    passou e não veio ─▶ FALTA     EXPIRADO
```

- Estados que **ocupam** horário (usados na constraint): `RESERVADO, AGUARDANDO_CONFIRMACAO,
  CONFIRMADO, PRESENTE, REALIZADO, FALTA`.
- **Remarcação** altera o `periodo` do mesmo agendamento (com `@Version` e histórico). A constraint
  revalida o novo horário. Depois do commit, publica `AgendamentoRemarcado` com o slot antigo, que vira uma `VagaLiberada`.
- Regras puras no domínio: `PoliticaCancelamento.podeCancelar(agora, inicio, antecedencia)`,
  `CalculadoraDisponibilidade.slots(expediente, ausencias, ocupados, duracao, passo)`.

---

## 5. Modelo de dados

O DDL completo proposto está em [`ddl-proposta.sql`](ddl-proposta.sql). Ele vira `V1__…` a `Vn__…`
no Flyway, um script por módulo. Destaques:

- Todas as tabelas de negócio têm `clinica_id uuid not null` e índice começando por `clinica_id`.
- PKs `uuid` geradas na aplicação (UUIDv7, ordenáveis no tempo).
- `timestamptz` em tudo. Datas de expediente usam `time` + o fuso da clínica.
- **Anti-overbooking**:
  ```sql
  CONSTRAINT agendamento_sem_sobreposicao EXCLUDE USING gist (
    clinica_id WITH =, profissional_id WITH =, periodo WITH &&)
  WHERE (status IN ('RESERVADO','AGUARDANDO_CONFIRMACAO','CONFIRMADO','PRESENTE','REALIZADO','FALTA'))
  ```
  `periodo tstzrange` usa o limite `[)`, então consultas encostadas (10:00–10:30 e 10:30–11:00)
  **não** conflitam. Quando a constraint é violada, o código SQLSTATE `23P01` vira
  `HorarioIndisponivelException` e depois HTTP 409.
- **Prontuário imutável**: um trigger rejeita UPDATE/DELETE em `registro_clinico` finalizado, e
  `adendo` é append-only. Não há DELETE físico em nenhuma tabela de prontuário: o role da aplicação
  não tem o privilégio.
- **Auditoria**: `evento_auditoria` é append-only (sem UPDATE/DELETE para o role da aplicação, e um
  trigger de bloqueio). Cada linha guarda `hash = sha256(hash_anterior || conteúdo)`, então qualquer
  adulteração é detectável. O particionamento mensal por `ocorrido_em` fica preparado.
- **Criptografia de colunas sensíveis** (ADR-0006): é feita na aplicação (AES-256-GCM, envelope com
  chave mestra fora do banco, via variável de ambiente ou Docker secret). Vale para CPF, conteúdo de
  prontuário, tokens OAuth do Google e telefone. Para buscas, guarda-se um HMAC-SHA256 em coluna
  separada (`cpf_hmac`). A pgcrypto fica instalada para `gen_random_bytes`/digest, mas a chave nunca
  passa pelo SQL. Assim ela não aparece em `pg_stat_statements` nem nos logs.
- Dois roles no PostgreSQL: `clinica_migracao` (dono, usado só pelo Flyway) e `clinica_app` (DML
  restrito).

---

## 6. Endpoints (REST, prefixo `/api/v1`, Problem Details RFC 9457)

Autenticação: sessão BFF (cookie). Os webhooks usam assinatura e token próprios. O "ator" sai sempre
da sessão, nunca de um parâmetro.

**Sessão / identidade**
| Método | Rota | Perfil |
|---|---|---|
| GET | `/oauth2/authorization/keycloak` | público (inicia login PKCE) |
| GET | `/login/oauth2/code/keycloak` | público (callback) |
| POST | `/logout` | autenticado (logout global no Keycloak) |
| GET | `/api/v1/me` | autenticado (perfil, permissões, clínica) |
| GET | `/api/v1/csrf` | público (entrega o token CSRF ao SPA) |
| POST | `/api/v1/me/telefone/codigo` · `/api/v1/me/telefone/verificar` | PACIENTE (rate limit) |

**Clínica e profissionais**
| GET/PUT | `/api/v1/clinica` · `/api/v1/clinica/configuracoes` | ADMIN |
| GET | `/api/v1/publico/clinica` (endereço, serviços, preparo) | público |
| CRUD | `/api/v1/servicos` | ADMIN (GET público) |
| CRUD | `/api/v1/profissionais` | ADMIN (GET público resumido) |
| PUT | `/api/v1/profissionais/{id}/expediente` | ADMIN, o próprio PROFISSIONAL |
| CRUD | `/api/v1/profissionais/{id}/ausencias` | ADMIN, RECEPCAO, o próprio |

**Pacientes**
| GET/POST | `/api/v1/pacientes` (busca por nome/CPF/telefone) | RECEPCAO, ADMIN |
| GET/PUT | `/api/v1/pacientes/{id}` | RECEPCAO, ADMIN, PROFISSIONAL (resumo) |
| GET/PUT | `/api/v1/me/paciente` · `/api/v1/me/consentimentos` | PACIENTE |
| POST | `/api/v1/me/exportacao-dados` · `/api/v1/pacientes/{id}/anonimizacao` | PACIENTE / ADMIN |

**Agenda**
| GET | `/api/v1/disponibilidade?servicoId&profissionalId&de&ate` | público (rate limit) |
| GET | `/api/v1/disponibilidade/stream?profissionalId` (SSE) | público (rate limit) |
| GET | `/api/v1/agenda/dia?data` (grade da recepção + resumo) | RECEPCAO, ADMIN, PROFISSIONAL |
| POST | `/api/v1/agendamentos` | PACIENTE (para si), RECEPCAO |
| GET | `/api/v1/agendamentos/{id}` · `/api/v1/me/agendamentos` | dono, RECEPCAO, profissional do caso |
| POST | `/api/v1/agendamentos/{id}/remarcacao` | dono, RECEPCAO |
| POST | `/api/v1/agendamentos/{id}/cancelamento` | dono, RECEPCAO |
| POST | `/api/v1/agendamentos/{id}/confirmacao` · `/presenca` · `/falta` | RECEPCAO (confirmação: dono também) |
| POST | `/api/v1/agendamentos/{id}/lembrete` | RECEPCAO |
| GET | `/api/v1/agendamentos/{id}.ics` | dono ("Adicionar ao Google Agenda" via link de template + ICS) |
| GET/POST | `/api/v1/acoes/{tokenAssinado}` (link seguro do e-mail: confirmar/cancelar) | token de uso único |

**Prontuário**
| GET | `/api/v1/pacientes/{id}/prontuario` (linha do tempo) | PROFISSIONAL autorizado |
| POST/PUT | `/api/v1/pacientes/{id}/registros` · `/registros/{rid}` (rascunho) | PROFISSIONAL autor |
| POST | `/api/v1/registros/{rid}/finalizacao` · `/adendos` · `/assinatura` | PROFISSIONAL autor |
| POST/GET | `/api/v1/registros/{rid}/anexos` · `/anexos/{aid}` (download por stream, nunca URL pública) | PROFISSIONAL autorizado |
| GET | `/api/v1/registros/{rid}/documento.pdf` (atestado, receita etc.) | PROFISSIONAL autorizado, PACIENTE dono (só documentos emitidos) |
| CRUD | `/api/v1/modelos-prontuario` | ADMIN |

**Pagamentos, fiscal e lista de espera**
| GET | `/api/v1/agendamentos/{id}/cobranca` (QR Pix, status) | dono, RECEPCAO |
| POST | `/api/v1/webhooks/asaas` | token `asaas-access-token` + idempotência por id de evento |
| POST | `/api/v1/demo/gateway/{cobrancaId}/{aprovar|recusar|estornar}` | só existe no profile `demo` |
| GET | `/api/v1/notas-fiscais?periodo` | ADMIN |
| POST/GET/DELETE | `/api/v1/lista-espera` · `/api/v1/me/lista-espera` | PACIENTE, RECEPCAO |
| POST | `/api/v1/vagas/{slot}/oferta-lista-espera` | RECEPCAO |
| POST | `/api/v1/ofertas/{id}/aceite` · `/recusa` | PACIENTE dono |

**Notificações, IA, integrações, relatórios, auditoria**
| GET/POST | `/api/v1/webhooks/whatsapp` (verificação + X-Hub-Signature-256) | Meta |
| GET | `/api/v1/demo/caixa-saida` · POST `/api/v1/demo/caixa-saida/{id}/resposta` | ADMIN, profile demo |
| CRUD | `/api/v1/templates-mensagem` | ADMIN |
| POST | `/api/v1/assistente/mensagens` | PACIENTE (portal) |
| GET | `/api/v1/integracoes/google/conectar` · `/callback` · POST `/api/v1/webhooks/google-calendar` | PROFISSIONAL / Google |
| GET | `/api/v1/relatorios/indicadores?periodo=7d|30d|90d&de&ate&profissionalId` | ADMIN |
| GET | `/api/v1/relatorios/indicadores/export?formato=csv|pdf` | ADMIN |
| GET | `/api/v1/auditoria?recurso&ator&de&ate` | ADMIN |

Operacionais (rede interna, não roteados pelo Caddy público): `/actuator/health/{liveness,readiness}`,
`/actuator/prometheus`.

---

## 7. Segurança: pontos de desenho que vale validar

1. **BFF**: `spring-boot-starter-oauth2-client` como cliente confidencial, com
   `oauth2Login` + PKCE. Os tokens ficam na sessão (Spring Session Redis). O cookie é
   `__Host-SESSION; HttpOnly; Secure; SameSite=Strict; Path=/`. O CSRF usa
   `CookieCsrfTokenRepository` + header `X-XSRF-TOKEN`, no padrão SPA do Spring Security 7.
2. **Efeito do `SameSite=Strict`**: um link vindo de outro site (e-mail, WhatsApp) faz o primeiro
   carregamento chegar **sem** cookie. Por isso as rotas de entrada (`/acoes/{token}`, links de
   lembrete) usam token próprio de uso único ou fazem um redirecionamento same-site antes de exigir
   sessão. Keycloak e app ficam no mesmo "site" (`auth.dominio` e `app.dominio`), então o callback
   OAuth funciona.
3. **Verificação de telefone**: o Keycloak não envia código por WhatsApp nativamente. O backend
   gera um OTP de 6 dígitos (hash no Redis, TTL de 10 min, 5 tentativas, rate limit), envia por
   `NotificationSender` e grava `telefone_verificado=true` como atributo no Keycloak e no paciente.
   Só um número verificado é aceito pelo assistente de WhatsApp.
4. **MFA da equipe**: um fluxo de autenticação do realm exige WebAuthn Passwordless ou TOTP para
   usuários com os papéis `ADMIN|RECEPCAO|PROFISSIONAL` (condição por papel). No demo, a variável
   `KC_DEMO_MFA=false` troca o fluxo.
5. **Permissões finas**: os papéis vêm do Keycloak, e as permissões (`agenda:escrever`,
   `prontuario:ler`…) ficam mapeadas na aplicação. A autorização por recurso (é o dono? é o
   profissional do caso?) usa `@PreAuthorize` com beans de política por módulo. Toda query de
   repositório recebe `ClinicaId` e o ator, o que protege contra IDOR.
6. **Mascaramento**: um `MaskingJsonGeneratorDecorator` do logstash-encoder mascara CPF, telefone e
   e-mail. Conteúdo clínico nunca entra em log.

---

## 8. Estimativa de memória por container

Os limites (`mem_limit`) ficam no compose, e a JVM usa `-XX:MaxRAMPercentage=70`,
`-XX:+UseCompactObjectHeaders` (Java 25) e `-XX:+UseSerialGC` nos containers pequenos.

| Container | Uso típico | Limite proposto | demo | demo-leve | prod (VPS) |
|---|---|---|---|---|---|
| backend (Spring Boot) | 450–600 MB | 768 MB | ✓ | ✓ | ✓ |
| PostgreSQL 18 | 120–250 MB | 384 MB | ✓ | ✓ | ✓ |
| Keycloak 26 | 450–600 MB | 768 MB | ✓ | — | ✓ (ou compartilhado) |
| Redis | 15–40 MB | 64 MB | ✓ | ✓ | ✓ |
| Caddy (+ estáticos do front) | 20–40 MB | 64 MB | ✓ | ✓ | ✓ (compartilhado) |
| SeaweedFS (S3 local) | 100–150 MB | 192 MB | ✓ | ✓ | — (B2/R2 gerenciado) |
| Mailpit | 15–30 MB | 64 MB | ✓ | ✓ | — |
| Prometheus | 100–200 MB | 256 MB | ✓ | — | opcional* |
| Grafana | 80–150 MB | 192 MB | ✓ | — | opcional* |
| Ollama + modelo ~1,5–3B (perfil `ia`) | 1,5–3 GB | 3 GB | opcional | — | — |
| **Total (sem Ollama)** | | | **~2,8 GB** | **~1,5 GB** | **~2,0–2,6 GB** |

\* Em produção: Grafana Cloud free + Grafana Alloy (~80 MB) no lugar de Prometheus/Grafana locais.

Sua máquina tem 16 GB, então o perfil `demo` completo (inclusive com Ollama) cabe com folga.
**VPS**: dois projetos deste porte cabem num **KVM 2 (8 GB)** compartilhando Caddy e Keycloak, ainda
com ~2 GB de folga para cache de disco do PostgreSQL. KVM 1 (4 GB) não é recomendado.

Observação: hoje a sua máquina tem só Java 8 e não tem Node. **Não é preciso instalar nada além
do Docker**: todos os builds (Maven, Vite) rodam em containers multi-stage. Para desenvolver com IDE,
recomendo instalar o JDK 25 (Temurin) e o Node 24.

---

## 9. Plano de entregas (cada fase termina com testes verdes, Flyway, README e OpenAPI atualizados)

| Fase | Conteúdo | Critério de pronto |
|---|---|---|
| **0** | Repositório monorepo (`backend/`, `frontend/`, `infra/`, `docs/`), ADRs, DDL base (clínica, auditoria, outbox, scheduler), esqueleto dos 14 módulos com teste Modulith/ArchUnit, compose `demo`/`demo-leve`/`prod`, realm Keycloak importável, design tokens + shell do front (menu lateral, banner DEMO), CI (build, testes, Checkstyle, SpotBugs, Trivy em dependências e imagens, ZAP baseline) | `docker compose --profile demo up` sobe tudo saudável; login de teste chega numa página vazia autenticada; CI verde |
| **1** | BFF completo, cadastro do paciente com verificação de e-mail e telefone, MFA da equipe, RBAC + permissões, CRUD de clínica/profissionais/serviços/expediente/pacientes/consentimentos, auditoria + hash chain, rate limiting | Testes de segurança (Testcontainers + Keycloak), Playwright cadastro/login |
| **2** | Disponibilidade, agendamento, hold, remarcação, cancelamento, SSE, tela Agenda da recepção, portal de agendamento (4 etapas) | Teste de concorrência (N threads, mesmo slot → 1 sucesso, N−1 recebem 409) |
| **3** | NotificationSender (Fake + Meta Cloud API + e-mail + SMS), templates, lembretes 24h/2h, botões interativos, Caixa de saída do demo, webhook Meta | Fluxo de confirmação pelo botão na Caixa de saída |
| **4** | Simulador de gateway + Asaas sandbox, Pix/cartão/boleto, hold com expiração, webhooks idempotentes, reembolso, NFS-e, lista de espera com ofertas | Webhook duplicado sem efeito duplo; oferta expira e passa ao próximo |
| **5** | Prontuário (modelos, registros, finalização, adendos, anexos cifrados, PDF com assinatura simulada), auditoria de leitura | Trigger de imutabilidade testado; toda leitura gera evento de auditoria |
| **6** | Google Agenda (flag), projeções de relatórios, tela de Indicadores, exportação CSV/PDF | Seeds geram indicadores preenchidos |
| **7** | Assistente de IA (portal + WhatsApp), observabilidade (dashboards, alertas), revisão de segurança, documentação e runbook de produção | Evals do assistente (não dá orientação médica, confirma antes de agir) |

Os seeds de demonstração crescem a cada fase.

---

## 10. Decisões pendentes (preciso da sua resposta antes de começar a Fase 0)

Estão listadas na mensagem do chat e repetidas aqui:

- ~~D1. Armazenamento de objetos~~ **DECIDIDO (ADR-0010)**: SeaweedFS local; em produção, S3
  gerenciado (Backblaze B2 ou Cloudflare R2); arquivos cifrados na aplicação antes do envio.
- **D2 — DECIDIDO (ADR-0012)**: qualquer PROFISSIONAL da clínica lê o prontuário, sempre com
  auditoria. Só o autor edita o rascunho ou cria adendo do próprio registro. RECEPCAO e ADMIN não
  leem conteúdo clínico.
- **D3 — DECIDIDO**: CPF obrigatório e validado (dígitos verificadores), cifrado e com HMAC
  único por clínica. `paciente.cpf_cifrado`/`cpf_hmac` passam a ser `NOT NULL`.
- **D4 — DECIDIDO (ADR-0011)**: grade própria para a Agenda da recepção.
- **D5 — DECIDIDO**: o protótipo não está acessível nesta sessão (fica em outra conversa do app
  Claude). As telas seguem a descrição textual de "DIRETRIZES DE INTERFACE". Se o protótipo for
  anexado depois, os ajustes de fidelidade entram como pendência.

**Status da proposta: APROVADA em 2026-10-22. Fase 0 em execução.**
