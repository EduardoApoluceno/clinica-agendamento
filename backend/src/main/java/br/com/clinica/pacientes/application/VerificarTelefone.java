package br.com.clinica.pacientes.application;

import br.com.clinica.auditoria.EventoAuditoria;
import br.com.clinica.auditoria.RegistradorAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.ProvedorAtor;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.compartilhado.domain.DadoInvalidoException;
import br.com.clinica.compartilhado.domain.RecursoNaoEncontradoException;
import br.com.clinica.compartilhado.domain.Telefone;
import br.com.clinica.notificacoes.EnvioMensagens;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoComHash;
import br.com.clinica.pacientes.application.PacientesPortas.CodigoVerificacaoRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.PacienteRepositorio;
import br.com.clinica.pacientes.application.PacientesPortas.ProtetorCodigos;
import br.com.clinica.pacientes.domain.CodigoVerificacao;
import br.com.clinica.pacientes.domain.Contato;
import br.com.clinica.pacientes.domain.Paciente;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificação do celular do paciente por código de 6 dígitos (WhatsApp; SMS como fallback na Fase 3).
 * Limites: 3 envios/hora, 5 tentativas por código, validade de 10 minutos.
 */
public class VerificarTelefone {

    private final PacienteRepositorio pacientes;
    private final CodigoVerificacaoRepositorio codigos;
    private final ProtetorCodigos protetor;
    private final EnvioMensagens envio;
    private final ContextoClinica contexto;
    private final ProvedorAtor provedorAtor;
    private final RegistradorAuditoria auditoria;
    private final Clock relogio;
    private final boolean exporCodigoDemo;

    public VerificarTelefone(PacienteRepositorio pacientes, CodigoVerificacaoRepositorio codigos,
                             ProtetorCodigos protetor, EnvioMensagens envio, ContextoClinica contexto,
                             ProvedorAtor provedorAtor, RegistradorAuditoria auditoria, Clock relogio,
                             boolean exporCodigoDemo) {
        this.pacientes = pacientes;
        this.codigos = codigos;
        this.protetor = protetor;
        this.envio = envio;
        this.contexto = contexto;
        this.provedorAtor = provedorAtor;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.exporCodigoDemo = exporCodigoDemo;
    }

    @Transactional
    public CodigoEnviado solicitar(Telefone telefone) {
        Paciente paciente = meuPaciente();
        Instant agora = relogio.instant();
        if (codigos.enviosDesde(contexto.atual(), paciente.id(), agora.minus(Duration.ofHours(1)))
                >= CodigoVerificacao.MAXIMO_ENVIOS_POR_HORA) {
            throw new ConflitoException("limite-envios", "Muitas solicitações. Tente novamente em uma hora.");
        }
        Contato destino = Contato.celular(telefone, true);
        exigirNumeroDisponivel(paciente, destino);
        String codigo = protetor.gerar();
        var desafio = new CodigoVerificacao(UUID.randomUUID(), paciente.id(), destino, 0,
                agora.plus(CodigoVerificacao.VALIDADE), Optional.empty());
        codigos.salvar(contexto.atual(), desafio, protetor.proteger(codigo), agora);
        envio.enviarCodigoVerificacao(telefone, codigo);
        auditoria.registrar(EventoAuditoria.de("CODIGO_VERIFICACAO_ENVIADO", GerirPacientes.RECURSO, paciente.id())
                .doPaciente(paciente.id()).com(Map.of("destino", telefone.mascarado())));
        return new CodigoEnviado(telefone.mascarado(), desafio.expiraEm(),
                exporCodigoDemo ? Optional.of(codigo) : Optional.empty());
    }

    @Transactional(noRollbackFor = DadoInvalidoException.class)
    public Paciente confirmar(String codigoInformado) {
        Paciente paciente = meuPaciente();
        Instant agora = relogio.instant();
        CodigoComHash pendente = codigos.ultimoPendente(contexto.atual(), paciente.id())
                .filter(c -> c.codigo().utilizavel(agora))
                .orElseThrow(() -> new DadoInvalidoException("codigo-expirado", "Código expirado. Solicite um novo."));
        if (codigoInformado == null || !protetor.confere(codigoInformado.strip(), pendente.hash())) {
            codigos.registrarTentativa(pendente.codigo().id());
            throw new DadoInvalidoException("codigo-incorreto", "Código incorreto.");
        }
        codigos.marcarUsado(pendente.codigo().id(), agora);
        Contato contato = pendente.codigo().destino();
        exigirNumeroDisponivel(paciente, contato);
        Paciente salvo = pacientes.salvar(paciente.comContato(contato).comContatoVerificado(contato, agora));
        auditoria.registrar(EventoAuditoria.de("TELEFONE_VERIFICADO", GerirPacientes.RECURSO, paciente.id())
                .doPaciente(paciente.id()).com(Map.of("destino", contato.mascarado())));
        return salvo;
    }

    /** Um número verificado identifica um único paciente (vínculo seguro do WhatsApp). */
    private void exigirNumeroDisponivel(Paciente paciente, Contato contato) {
        pacientes.buscarPorContatoVerificado(contexto.atual(), contato)
                .filter(outro -> !outro.id().equals(paciente.id()))
                .ifPresent(outro -> {
                    throw new ConflitoException("telefone-em-uso", "Este número já está vinculado a outra conta.");
                });
    }

    private Paciente meuPaciente() {
        return pacientes.buscarPorUsuario(contexto.atual(), provedorAtor.atual().sub())
                .orElseThrow(() -> new RecursoNaoEncontradoException("paciente"));
    }

    /**
     * Resultado da solicitação.
     *
     * @param codigoDemo preenchido apenas no modo demonstração
     */
    public record CodigoEnviado(String destinoMascarado, Instant expiraEm, Optional<String> codigoDemo) { }
}
