# ADR-0018: Hash encadeado da auditoria calculado no PostgreSQL

- Status: **Aceito** · Data: 2026-10-23 · Complementa o ADR-0005

## Contexto
O ADR-0005 definiu a trilha de auditoria append-only com hash encadeado: cada evento guarda o hash do
anterior, e qualquer alteração no meio da cadeia é detectável. Calcular o hash em Java tem dois riscos:
a serialização (datas, JSON dos detalhes, nulos) pode divergir entre a gravação e a verificação, e duas
transações concorrentes podem apontar para o mesmo "anterior".

## Decisão
- A função `fn_hash_auditoria` (migração V2) calcula `sha256(hash_anterior || campos)`, com os campos
  unidos pelo separador `chr(31)`, nulos como texto vazio, `ocorrido_em` em microssegundos desde a época
  e `detalhes` na forma canônica do `jsonb`.
- O `INSERT` chama a função lendo o último hash na mesma instrução. Antes, a transação pega
  `pg_advisory_xact_lock(hashtext('evento_auditoria'))`, o que serializa a cadeia: cada evento aponta para
  um anterior já confirmado.
- `ocorrido_em` não tem mais `DEFAULT now()`: a aplicação envia o instante (relógio injetável nos testes).
- A verificação diária (db-scheduler) recalcula a cadeia com a **mesma** função, por janela (`lag`),
  e aponta o primeiro registro corrompido.
- O role da aplicação não tem `UPDATE`, `DELETE` nem `TRUNCATE` em `evento_auditoria` (V1), e o trigger
  `trg_auditoria_imutavel` recusa alterações mesmo de outros roles.

## Consequências
+ Gravação e verificação usam a mesma definição: não há divergência de serialização entre elas.
+ A concorrência não quebra a cadeia.
− O advisory lock serializa as gravações de auditoria. No volume de uma clínica isso é desprezível; se virar
  gargalo, a saída é uma cadeia por clínica (lock por `clinica_id`).
− Mudar a fórmula exige uma nova função versionada; a antiga continua valendo para os registros já gravados.
− O hash detecta adulteração, mas não a impede: quem tiver superusuário no banco pode recalcular a cadeia.
  Mitigação futura: publicar o último hash do dia fora do banco (por exemplo, no backup ou no log externo).

## Alternativas consideradas
- **Hash em Java:** mais fácil de testar isolado, mas exige serialização canônica idêntica nos dois lados e
  um lock à parte.
- **Trigger `BEFORE INSERT`:** esconde a regra no banco e complica o envio do instante pela aplicação.
- **Log externo imutável (WORM/S3 object lock):** mais forte, porém é outra peça de infraestrutura; fica como
  evolução.
