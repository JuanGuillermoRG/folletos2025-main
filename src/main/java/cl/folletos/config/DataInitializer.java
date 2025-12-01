package cl.folletos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import cl.folletos.modelo.Folleto;
import cl.folletos.repositorio.FolletoRepositorio;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    @Autowired
    private FolletoRepositorio folletoRepo;

    @Autowired
    private Environment env;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try {
            String url = env.getProperty("spring.datasource.url");
            logger.info("Spring datasource.url={}", url);
        } catch (Exception e) {
            logger.warn("No se pudo leer property datasource: {}", e.getMessage());
        }

        long count = 0;
        try {
            count = folletoRepo.count();
        } catch (Exception ex) {
            logger.error("Error al contar folletos: {}", ex.getMessage());
            return;
        }

        logger.info("Folleto rows en DB: {}", count);
        if (count == 0) {
            logger.info("No se encontraron folletos; creando ejemplos de prueba...");
            // General folletos
            Folleto g1 = new Folleto();
            g1.setTitulo("Folleto General A");
            g1.setAno(1940);
            g1.setDescripcion("Ejemplo general A.");
            g1.setCategoria("FOLLETOS");
            folletoRepo.save(g1);

            Folleto g2 = new Folleto();
            g2.setTitulo("Folleto General B");
            g2.setAno(1945);
            g2.setDescripcion("Ejemplo general B.");
            g2.setCategoria("FOLLETOS");
            folletoRepo.save(g2);

            // Compaginados
            Folleto c1 = new Folleto();
            c1.setTitulo("Folleto Compaginado A");
            c1.setAno(1950);
            c1.setDescripcion("Ejemplo compaginado A.");
            c1.setCategoria("COMPAGINADOS");
            folletoRepo.save(c1);

            Folleto c2 = new Folleto();
            c2.setTitulo("Folleto Compaginado B");
            c2.setAno(1960);
            c2.setDescripcion("Ejemplo compaginado B.");
            c2.setCategoria("COMPAGINADOS");
            folletoRepo.save(c2);

            // Locales (solo PDF)
            Folleto l1 = new Folleto();
            l1.setTitulo("Folleto Local A");
            l1.setAno(1975);
            l1.setDescripcion("Ejemplo local A (solo PDF).");
            l1.setCategoria("LOCALES");
            folletoRepo.save(l1);

            Folleto l2 = new Folleto();
            l2.setTitulo("Folleto Local B");
            l2.setAno(1985);
            l2.setDescripcion("Ejemplo local B (solo PDF).");
            l2.setCategoria("LOCALES");
            folletoRepo.save(l2);

            logger.info("Se insertaron folletos de ejemplo. Reinicia la app o refresca /folletos.");
        } else {
            logger.info("Ya existen folletos en la base de datos, no se inserta semilla.");
        }
    }
}