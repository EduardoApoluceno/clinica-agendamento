package br.com.clinica.compartilhado.domain;

/**
 * Permissões finas (autoridades Spring sem prefixo). Os papéis agregam permissões; os endpoints
 * exigem permissões, nunca papéis, para que a matriz possa evoluir sem tocar nos controllers.
 */
public enum Permissao {
    CLINICA_LER("clinica:ler"),
    CLINICA_ADMINISTRAR("clinica:administrar"),
    PROFISSIONAIS_LER("profissionais:ler"),
    PROFISSIONAIS_ADMINISTRAR("profissionais:administrar"),
    AGENDA_PROPRIA_GERIR("agenda-propria:gerir"),
    AUSENCIAS_GERIR("ausencias:gerir"),
    AGENDA_LER("agenda:ler"),
    AGENDA_ESCREVER("agenda:escrever"),
    AGENDAMENTO_PROPRIO("agendamento-proprio:gerir"),
    PACIENTES_LER("pacientes:ler"),
    PACIENTES_ESCREVER("pacientes:escrever"),
    PACIENTES_ANONIMIZAR("pacientes:anonimizar"),
    CADASTRO_PROPRIO("cadastro-proprio:gerir"),
    AUDITORIA_LER("auditoria:ler"),
    CAIXA_SAIDA_LER("caixa-saida:ler");

    private final String autoridade;

    Permissao(String autoridade) {
        this.autoridade = autoridade;
    }

    public String autoridade() {
        return autoridade;
    }
}
