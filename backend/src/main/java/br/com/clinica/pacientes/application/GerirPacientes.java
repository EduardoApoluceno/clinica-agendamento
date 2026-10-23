package br.com.clinica.pacientes.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.Pagina;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ResumoPaciente;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import br.com.clinica.pacientes.domain.Paciente.DadosPessoais;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro de pacientes pela equipe (recepção/administração). Leituras de ficha são auditadas. */
public class GerirPacientes {

    static final String RECURSO = "PACIENTE";
    private static final int TAMANHO_MINIMO_BUSCA = 3;

    private final PacienteRepositorio pacientes;
    private final ContextoClinica contexto;
    private final RegistradorAuditoria auditoria;

    public GerirPacientes(PacienteRepositorio pacientes, ContextoClinica contexto, RegistradorAuditoria auditoria) {
        this.pacientes = pacientes;
        this.contexto = contexto;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Pagina<ResumoPaciente> pesquisar(String termo, Pagina.Pedido pedido) {
        String limpo = termo == null ? "" : termo.strip();
        if (limpo.length() < TAMANHO_MINIMO_BUSCA) {
            return new Pagina<>(List.of(), pedido.pagina(), pedido.tamanho(), 0);
        }
        return pacientes.pesquisar(contexto.atual(), limpo, pedido);
    }

    @Transactional
    public Paciente obter(UUID id) {
        Paciente paciente = pacientes.buscar(contexto.atual(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
        auditoria.registrar(EventoAuditoria.de("PACIENTE_CONSULTADO", RECURSO, id).doPaciente(id));
        return paciente;
    }

    @Transactional
    public Paciente cadastrar(DadosPessoais dados, List<Contato> contatos) {
        exigirCpfLivre(dados, Optional.empty());
        Paciente salvo = pacientes.salvar(Paciente.novo(contexto.atual(), dados, Paciente.Origem.RECEPCAO,
                Optional.empty(), contatos));
        auditoria.registrar(EventoAuditoria.de("PACIENTE_CADASTRADO", RECURSO, salvo.id()).doPaciente(salvo.id()));
        return salvo;
    }

    @Transactional
    public Paciente atualizar(UUID id, DadosPessoais dados, List<Contato> novosContatos, long versao) {
        Paciente atual = pacientes.buscar(contexto.atual(), id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
        if (atual.versao() != versao) {
            throw new ConflitoException("edicao-concorrente", "Os dados foram alterados por outra pessoa. Recarregue.");
        }
        Paciente alterado = atual.comDados(dados);
        for (Contato contato : novosContatos) {
            alterado = alterado.comContato(contato);
        }
        Paciente salvo = pacientes.salvar(alterado);
        auditoria.registrar(EventoAuditoria.de("PACIENTE_ATUALIZADO", RECURSO, id).doPaciente(id));
        return salvo;
    }

    private void exigirCpfLivre(DadosPessoais dados, Optional<UUID> exceto) {
        pacientes.buscarPorCpf(contexto.atual(), dados.cpf())
                .filter(p -> exceto.map(id -> !id.equals(p.id())).orElse(true))
                .ifPresent(p -> {
                    throw new ConflitoException("cpf-ja-cadastrado", "Já existe paciente com este CPF.");
                });
    }
}
