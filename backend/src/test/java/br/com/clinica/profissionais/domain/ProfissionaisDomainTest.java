package br.com.clinica.profissionais.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Periodo;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfissionaisDomainTest {

    private static final ClinicaId CLINICA = new ClinicaId(UUID.randomUUID());

    static Profissional.DadosProfissional dados(String sub) {
        return new Profissional.DadosProfissional("Dra. Ana", new RegistroConselho("crm", "12.345", "ro"),
                Optional.ofNullable(sub), Set.of(), Set.of());
    }

    static TipoServico.DadosServico servico(int minutos, long preco, PoliticaPagamento politica) {
        return new TipoServico.DadosServico("Consulta", Optional.of(" "), Optional.empty(), Duration.ofMinutes(minutos),
                new Dinheiro(preco), politica);
    }

    @Test
    void registroNormalizaERecusaInvalidos() {
        var r = new RegistroConselho(" crm ", "12.345", "ro");
        assertThat(r).hasToString("CRM 12345/RO");
        assertThatThrownBy(() -> new RegistroConselho("XYZ", "1", "RO")).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new RegistroConselho("CRM", "", "RO")).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new RegistroConselho("CRM", "1", "XX")).isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void profissionalNovoAtualizaDesativaEReconheceDono() {
        Profissional p = Profissional.novo(CLINICA, dados("sub-1"));
        assertThat(p.ativo()).isTrue();
        assertThat(p.pertenceA("sub-1")).isTrue();
        assertThat(p.pertenceA("sub-2")).isFalse();
        assertThat(p.comAtivo(false).ativo()).isFalse();
        assertThat(p.atualizado(dados(null)).usuarioSub()).isEmpty();
        assertThatThrownBy(() -> Profissional.novo(CLINICA, new Profissional.DadosProfissional(" ",
                new RegistroConselho("CRM", "1", "RO"), Optional.empty(), Set.of(), Set.of())))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void politicaDePagamentoCalculaValorAntecipado() {
        Dinheiro preco = new Dinheiro(25_000);
        assertThat(PoliticaPagamento.nenhuma().valorAntecipado(preco)).isEqualTo(Dinheiro.ZERO);
        assertThat(new PoliticaPagamento(PoliticaPagamento.Tipo.INTEGRAL, Optional.empty()).valorAntecipado(preco))
                .isEqualTo(preco);
        assertThat(new PoliticaPagamento(PoliticaPagamento.Tipo.SINAL, Optional.of(new Dinheiro(5_000)))
                .valorAntecipado(preco)).isEqualTo(new Dinheiro(5_000));
        assertThatThrownBy(() -> new PoliticaPagamento(PoliticaPagamento.Tipo.SINAL, Optional.empty()))
                .isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new PoliticaPagamento(PoliticaPagamento.Tipo.SINAL, Optional.of(Dinheiro.ZERO)))
                .isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new PoliticaPagamento(PoliticaPagamento.Tipo.NENHUM, Optional.of(new Dinheiro(1))))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void tipoServicoValidaDuracaoESinal() {
        TipoServico s = TipoServico.novo(CLINICA, servico(30, 25_000, PoliticaPagamento.nenhuma()));
        assertThat(s.descricao()).isEmpty();
        assertThat(s.atualizado(servico(45, 30_000, PoliticaPagamento.nenhuma()), false).ativo()).isFalse();
        assertThatThrownBy(() -> TipoServico.novo(CLINICA, servico(32, 1, PoliticaPagamento.nenhuma())))
                .isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> TipoServico.novo(CLINICA, servico(600, 1, PoliticaPagamento.nenhuma())))
                .isInstanceOf(DadoInvalidoException.class);
        var sinalAlto = new PoliticaPagamento(PoliticaPagamento.Tipo.SINAL, Optional.of(new Dinheiro(9_999)));
        assertThatThrownBy(() -> TipoServico.novo(CLINICA, servico(30, 1_000, sinalAlto)))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void expedienteOrdenaAgrupaERecusaSobreposicao() {
        var tarde = new Expediente.Faixa(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(18, 0));
        var manha = new Expediente.Faixa(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(12, 0));
        var expediente = new Expediente(List.of(tarde, manha));
        assertThat(expediente.faixas()).containsExactly(manha, tarde);
        assertThat(expediente.porDia().get(DayOfWeek.MONDAY)).hasSize(2);
        var sobreposta = new Expediente.Faixa(DayOfWeek.MONDAY, LocalTime.of(11, 0), LocalTime.of(13, 0));
        assertThatThrownBy(() -> new Expediente(List.of(manha, sobreposta))).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new Expediente.Faixa(DayOfWeek.MONDAY, LocalTime.NOON, LocalTime.NOON))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void ausenciaLimitaDuracaoENormalizaMotivo() {
        Instant inicio = Instant.parse("2026-12-20T04:00:00Z");
        var ausencia = Ausencia.nova(UUID.randomUUID(), Ausencia.Tipo.FERIAS,
                new Periodo(inicio, inicio.plus(Duration.ofDays(10))), Optional.of("  "));
        assertThat(ausencia.motivo()).isEmpty();
        assertThatThrownBy(() -> Ausencia.nova(UUID.randomUUID(), Ausencia.Tipo.BLOQUEIO,
                new Periodo(inicio, inicio.plus(Duration.ofDays(200))), Optional.empty()))
                .isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void conflitoCarregaCategoria() {
        assertThat(new ConflitoException("a", "b").codigo()).isEqualTo("a");
    }
}
