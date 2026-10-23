package br.com.clinica.agenda.application;

import br.com.clinica.agenda.domain.Agendamento;
import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.AcessoNegadoException;
import br.com.clinica.compartilhado.domain.Papel;
import br.com.clinica.compartilhado.domain.Permissao;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.pacientes.ConsultaPacientes;
import br.com.clinica.pacientes.ConsultaPacientes.PacienteAgenda;
import br.com.clinica.profissionais.CatalogoAgenda;
import java.util.Optional;
import java.util.UUID;

/**
 * Quem é o ator diante da agenda: equipe (recepção/admin, vê e altera tudo), profissional (só a
 * própria agenda) ou paciente (só os próprios agendamentos). Quem não pode ver recebe "não encontrado".
 */
public class AcessoAgenda {

    public enum Perfil { EQUIPE, PROFISSIONAL, PACIENTE }

    private final ProvedorAtor provedorAtor;
    private final ConsultaPacientes pacientes;
    private final CatalogoAgenda catalogo;

    public AcessoAgenda(ProvedorAtor provedorAtor, ConsultaPacientes pacientes, CatalogoAgenda catalogo) {
        this.provedorAtor = provedorAtor;
        this.pacientes = pacientes;
        this.catalogo = catalogo;
    }

    public Acesso atual() {
        Ator ator = provedorAtor.atual();
        if (ator.tem(Permissao.AGENDA_ESCREVER)) {
            return new Acesso(ator, Perfil.EQUIPE, Optional.empty(), Optional.empty());
        }
        if (ator.possui(Papel.PROFISSIONAL) && ator.tem(Permissao.AGENDA_PROPRIA_GERIR)) {
            Optional<UUID> profissional = catalogo.profissionaisAtivos().stream()
                    .filter(p -> p.usuarioSub().filter(ator.sub()::equals).isPresent())
                    .map(CatalogoAgenda.ProfissionalAgendavel::id).findFirst();
            return new Acesso(ator, Perfil.PROFISSIONAL, profissional, Optional.empty());
        }
        if (ator.tem(Permissao.AGENDAMENTO_PROPRIO)) {
            return new Acesso(ator, Perfil.PACIENTE, Optional.empty(), pacientes.porUsuario(ator.sub()));
        }
        throw new AcessoNegadoException("agenda-sem-acesso", "Seu perfil não tem acesso à agenda.");
    }

    /**
     * @param profissional cadastro vinculado à conta (perfil PROFISSIONAL)
     * @param paciente cadastro vinculado à conta (perfil PACIENTE); vazio se ainda não completou o cadastro
     */
    public record Acesso(Ator ator, Perfil perfil, Optional<UUID> profissional, Optional<PacienteAgenda> paciente) {

        public boolean equipe() {
            return perfil == Perfil.EQUIPE;
        }

        public boolean pode(Agendamento agendamento) {
            return switch (perfil) {
                case EQUIPE -> true;
                case PROFISSIONAL -> profissional.filter(agendamento.profissionalId()::equals).isPresent();
                case PACIENTE -> paciente.filter(p -> p.id().equals(agendamento.pacienteId())).isPresent();
            };
        }

        public Agendamento exigirVisivel(Optional<Agendamento> agendamento) {
            return agendamento.filter(this::pode).orElseThrow(() -> new RecursoNaoEncontradoException("agendamento"));
        }

        /** Remarcar e cancelar são da recepção ou do próprio paciente; o profissional pede à recepção. */
        public void exigirAlterar() {
            if (perfil == Perfil.PROFISSIONAL) {
                throw new AcessoNegadoException("agenda-alteracao-negada",
                        "Remarcações e cancelamentos são feitos pela recepção.");
            }
        }

        /** Presença, falta e conclusão são registradas pela equipe ou pelo profissional do atendimento. */
        public void exigirConduzir() {
            if (perfil == Perfil.PACIENTE) {
                throw new AcessoNegadoException("agenda-conducao-negada", "Operação exclusiva da equipe da clínica.");
            }
        }
    }
}
