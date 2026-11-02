package br.com.operadorzero.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tarefas agendadas: fila de midia, limpeza de envios e expiracao de Recons. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
