package br.com.clinica.clinica.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.clinica.application.ClinicaRepositorio.ConfiguracaoComVersao;
import br.com.clinica.clinica.domain.Clinica;
import br.com.clinica.clinica.domain.Cnpj;
import br.com.clinica.clinica.domain.ConfiguracaoAgenda;
import br.com.clinica.clinica.domain.Endereco;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/** Casos de uso de consulta e manutenção dos dados e configurações da clínica atual. */
public class GerirClinica {

    private static final String RECURSO = "CLINICA";

    private final ClinicaRepositorio repositorio;
    private final ContextoClinica contexto;
    private final RegistradorAuditoria auditoria;

    public GerirClinica(ClinicaRepositorio repositorio, ContextoClinica contexto, RegistradorAuditoria auditoria) {
        this.repositorio = repositorio;
        this.contexto = contexto;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Clinica obter() {
        return repositorio.buscar(contexto.atual()).orElseThrow(() -> new RecursoNaoEncontradoException("clinica"));
    }

    @Transactional
    public Clinica atualizarDados(DadosClinica dados) {
        Clinica atual = obter();
        if (atual.versao() != dados.versao()) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
        Clinica salva = repositorio.atualizar(atual.comDados(dados.nome(), dados.cnpj(), dados.endereco(),
                dados.telefone()));
        auditoria.registrar(EventoAuditoria.de("CLINICA_ATUALIZADA", RECURSO, salva.id()));
        return salva;
    }

    @Transactional(readOnly = true)
    public ConfiguracaoComVersao configuracao() {
        return repositorio.configuracao(contexto.atual());
    }

    @Transactional
    public ConfiguracaoComVersao atualizarConfiguracao(ConfiguracaoAgenda configuracao, long versao) {
        ConfiguracaoComVersao salva = repositorio.atualizarConfiguracao(contexto.atual(), configuracao, versao);
        auditoria.registrar(EventoAuditoria.de("CONFIGURACAO_AGENDA_ATUALIZADA", RECURSO, contexto.atual()));
        return salva;
    }

    /** Comando de atualização dos dados cadastrais. */
    public record DadosClinica(String nome, Cnpj cnpj, Endereco endereco, Optional<String> telefone, long versao) { }
}
