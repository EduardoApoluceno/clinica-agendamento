package br.com.clinica.profissionais.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.Dinheiro;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.PoliticaPagamento;
import br.com.clinica.profissionais.domain.TipoServico;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class TipoServicoRepositorioJpa implements TipoServicoRepositorio {

    private final TipoServicoJpa jpa;

    TipoServicoRepositorioJpa(TipoServicoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<TipoServico> buscar(ClinicaId clinica, UUID id) {
        return jpa.findByClinicaIdAndId(clinica.valor(), id).map(TipoServicoRepositorioJpa::paraDominio);
    }

    @Override
    public List<TipoServico> listar(ClinicaId clinica, boolean apenasAtivos) {
        var entidades = apenasAtivos ? jpa.findByClinicaIdAndAtivoTrueOrderByNome(clinica.valor())
                : jpa.findByClinicaIdOrderByNome(clinica.valor());
        return entidades.stream().map(TipoServicoRepositorioJpa::paraDominio).toList();
    }

    @Override
    public TipoServico salvar(TipoServico s) {
        TipoServicoEntity e = jpa.findByClinicaIdAndId(s.clinicaId().valor(), s.id())
                .map(existente -> Gravacao.exigirVersao(existente, existente.version, s.versao()))
                .orElseGet(TipoServicoEntity::new);
        e.id = s.id();
        e.clinicaId = s.clinicaId().valor();
        e.nome = s.nome();
        e.descricao = s.descricao().orElse(null);
        e.instrucoesPreparo = s.instrucoesPreparo().orElse(null);
        e.duracaoMinutos = (int) s.duracao().toMinutes();
        e.precoCentavos = s.preco().centavos();
        e.politicaPagamento = s.politicaPagamento().tipo().name();
        e.valorSinalCentavos = s.politicaPagamento().valorSinal().map(Dinheiro::centavos).orElse(null);
        e.ativo = s.ativo();
        return paraDominio(Gravacao.gravar(() -> jpa.saveAndFlush(e), "Já existe um serviço com este nome."));
    }

    @Override
    public boolean existemTodos(ClinicaId clinica, Set<UUID> ids) {
        return ids.isEmpty() || jpa.countByClinicaIdAndIdIn(clinica.valor(), ids) == ids.size();
    }

    static TipoServico paraDominio(TipoServicoEntity e) {
        var politica = new PoliticaPagamento(PoliticaPagamento.Tipo.valueOf(e.politicaPagamento),
                Optional.ofNullable(e.valorSinalCentavos).map(Dinheiro::new));
        return new TipoServico(e.id, new ClinicaId(e.clinicaId), e.nome, Optional.ofNullable(e.descricao),
                Optional.ofNullable(e.instrucoesPreparo), Duration.ofMinutes(e.duracaoMinutos),
                new Dinheiro(e.precoCentavos), politica, e.ativo, e.version);
    }
}
