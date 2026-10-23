package br.com.clinica.clinica.infrastructure;

import br.com.clinica.clinica.RegrasAgenda;
import br.com.clinica.clinica.application.ClinicaRepositorio;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
class RegrasAgendaAdaptador implements RegrasAgenda {

    private final ClinicaRepositorio repositorio;
    private final ContextoClinica contexto;

    RegrasAgendaAdaptador(ClinicaRepositorio repositorio, ContextoClinica contexto) {
        this.repositorio = repositorio;
        this.contexto = contexto;
    }

    @Override
    public Parametros parametros() {
        Clinica clinica = repositorio.buscar(contexto.atual())
                .orElseThrow(() -> new RecursoNaoEncontradoException("clinica"));
        ConfiguracaoAgenda c = repositorio.configuracao(contexto.atual()).configuracao();
        return new Parametros(clinica.fusoHorario(), c.antecedenciaMinimaAgendamento(),
                c.antecedenciaMinimaCancelamento(), c.horizonteAgendamento(),
                Duration.ofMinutes(c.minutosHoldPagamento()));
    }
}
