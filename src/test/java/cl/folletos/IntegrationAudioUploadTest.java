package cl.folletos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import cl.folletos.modelo.Folleto;
import cl.folletos.modelo.FolletoFile;
import cl.folletos.repositorio.FolletoFileRepositorio;
import cl.folletos.servicio.FileStorageService;
import cl.folletos.servicio.FolletoServicio;

@SpringBootTest
public class IntegrationAudioUploadTest {
    private static final Logger logger = LoggerFactory.getLogger(IntegrationAudioUploadTest.class);

    @Autowired
    private FolletoServicio folletoServicio;

    @Autowired
    private FileStorageService storageService;

    @Autowired
    private FolletoFileRepositorio fileRepo;

    @Test
    public void testMultipleAudioPersist() throws Exception {
        // Create a Folleto
        Folleto f = new Folleto();
        f.setTitulo("Test Audio Multiple");
        f.setAno(2025);
        f = folletoServicio.guardar(f);
        Long id = f.getId();
        logger.info("Created Folleto id={}", id);

        // store two small fake mp3 files
        byte[] b1 = new byte[] {0x00, 0x11, 0x22};
        byte[] b2 = new byte[] {0x33, 0x44, 0x55};
        String fn1 = storageService.storeBytes(id, b1, "test_part1.mp3", "audio", "audio/mpeg");
        String fn2 = storageService.storeBytes(id, b2, "test_part2.mp3", "audio", "audio/mpeg");
        logger.info("Stored files: {}, {}", fn1, fn2);

        // create FolletoFile entries and attach
        FolletoFile ff1 = new FolletoFile();
        ff1.setOriginalName("test_part1.mp3"); ff1.setFilename(fn1); ff1.setType("audio"); ff1.setFolleto(f);
        FolletoFile ff2 = new FolletoFile();
        ff2.setOriginalName("test_part2.mp3"); ff2.setFilename(fn2); ff2.setType("audio"); ff2.setFolleto(f);
        f.getFiles().add(ff1);
        f.getFiles().add(ff2);

        // save parent (cascade should persist children)
        f = folletoServicio.guardar(f);

        Optional<Folleto> re = folletoServicio.porId(id);
        assertTrue(re.isPresent(), "Folleto should exist after save");
        Folleto loaded = re.get();
        int audioCount = 0;
        for (FolletoFile x : loaded.getFiles()) if ("audio".equalsIgnoreCase(x.getType())) audioCount++;
        logger.info("Audio files persisted for folleto id={} : count={}", id, audioCount);
        assertTrue(audioCount >= 2, "Expected at least 2 audio files persisted");

        // cleanup: not deleting DB entries to keep test simple (test DB will update)
    }
}
