# ADR-0017: Fluxo de login com MFA obrigatório para a equipe (Keycloak)

- Status: **Aceito** · Data: 2026-10-23

## Contexto
A equipe (ADMIN, RECEPCAO, PROFISSIONAL) acessa dados de saúde; o requisito pede segundo fator para ela.
Pacientes entram pelo portal e não devem ser barrados por MFA no primeiro contato. O Keycloak 26 permite
montar o fluxo de navegador com condições por papel e por credencial.

## Decisão
Fluxo `navegador-clinica` no realm (definido em `infra/keycloak/realm-*/clinica-realm.json`):

```
navegador-clinica
├─ auth-cookie                                   ALTERNATIVE  (sessão SSO já aberta)
└─ formulários                                   ALTERNATIVE
   ├─ auth-username-password-form                REQUIRED
   └─ mfa equipe                                 CONDITIONAL
      ├─ conditional-user-role = EQUIPE          REQUIRED
      ├─ mfa passkey                             CONDITIONAL
      │  ├─ conditional-user-configured          REQUIRED
      │  └─ webauthn-authenticator               REQUIRED
      └─ mfa totp                                CONDITIONAL
         ├─ conditional-credential (sem passkey) REQUIRED
         └─ auth-otp-form                        REQUIRED
```

- `EQUIPE` é um papel composto herdado por ADMIN, RECEPCAO e PROFISSIONAL; PACIENTE não o tem.
- Membro da equipe com passkey cadastrada usa a passkey. Sem passkey, cai no TOTP; se ainda não tem TOTP,
  o `auth-otp-form` força o cadastro (ação `CONFIGURE_TOTP`) já no primeiro login.
- Política TOTP: SHA-1, 6 dígitos, 30 s (compatível com os apps comuns); código não reutilizável.
- O fluxo é escolhido pela variável `KC_FLUXO_NAVEGADOR` (padrão `navegador-clinica`). No demo, quem quiser
  explorar sem celular pode usar `KC_FLUXO_NAVEGADOR=browser`, que desliga o 2º fator.
- Cadastro: fluxo explícito `cadastro-clinica` (`registration-user-creation` + `registration-password-action`).
  Sem ele, a importação de um realm com fluxos próprios gerava um formulário de cadastro **sem senha**
  (achado pelo E2E em 2026-10-23).
- Pacientes: senha forte (`passwordPolicy` com lista de senhas vazadas) e e-mail verificado; MFA opcional
  fica para depois.
- Coberto por E2E (`frontend/e2e/equipe-mfa.spec.ts`): cadastro do TOTP no 1º login, login seguinte com
  código e recusa de código errado.

## Consequências
+ MFA da equipe é imposto no IdP, não depende do frontend nem do BFF.
+ Passkey, quando cadastrada, substitui o TOTP (resiste a phishing).
− A troca do fluxo por variável deixa possível subir **prod** sem MFA por engano. Pendência: a guarda do perfil
  `prod` (P0-7) deve recusar `KC_FLUXO_NAVEGADOR` diferente do padrão.
− Perda do celular com TOTP exige reset pelo ADMIN no console do Keycloak.

## Alternativas consideradas
- **OTP como ação obrigatória no usuário (`CONFIGURE_TOTP` fixo):** força o cadastro, mas não pede o código
  nos logins seguintes se o fluxo não tiver o passo OTP.
- **MFA para todos:** atrito alto no autocadastro do paciente, que é o canal principal de agendamento.
- **MFA no BFF (step-up próprio):** duplica o que o IdP já faz e espalha segredos TOTP pela aplicação.
