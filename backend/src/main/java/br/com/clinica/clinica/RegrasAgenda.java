package br.com.clinica.clinica;

import java.time.Duration;
import java.time.ZoneId;

/** API do módulo clínica para a agenda: fuso e prazos configurados em Configurações. */
public interface RegrasAgenda {

    Parametros parametros();

    /**
     * @param antecedenciaAgendamento mínimo entre "agora" e o início de um horário novo (paciente)
     * @param antecedenciaAlteracao mínimo para remarcar/cancelar sem falar com a recepção
     * @param hold quanto tempo um horário fica reservado esperando o pagamento antecipado
     */
    record Parametros(ZoneId fuso, Duration antecedenciaAgendamento, Duration antecedenciaAlteracao,
                      Duration horizonte, Duration hold) { }
}
