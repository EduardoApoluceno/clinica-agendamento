package br.com.clinica.pacientes.application;

import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.pacientes.domain.CodigoVerificacao;
import br.com.clinica.pacientes.domain.Consentimento;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Portas de saída do módulo pacientes. */
public final class PacientesPortas {

    private PacientesPortas() {
    }

    public interface PacienteRepositorio {
        Optional<Paciente> buscar(ClinicaId clinica, UUID id);

        Optional<Paciente> buscarPorUsuario(ClinicaId clinica, String usuarioSub);

        Optional<Paciente> buscarPorCpf(ClinicaId clinica, Cpf cpf);

        /** Paciente que já verificou este contato (ex.: número de WhatsApp). */
        Optional<Paciente> buscarPorContatoVerificado(ClinicaId clinica, Contato contato);

        /** Busca por nome (prefixo/trecho), CPF ou celular exatos. */
        Pagina<ResumoPaciente> pesquisar(ClinicaId clinica, String termo, Pagina.Pedido pedido);

        Paciente salvar(Paciente paciente);
    }

    public interface ConsentimentoRepositorio {
        List<Consentimento> listar(ClinicaId clinica, UUID paciente);

        void conceder(ClinicaId clinica, Consentimento consentimento, String origem, Optional<String> ip);

        boolean revogar(ClinicaId clinica, UUID paciente, Consentimento.Finalidade finalidade, Instant quando);
    }

    public interface CodigoVerificacaoRepositorio {
        void salvar(ClinicaId clinica, CodigoVerificacao codigo, byte[] codigoHash, Instant criadoEm);

        Optional<CodigoComHash> ultimoPendente(ClinicaId clinica, UUID paciente);

        int enviosDesde(ClinicaId clinica, UUID paciente, Instant desde);

        void registrarTentativa(UUID codigo);

        void marcarUsado(UUID codigo, Instant quando);
    }

    /** Geração e conferência de códigos sem guardar o valor em claro. */
    public interface ProtetorCodigos {
        String gerar();

        byte[] proteger(String codigo);

        boolean confere(String codigoInformado, byte[] protegido);
    }

    public record CodigoComHash(CodigoVerificacao codigo, byte[] hash) { }

    public record ResumoPaciente(UUID id, String nome, Optional<String> nomeSocial, LocalDate dataNascimento,
                                 String cpfMascarado, boolean possuiConta) { }
}
