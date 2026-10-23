package br.com.clinica.profissionais.adapters.in.web;

import br.com.clinica.profissionais.application.GerirProfissionais;
import br.com.clinica.profissionais.application.GerirServicos;
import br.com.clinica.profissionais.application.ProfissionaisPortas.Especialidade;
import br.com.clinica.profissionais.domain.PoliticaPagamento;
import br.com.clinica.profissionais.domain.TipoServico;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catálogo público para o portal: só serviços e profissionais ativos, sem dados internos. */
@RestController
class CatalogoPublicoController {

    private final GerirServicos servicos;
    private final GerirProfissionais profissionais;

    CatalogoPublicoController(GerirServicos servicos, GerirProfissionais profissionais) {
        this.servicos = servicos;
        this.profissionais = profissionais;
    }

    @GetMapping("/api/v1/publico/servicos")
    List<ServicoPublico> servicos() {
        return servicos.listar(true).stream().map(ServicoPublico::de).toList();
    }

    @GetMapping("/api/v1/publico/profissionais")
    List<ProfissionalPublico> profissionais() {
        Map<UUID, String> nomesEspecialidades = servicos.especialidades().stream()
                .collect(Collectors.toMap(Especialidade::id, Especialidade::nome));
        return profissionais.listar(true).stream()
                .map(p -> new ProfissionalPublico(p.id(), p.nome(), p.registro().toString(),
                        p.especialidades().stream().map(nomesEspecialidades::get).collect(Collectors.toSet()),
                        p.servicos()))
                .toList();
    }

    record ServicoPublico(UUID id, String nome, String descricao, String instrucoesPreparo, int duracaoMinutos,
                          long precoCentavos, PoliticaPagamento.Tipo politicaPagamento, long valorAntecipadoCentavos) {

        static ServicoPublico de(TipoServico s) {
            return new ServicoPublico(s.id(), s.nome(), s.descricao().orElse(null), s.instrucoesPreparo().orElse(null),
                    (int) s.duracao().toMinutes(), s.preco().centavos(), s.politicaPagamento().tipo(),
                    s.politicaPagamento().valorAntecipado(s.preco()).centavos());
        }
    }

    record ProfissionalPublico(UUID id, String nome, String registro, Set<String> especialidades, Set<UUID> servicos) { }
}
