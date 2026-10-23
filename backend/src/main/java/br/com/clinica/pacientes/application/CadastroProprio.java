package br.com.clinica.pacientes.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.Ator;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.Email;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.pacientes.application.PacientesPortas.ConsentimentoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.domain.Consentimento;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import br.com.clinica.pacientes.domain.Paciente.DadosPessoais;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autoatendimento do paciente com conta: completar/atualizar o próprio cadastro e exportar os
 * próprios dados (LGPD art. 18). O paciente é sempre resolvido pela conta logada.
 */
public class CadastroProprio {

    private final PacienteRepositorio pacientes;
    private final ConsentimentoRepositorio consentimentos;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;
    private final Clock relogio;

    public CadastroProprio(PacienteRepositorio pacientes, ConsentimentoRepositorio consentimentos,
                           ContextoClinica contexto, ProvedorAtor provedorAtor, RegistradorAuditoria auditoria,
                           Clock relogio) {
        this.pacientes = pacientes;
        this.consentimentos = consentimentos;
        this.contexto = contexto;
        this.provedorAtor = provedorAtor;
        this.auditoria = auditoria;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public Optional<Paciente> meu() {
        return pacientes.buscarPorUsuario(contexto.atual(), provedorAtor.atual().sub());
    }

    /**
     * Primeiro acesso: cria o cadastro ligado à conta. Se a recepção já cadastrou o CPF, só vincula
     * quando o e-mail verificado da conta ou o celular informado constam naquele cadastro — impede que
     * alguém tome o cadastro de outra pessoa sabendo apenas o CPF.
     */
    @Transactional
    public Paciente completar(DadosPessoais dados, Telefone celular, Optional<Email> emailVerificadoDaConta) {
        Ator ator = provedorAtor.atual();
        if (meu().isPresent()) {
            throw new ConflitoException("cadastro-ja-completo", "Seu cadastro já foi concluído.");
        }
        Contato contatoCelular = Contato.celular(celular, true);
        Optional<Contato> contatoEmail = emailVerificadoDaConta
                .map(e -> Contato.email(e, true, Optional.of(relogio.instant())));
        Paciente paciente = pacientes.buscarPorCpf(contexto.atual(), dados.cpf())
                .map(existente -> vincularExistente(existente, ator, contatoCelular, contatoEmail))
                .orElseGet(() -> Paciente.novo(contexto.atual(), dados, Paciente.Origem.PORTAL,
                        Optional.of(ator.sub()), contatoEmail.map(e -> List.of(contatoCelular, e))
                                .orElse(List.of(contatoCelular))));
        Paciente salvo = pacientes.salvar(paciente.comContato(contatoCelular));
        auditoria.registrar(EventoAuditoria.de("CADASTRO_PROPRIO_CONCLUIDO", GerirPacientes.RECURSO, salvo.id())
                .doPaciente(salvo.id()));
        return salvo;
    }

    @Transactional
    public Paciente atualizar(DadosPessoais dados, long versao) {
        Paciente atual = meu().orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
        if (atual.versao() != versao) {
            throw new ConflitoException("edicao-concorrente", "Seus dados foram alterados em outra sessão. Recarregue.");
        }
        // Trocar o próprio CPF permitiria ocupar o CPF de outra pessoa antes dela (ADR-0016).
        if (!atual.dados().cpf().equals(dados.cpf())) {
            throw new ConflitoException("cpf-imutavel", "Para corrigir o CPF, procure a recepção.");
        }
        Paciente salvo = pacientes.salvar(atual.comDados(dados));
        auditoria.registrar(EventoAuditoria.de("CADASTRO_PROPRIO_ATUALIZADO", GerirPacientes.RECURSO, salvo.id())
                .doPaciente(salvo.id()));
        return salvo;
    }

    /** Portabilidade/acesso: todos os dados pessoais mantidos sobre o titular. */
    @Transactional
    public ExportacaoDados exportar() {
        Paciente paciente = meu().orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
        List<Consentimento> lista = consentimentos.listar(contexto.atual(), paciente.id());
        auditoria.registrar(EventoAuditoria.de("DADOS_EXPORTADOS", GerirPacientes.RECURSO, paciente.id())
                .doPaciente(paciente.id()));
        return new ExportacaoDados(paciente, lista, relogio.instant().toString());
    }

    private static Paciente vincularExistente(Paciente existente, Ator ator, Contato celular, Optional<Contato> email) {
        boolean comprovado = existente.possuiContato(celular) || email.filter(existente::possuiContato).isPresent();
        if (!comprovado || existente.usuarioSub().isPresent()) {
            throw new ConflitoException("cpf-ja-cadastrado",
                    "Já existe um cadastro com este CPF. Procure a recepção para vincular à sua conta.");
        }
        Paciente vinculado = existente.vinculadoA(ator.sub());
        return email.map(vinculado::comContato).orElse(vinculado);
    }

    /** Pacote de dados do titular. */
    public record ExportacaoDados(Paciente paciente, List<Consentimento> consentimentos, String geradoEm) { }
}
