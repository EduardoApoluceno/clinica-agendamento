# Pendências

## Estado em 2026-10-22: Fase 1 em andamento

### Pronto (backend + Keycloak)
- **Clínica:** dados e regras da agenda, com concorrência otimista, endpoint público e criação idempotente na 1ª subida.
- **Profissionais:** especialidades, serviços (duração, preço e política de pagamento NENHUM/SINAL/INTEGRAL), expediente semanal
  sem sobreposição, ausências. O próprio profissional edita a própria agenda; a recepção registra ausências.
- **Pacientes:** CPF obrigatório e validado; CPF e contatos cifrados (AES-256-GCM) com HMAC para busca; consentimentos LGPD
  com histórico; exportação de dados; verificação de celular por código (HMAC, 10 min, 5 tentativas, 3 envios/h).
  O vínculo de cadastro feito pela recepção exige comprovar o e-mail ou o celular (anti-apropriação de CPF).
- **Notificações (mínimo):** porta `EnvioMensagens` + caixa de saída de demonstração (`/api/v1/demo/caixa-saida`).
- **Auditoria:** trilha com hash encadeado calculado no banco (`fn_hash_auditoria`), verificação diária (db-scheduler),
  consulta para ADMIN, leituras de ficha auditadas.
- **RBAC fino:** papéis → permissões (`Permissao`); `@PreAuthorize` por permissão; equipe não herda PACIENTE.
- **Banco:** migrações V2–V6; o role da aplicação não exclui pacientes, consentimentos nem auditoria.
- **Keycloak (testado com Keycloak 26.8.1 real):**
  - MFA obrigatório para a equipe: passkey quando cadastrada; senão TOTP, com cadastro forçado no 1º login.
    Desliga no demo com `KC_FLUXO_NAVEGADOR=browser`.
  - PACIENTE como papel padrão do autocadastro.
  - Lista de senhas comuns/vazadas (`passwordBlacklist`).
- **Testes:** 107 unitários/arquitetura verdes, com 99,2% de cobertura em domain/application.
  Testes de integração (Testcontainers) em `AplicacaoIntegracaoTest` e `CadastrosIntegracaoTest` rodam no CI.
- **Cliente da API gerado com Orval** (`frontend/src/api/gerado`, hooks TanStack Query + schemas Zod) a partir de
  `frontend/openapi/clinica.json`. Para regenerar: `npm run api:baixar && npm run api:gerar` com o backend de pé.

### A fazer para fechar a Fase 1 (próxima sessão)
1. **Frontend:** terminar e ligar as telas.
   - Já escritas em `frontend/rascunhos/`: Configurações e Serviços. Mover para `src/pages/` e corrigir os tipos opcionais.
   - Ainda por escrever: Profissionais (lista, ficha, expediente, ausências), Pacientes (busca, ficha, cadastro),
     Meu cadastro (completar, verificar celular, consentimentos, exportar), Minha agenda, Auditoria e Caixa de saída.
   - Menu por permissão (o `/api/v1/me` já devolve `permissoes`).
   - Mensagens do Zod em pt-BR (`z.config(z.locales.pt())`).
2. Conferir no CI os testes de integração novos (`CadastrosIntegracaoTest`).
3. **Playwright:** cadastro com verificação e login com MFA.
4. **ADRs a escrever:**
   - verificação de telefone no banco (em vez de Redis);
   - regra de vínculo de cadastro;
   - fluxo de MFA do Keycloak;
   - hash da auditoria calculado no banco.
5. Notificação de login em novo dispositivo: o Keycloak não tem nativamente (avaliar um event listener SPI).
6. Anonimização LGPD: implementar na Fase 5, junto com as regras de guarda do prontuário.

## Pendências da Fase 0 (abertas)

| # | Pendência | Situação |
|---|---|---|
| P0-1 | Validar a stack Docker local de ponta a ponta | A virtualização da CPU (AMD SVM) está **desligada na BIOS**: ativar `SVM Mode` no UEFI da placa ASUS. O CI já sobe o compose demo-leve com sucesso |
| P0-4 | Renovate para atualizações de dependências | Aberta |
| P0-5 | ZAP baseline bloqueante | Quando houver telas reais |
| P0-6 | Protótipo visual aprovado não disponível | Telas seguem a descrição do prompt |
| P0-7 | Guarda no perfil `prod` contra segredos de demonstração | Parcial: `expor-codigos` e modo SIMPLES já são recusados em prod; falta checar as chaves |
