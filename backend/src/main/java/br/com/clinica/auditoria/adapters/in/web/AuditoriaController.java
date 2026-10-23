package br.com.clinica.auditoria.adapters.in.web;

import br.com.clinica.auditoria.application.ConsultaAuditoria;
import br.com.clinica.auditoria.application.ConsultaAuditoria.RegistroAuditoria;
import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.application.Pagina;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta da trilha de auditoria (somente ADMIN; nunca expõe conteúdo clínico). */
@RestController
@RequestMapping("/api/v1/auditoria")
class AuditoriaController {

    private final ConsultaAuditoria consulta;
    private final ContextoClinica contextoClinica;

    AuditoriaController(ConsultaAuditoria consulta, ContextoClinica contextoClinica) {
        this.consulta = consulta;
        this.contextoClinica = contextoClinica;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auditoria:ler')")
    Pagina<RegistroAuditoria> buscar(@ParameterObject FiltroAuditoria filtro) {
        return consulta.buscar(contextoClinica.atual(), filtro.paraConsulta(), filtro.pedido());
    }

    /** Parâmetros de consulta (todos opcionais). */
    record FiltroAuditoria(
            String recursoTipo, String recursoId, String ator, UUID pacienteId,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant de,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant ate,
            Integer pagina, Integer tamanho) {

        private static final int TAMANHO_PADRAO = 50;

        ConsultaAuditoria.Filtro paraConsulta() {
            return new ConsultaAuditoria.Filtro(Optional.ofNullable(recursoTipo), Optional.ofNullable(recursoId),
                    Optional.ofNullable(ator), Optional.ofNullable(pacienteId), Optional.ofNullable(de),
                    Optional.ofNullable(ate));
        }

        Pagina.Pedido pedido() {
            return new Pagina.Pedido(pagina == null ? 0 : pagina, tamanho == null ? TAMANHO_PADRAO : tamanho);
        }
    }
}
