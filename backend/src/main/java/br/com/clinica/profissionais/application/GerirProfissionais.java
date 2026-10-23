package br.com.clinica.profissionais.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.Profissional.DadosProfissional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro e manutenção de profissionais (ADMIN). */
public class GerirProfissionais {

    private static final String RECURSO = "PROFISSIONAL";

    private final ProfissionalRepositorio profissionais;
    private final TipoServicoRepositorio servicos;
    private final EspecialidadeRepositorio especialidades;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;

    public GerirProfissionais(ProfissionalRepositorio profissionais, TipoServicoRepositorio servicos,
                              EspecialidadeRepositorio especialidades, ContextoClinica contexto,
                              ProvedorAtor provedorAtor, RegistradorAuditoria auditoria) {
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.especialidades = especialidades;
        this.contexto = contexto;
        this.provedorAtor = provedorAtor;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<Profissional> listar(boolean apenasAtivos) {
        return profissionais.listar(contexto.atual(), apenasAtivos);
    }

    @Transactional(readOnly = true)
    public Profissional obter(UUID id) {
        return profissionais.buscar(contexto.atual(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional"));
    }

    /** Cadastro vinculado à conta do usuário logado. */
    @Transactional(readOnly = true)
    public Profissional doUsuarioAtual() {
        return profissionais.buscarPorUsuario(contexto.atual(), provedorAtor.atual().sub())
                .orElseThrow(() -> new RecursoNaoEncontradoException("profissional"));
    }

    @Transactional
    public Profissional cadastrar(DadosProfissional dados) {
        validarReferencias(dados);
        Profissional salvo = profissionais.salvar(Profissional.novo(contexto.atual(), dados));
        auditoria.registrar(EventoAuditoria.de("PROFISSIONAL_CADASTRADO", RECURSO, salvo.id()));
        return salvo;
    }

    @Transactional
    public Profissional atualizar(UUID id, DadosProfissional dados, long versao) {
        Profissional atual = obter(id);
        exigirVersao(atual, versao);
        validarReferencias(dados);
        Profissional salvo = profissionais.salvar(atual.atualizado(dados));
        auditoria.registrar(EventoAuditoria.de("PROFISSIONAL_ATUALIZADO", RECURSO, id));
        return salvo;
    }

    @Transactional
    public Profissional alterarAtivo(UUID id, boolean ativo, long versao) {
        Profissional atual = obter(id);
        exigirVersao(atual, versao);
        Profissional salvo = profissionais.salvar(atual.comAtivo(ativo));
        auditoria.registrar(EventoAuditoria.de(ativo ? "PROFISSIONAL_REATIVADO" : "PROFISSIONAL_DESATIVADO",
                RECURSO, id).com(Map.of("ativo", String.valueOf(ativo))));
        return salvo;
    }

    /** Impede vincular especialidades/serviços inexistentes ou de outra clínica. */
    private void validarReferencias(DadosProfissional dados) {
        if (!especialidades.existemTodas(contexto.atual(), dados.especialidades())) {
            throw new DadoInvalidoException("especialidade-invalida", "Especialidade inexistente.");
        }
        if (!servicos.existemTodos(contexto.atual(), dados.servicos())) {
            throw new DadoInvalidoException("servico-invalido", "Serviço inexistente.");
        }
    }

    private static void exigirVersao(Profissional atual, long versao) {
        if (atual.versao() != versao) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
    }
}
