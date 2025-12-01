package cl.folletos.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

import cl.folletos.tools.ImportRunnerConfig;
import cl.folletos.modelo.Musica;
import cl.folletos.servicio.FileStorageService;
import cl.folletos.servicio.MusicaServicio;

public class ImportFromFolderRunner {
    public static void main(String[] args) throws Exception {
        String folder = args != null && args.length > 0 ? args[0]
                : "C:\\Users\\juani\\OneDrive\\Desktop\\Cantos del Profeta";
        System.out.println("Starting import for folder: " + folder);

        SpringApplication app = new SpringApplication(ImportRunnerConfig.class);
        app.setWebApplicationType(WebApplicationType.NONE);

        // Prevent security / servlet auto-configuration from being applied in this runner
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration");
        app.setDefaultProperties(defaults);

        ConfigurableApplicationContext ctx = app.run(args == null ? new String[0] : args);
        try {
            MusicaServicio musicaServicio = ctx.getBean(MusicaServicio.class);
            FileStorageService storageService = ctx.getBean(FileStorageService.class);

            Path p = Paths.get(folder).toAbsolutePath().normalize();
            if (!Files.exists(p) || !Files.isDirectory(p)) {
                System.err.println("Folder does not exist or is not a directory: " + p.toString());
                return;
            }

            String albumTitle = p.getFileName().toString();
            Musica album = new Musica();
            album.setTitulo(albumTitle);
            album = musicaServicio.guardar(album);
            System.out.println("Created album id=" + album.getId() + " title='" + album.getTitulo() + "'");

            // make id final for use in lambda
            final Long albumIdForStorage = album.getId();

            List<String> stored = new ArrayList<>();
            Files.list(p).filter(fp -> Files.isRegularFile(fp)).forEach(fp -> {
                try {
                    String filename = fp.getFileName().toString();
                    byte[] data = Files.readAllBytes(fp);
                    String contentType = Files.probeContentType(fp);
                    String storedName = storageService.storeBytes(albumIdForStorage, data, filename, "audio", contentType);
                    if (storedName != null) {
                        stored.add(storedName);
                        System.out.println("Imported file: " + filename + " -> " + storedName);
                    } else {
                        System.out.println("Skipped (not stored as audio): " + filename);
                    }
                } catch (Exception ex) {
                    System.err.println("Error importing file " + fp.toString() + ": " + ex.getMessage());
                }
            });

            if (!stored.isEmpty()) {
                album.setAudioFilenames(String.join(",", stored));
                musicaServicio.guardar(album);
                System.out.println("Saved album with " + stored.size() + " tracks.");
            } else {
                System.out.println("No audio files were imported.");
            }
        } finally {
            Thread.sleep(500);
            ctx.close();
        }
    }
}