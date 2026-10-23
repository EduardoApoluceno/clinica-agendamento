package br.com.clinica.agenda.application;

import br.com.clinica.agenda.EventosAgenda.VagaLiberada;
import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/** Job (a cada minuto): holds de pagamento vencidos viram EXPIRADO e o horário volta a ficar livre. */
public class ExpirarReservas {

    private static final int LOTE = 200;

    private final AgendamentoRepositorio repositorio;
    private final OperacoesAgenda operacoes;

    public ExpirarReservas(AgendamentoRepositorio repositorio, OperacoesAgenda operacoes) {
        this.repositorio = repositorio;
        this.operacoes = operacoes;
    }

    /** @return quantas reservas expiraram */
    @Transactional
    public int executar() {
        Instant agora = operacoes.agora();
        int expiradas = 0;
        for (Agendamento reserva : repositorio.reservasVencidas(agora, LOTE)) {
            operacoes.expirar(reserva, agora);
            if (reserva.periodo().inicio().isAfter(agora)) {
                operacoes.publicar(new VagaLiberada(reserva.profissionalId(), reserva.servicoId(),
                        reserva.periodo().inicio(), reserva.periodo().fim()));
            }
            expiradas++;
        }
        return expiradas;
    }
}
