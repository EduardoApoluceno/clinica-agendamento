# ADR-0002: Padrão BFF com Keycloak (tokens nunca no navegador)

- Status: Proposto · Data: 2026-10-22

## Decisão
- O backend Spring é o cliente OAuth2 **confidencial** (`oauth2Login`, Authorization Code + PKCE).
- Os tokens ficam na sessão do servidor (Spring Session + Redis). O navegador só recebe
  `__Host-SESSION` (HttpOnly, Secure, SameSite=Strict).
- CSRF com `CookieCsrfTokenRepository` + header `X-XSRF-TOKEN`.
- SPA e API na **mesma origem** (Caddy), o que dispensa CORS no fluxo normal. CORS restrito como defesa extra.
- Logout: invalida a sessão local e chama o end-session do Keycloak (logout global).
- Links externos (e-mail/WhatsApp) não dependem de cookie na primeira navegação (efeito do
  SameSite=Strict): usam token de ação de uso único ou redirecionamento same-site.
- Keycloak e app no mesmo "site" (`auth.dominio` / `app.dominio`) para o callback OAuth funcionar.

## Consequências
+ XSS não consegue exfiltrar tokens.
− A API não é consumível diretamente por terceiros (não é requisito).
