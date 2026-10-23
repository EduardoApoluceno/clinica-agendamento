package br.com.clinica.auditoria.infrastructure;

import br.com.clinica.auditoria.application.CadeiaAuditoria;
import br.com.clinica.auditoria.application.VerificarCadeiaAuditoria;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AuditoriaConfig {

    @Bean
    VerificarCadeiaAuditoria verificarCadeiaAuditoria(CadeiaAuditoria cadeia) {
        return new VerificarCadeiaAuditoria(cadeia);
    }

    /** Verificação diária às 03:00 no fuso da clínica (job persistido, executa em uma só instância). */
    @Bean
    RecurringTask<Void> tarefaVerificarCadeiaAuditoria(VerificarCadeiaAuditoria caso,
                                                       @Value("${app.clinica.fuso-horario}") String fuso) {
        return Tasks.recurring("verificar-cadeia-auditoria", Schedules.cron("0 0 3 * * *", ZoneId.of(fuso)))
                .execute((instancia, contexto) -> caso.executar());
    }
}
