package br.com.clinica.agenda.adapters.in.web;

import br.com.clinica.agenda.EventosAgenda.EventoAgenda;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Avisos "a agenda do profissional X mudou" por SSE, depois do commit. O aviso não traz dados: a
 * tela recarrega os horários pela API normal (que aplica as regras de acesso). Conexões são
 * limitadas e expiram; o EventSource do navegador reconecta sozinho.
 */
@Component
class AvisosDisponibilidade {

    static final long TEMPO_CONEXAO_MS = Duration.ofMinutes(5).toMillis();
    private static final int MAXIMO_CONEXOES = 300;

    /** emissor → profissional assinado (vazio = todos, usado pela grade da recepção). */
    private final Map<SseEmitter, Optional<UUID>> conexoes = new ConcurrentHashMap<>();

    SseEmitter assinar(Optional<UUID> profissional) {
        if (conexoes.size() >= MAXIMO_CONEXOES) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Muitas conexões de avisos abertas.");
        }
        SseEmitter emissor = new SseEmitter(TEMPO_CONEXAO_MS);
        conexoes.put(emissor, profissional);
        emissor.onCompletion(() -> conexoes.remove(emissor));
        emissor.onTimeout(() -> conexoes.remove(emissor));
        emissor.onError(e -> conexoes.remove(emissor));
        enviar(emissor, "conectado", "");
        return emissor;
    }

    @TransactionalEventListener
    void aoMudarAgenda(EventoAgenda evento) {
        String profissional = evento.profissionalId().toString();
        conexoes.forEach((emissor, filtro) -> {
            if (filtro.isEmpty() || filtro.get().equals(evento.profissionalId())) {
                enviar(emissor, "agenda-alterada", profissional);
            }
        });
    }

    private void enviar(SseEmitter emissor, String nome, String dados) {
        try {
            emissor.send(SseEmitter.event().name(nome).data(dados));
        } catch (IOException | IllegalStateException e) {
            conexoes.remove(emissor);
            emissor.completeWithError(e);
        }
    }
}
