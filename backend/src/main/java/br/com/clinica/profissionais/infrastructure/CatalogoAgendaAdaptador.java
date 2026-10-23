package br.com.clinica.profissionais.infrastructure;

import br.com.clinica.compartilhado.application.ContextoClinica;
import br.com.clinica.compartilhado.domain.Periodo;
import br.com.clinica.profissionais.CatalogoAgenda;
import br.com.clinica.profissionais.application.ProfissionaisPortas.AgendaProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.ProfissionalRepositorio;
import br.com.clinica.profissionais.application.ProfissionaisPortas.TipoServicoRepositorio;
import br.com.clinica.profissionais.domain.Profissional;
import br.com.clinica.profissionais.domain.TipoServico;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Leitura para a agenda, sem as checagens de permissão dos casos de uso (a agenda faz as dela). */
@Component
@Transactional(readOnly = true)
class CatalogoAgendaAdaptador implements CatalogoAgenda {

    private final ProfissionalRepositorio profissionais;
    private final TipoServicoRepositorio servicos;
    private final AgendaProfissionalRepositorio agenda;
    private final ContextoClinica contexto;

    CatalogoAgendaAdaptador(ProfissionalRepositorio profissionais, TipoServicoRepositorio servicos,
                            AgendaProfissionalRepositorio agenda, ContextoClinica contexto) {
        this.profissionais = profissionais;
        this.servicos = servicos;
        this.agenda = agenda;
        this.contexto = contexto;
    }

    @Override
    public Optional<ServicoAgendavel> servico(UUID id) {
        return servicos.buscar(contexto.atual(), id).map(CatalogoAgendaAdaptador::paraAgenda);
    }

    @Override
    public Optional<ProfissionalAgendavel> profissional(UUID id) {
        return profissionais.buscar(contexto.atual(), id).map(CatalogoAgendaAdaptador::paraAgenda);
    }

    @Override
    public List<ProfissionalAgendavel> profissionaisAtivos() {
        return profissionais.listar(contexto.atual(), true).stream().map(CatalogoAgendaAdaptador::paraAgenda).toList();
    }

    @Override
    public List<FaixaSemanal> expediente(UUID profissional) {
        return agenda.expediente(contexto.atual(), profissional).faixas().stream()
                .map(f -> new FaixaSemanal(f.dia(), f.inicio(), f.fim())).toList();
    }

    @Override
    public List<AusenciaAgenda> ausencias(UUID profissional, Periodo janela) {
        return agenda.ausencias(contexto.atual(), profissional, janela).stream()
                .map(a -> new AusenciaAgenda(a.periodo(), a.tipo().name())).toList();
    }

    private static ServicoAgendavel paraAgenda(TipoServico s) {
        return new ServicoAgendavel(s.id(), s.nome(), s.duracao(), s.preco(),
                s.politicaPagamento().valorAntecipado(s.preco()), s.ativo());
    }

    private static ProfissionalAgendavel paraAgenda(Profissional p) {
        return new ProfissionalAgendavel(p.id(), p.nome(), p.usuarioSub(), p.servicos(), p.ativo());
    }
}
