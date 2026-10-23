# ADR-0015: Códigos de verificação de celular no PostgreSQL (e não no Redis)

- Status: **Aceito** · Data: 2026-10-23

## Contexto
O paciente confirma o celular com um código de 6 dígitos antes de receber lembretes e de ter o número
ligado ao agendamento pelo WhatsApp. É preciso guardar o código pendente, contar as tentativas e limitar
os envios. O Redis já está na stack (sessões), e o padrão comum seria um valor com TTL lá.

## Decisão
Guardar os desafios na tabela `codigo_verificacao` (migração V5), no PostgreSQL:

- O código **não** é gravado em claro: guardamos `codigo_hmac` (HMAC com a chave da aplicação), e o destino
  cifrado (`destino_cifrado`) com `destino_hmac` para busca, como os demais contatos (ADR-0006).
- Regras em `CodigoVerificacao`: validade de 10 min, até 5 tentativas por código, até 3 envios por hora
  por paciente. A contagem de envios é uma consulta na própria tabela (`criado_em` na última hora).
- A confirmação roda na mesma transação que marca o contato como verificado e grava a auditoria
  (`TELEFONE_VERIFICADO`). Uma tentativa errada é persistida mesmo com o erro (`noRollbackFor`).
- Um número verificado só pode pertencer a um paciente (`telefone-em-uso`), checado no envio e na confirmação.
- No perfil demo, `app.demo.expor-codigos=true` devolve o código na resposta e a mensagem vai para a caixa
  de saída. O perfil `prod` recusa essa opção na subida.

## Consequências
+ Código, tentativa, contato verificado e auditoria são atômicos; não há estado dividido entre dois armazenamentos.
+ O histórico de envios fica consultável (suporte, abuso) e entra no backup do banco.
+ Nenhum segredo legível no banco: vazar a tabela não revela códigos válidos.
− Os registros vencidos não expiram sozinhos: falta uma limpeza periódica (db-scheduler), anotada para a Fase 3.
− Um pouco mais de escrita no PostgreSQL do que no Redis; irrelevante no volume de uma clínica.

## Alternativas consideradas
- **Redis com TTL:** expira sozinho, mas a confirmação deixaria de ser atômica com a gravação do contato e da
  auditoria, e o limite por hora exigiria uma segunda chave. O Redis também não entra no backup.
- **Código em claro com TTL curto:** mais simples, porém expõe códigos válidos a quem lê o banco ou os logs.
