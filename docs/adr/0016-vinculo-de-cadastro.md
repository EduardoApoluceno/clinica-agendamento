# ADR-0016: Vínculo da conta do portal a um cadastro feito pela recepção

- Status: **Aceito** · Data: 2026-10-23

## Contexto
Um paciente pode existir antes de ter conta: a recepção cadastra quem chega pelo balcão ou por telefone.
Depois, a pessoa cria a conta no portal (Keycloak) e completa o cadastro informando o CPF. O CPF é único
por clínica, mas não é segredo: quem souber o CPF de outra pessoa não pode tomar o cadastro dela e ver
seus dados e agendamentos.

## Decisão
No primeiro acesso (`POST /api/v1/me/paciente`, `CadastroProprio.completar`):

1. Sem cadastro com aquele CPF → cria um novo, com origem `PORTAL`, ligado à conta (`usuario_sub`).
2. Já existe cadastro com o CPF → só vincula se a pessoa **comprovar um contato que já consta nele**:
   - o **e-mail verificado** da conta (`email_verified=true` no token do Keycloak); ou
   - o **celular** informado no formulário, igual a um celular do cadastro existente.
3. Se nenhum contato confere, ou se o cadastro já está ligado a outra conta, a resposta é
   `409 cpf-ja-cadastrado` com a orientação "Procure a recepção para vincular à sua conta". A mensagem é a
   mesma nos dois casos, para não revelar se o CPF existe e de quem é.
4. O celular entra sempre como contato **não verificado**; a confirmação por código (ADR-0015) vem depois.
5. No portal, o titular não edita o CPF: a tela bloqueia o campo e a correção passa pela recepção.

## Consequências
+ Saber o CPF não basta para assumir um cadastro: é preciso controlar o e-mail ou ter o celular cadastrado.
+ Quem foi atendido antes recupera o histórico sem pedir nada à recepção, no caso comum.
− Se a recepção cadastrou o paciente sem contato (ou com contato errado), o vínculo exige atendimento humano.
− Usar só o celular informado (sem código) aceita alguém que saiba o CPF **e** o celular da vítima. É um risco
  aceito: a conta fica com o celular não verificado, e o agendamento pelo WhatsApp exige a verificação.
  Endurecer depois: exigir o código do ADR-0015 antes de vincular.

## Alternativas consideradas
- **Vincular só pelo CPF:** é o comportamento ingênuo e permite apropriação do cadastro.
- **Sempre exigir a recepção:** seguro, mas joga trabalho manual em todo paciente antigo.
- **Data de nascimento como prova:** é dado fácil de achar; não serve como fator de posse.
