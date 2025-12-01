package cl.folletos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cl.folletos.modelo.Folleto;
import cl.folletos.servicio.FolletoServicio;

@SpringBootTest
public class IntegrationEditFolletoTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationEditFolletoTest.class);

    @Autowired
    private FolletoServicio folletoServicio;

    @Test
    public void testEditFolletoTitlePersists() throws Exception {
        // create a folleto
        Folleto f = new Folleto();
        f.setTitulo("Original Title");
        f.setAno(2025);
        f = folletoServicio.guardar(f);
        Long id = f.getId();
        logger.info("Created Folleto id={}", id);

        // update title as if the admin edited it
        f.setTitulo("10");
        folletoServicio.guardar(f);

        Optional<Folleto> re = folletoServicio.porId(id);
        assertEquals(true, re.isPresent(), "Folleto should exist after save");
        Folleto loaded = re.get();
        logger.info("Loaded Folleto id={} titulo={}", id, loaded.getTitulo());
        assertEquals("10", loaded.getTitulo(), "Title should be updated to '10'");
    }
}
