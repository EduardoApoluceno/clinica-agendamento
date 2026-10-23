package br.com.clinica.profissionais.adapters.in.web;

import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.profissionais.application.GerirServicos;
import br.com.clinica.profissionais.application.ProfissionaisPortas.Especialidade;
import br.com.clinica.profissionais.domain.PoliticaPagamento;
import br.com.clinica.profissionais.domain.TipoServico;
import br.com.clinica.profissionais.domain.TipoServico.DadosServico;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ServicosController {

    private final GerirServicos gerir;

    ServicosController(GerirServicos gerir) {
        this.gerir = gerir;
    }

    @GetMapping("/api/v1/servicos")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    List<ServicoResposta> listar(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        return gerir.listar(!incluirInativos).stream().map(ServicoResposta::de).toList();
    }

    @PostMapping("/api/v1/servicos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    ServicoResposta cadastrar(@Valid @RequestBody ServicoRequisicao req) {
        return ServicoResposta.de(gerir.cadastrar(req.paraDados()));
    }

    @PutMapping("/api/v1/servicos/{id}")
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    ServicoResposta atualizar(@PathVariable UUID id, @Valid @RequestBody ServicoRequisicao req) {
        return ServicoResposta.de(gerir.atualizar(id, req.paraDados(), req.ativo() == null || req.ativo(),
                req.versao() == null ? -1 : req.versao()));
    }

    @GetMapping("/api/v1/especialidades")
    @PreAuthorize("hasAuthority('profissionais:ler')")
    List<Especialidade> especialidades() {
        return gerir.especialidades();
    }

    @PostMapping("/api/v1/especialidades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('profissionais:administrar')")
    Especialidade criarEspecialidade(@Valid @RequestBody EspecialidadeRequisicao req) {
        return gerir.criarEspecialidade(req.nome());
    }

    record EspecialidadeRequisicao(@NotBlank @Size(max = 120) String nome) { }

    record ServicoRequisicao(@NotBlank @Size(max = 120) String nome, @Size(max = 1000) String descricao,
                             @Size(max = 2000) String instrucoesPreparo,
                             @NotNull @Min(5) @Max(480) Integer duracaoMinutos,
                             @NotNull @Min(0) @Max(100_000_000) Long precoCentavos,
                             @NotNull PoliticaPagamento.Tipo politicaPagamento,
                             @Min(1) Long valorSinalCentavos, Boolean ativo, Long versao) {

        DadosServico paraDados() {
            var politica = new PoliticaPagamento(politicaPagamento,
                    Optional.ofNullable(valorSinalCentavos).map(Dinheiro::new));
            return new DadosServico(nome, Optional.ofNullable(descricao), Optional.ofNullable(instrucoesPreparo),
                    Duration.ofMinutes(duracaoMinutos), new Dinheiro(precoCentavos), politica);
        }
    }

    record ServicoResposta(UUID id, String nome, String descricao, String instrucoesPreparo, int duracaoMinutos,
                           long precoCentavos, PoliticaPagamento.Tipo politicaPagamento, Long valorSinalCentavos,
                           boolean ativo, long versao) {

        static ServicoResposta de(TipoServico s) {
            return new ServicoResposta(s.id(), s.nome(), s.descricao().orElse(null),
                    s.instrucoesPreparo().orElse(null), (int) s.duracao().toMinutes(), s.preco().centavos(),
                    s.politicaPagamento().tipo(), s.politicaPagamento().valorSinal().map(Dinheiro::centavos).orElse(null),
                    s.ativo(), s.versao());
        }
    }
}
