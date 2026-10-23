package br.com.clinica.compartilhado.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Informa ao frontend se é ambiente de demonstração (banner fixo), sem exigir login. */
@RestController
class AmbienteController {

    private final boolean demo;

    AmbienteController(@Value("${app.demo.ativo:false}") boolean demo) {
        this.demo = demo;
    }

    @GetMapping("/api/v1/publico/ambiente")
    AmbienteResposta ambiente() {
        return new AmbienteResposta(demo);
    }

    record AmbienteResposta(boolean demo) { }
}
