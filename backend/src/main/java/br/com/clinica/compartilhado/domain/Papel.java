package br.com.clinica.compartilhado.domain;

import static br.com.clinica.compartilhado.domain.Permissao.AGENDAMENTO_PROPRIO;
import static br.com.clinica.compartilhado.domain.Permissao.AGENDA_ESCREVER;
import static br.com.clinica.compartilhado.domain.Permissao.AGENDA_LER;
import static br.com.clinica.compartilhado.domain.Permissao.AGENDA_PROPRIA_GERIR;
import static br.com.clinica.compartilhado.domain.Permissao.AUDITORIA_LER;
import static br.com.clinica.compartilhado.domain.Permissao.AUSENCIAS_GERIR;
import static br.com.clinica.compartilhado.domain.Permissao.CADASTRO_PROPRIO;
import static br.com.clinica.compartilhado.domain.Permissao.CAIXA_SAIDA_LER;
import static br.com.clinica.compartilhado.domain.Permissao.CLINICA_ADMINISTRAR;
import static br.com.clinica.compartilhado.domain.Permissao.CLINICA_LER;
import static br.com.clinica.compartilhado.domain.Permissao.PACIENTES_ANONIMIZAR;
import static br.com.clinica.compartilhado.domain.Permissao.PACIENTES_ESCREVER;
import static br.com.clinica.compartilhado.domain.Permissao.PACIENTES_LER;
import static br.com.clinica.compartilhado.domain.Permissao.PROFISSIONAIS_ADMINISTRAR;
import static br.com.clinica.compartilhado.domain.Permissao.PROFISSIONAIS_LER;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Papéis de acesso (RBAC) e a matriz de permissões de cada um. */
public enum Papel {
    ADMIN(EnumSet.of(CLINICA_LER, CLINICA_ADMINISTRAR, PROFISSIONAIS_LER, PROFISSIONAIS_ADMINISTRAR, AUSENCIAS_GERIR,
            AGENDA_LER, AGENDA_ESCREVER, PACIENTES_LER, PACIENTES_ESCREVER, PACIENTES_ANONIMIZAR, AUDITORIA_LER,
            CAIXA_SAIDA_LER)),
    RECEPCAO(EnumSet.of(CLINICA_LER, PROFISSIONAIS_LER, AUSENCIAS_GERIR, AGENDA_LER, AGENDA_ESCREVER, PACIENTES_LER,
            PACIENTES_ESCREVER, CAIXA_SAIDA_LER)),
    /** Vê e conduz só a própria agenda (agenda-propria:gerir); a grade completa é da recepção. */
    PROFISSIONAL(EnumSet.of(CLINICA_LER, PROFISSIONAIS_LER, AGENDA_PROPRIA_GERIR, PACIENTES_LER)),
    PACIENTE(EnumSet.of(CADASTRO_PROPRIO, AGENDAMENTO_PROPRIO));

    public static final String PREFIXO_AUTORIDADE = "ROLE_";

    private final Set<Permissao> permissoes;

    Papel(Set<Permissao> permissoes) {
        this.permissoes = permissoes;
    }

    /** Converte o nome vindo do IdP; ignora papéis desconhecidos (ex.: default-roles do Keycloak). */
    public static Optional<Papel> doNome(String nome) {
        String normalizado = nome.toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.name().equals(normalizado)).findFirst();
    }

    /**
     * Papéis efetivos: o Keycloak dá PACIENTE como papel padrão a toda conta (autocadastro),
     * então membros da equipe o recebem também; para eles, PACIENTE é descartado.
     */
    public static Set<Papel> efetivos(Collection<Papel> recebidos) {
        EnumSet<Papel> papeis = recebidos.isEmpty() ? EnumSet.noneOf(Papel.class) : EnumSet.copyOf(recebidos);
        if (papeis.stream().anyMatch(Papel::equipe)) {
            papeis.remove(PACIENTE);
        }
        return papeis;
    }

    public String autoridade() {
        return PREFIXO_AUTORIDADE + name();
    }

    public Set<Permissao> permissoes() {
        return EnumSet.copyOf(permissoes);
    }

    public boolean equipe() {
        return this != PACIENTE;
    }
}
