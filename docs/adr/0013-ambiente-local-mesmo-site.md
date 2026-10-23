# ADR-0013: Endereços locais e "mesmo site" para o fluxo OIDC

- Status: Aceito · Data: 2026-10-22

## Contexto
O cookie de sessão é `SameSite=Strict`. O retorno do Keycloak (`/login/oauth2/code/keycloak`)
precisa chegar com esse cookie, então Keycloak e aplicação têm que ser o mesmo *site* (mesmo
domínio registrável). Além disso, dentro do Docker o backend não alcança `localhost:8180`.

## Decisão
- Localmente: app em `http://localhost:8080` (Caddy `web`) e Keycloak em `http://localhost:8180`.
  Portas diferentes no mesmo host contam como o mesmo site. Navegadores tratam `localhost` como
  contexto seguro, então cookies `Secure`/`__Host-` funcionam sem HTTPS local nem certificado
  autoassinado.
- O Keycloak usa `KC_HOSTNAME=<url pública>` e `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`, e escuta na
  porta 8180 também dentro da rede Docker.
- O backend monta o `ClientRegistration` manualmente, sem discovery:
  - `authorization_uri` e `end_session_endpoint` usam a URL **pública** (navegador);
  - `token_uri` e `jwk_set_uri` usam a URL **interna** (`http://keycloak:8180`);
  - o `issuer` esperado no ID token é sempre o público.
- Produção: `app.dominio` e `auth.dominio` (mesmo site). As URLs pública e interna podem ser
  iguais ou a interna pode apontar para a rede Docker.

## Consequências
+ Um único comando local, sem certificados e sem editar o arquivo hosts.
− O HSTS e o `Secure` reais só são exercitados no VPS (com TLS na borda).
