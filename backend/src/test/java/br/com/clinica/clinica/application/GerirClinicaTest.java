package br.com.clinica.clinica.application;

import br.com.clinica.Fixtures;
import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.application.ClinicaRepositorio.ConfiguracaoComVersao;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.Cnpj;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.clinica.domain.Endereco;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GerirClinicaTest {

    private final ClinicaRepositorio repositorio = mock(ClinicaRepositorio.class);
    private final RegistradorAuditoria auditoria = mock(RegistradorAuditoria.class);
    private final GerirClinica caso = new GerirClinica(repositorio, Fixtures.CONTEXTO, auditoria);

    private final Endereco endereco = new Endereco("Rua A", "1", Optional.empty(), "Centro", "Porto Velho", "RO",
            "76801000");
    private final Clinica clinica = new Clinica(Fixtures.CLINICA, "Vida", Cnpj.de("11222333000181"),
            ZoneId.of("America/Porto_Velho"), endereco, Optional.empty(), 2);

    @Test
    void obterFalhaQuandoNaoExiste() {
        when(repositorio.buscar(Fixtures.CLINICA)).thenReturn(Optional.empty());
        assertThatThrownBy(caso::obter).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void atualizaDadosComVersaoCorretaEAudita() {
        when(repositorio.buscar(Fixtures.CLINICA)).thenReturn(Optional.of(clinica));
        when(repositorio.atualizar(any())).thenAnswer(i -> i.getArgument(0));
        Clinica salva = caso.atualizarDados(new GerirClinica.DadosClinica("Nova", clinica.cnpj(), endereco,
                Optional.empty(), 2));
        assertThat(salva.nome()).isEqualTo("Nova");
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }

    @Test
    void recusaVersaoDesatualizada() {
        when(repositorio.buscar(Fixtures.CLINICA)).thenReturn(Optional.of(clinica));
        assertThatThrownBy(() -> caso.atualizarDados(new GerirClinica.DadosClinica("Nova", clinica.cnpj(), endereco,
                Optional.empty(), 1))).isInstanceOf(ConflitoException.class);
    }

    @Test
    void consultaEAtualizaConfiguracao() {
        var cv = new ConfiguracaoComVersao(ConfiguracaoAgenda.padrao(), 4);
        when(repositorio.configuracao(Fixtures.CLINICA)).thenReturn(cv);
        when(repositorio.atualizarConfiguracao(Fixtures.CLINICA, cv.configuracao(), 4)).thenReturn(cv);
        assertThat(caso.configuracao()).isEqualTo(cv);
        assertThat(caso.atualizarConfiguracao(cv.configuracao(), 4)).isEqualTo(cv);
        verify(auditoria).registrar(any(EventoAuditoria.class));
    }
}
