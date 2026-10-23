package br.com.clinica.agenda.application;

import br.com.clinica.agenda.application.AgendaPortas.AgendamentoRepositorio;
import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.FaixaTrabalho;
import br.com.clinica.agenda.domain.CalculadoraDisponibilidade.Horario;
import br.com.clinica.agenda.domain.PoliticaAntecedencia;
import br.com.clinica.clinica.RegrasAgenda.Parametros;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.compartilhado.domain.Permissao;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.AusenciaAgenda;
import br.com.clinica.profissionais.CatalogoAgenda.ServicoAgendavel;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Horários de um profissional para um serviço (público: só horários, nenhum dado de paciente). Para
 * o paciente valem a antecedência mínima e o horizonte; a recepção vê a partir de agora, até 1 ano.
 */
public class ConsultarDisponibilidade {

    private static final Duration HORIZONTE_EQUIPE = Duration.ofDays(365);

    private final AgendamentoRepositorio repositorio;
    private final CatalogoAgenda catalogo;
    private final ProvedorAtor provedorAtor;
    private final OperacoesAgenda operacoes;

    public ConsultarDisponibilidade(AgendamentoRepositorio repositorio, CatalogoAgenda catalogo,
                                    ProvedorAtor provedorAtor, OperacoesAgenda operacoes) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.provedorAtor = provedorAtor;
        this.operacoes = operacoes;
    }

    @Transactional(readOnly = true)
    public List<Horario> horarios(UUID servicoId, UUID profissionalId, LocalDate de,
                                  LocalDate ate) {
        ServicoAgendavel servico = catalogo.servico(servicoId).filter(ServicoAgendavel::ativo)
                .orElseThrow(() -> new RecursoNaoEncontradoException("servico"));
        catalogo.profissional(profissionalId).filter(p -> p.atende(servicoId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional"));
        Parametros parametros = operacoes.parametros();
        Instant agora = operacoes.agora();
        Periodo janela = new Periodo(de.atStartOfDay(parametros.fuso()).toInstant(),
                ate.plusDays(1).atStartOfDay(parametros.fuso()).toInstant());

        boolean equipe = provedorAtor.atual().tem(Permissao.AGENDA_ESCREVER);
        PoliticaAntecedencia politica = AgendarConsulta.politica(parametros);
        Instant naoAntesDe = equipe ? agora : politica.inicioPermitido(agora);
        Instant naoDepoisDe = equipe ? agora.plus(HORIZONTE_EQUIPE) : politica.fimPermitido(agora);

        List<Periodo> ocupados = repositorio.ocupando(operacoes.clinica(), profissionalId, janela).stream()
                .filter(a -> !a.reservaVencida(agora)).map(Agendamento::periodo).toList();
        return CalculadoraDisponibilidade.horarios(new CalculadoraDisponibilidade.Pedido(de, ate, parametros.fuso(),
                faixas(profissionalId), catalogo.ausencias(profissionalId, janela).stream()
                        .map(AusenciaAgenda::periodo).toList(),
                ocupados, servico.duracao(), naoAntesDe, naoDepoisDe));
    }

    private List<FaixaTrabalho> faixas(UUID profissional) {
        return catalogo.expediente(profissional).stream()
                .map(f -> new FaixaTrabalho(f.dia(), f.inicio(), f.fim())).toList();
    }
}
