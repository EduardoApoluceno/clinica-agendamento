# Pendências

## Fase 0 (abertas)

| # | Pendência | Motivo / próximo passo |
|---|---|---|
| P0-1 | **Validar a stack Docker de ponta a ponta** (`docker compose --profile demo up`, login no Keycloak, `/api/v1/me`, teste de integração com Testcontainers `AplicacaoIntegracaoTest`) | O Docker Desktop desta máquina não sobe: o WSL2/Virtual Machine Platform não está habilitado. Habilitar (`wsl --install` como admin + reiniciar) e rodar a validação |
| P0-2 | Conferir o tema de login `clinica` no Keycloak 26.8 (herança de `keycloak.v2`, nome do CSS do pai) | Depende de P0-1 |
| P0-3 | Inicializar o repositório git e o remoto no GitHub para o CI rodar | Aguardando o usuário (nome do repositório, público/privado) |
| P0-4 | Configurar o Renovate para atualizações de dependências | Depende de P0-3 |
| P0-5 | ZAP baseline em modo bloqueante (`fail_action: true`) | Quando houver telas reais (Fase 2) |
| P0-6 | Protótipo visual aprovado não disponível nesta sessão | As telas seguem a descrição do prompt. Anexar o protótipo para ajustes de fidelidade |
| P0-7 | Guarda de inicialização no perfil `prod` contra segredos de demonstração (ex.: `demo-segredo-bff…`) | Fase 1 |

## Planejado para a Fase 1
- Atribuição automática do papel PACIENTE no autocadastro (fluxo/listener no Keycloak).
- Claim `clinica_id` via user profile declarativo do Keycloak.
- Verificação de telefone (OTP via NotificationSender), MFA obrigatório da equipe (passkeys/TOTP),
  checagem de senhas vazadas.
- Rate limiting Bucket4j + Redis.
- Gravação da auditoria com hash encadeado + job de verificação.
- Templates próprios do tema de login (cadastro com telefone, textos pt-BR).
