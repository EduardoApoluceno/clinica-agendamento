# ADR-0009: Topologia de produção num VPS compartilhado

- Status: Rascunho (decisão final na fase de produção) · Data: 2026-10-22

## Decisão proposta
- Um compose por projeto, com rede, PostgreSQL e volumes próprios.
- Um **Caddy compartilhado** num compose "edge" à parte, ligado às redes de borda de cada projeto e
  roteando por domínio. O Caddy do perfil `demo` existe só para uso local.
- **Keycloak compartilhado** (um realm por projeto) se o outro projeto também usar Keycloak. Isso
  economiza ~600 MB. O admin console fica fora da internet (acesso por túnel SSH).
- VPS recomendado: Hostinger KVM 2 (8 GB) para os dois projetos.
