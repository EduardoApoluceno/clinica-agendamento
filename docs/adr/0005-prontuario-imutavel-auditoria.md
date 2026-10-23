# ADR-0005: Prontuário imutável e auditoria encadeada

- Status: Proposto · Data: 2026-10-22

## Decisão
- `registro_clinico` fica editável só como RASCUNHO. Na finalização, o conteúdo recebe um hash
  sha256 e uma assinatura (porta `AssinaturaDigital`: no demo é `AssinaturaSimulada`, com marca
  d'água "SEM VALOR LEGAL"). Depois disso, só se corrige por `adendo` versionado.
- Triggers bloqueiam UPDATE de registro finalizado e qualquer DELETE. O role `clinica_app` não tem
  DELETE nessas tabelas. A retenção é de ≥ 20 anos (Lei 13.787/2018), sem exclusão física.
- Toda leitura ou escrita de prontuário gera `evento_auditoria` **na mesma transação** (não por
  evento assíncrono), para não perder o registro de acesso.
- `evento_auditoria` é append-only, com `hash = sha256(hash_anterior + linha canônica)`. Um job
  diário verifica a cadeia e alerta se houver quebra.
- Na anonimização LGPD, o prontuário é preservado (guarda obrigatória), e os dados cadastrais não
  clínicos e de marketing são anonimizados.
- Porta `PrescricaoDigital` (Memed etc.) definida, com implementação nula por enquanto.
