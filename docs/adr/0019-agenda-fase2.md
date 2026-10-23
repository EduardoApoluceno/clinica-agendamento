# ADR-0019: Decisões de implementação da agenda (Fase 2)

- Status: **Aceito** · Data: 2026-10-23 · Complementa os ADRs 0004 e 0011

## Contexto
A Fase 2 entrega disponibilidade, agendamento, remarcação, cancelamento, a grade da recepção e o portal
do paciente. Algumas escolhas não estavam fixadas na proposta da Fase 0.

## Decisão
1. **Horários oferecidos.** Cada faixa do expediente gera horários a partir do seu início, em passos da
   duração do serviço (consulta de 30 min: 08:00, 08:30…). Ausências removem o horário. Agendamentos
   que ocupam o horário o deixam visível como indisponível (riscado no portal). A recepção pode marcar em
   qualquer início dentro do expediente (o período inteiro precisa caber numa faixa e não tocar ausência).
2. **Prazos.** O paciente respeita a antecedência mínima para marcar, o horizonte e a antecedência mínima
   para remarcar ou cancelar (configurados em Configurações). A equipe não está sujeita a eles, mas
   ninguém marca no passado.
3. **Quem faz o quê.** Novas permissões: `agenda:ler` e `agenda:escrever` (ADMIN, RECEPCAO) e
   `agendamento-proprio:gerir` (PACIENTE). O PROFISSIONAL usa `agenda-propria:gerir`: vê só a própria
   coluna da grade e registra presença, falta e conclusão dos próprios atendimentos; remarcar e cancelar
   ficam com a recepção. Quem não pode ver um agendamento recebe 404 (IDOR).
4. **Paciente agenda sozinho** só com cadastro completo e celular verificado (vínculo exigido pela
   especificação para o WhatsApp). Sem isso a API responde 422 com o código do problema, e o portal
   leva a "Meu cadastro".
5. **Hold de pagamento.** A máquina de estados, a constraint, o job de expiração (a cada minuto, com
   `FOR UPDATE SKIP LOCKED`) e a liberação de holds vencidos na hora de marcar estão implementados e
   testados. A cobrança antecipada fica **desligada** (`app.agenda.cobranca-antecipada=false`) até a
   Fase 4: sem o módulo de pagamentos, o horário expiraria sem que o paciente conseguisse pagar.
6. **Tempo real (SSE).** `GET /api/v1/publico/disponibilidade/stream` envia só o evento
   `agenda-alterada` com o id do profissional, depois do commit. Não envia dados de agendamento: a tela
   recarrega pela API normal, que aplica as regras de acesso. O servidor limita a 300 conexões e encerra
   cada uma após 5 minutos (o `EventSource` reconecta). O Caddy não comprime `*/stream` (o encoder
   seguraria os eventos no buffer).
7. **Grade da recepção** (ADR-0011): CSS Grid próprio. Cada coluna é uma lista (`ul`/`li` com botões)
   em ordem de horário, em vez de `role="grid"` com navegação por setas. Tab percorre os itens na ordem
   em que aparecem, e cada botão tem o rótulo completo (horário, paciente, serviço, status). Horários
   livres aparecem em blocos de 30 min.
8. **Módulos.** A agenda consome APIs públicas novas: `profissionais.CatalogoAgenda`,
   `pacientes.ConsultaPacientes` (só nome e vínculo; CPF e contatos não saem do módulo) e
   `clinica.RegrasAgenda`. Os eventos ficam em `agenda.EventosAgenda`, sem dados pessoais.
9. **Semente de demonstração.** Nos perfis `demo` e `demo-leve`, cada módulo cria os próprios dados
   fictícios (serviços e profissionais; pacientes; agendamentos de 14 dias para trás e para frente), de
   forma idempotente. Os usuários do realm de demonstração têm ids fixos, para a semente vincular
   profissionais e pacientes às contas de teste.

## Consequências
- Quem já tinha o perfil `demo` de pé precisa recriar o banco uma vez (`docker compose --profile demo down -v`):
  o realm só é importado na primeira subida.
- Ao ligar a cobrança antecipada na Fase 4, basta mudar a configuração: o domínio já cria `RESERVADO`
  com prazo e o job já expira.
- O teste de integração com 20 requisições simultâneas no mesmo horário garante exatamente um 201 e
  dezenove 409 com PostgreSQL real.

## Alternativas consideradas
- Enviar o agendamento pelo SSE: descartado (dados pessoais por um canal público e regras de acesso
  duplicadas).
- `role="grid"` com navegação por setas na grade: mais código e mais pontos de falha de acessibilidade.
  A lista em ordem de horário atende a WCAG AA com menos complexidade. Dá para rever se a recepção pedir
  atalhos de teclado.
