package br.com.clinica.profissionais.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.profissionais.application.ProfissionaisPortas.Especialidade;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.TipoServico;
import br.com.clinica.profissionais.domain.TipoServico.DadosServico;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo de serviços e especialidades da clínica. */
public class GerirServicos {

    private static final String RECURSO = "TIPO_SERVICO";

    private final TipoServicoRepositorio servicos;
    private final EspecialidadeRepositorio especialidades;
    private final ContextoClinica contexto;
    private final RegistradorAuditoria auditoria;

    public GerirServicos(TipoServicoRepositorio servicos, EspecialidadeRepositorio especialidades,
                         ContextoClinica contexto, RegistradorAuditoria auditoria) {
        this.servicos = servicos;
        this.especialidades = especialidades;
        this.contexto = contexto;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<TipoServico> listar(boolean apenasAtivos) {
        return servicos.listar(contexto.atual(), apenasAtivos);
    }

    @Transactional(readOnly = true)
    public TipoServico obter(UUID id) {
        return servicos.buscar(contexto.atual(), id).orElseThrow(() -> new RecursoNaoEncontradoException("servico"));
    }

    @Transactional
    public TipoServico cadastrar(DadosServico dados) {
        TipoServico salvo = servicos.salvar(TipoServico.novo(contexto.atual(), dados));
        auditoria.registrar(EventoAuditoria.de("SERVICO_CADASTRADO", RECURSO, salvo.id()));
        return salvo;
    }

    @Transactional
    public TipoServico atualizar(UUID id, DadosServico dados, boolean ativo, long versao) {
        TipoServico atual = obter(id);
        if (atual.versao() != versao) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
        TipoServico salvo = servicos.salvar(atual.atualizado(dados, ativo));
        auditoria.registrar(EventoAuditoria.de("SERVICO_ATUALIZADO", RECURSO, id));
        return salvo;
    }

    @Transactional(readOnly = true)
    public List<Especialidade> especialidades() {
        return especialidades.listar(contexto.atual());
    }

    @Transactional
    public Especialidade criarEspecialidade(String nome) {
        Especialidade criada = especialidades.criar(contexto.atual(), nome.strip());
        auditoria.registrar(EventoAuditoria.de("ESPECIALIDADE_CRIADA", "ESPECIALIDADE", criada.id()));
        return criada;
    }
}
