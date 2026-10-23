# ADR-0010: Armazenamento de objetos S3-compatível (substituto do MinIO)

- Status: **Aceito** · Data: 2026-10-22

## Contexto
O prompt original pedia MinIO. Porém o MinIO Community Edition parou de publicar imagens Docker em
out/2025, entrou em modo de manutenção em dez/2025 e teve o repositório arquivado em 2026. Não há
caminho de atualização com correções de segurança.

## Decisão
- **Local (perfis demo / demo-leve / testes):** SeaweedFS em container, com a API S3 habilitada.
- **Produção (VPS):** S3 gerenciado: **Backblaze B2 ou Cloudflare R2** (a escolha final fica para a
  fase de produção). Assim não há container de armazenamento no VPS, e o armazenamento já fica fora
  da Hostinger.
- **Criptografia na aplicação antes do envio**: todo arquivo é cifrado com AES-256-GCM (envelope: DEK
  aleatória por arquivo, cifrada pela KEK da aplicação, conforme ADR-0006) antes do `PutObject`. O
  provedor só recebe bytes cifrados, e a chave nunca sai da aplicação. O nome do objeto é aleatório,
  e o nome original fica cifrado no banco.
- A aplicação depende só da API S3 (AWS SDK v2). Endpoint, região, bucket e credenciais vêm de
  variáveis de ambiente, então trocar entre SeaweedFS, B2 e R2 é só configuração.
- Integridade: sha256 do arquivo em claro guardado no banco e conferido no download.

## Consequências
+ Menos RAM e menos operação no VPS. A durabilidade fica com o provedor.
+ Um vazamento do bucket não expõe conteúdo.
− Perder a KEK torna os anexos ilegíveis. A KEK precisa de backup próprio, separado dos dados
  (coberto no runbook de produção).
− Backup externo: em produção, replicar para um segundo bucket/provedor ou usar versionamento +
  object lock (definir na fase de produção).

## Alternativas descartadas
Garage (AGPL, menos ferramentas), fork comunitário do MinIO (manutenção de terceiro) e SeaweedFS
também em produção (consome RAM do VPS e exige backup próprio).
