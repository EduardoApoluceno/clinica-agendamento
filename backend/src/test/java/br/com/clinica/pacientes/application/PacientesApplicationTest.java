package br.com.clinica.pacientes.application;

import br.com.clinica.Fixtures;
import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.notificacoes.EnvioMensagens;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoComHash;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoVerificacaoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ConsentimentoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ProtetorCodigos;
import br.com.clinica.pacientes.domain.CodigoVerificacao;
import br.com.clinica.pacientes.domain.Consentimento;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PacientesApplicationTest {

    private final PacienteRepositorio pacientes = mock(PacienteRepositorio.class);
    private final ConsentimentoRepositorio consentimentos = mock(ConsentimentoRepositorio.class);
    private final RegistradorAuditoria auditoria = mock(RegistradorAuditoria.class);

    private static Paciente.DadosPessoais dados(String cpf) {
        return new Paciente.DadosPessoais("João da Silva", Optional.empty(), LocalDate.of(1980, 5, 10), Cpf.de(cpf),
                Optional.empty(), Paciente.Canal.WHATSAPP);
    }

    private final Contato celularJoao = Contato.celular(Telefone.de("69999991234"), true);
    private final Paciente joaoDaRecepcao = Paciente.novo(Fixtures.CLINICA, dados("52998224725"),
            Paciente.Origem.RECEPCAO, Optional.empty(), List.of(celularJoao));

    @Nested
    class GerirPacientesTest {
        private final GerirPacientes caso = new GerirPacientes(pacientes, Fixtures.CONTEXTO, auditoria);

        @Test
        void cadastraRecusandoCpfDuplicado() {
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            assertThat(caso.cadastrar(dados("52998224725"), List.of()).origem()).isEqualTo(Paciente.Origem.RECEPCAO);
            when(pacientes.buscarPorCpf(eq(Fixtures.CLINICA), any())).thenReturn(Optional.of(joaoDaRecepcao));
            assertThatThrownBy(() -> caso.cadastrar(dados("52998224725"), List.of())).isInstanceOf(ConflitoException.class);
        }

        @Test
        void leituraDaFichaEhAuditada() {
            when(pacientes.buscar(Fixtures.CLINICA, joaoDaRecepcao.id())).thenReturn(Optional.of(joaoDaRecepcao));
            caso.obter(joaoDaRecepcao.id());
            verify(auditoria).registrar(any(EventoAuditoria.class));
            assertThatThrownBy(() -> caso.obter(UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void atualizaAdicionandoContatosComControleDeVersao() {
            when(pacientes.buscar(Fixtures.CLINICA, joaoDaRecepcao.id())).thenReturn(Optional.of(joaoDaRecepcao));
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            Contato email = Contato.email(new Email("joao@x.com"), false, Optional.empty());
            assertThat(caso.atualizar(joaoDaRecepcao.id(), dados("52998224725"), List.of(email), 0).contatos())
                    .hasSize(2);
            assertThatThrownBy(() -> caso.atualizar(joaoDaRecepcao.id(), dados("52998224725"), List.of(), 3))
                    .isInstanceOf(ConflitoException.class);
            assertThatThrownBy(() -> caso.atualizar(UUID.randomUUID(), dados("52998224725"), List.of(), 0))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void naoTrocaCpfParaOutroJaCadastrado() {
            Paciente outro = Paciente.novo(Fixtures.CLINICA, dados("39053344705"), Paciente.Origem.RECEPCAO,
                    Optional.empty(), List.of());
            when(pacientes.buscar(Fixtures.CLINICA, joaoDaRecepcao.id())).thenReturn(Optional.of(joaoDaRecepcao));
            when(pacientes.buscarPorCpf(eq(Fixtures.CLINICA), eq(Cpf.de("39053344705")))).thenReturn(Optional.of(outro));
            when(pacientes.buscarPorCpf(eq(Fixtures.CLINICA), eq(Cpf.de("52998224725"))))
                    .thenReturn(Optional.of(joaoDaRecepcao));
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));

            assertThatThrownBy(() -> caso.atualizar(joaoDaRecepcao.id(), dados("39053344705"), List.of(), 0))
                    .isInstanceOf(ConflitoException.class);
            assertThat(caso.atualizar(joaoDaRecepcao.id(), dados("52998224725"), List.of(), 0)).isNotNull();
        }

        @Test
        void buscaCurtaNaoConsultaRepositorio() {
            assertThat(caso.pesquisar("jo", new Pagina.Pedido(0, 10)).itens()).isEmpty();
            verify(pacientes, never()).pesquisar(any(), anyString(), any());
            caso.pesquisar("joão", new Pagina.Pedido(0, 10));
            verify(pacientes).pesquisar(Fixtures.CLINICA, "joão", new Pagina.Pedido(0, 10));
        }
    }

    @Nested
    class CadastroProprioTest {
        private final CadastroProprio caso = new CadastroProprio(pacientes, consentimentos, Fixtures.CONTEXTO,
                Fixtures.ator("conta-carla", Papel.PACIENTE), auditoria, Fixtures.RELOGIO);

        @Test
        void criaCadastroNovoComEmailVerificadoDaConta() {
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            Paciente criado = caso.completar(dados("39053344705"), Telefone.de("69988880000"),
                    Optional.of(new Email("carla@x.com")));
            assertThat(criado.usuarioSub()).contains("conta-carla");
            assertThat(criado.origem()).isEqualTo(Paciente.Origem.PORTAL);
            assertThat(criado.contatos()).hasSize(2).anyMatch(c -> c.tipo() == Contato.Tipo.EMAIL && c.verificado());
        }

        @Test
        void naoSeApropriaDeCadastroAlheioSabendoSoOCpf() {
            when(pacientes.buscarPorCpf(eq(Fixtures.CLINICA), any())).thenReturn(Optional.of(joaoDaRecepcao));
            assertThatThrownBy(() -> caso.completar(dados("52998224725"), Telefone.de("69988880000"), Optional.empty()))
                    .isInstanceOf(ConflitoException.class);
            verify(pacientes, never()).salvar(any());
        }

        @Test
        void vinculaCadastroDaRecepcaoQuandoComprovaOContato() {
            when(pacientes.buscarPorCpf(eq(Fixtures.CLINICA), any())).thenReturn(Optional.of(joaoDaRecepcao));
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            Paciente vinculado = caso.completar(dados("52998224725"), Telefone.de("69999991234"), Optional.empty());
            assertThat(vinculado.id()).isEqualTo(joaoDaRecepcao.id());
            assertThat(vinculado.usuarioSub()).contains("conta-carla");
        }

        @Test
        void naoCompletaDuasVezesEAtualizaComVersao() {
            Paciente meu = joaoDaRecepcao.vinculadoA("conta-carla");
            when(pacientes.buscarPorUsuario(Fixtures.CLINICA, "conta-carla")).thenReturn(Optional.of(meu));
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            assertThatThrownBy(() -> caso.completar(dados("52998224725"), Telefone.de("69999991234"), Optional.empty()))
                    .isInstanceOf(ConflitoException.class);
            assertThat(caso.atualizar(dados("52998224725"), 0)).isNotNull();
            assertThatThrownBy(() -> caso.atualizar(dados("52998224725"), 7)).isInstanceOf(ConflitoException.class);
        }

        @Test
        void titularNaoTrocaOProprioCpf() {
            Paciente meu = joaoDaRecepcao.vinculadoA("conta-carla");
            when(pacientes.buscarPorUsuario(Fixtures.CLINICA, "conta-carla")).thenReturn(Optional.of(meu));
            assertThatThrownBy(() -> caso.atualizar(dados("39053344705"), 0))
                    .isInstanceOf(ConflitoException.class).hasMessageContaining("recepção");
            verify(pacientes, never()).salvar(any());
        }

        @Test
        void exportaDadosDoTitularEAudita() {
            Paciente meu = joaoDaRecepcao.vinculadoA("conta-carla");
            when(pacientes.buscarPorUsuario(Fixtures.CLINICA, "conta-carla")).thenReturn(Optional.of(meu));
            when(consentimentos.listar(Fixtures.CLINICA, meu.id())).thenReturn(List.of());
            CadastroProprio.ExportacaoDados exportacao = caso.exportar();
            assertThat(exportacao.paciente()).isEqualTo(meu);
            assertThat(exportacao.geradoEm()).isEqualTo(Fixtures.AGORA.toString());
            verify(auditoria).registrar(any(EventoAuditoria.class));
        }

        @Test
        void semCadastroNaoAtualizaNemExporta() {
            assertThat(caso.meu()).isEmpty();
            assertThatThrownBy(() -> caso.atualizar(dados("52998224725"), 0)).isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(caso::exportar).isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }

    @Nested
    class GerirConsentimentosTest {
        private final GerirConsentimentos caso = new GerirConsentimentos(pacientes, consentimentos, Fixtures.CONTEXTO,
                Fixtures.ator("conta-joao", Papel.PACIENTE), auditoria, Fixtures.RELOGIO);
        private final Paciente meu = joaoDaRecepcao.vinculadoA("conta-joao");

        @Test
        void concederSubstituiVigenteERevogarAudita() {
            when(pacientes.buscarPorUsuario(Fixtures.CLINICA, "conta-joao")).thenReturn(Optional.of(meu));
            caso.conceder(Consentimento.Finalidade.LEMBRETES_WHATSAPP, "2026-10");
            verify(consentimentos).revogar(Fixtures.CLINICA, meu.id(), Consentimento.Finalidade.LEMBRETES_WHATSAPP,
                    Fixtures.AGORA);
            verify(consentimentos).conceder(eq(Fixtures.CLINICA), any(), eq("PORTAL"), eq(Optional.of("10.0.0.1")));
            when(consentimentos.revogar(any(), any(), any(), any())).thenReturn(true);
            caso.revogar(Consentimento.Finalidade.LEMBRETES_WHATSAPP);
            assertThat(caso.meus()).isEmpty();
            assertThat(caso.doPaciente(meu.id())).isEmpty();
        }

        @Test
        void exigeCadastro() {
            assertThatThrownBy(() -> caso.revogar(Consentimento.Finalidade.LEMBRETES_SMS))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }

    @Nested
    class VerificarTelefoneTest {
        private final CodigoVerificacaoRepositorio codigos = mock(CodigoVerificacaoRepositorio.class);
        private final ProtetorCodigos protetor = mock(ProtetorCodigos.class);
        private final EnvioMensagens envio = mock(EnvioMensagens.class);
        private final Paciente meu = joaoDaRecepcao.vinculadoA("conta-joao");
        private final Telefone novoNumero = Telefone.de("69977776666");

        private VerificarTelefone caso(boolean demo) {
            when(pacientes.buscarPorUsuario(Fixtures.CLINICA, "conta-joao")).thenReturn(Optional.of(meu));
            return new VerificarTelefone(pacientes, codigos, protetor, envio, Fixtures.CONTEXTO,
                    Fixtures.ator("conta-joao", Papel.PACIENTE), auditoria, Fixtures.RELOGIO, demo);
        }

        private CodigoComHash pendente(int tentativas, Duration expiraEm) {
            return new CodigoComHash(new CodigoVerificacao(UUID.randomUUID(), meu.id(), Contato.celular(novoNumero, true),
                    tentativas, Fixtures.AGORA.plus(expiraEm), Optional.empty()), new byte[] {1});
        }

        @Test
        void solicitaEnviaEExpoeCodigoSoNoDemo() {
            when(protetor.gerar()).thenReturn("123456");
            var resultado = caso(true).solicitar(novoNumero);
            verify(envio).enviarCodigoVerificacao(novoNumero, "123456");
            assertThat(resultado.codigoDemo()).contains("123456");
            assertThat(resultado.destinoMascarado()).isEqualTo("(69) *****-6666");
            assertThat(caso(false).solicitar(novoNumero).codigoDemo()).isEmpty();
        }

        @Test
        void limitaEnviosPorHora() {
            when(codigos.enviosDesde(any(), any(), any())).thenReturn(CodigoVerificacao.MAXIMO_ENVIOS_POR_HORA);
            assertThatThrownBy(() -> caso(false).solicitar(novoNumero)).isInstanceOf(ConflitoException.class);
            verify(envio, never()).enviarCodigoVerificacao(any(), anyString());
        }

        @Test
        void recusaNumeroJaVerificadoPorOutroPaciente() {
            Paciente outro = Paciente.novo(Fixtures.CLINICA, dados("39053344705"), Paciente.Origem.PORTAL,
                    Optional.of("outra"), List.of());
            when(pacientes.buscarPorContatoVerificado(eq(Fixtures.CLINICA), any())).thenReturn(Optional.of(outro));
            assertThatThrownBy(() -> caso(false).solicitar(novoNumero)).isInstanceOf(ConflitoException.class);
        }

        @Test
        void confirmaCodigoCorretoEVerificaContato() {
            var desafio = pendente(0, Duration.ofMinutes(5));
            when(codigos.ultimoPendente(Fixtures.CLINICA, meu.id())).thenReturn(Optional.of(desafio));
            when(protetor.confere("123456", desafio.hash())).thenReturn(true);
            when(pacientes.salvar(any())).thenAnswer(i -> i.getArgument(0));
            Paciente verificado = caso(false).confirmar(" 123456 ");
            assertThat(verificado.celularVerificado()).map(Contato::valor).contains(novoNumero.e164());
            verify(codigos).marcarUsado(desafio.codigo().id(), Fixtures.AGORA);
        }

        @Test
        void codigoErradoContaTentativa() {
            var desafio = pendente(0, Duration.ofMinutes(5));
            when(codigos.ultimoPendente(Fixtures.CLINICA, meu.id())).thenReturn(Optional.of(desafio));
            assertThatThrownBy(() -> caso(false).confirmar("000000")).isInstanceOf(DadoInvalidoException.class);
            verify(codigos).registrarTentativa(desafio.codigo().id());
            assertThatThrownBy(() -> caso(false).confirmar(null)).isInstanceOf(DadoInvalidoException.class);
        }

        @Test
        void codigoExpiradoOuEsgotadoNaoServe() {
            when(codigos.ultimoPendente(Fixtures.CLINICA, meu.id())).thenReturn(Optional.of(pendente(0, Duration.ofMinutes(-1))));
            assertThatThrownBy(() -> caso(false).confirmar("123456")).isInstanceOf(DadoInvalidoException.class)
                    .hasMessageContaining("expirado");
            when(codigos.ultimoPendente(Fixtures.CLINICA, meu.id()))
                    .thenReturn(Optional.of(pendente(CodigoVerificacao.MAXIMO_TENTATIVAS, Duration.ofMinutes(5))));
            assertThatThrownBy(() -> caso(false).confirmar("123456")).isInstanceOf(DadoInvalidoException.class);
        }
    }
}
