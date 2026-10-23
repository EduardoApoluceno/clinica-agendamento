package br.com.clinica.pacientes.domain;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Cpf;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Paciente da clínica. Contatos são uma lista pequena (e-mail e celulares); um celular verificado
 * é o vínculo usado pelo WhatsApp.
 */
public record Paciente(UUID id, ClinicaId clinicaId, Optional<String> usuarioSub, DadosPessoais dados,
                       Origem origem, List<Contato> contatos, long versao) {

    public enum Canal { WHATSAPP, EMAIL, SMS }

    public enum Origem { PORTAL, WHATSAPP, RECEPCAO }

    private static final int MAXIMO_CONTATOS = 6;

    public Paciente {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(clinicaId, "clinicaId");
        Objects.requireNonNull(dados, "dados");
        Objects.requireNonNull(origem, "origem");
        usuarioSub = usuarioSub == null ? Optional.empty() : usuarioSub.filter(s -> !s.isBlank());
        contatos = List.copyOf(contatos);
        if (contatos.size() > MAXIMO_CONTATOS) {
            throw new DadoInvalidoException("contatos-demais", "Limite de contatos atingido.");
        }
    }

    public static Paciente novo(ClinicaId clinica, DadosPessoais dados, Origem origem, Optional<String> usuarioSub,
                                List<Contato> contatos) {
        return new Paciente(UUID.randomUUID(), clinica, usuarioSub, dados, origem, contatos, 0);
    }

    public Paciente comDados(DadosPessoais novos) {
        if (!novos.cpf().equals(dados.cpf())) {
            throw new ConflitoException("cpf-imutavel", "O CPF não pode ser alterado. Procure a recepção.");
        }
        return new Paciente(id, clinicaId, usuarioSub, novos, origem, contatos, versao);
    }

    public Paciente vinculadoA(String sub) {
        if (usuarioSub.isPresent() && !usuarioSub.get().equals(sub)) {
            throw new ConflitoException("paciente-ja-vinculado", "Este cadastro já pertence a outra conta.");
        }
        return new Paciente(id, clinicaId, Optional.of(sub), dados, origem, contatos, versao);
    }

    /** Adiciona o contato se ainda não existir (mantém verificação já feita). */
    public Paciente comContato(Contato novo) {
        if (contatos.stream().anyMatch(c -> c.mesmoMeio(novo))) {
            return this;
        }
        List<Contato> lista = new ArrayList<>(contatos);
        lista.add(novo);
        return new Paciente(id, clinicaId, usuarioSub, dados, origem, lista, versao);
    }

    public Paciente comContatoVerificado(Contato contato, Instant quando) {
        List<Contato> lista = contatos.stream()
                .map(c -> c.mesmoMeio(contato) ? c.verificadoEm(quando) : c).toList();
        return new Paciente(id, clinicaId, usuarioSub, dados, origem, lista, versao);
    }

    public boolean possuiContato(Contato contato) {
        return contatos.stream().anyMatch(c -> c.mesmoMeio(contato));
    }

    public Optional<Contato> celularVerificado() {
        return contatos.stream().filter(c -> c.tipo() == Contato.Tipo.CELULAR && c.verificado()).findFirst();
    }

    /** Dados cadastrais editáveis. */
    public record DadosPessoais(String nome, Optional<String> nomeSocial, LocalDate dataNascimento, Cpf cpf,
                                Optional<String> sexo, Canal canalPreferido) {

        private static final int TAMANHO_MAXIMO_NOME = 160;
        private static final int IDADE_MAXIMA = 130;

        public DadosPessoais {
            Objects.requireNonNull(cpf, "cpf");
            Objects.requireNonNull(canalPreferido, "canalPreferido");
            if (nome == null || nome.isBlank() || nome.length() > TAMANHO_MAXIMO_NOME) {
                throw new DadoInvalidoException("nome-invalido", "Informe o nome completo (até 160 caracteres).");
            }
            nome = nome.strip();
            nomeSocial = nomeSocial == null ? Optional.empty() : nomeSocial.map(String::strip).filter(n -> !n.isEmpty());
            sexo = sexo == null ? Optional.empty() : sexo.filter(s -> !s.isBlank());
            if (dataNascimento == null || dataNascimento.isAfter(LocalDate.now())
                    || dataNascimento.isBefore(LocalDate.now().minusYears(IDADE_MAXIMA))) {
                throw new DadoInvalidoException("nascimento-invalido", "Data de nascimento inválida.");
            }
        }

        /** Nome de tratamento: o nome social, quando informado. */
        public String nomeExibicao() {
            return nomeSocial.orElse(nome);
        }
    }
}
