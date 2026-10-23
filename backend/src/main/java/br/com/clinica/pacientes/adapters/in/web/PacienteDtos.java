package br.com.clinica.pacientes.adapters.in.web;

import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.pacientes.domain.Consentimento;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import br.com.clinica.pacientes.domain.Paciente.DadosPessoais;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** DTOs REST do módulo pacientes (nunca expõem entidades). */
final class PacienteDtos {

    private PacienteDtos() {
    }

    record DadosPessoaisDto(@NotBlank @Size(max = 160) String nome, @Size(max = 160) String nomeSocial,
                            @NotNull @Past LocalDate dataNascimento, @NotBlank @Size(max = 14) String cpf,
                            @Size(max = 20) String sexo, @NotNull Paciente.Canal canalPreferido) {

        DadosPessoais paraDominio() {
            return new DadosPessoais(nome, Optional.ofNullable(nomeSocial), dataNascimento, Cpf.de(cpf),
                    Optional.ofNullable(sexo), canalPreferido);
        }
    }

    record ContatoDto(@NotNull Contato.Tipo tipo, @NotBlank @Size(max = 254) String valor, boolean principal,
                      Instant verificadoEm) {

        static ContatoDto de(Contato c, boolean completo) {
            return new ContatoDto(c.tipo(), completo ? c.valor() : c.mascarado(), c.principal(),
                    c.verificadoEm().orElse(null));
        }

        Contato paraDominio() {
            return new Contato(tipo, valor, principal, Optional.empty());
        }
    }

    record PacienteResposta(UUID id, String nome, String nomeSocial, LocalDate dataNascimento, String cpf,
                            String sexo, Paciente.Canal canalPreferido, Paciente.Origem origem, boolean possuiConta,
                            List<ContatoDto> contatos, long versao) {

        /** {@code completo=false} mascara CPF e contatos (quem não precisa deles). */
        static PacienteResposta de(Paciente p, boolean completo) {
            DadosPessoais d = p.dados();
            return new PacienteResposta(p.id(), d.nome(), d.nomeSocial().orElse(null), d.dataNascimento(),
                    completo ? d.cpf().formatado() : d.cpf().mascarado(), d.sexo().orElse(null), d.canalPreferido(),
                    p.origem(), p.usuarioSub().isPresent(),
                    p.contatos().stream().map(c -> ContatoDto.de(c, completo)).toList(), p.versao());
        }
    }

    record ConsentimentoDto(Consentimento.Finalidade finalidade, String versaoTermo, Instant concedidoEm,
                            Instant revogadoEm, boolean vigente) {

        static ConsentimentoDto de(Consentimento c) {
            return new ConsentimentoDto(c.finalidade(), c.versaoTermo(), c.concedidoEm(), c.revogadoEm().orElse(null),
                    c.vigente());
        }
    }
}
