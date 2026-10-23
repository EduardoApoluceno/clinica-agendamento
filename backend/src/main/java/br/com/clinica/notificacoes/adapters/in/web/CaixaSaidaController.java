package br.com.clinica.notificacoes.adapters.in.web;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.notificacoes.application.CaixaSaida;
import br.com.clinica.notificacoes.application.CaixaSaida.MensagemSaida;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Caixa de saída do modo demonstração (só existe com app.demo.ativo=true). */
@RestController
@ConditionalOnProperty(name = "app.demo.ativo", havingValue = "true")
class CaixaSaidaController {

    private static final int LIMITE = 100;

    private final CaixaSaida caixaSaida;
    private final ContextoClinica contexto;

    CaixaSaidaController(CaixaSaida caixaSaida, ContextoClinica contexto) {
        this.caixaSaida = caixaSaida;
        this.contexto = contexto;
    }

    @GetMapping("/api/v1/demo/caixa-saida")
    @PreAuthorize("hasAuthority('caixa-saida:ler')")
    List<MensagemSaida> listar() {
        return caixaSaida.recentes(contexto.atual(), LIMITE);
    }
}
