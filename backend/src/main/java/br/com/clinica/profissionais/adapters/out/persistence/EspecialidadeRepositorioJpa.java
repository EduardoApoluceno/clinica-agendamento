package br.com.clinica.profissionais.adapters.out.persistence;

import br.com.clinica.compartilhado.domain.ClinicaId;
import br.com.clinica.compartilhado.domain.ConflitoException;
import br.com.clinica.profissionais.application.ProfissionaisPortas.Especialidade;
import br.com.clinica.profissionais.application.ProfissionaisPortas.EspecialidadeRepositorio;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EspecialidadeRepositorioJpa implements EspecialidadeRepositorio {

    private final EspecialidadeJpa jpa;

    EspecialidadeRepositorioJpa(EspecialidadeJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Especialidade> listar(ClinicaId clinica) {
        return jpa.findByClinicaIdOrderByNome(clinica.valor()).stream().map(e -> new Especialidade(e.id, e.nome)).toList();
    }

    @Override
    public Especialidade criar(ClinicaId clinica, String nome) {
        if (jpa.existsByClinicaIdAndNomeIgnoreCase(clinica.valor(), nome)) {
            throw new ConflitoException("especialidade-duplicada", "Especialidade já cadastrada.");
        }
        EspecialidadeEntity e = new EspecialidadeEntity();
        e.id = UUID.randomUUID();
        e.clinicaId = clinica.valor();
        e.nome = nome;
        jpa.save(e);
        return new Especialidade(e.id, e.nome);
    }

    @Override
    public boolean existemTodas(ClinicaId clinica, Set<UUID> ids) {
        return ids.isEmpty() || jpa.countByClinicaIdAndIdIn(clinica.valor(), ids) == ids.size();
    }
}
