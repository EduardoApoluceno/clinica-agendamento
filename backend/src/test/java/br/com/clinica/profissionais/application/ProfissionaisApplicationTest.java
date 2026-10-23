package br.com.clinica.profissionais.application;

import br.com.clinica.Fixtures;
import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.Especialidade;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.Ausencia;
import br.com.clinica.profissionais.domain.Expediente;
import br.com.clinica.profissionais.domain.PoliticaPagamento;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.RegistroConselho;
import br.com.clinica.profissionais.domain.TipoServico;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

class ProfissionaisApplicationTest {

    private final ProfissionalRepositorio profissionais = mock(ProfissionalRepositorio.class);
    private final TipoServicoRepositorio servicos = mock(TipoServicoRepositorio.class);
    private final EspecialidadeRepositorio especialidades = mock(EspecialidadeRepositorio.class);
    private final AgendaProfissionalRepositorio agenda = mock(AgendaProfissionalRepositorio.class);
    private final RegistradorAuditoria auditoria = mock(RegistradorAuditoria.class);

    private final Profissional anaDaConta = Profissional.novo(Fixtures.CLINICA, new Profissional.DadosProfissional(
            "Dra. Ana", new RegistroConselho("CRM", "123", "RO"), Optional.of("dra.ana"), Set.of(), Set.of()));

    private Profissional.DadosProfissional dados(Set<UUID> esp, Set<UUID> srv) {
        return new Profissional.DadosProfissional("Dra. Ana", new RegistroConselho("CRM", "123", "RO"),
                Optional.empty(), esp, srv);
    }

    @Nested
    class GerirProfissionaisTest {
        private final GerirProfissionais caso = new GerirProfissionais(profissionais, servicos, especialidades,
                Fixtures.CONTEXTO, Fixtures.ator("dra.ana", Papel.PROFISSIONAL), auditoria);

        @Test
        void cadastraValidandoReferenciasDaClinica() {
            when(especialidades.existemTodas(any(), any())).thenReturn(true);
            when(servicos.existemTodos(any(), any())).thenReturn(true);
            when(profissionais.salvar(any())).thenAnswer(i -> i.getArgument(0));
            assertThat(caso.cadastrar(dados(Set.of(), Set.of())).nome()).isEqualTo("Dra. Ana");
            verify(auditoria).registrar(any(EventoAuditoria.class));
        }

        @Test
        void recusaEspecialidadeOuServicoDeOutraClinica() {
            when(especialidades.existemTodas(any(), any())).thenReturn(false);
            assertThatThrownBy(() -> caso.cadastrar(dados(Set.of(UUID.randomUUID()), Set.of())))
                    .isInstanceOf(DadoInvalidoException.class);
            when(especialidades.existemTodas(any(), any())).thenReturn(true);
            when(servicos.existemTodos(any(), any())).thenReturn(false);
            assertThatThrownBy(() -> caso.cadastrar(dados(Set.of(), Set.of(UUID.randomUUID()))))
                    .isInstanceOf(DadoInvalidoException.class);
            verify(profissionais, never()).salvar(any());
        }

        @Test
        void atualizaEDesativaComControleDeVersao() {
            when(profissionais.buscar(Fixtures.CLINICA, anaDaConta.id())).thenReturn(Optional.of(anaDaConta));
            when(especialidades.existemTodas(any(), any())).thenReturn(true);
            when(servicos.existemTodos(any(), any())).thenReturn(true);
            when(profissionais.salvar(any())).thenAnswer(i -> i.getArgument(0));
            assertThat(caso.atualizar(anaDaConta.id(), dados(Set.of(), Set.of()), 0).usuarioSub()).isEmpty();
            assertThat(caso.alterarAtivo(anaDaConta.id(), false, 0).ativo()).isFalse();
            assertThatThrownBy(() -> caso.alterarAtivo(anaDaConta.id(), true, 9)).isInstanceOf(ConflitoException.class);
        }

        @Test
        void consultas() {
            when(profissionais.listar(Fixtures.CLINICA, true)).thenReturn(List.of(anaDaConta));
            when(profissionais.buscarPorUsuario(Fixtures.CLINICA, "dra.ana")).thenReturn(Optional.of(anaDaConta));
            assertThat(caso.listar(true)).containsExactly(anaDaConta);
            assertThat(caso.doUsuarioAtual()).isEqualTo(anaDaConta);
            assertThatThrownBy(() -> caso.obter(UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }

    @Nested
    class GerirServicosTest {
        private final GerirServicos caso = new GerirServicos(servicos, especialidades, Fixtures.CONTEXTO, auditoria);
        private final TipoServico.DadosServico dados = new TipoServico.DadosServico("Consulta", Optional.empty(),
                Optional.empty(), Duration.ofMinutes(30), new Dinheiro(25_000), PoliticaPagamento.nenhuma());

        @Test
        void cadastraAtualizaEListaServicos() {
            when(servicos.salvar(any())).thenAnswer(i -> i.getArgument(0));
            TipoServico criado = caso.cadastrar(dados);
            when(servicos.buscar(Fixtures.CLINICA, criado.id())).thenReturn(Optional.of(criado));
            assertThat(caso.atualizar(criado.id(), dados, false, 0).ativo()).isFalse();
            assertThatThrownBy(() -> caso.atualizar(criado.id(), dados, true, 5)).isInstanceOf(ConflitoException.class);
            assertThatThrownBy(() -> caso.obter(UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
            when(servicos.listar(Fixtures.CLINICA, true)).thenReturn(List.of(criado));
            assertThat(caso.listar(true)).hasSize(1);
        }

        @Test
        void especialidades() {
            var esp = new Especialidade(UUID.randomUUID(), "Cardiologia");
            when(especialidades.criar(Fixtures.CLINICA, "Cardiologia")).thenReturn(esp);
            when(especialidades.listar(Fixtures.CLINICA)).thenReturn(List.of(esp));
            assertThat(caso.criarEspecialidade("  Cardiologia ")).isEqualTo(esp);
            assertThat(caso.especialidades()).containsExactly(esp);
        }
    }

    @Nested
    class GerirAgendaProfissionalTest {
        private final Expediente expediente = new Expediente(List.of());
        private final Periodo periodo = new Periodo(Fixtures.AGORA, Fixtures.AGORA.plus(Duration.ofDays(2)));

        private GerirAgendaProfissional caso(String sub, Papel... papeis) {
            when(profissionais.buscar(Fixtures.CLINICA, anaDaConta.id())).thenReturn(Optional.of(anaDaConta));
            return new GerirAgendaProfissional(profissionais, agenda, Fixtures.CONTEXTO, Fixtures.ator(sub, papeis),
                    auditoria);
        }

        @Test
        void proprioProfissionalDefineExpediente() {
            caso("dra.ana", Papel.PROFISSIONAL).definirExpediente(anaDaConta.id(), expediente);
            verify(agenda).substituirExpediente(Fixtures.CLINICA, anaDaConta.id(), expediente);
        }

        @Test
        void outroProfissionalNaoAlteraERecebeNaoEncontrado() {
            var caso = caso("dr.bruno", Papel.PROFISSIONAL);
            assertThatThrownBy(() -> caso.definirExpediente(anaDaConta.id(), expediente))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            verify(agenda, never()).substituirExpediente(any(), any(), any());
        }

        @Test
        void recepcaoRegistraERemoveAusenciaMasNaoDefineExpediente() {
            var caso = caso("recepcao", Papel.RECEPCAO);
            Ausencia ausencia = caso.registrarAusencia(anaDaConta.id(), Ausencia.Tipo.FERIAS, periodo, Optional.empty());
            verify(agenda).salvarAusencia(eq(Fixtures.CLINICA), eq(ausencia), anyString());
            when(agenda.removerAusencia(Fixtures.CLINICA, anaDaConta.id(), ausencia.id())).thenReturn(true);
            caso.removerAusencia(anaDaConta.id(), ausencia.id());
            assertThatThrownBy(() -> caso.removerAusencia(anaDaConta.id(), UUID.randomUUID()))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
            assertThatThrownBy(() -> caso.definirExpediente(anaDaConta.id(), expediente))
                    .isInstanceOf(RecursoNaoEncontradoException.class);
        }

        @Test
        void consultasExigemProfissionalExistente() {
            var caso = caso("admin", Papel.ADMIN);
            when(agenda.expediente(Fixtures.CLINICA, anaDaConta.id())).thenReturn(expediente);
            when(agenda.ausencias(Fixtures.CLINICA, anaDaConta.id(), periodo)).thenReturn(List.of());
            assertThat(caso.expediente(anaDaConta.id())).isEqualTo(expediente);
            assertThat(caso.ausencias(anaDaConta.id(), periodo)).isEmpty();
            assertThatThrownBy(() -> caso.expediente(UUID.randomUUID())).isInstanceOf(RecursoNaoEncontradoException.class);
        }
    }
}
