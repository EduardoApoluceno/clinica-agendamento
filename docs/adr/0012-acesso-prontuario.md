# ADR-0012: Regra de acesso ao prontuário

- Status: **Aceito** · Data: 2026-10-22

## Contexto
O requisito diz "acesso restrito ao profissional responsável e a perfis autorizados". A clínica é
pequena e o atendimento é multiprofissional. O usuário escolheu acesso amplo entre profissionais.

## Decisão
| Ação | PROFISSIONAL (qualquer) | PROFISSIONAL autor | RECEPCAO | ADMIN | PACIENTE |
|---|---|---|---|---|---|
| Ler linha do tempo / registros finalizados | ✓ | ✓ | — | — | — |
| Ler rascunho | — | ✓ | — | — | — |
| Criar registro | ✓ | ✓ | — | — | — |
| Editar rascunho / finalizar | — | ✓ | — | — | — |
| Criar adendo | — | ✓ | — | — | — |
| Baixar documento emitido (atestado, receita, pedido de exame) | ✓ | ✓ | — | — | ✓ (próprio) |

- **Toda** leitura e escrita gera `evento_auditoria` na mesma transação, com ator, paciente, registro,
  IP e correlation-id.
- O ADMIN consulta a trilha de quem acessou o quê, mas não o conteúdo clínico.
- Mitigação do acesso amplo: o profissional que abre o prontuário de um paciente sem agendamento com
  ele nos últimos 12 meses precisa informar uma justificativa (texto curto), que é auditada e aparece
  como alerta no relatório de auditoria. Se o usuário preferir, isso pode ser desligado depois por
  configuração da clínica.

## Consequências
+ Simples e adequado ao atendimento multiprofissional.
− A superfície de acesso é maior. Compensamos com auditoria total e alerta para acesso sem vínculo.
