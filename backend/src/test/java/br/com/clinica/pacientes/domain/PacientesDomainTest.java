package br.com.clinica.pacientes.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.Telefone;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PacientesDomainTest {

    static final ClinicaId CLINICA = new ClinicaId(UUID.randomUUID());
    static final Instant AGORA = Instant.parse("2026-10-22T12:00:00Z");

    static Paciente.DadosPessoais dados(String cpf) {
        return new Paciente.DadosPessoais("João da Silva", Optional.of(" "), LocalDate.of(1980, 5, 10), Cpf.de(cpf),
                Optional.empty(), Paciente.Canal.WHATSAPP);
    }

    @Test
    void contatoNormalizaMascaraEVerifica() {
        Contato celular = new Contato(Contato.Tipo.CELULAR, "(69) 99999-1234", true, null);
        assertThat(celular.valor()).isEqualTo("+5569999991234");
        assertThat(celular.mascarado()).isEqualTo("(69) *****-1234");
        assertThat(celular.verificado()).isFalse();
        assertThat(celular.verificadoEm(AGORA).verificado()).isTrue();
        Contato email = Contato.email(new Email("A@B.com"), false, Optional.empty());
        assertThat(email.mascarado()).isEqualTo("a***@b.com");
        assertThat(celular.mesmoMeio(Contato.celular(Telefone.de("69999991234"), false))).isTrue();
        assertThat(celular.mesmoMeio(email)).isFalse();
    }

    @Test
    void pacienteGerenciaContatosSemDuplicar() {
        Contato celular = Contato.celular(Telefone.de("69999991234"), true);
        Paciente p = Paciente.novo(CLINICA, dados("52998224725"), Paciente.Origem.RECEPCAO, Optional.empty(),
                List.of(celular));
        assertThat(p.comContato(celular)).isSameAs(p);
        assertThat(p.possuiContato(celular)).isTrue();
        assertThat(p.celularVerificado()).isEmpty();
        Paciente verificado = p.comContatoVerificado(celular, AGORA);
        assertThat(verificado.celularVerificado()).isPresent();
        assertThat(p.comContato(Contato.email(new Email("j@x.com"), false, Optional.empty())).contatos()).hasSize(2);
        assertThatThrownBy(() -> new Paciente(UUID.randomUUID(), CLINICA, Optional.empty(), dados("52998224725"),
                Paciente.Origem.PORTAL, Collections.nCopies(7, celular), 0)).isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void cpfImutavelEVinculoUnico() {
        Paciente p = Paciente.novo(CLINICA, dados("52998224725"), Paciente.Origem.RECEPCAO, Optional.empty(), List.of());
        assertThat(p.comDados(dados("52998224725")).dados().nomeSocial()).isEmpty();
        assertThatThrownBy(() -> p.comDados(dados("39053344705"))).isInstanceOf(ConflitoException.class);
        Paciente vinculado = p.vinculadoA("sub-1");
        assertThat(vinculado.usuarioSub()).contains("sub-1");
        assertThat(vinculado.vinculadoA("sub-1").usuarioSub()).contains("sub-1");
        assertThatThrownBy(() -> vinculado.vinculadoA("sub-2")).isInstanceOf(ConflitoException.class);
    }

    @Test
    void dadosPessoaisValidamNascimentoENomeSocial() {
        var comSocial = new Paciente.DadosPessoais("Carlos Souza", Optional.of("Carla"), LocalDate.of(1990, 1, 1),
                Cpf.de("52998224725"), Optional.of("F"), Paciente.Canal.EMAIL);
        assertThat(comSocial.nomeExibicao()).isEqualTo("Carla");
        assertThat(dados("52998224725").nomeExibicao()).isEqualTo("João da Silva");
        assertThatThrownBy(() -> new Paciente.DadosPessoais("X", Optional.empty(), LocalDate.now().plusDays(1),
                Cpf.de("52998224725"), Optional.empty(), Paciente.Canal.SMS)).isInstanceOf(DadoInvalidoException.class);
        assertThatThrownBy(() -> new Paciente.DadosPessoais(" ", Optional.empty(), LocalDate.of(1990, 1, 1),
                Cpf.de("52998224725"), Optional.empty(), Paciente.Canal.SMS)).isInstanceOf(DadoInvalidoException.class);
    }

    @Test
    void consentimentoECodigoDeVerificacao() {
        var c = new Consentimento(UUID.randomUUID(), UUID.randomUUID(), Consentimento.Finalidade.LEMBRETES_WHATSAPP,
                "2026-10", AGORA, null);
        assertThat(c.vigente()).isTrue();
        var codigo = new CodigoVerificacao(UUID.randomUUID(), UUID.randomUUID(),
                Contato.celular(Telefone.de("69999991234"), true), 0, AGORA.plus(Duration.ofMinutes(10)), null);
        assertThat(codigo.utilizavel(AGORA)).isTrue();
        assertThat(codigo.utilizavel(AGORA.plus(Duration.ofMinutes(11)))).isFalse();
        var esgotado = new CodigoVerificacao(codigo.id(), codigo.pacienteId(), codigo.destino(),
                CodigoVerificacao.MAXIMO_TENTATIVAS, codigo.expiraEm(), Optional.empty());
        assertThat(esgotado.utilizavel(AGORA)).isFalse();
    }
}
