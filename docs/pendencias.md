# Pendências

## Estado em 2026-10-23: Fase 2 entregue (agenda)

### Pronto
- **Backend (módulo agenda):** agendamento com a constraint anti-overbooking (migração V7), trava por
  profissional, concorrência otimista na remarcação, histórico append-only e auditoria de cada transição.
  Cancelamento, confirmação, presença, falta e conclusão. Eventos de domínio (`EventosAgenda`) e job que
  expira holds vencidos. Disponibilidade pública com antecedência e horizonte, e avisos em tempo real
  por SSE. Grade do dia por profissional, "meus agendamentos" e arquivo `.ics`. Decisões no ADR-0019.
- **Permissões:** `agenda:ler`, `agenda:escrever` (ADMIN, RECEPCAO) e `agendamento-proprio:gerir`
  (PACIENTE). O profissional vê e conduz só a própria agenda.
- **Frontend:** Agenda do dia (resumo, navegação, grade por profissional, painel com confirmar, presença,
  falta, concluir, remarcar e cancelar, novo agendamento com busca de paciente). Portal do paciente em
  4 etapas, com tela de sucesso, Google Agenda e `.ics`. Meus agendamentos (confirmar, remarcar, cancelar).
- **Demonstração:** a semente cria serviços, 2 profissionais com expediente, 6 pacientes (Carla e Diego
  vinculados às contas de teste, com celular verificado) e cerca de 300 agendamentos de 14 dias para trás
  e para frente. O realm de demonstração passou a ter ids fixos: recrie o banco uma vez
  (`docker compose --profile demo down -v`).
- **Testes:** unitários de domínio e aplicação da agenda, integração com PostgreSQL real (20 requisições
  simultâneas no mesmo horário → 1×201 e 19×409, RBAC/IDOR, paciente agendando para si) e Playwright
  (paciente agenda e cancela pelo portal; recepção agenda pela grade).

### Abertas da Fase 2
1. **Cobrança antecipada desligada** (`app.agenda.cobranca-antecipada=false`) até a Fase 4. O hold,
   a expiração e a liberação já estão prontos.
2. **"Enviar lembrete no WhatsApp"** (Fase 3) e **"Oferecer à lista de espera"** (Fase 4) aparecem
   desabilitados, com "(em breve)".
3. **Feriados** ainda não entram na disponibilidade: a tabela existe, mas não há tela para cadastrá-los.
4. **Rate limiting** (Bucket4j + Redis) nos endpoints públicos (disponibilidade e SSE), em login e em
   cadastro ainda não existe. O SSE tem limite próprio de conexões.
5. O E2E da recepção cadastra o TOTP de `recepcao@clinica.local`: como o de MFA, precisa de banco
   descartável.
6. Encaixe fora do expediente não é permitido nem para a recepção (avaliar com a clínica).

## Fase 1 concluída e fechada em 2026-10-23 (CI todo verde, inclusive o E2E)

### Pronto (backend + Keycloak)
- **Clínica:** dados e regras da agenda, com concorrência otimista, endpoint público e criação idempotente na 1ª subida.
- **Profissionais:** especialidades, serviços (duração, preço e política de pagamento NENHUM/SINAL/INTEGRAL), expediente semanal
  sem sobreposição, ausências. O próprio profissional edita a própria agenda; a recepção registra ausências.
- **Pacientes:** CPF obrigatório e validado; CPF e contatos cifrados (AES-256-GCM) com HMAC para busca; consentimentos LGPD
  com histórico; exportação de dados; verificação de celular por código (HMAC, 10 min, 5 tentativas, 3 envios/h).
  O vínculo de cadastro feito pela recepção exige comprovar o e-mail ou o celular (anti-apropriação de CPF, ADR-0016).
  A recepção não troca o CPF para um já cadastrado, e o titular não troca o próprio CPF (correção pela recepção).
- **Notificações (mínimo):** porta `EnvioMensagens` + caixa de saída de demonstração (`/api/v1/demo/caixa-saida`).
- **Auditoria:** trilha com hash encadeado calculado no banco (`fn_hash_auditoria`, ADR-0018), verificação diária
  (db-scheduler), consulta para ADMIN, leituras de ficha auditadas.
- **RBAC fino:** papéis → permissões (`Permissao`); `@PreAuthorize` por permissão; equipe não herda PACIENTE.
- **Banco:** migrações V2–V6; o role da aplicação não exclui pacientes, consentimentos nem auditoria.
- **Keycloak (testado com Keycloak 26.8.1 real):** MFA obrigatório para a equipe (ADR-0017), PACIENTE como papel
  padrão do autocadastro, lista de senhas comuns/vazadas.
- **Testes do backend:** unitários/arquitetura e integração (Testcontainers) verdes no CI; `CadastrosIntegracaoTest`
  conferido no CI do commit f326f8b.

### Pronto (frontend)
- Telas ligadas às rotas, carregadas sob demanda (bundle inicial de 577 kB → 294 kB):
  Configurações, Serviços, Profissionais (lista, ficha, expediente, ausências), Minha agenda,
  Pacientes (busca, ficha, cadastro, edição), Meu cadastro (completar, verificar celular, consentimentos,
  exportar), Auditoria e Caixa de saída.
- Menu e rotas por permissão (`/api/v1/me` → `permissoes`); a autorização real continua no servidor.
- Mensagens do Zod em pt-BR (`z.config(z.locales.pt())`) e validação de CPF/celular no navegador.
- Modelos do Orval: as respostas vêm com todos os campos opcionais (o springdoc não marca `required` em records
  de resposta). As telas tratam com `?? valor`; corpos passam por `corpoLimpo()` por causa de
  `exactOptionalPropertyTypes`. Melhorar na origem (customizer do springdoc) quando der para regenerar o OpenAPI.
- Testes: 31 no Vitest (menu por permissão, validadores, regra do expediente, fluxo de Meu cadastro com API simulada).
- **Playwright** (`frontend/e2e`, job **E2E Playwright** no CI, perfil demo com Keycloak e Mailpit):
  autocadastro com verificação de e-mail e de celular; login da equipe com cadastro de TOTP e login com código.
  Verde no CI. Ao rodar pela 1ª vez, achou dois bugs, já corrigidos: o Keycloak não subia no perfil `demo`
  (`KC_PROXY_HEADERS` vazio) e o botão Sair dava 403 (token CSRF cru no formulário; agora vai mascarado).
  Com verificação de e-mail, o Keycloak 26.8 só pede a senha depois do link do e-mail (comportamento padrão).
  "Esqueceu sua senha?" coberto (`e2e/senha.spec.ts`): link do e-mail, senha nova, senha antiga recusada.
- **Guarda do perfil prod** (`GuardaProducao`/`RegrasProducao`): o backend não sobe com senhas de banco/Redis,
  segredo OIDC ou chaves de criptografia vazios ou iguais aos de demonstração, nem com `KC_FLUXO_NAVEGADOR=browser`.
  O compose agora repassa `APP_CRIPTO_CHAVE_*` ao backend (antes não chegavam em prod).

### ADRs escritos nesta fase
- ADR-0015 códigos de verificação no PostgreSQL · ADR-0016 vínculo de cadastro · ADR-0017 fluxo de MFA ·
  ADR-0018 hash da auditoria no banco.

### Abertas da Fase 1
1. Notificação de login em novo dispositivo: o Keycloak não tem nativamente (avaliar um event listener SPI).
2. Limpeza periódica de `codigo_verificacao` vencidos (ADR-0015), junto com as notificações da Fase 3.
3. Anonimização LGPD: implementar na Fase 5, junto com as regras de guarda do prontuário.

## Pendências da Fase 0

Resolvidas em 2026-10-23: **P0-1** (SVM ligado na BIOS; o perfil `demo` sobe inteiro no Windows com Docker
Desktop) e **P0-7** (guarda do perfil prod, acima).

| # | Pendência | Situação |
|---|---|---|
| P0-4 | Renovate para atualizações de dependências | Aberta |
| P0-5 | ZAP baseline bloqueante | Agora há telas reais: avaliar o relatório e tornar bloqueante |
| P0-6 | Protótipo visual aprovado não disponível | Telas seguem a descrição do prompt |
