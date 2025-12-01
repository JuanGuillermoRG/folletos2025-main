package cl.folletos.tools;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.FilterType;

@Configuration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "cl.folletos.repositorio")
@EntityScan(basePackages = "cl.folletos.modelo")
@ComponentScan(basePackages = {"cl.folletos.servicio"}, excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "cl.folletos.config.*"))
@EnableTransactionManagement
public class ImportRunnerConfig {
    // Minimal configuration for the import runner: scans only servicio and JPA repos/entities
}