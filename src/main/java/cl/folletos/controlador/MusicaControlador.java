package cl.folletos.controlador;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;

import cl.folletos.modelo.Musica;
import cl.folletos.modelo.AudioTrack;
import cl.folletos.repositorio.AudioTrackRepositorio;
import cl.folletos.servicio.MusicaServicio;
import cl.folletos.servicio.FileStorageService;

@Controller
public class MusicaControlador {

    private static final Logger logger = LoggerFactory.getLogger(MusicaControlador.class);

    @Autowired
    private MusicaServicio musicaServicio;

    @Autowired
    private FileStorageService storageService;

    @Autowired
    private AudioTrackRepositorio trackRepo;

    @GetMapping("/musica")
    public String listar(Model model) {
        List<Musica> lista = musicaServicio.listarTodos();
        model.addAttribute("musicas", lista);
        return "musica/list";
    }

    @GetMapping("/musica/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Optional<Musica> m = musicaServicio.porId(id);
        if (m.isEmpty()) return "redirect:/musica";
        Musica musica = m.get();
        // Load AudioTrack entities for this album
        List<AudioTrack> archivos = trackRepo.findByMusicaIdOrderByIdAsc(musica.getId());
        model.addAttribute("musica", musica);
        model.addAttribute("archivos", archivos);
        return "musica/detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/musica/add")
    public String mostrarAgregar(Model model) {
        model.addAttribute("musica", new Musica());
        return "admin/musica_form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/add")
    public String agregar(@ModelAttribute Musica musica, @RequestParam(value = "audioFiles", required = false) MultipartFile[] audioFiles, Model model) {
        try {
            Musica saved = musicaServicio.guardar(musica);
            Long id = saved.getId();
            final Musica savedRef = new Musica(); savedRef.setId(id);
            if (audioFiles != null && audioFiles.length > 0) {
                Arrays.stream(audioFiles).filter(f -> f != null && !f.isEmpty()).forEach(f -> {
                    try {
                        String orig = StringUtils.cleanPath(f.getOriginalFilename());
                        AudioTrack t = new AudioTrack();
                        t.setOriginalName(orig);
                        t.setMusica(savedRef);
                        t = trackRepo.save(t); // generate id
                        String filename = storageService.computeFilenameWithId(orig, t.getId());
                        storageService.storeFileWithGivenName(id, f, filename, "audio");
                        t.setFilename(filename);
                        trackRepo.save(t);
                    } catch (IOException e) {
                        logger.error("Error guardando archivo de audio: {}", e.getMessage());
                    }
                });
            }
            return "redirect:/musica/" + id;
        } catch (Exception ex) {
            logger.error("Error al crear musica: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error al guardar: " + ex.getMessage());
            model.addAttribute("musica", musica);
            return "admin/musica_form";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/musica/edit/{id}")
    public String mostrarEditar(@PathVariable Long id, Model model) {
        Optional<Musica> m = musicaServicio.porId(id);
        if (m.isEmpty()) return "redirect:/musica";
        model.addAttribute("musica", m.get());
        return "admin/musica_form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/edit")
    public String editar(@ModelAttribute Musica musica, @RequestParam(value = "audioFiles", required = false) MultipartFile[] audioFiles, Model model) {
        Optional<Musica> opt = musicaServicio.porId(musica.getId());
        if (opt.isEmpty()) return "redirect:/musica";
        Musica existing = opt.get();
        final Musica existingRef = new Musica(); existingRef.setId(existing.getId());
        existing.setTitulo(musica.getTitulo());
        existing.setAno(musica.getAno());
        existing.setDescripcion(musica.getDescripcion());
        try {
            if (audioFiles != null && audioFiles.length > 0) {
                Arrays.stream(audioFiles).filter(f -> f != null && !f.isEmpty()).forEach(f -> {
                    try {
                        String orig = StringUtils.cleanPath(f.getOriginalFilename());
                        AudioTrack t = new AudioTrack();
                        t.setOriginalName(orig);
                        t.setMusica(existingRef);
                        t = trackRepo.save(t);
                        String filename = storageService.computeFilenameWithId(orig, t.getId());
                        storageService.storeFileWithGivenName(existing.getId(), f, filename, "audio");
                        t.setFilename(filename);
                        trackRepo.save(t);
                    } catch (IOException e) {
                        logger.error("Error guardando archivo de audio: {}", e.getMessage());
                    }
                });
            }
            musicaServicio.guardar(existing);
            return "redirect:/musica/" + existing.getId();
        } catch (Exception ex) {
            logger.error("Error al editar musica: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error al guardar: " + ex.getMessage());
            model.addAttribute("musica", existing);
            return "admin/musica_form";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/delete/{id}")
    public String eliminar(@PathVariable Long id) throws IOException {
        Optional<Musica> opt = musicaServicio.porId(id);
        if (opt.isPresent()) {
            Musica m = opt.get();
            List<AudioTrack> tracks = trackRepo.findByMusicaIdOrderByIdAsc(m.getId());
            for (AudioTrack t : tracks) {
                try { storageService.deleteFile(m.getId(), t.getFilename()); } catch (IOException e) { /* ignore */ }
            }
            musicaServicio.eliminar(m);
        }
        return "redirect:/musica";
    }

    // Servir archivos de audio para Musica (soporta Range)
    @GetMapping("/musica/files/{id}/{filename}")
    public ResponseEntity<Resource> servirArchivo(@PathVariable Long id, @PathVariable String filename,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) throws IOException {
        Resource resource = storageService.loadAsResource(id, filename);
        if (resource == null) return ResponseEntity.notFound().build();

        Path filePath = Paths.get(resource.getURI());
        long fileLength = Files.size(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "audio/mpeg";

        boolean inline = !download;

        if (rangeHeader == null) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            if (inline) headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
            else headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
            InputStreamResource body = new InputStreamResource(Files.newInputStream(filePath));
            return ResponseEntity.ok().headers(headers).contentLength(fileLength).body(body);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange r = ranges.get(0);
        long start = r.getRangeStart(fileLength);
        long end = r.getRangeEnd(fileLength);
        long rangeLength = end - start + 1;

        InputStream is = Files.newInputStream(filePath);
        is.skip(start);
        InputStreamResource body = new InputStreamResource(is);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileLength);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (inline) {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"");
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).contentLength(rangeLength).body(body);
        } else {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).contentLength(rangeLength).body(body);
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/musica/upload")
    public String mostrarUpload(@RequestParam(value = "albumId", required = false) Long albumId, Model model) {
        List<Musica> albums = musicaServicio.listarTodos();
        model.addAttribute("albums", albums);
        model.addAttribute("selectedAlbumId", albumId);
        return "admin/musica_upload";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/upload")
    public String uploadToAlbum(@RequestParam(value = "albumId", required = false) Long albumId,
                                @RequestParam(value = "newFolder", required = false) String newFolder,
                                @RequestParam(value = "audioFiles", required = false) MultipartFile[] audioFiles,
                                @RequestParam(value = "audioUrls", required = false) String audioUrls,
                                @RequestParam(value = "storeLinksOnly", required = false, defaultValue = "false") boolean storeLinksOnly,
                                Model model) {
        Musica album = null;
        try {
            if (newFolder != null && !newFolder.isBlank()) {
                Musica created = new Musica();
                created.setTitulo(newFolder.trim());
                album = musicaServicio.guardar(created);
            } else {
                if (albumId == null) {
                    model.addAttribute("errorMessage", "Debe seleccionar una carpeta o ingresar un nombre para crearla.");
                    model.addAttribute("albums", musicaServicio.listarTodos());
                    return "admin/musica_upload";
                }
                Optional<Musica> opt = musicaServicio.porId(albumId);
                if (opt.isEmpty()) {
                    model.addAttribute("errorMessage", "Álbum no encontrado");
                    model.addAttribute("albums", musicaServicio.listarTodos());
                    return "admin/musica_upload";
                }
                album = opt.get();
            }

            List<String> allStored = new java.util.ArrayList<>();
            final Long albumIdForStorage = album.getId();
            final Musica albumRef = new Musica(); albumRef.setId(albumIdForStorage);
            final Musica albumFinal = album;

            // multipart file uploads
            if (audioFiles != null && audioFiles.length > 0) {
                Arrays.stream(audioFiles).filter(f -> f != null && !f.isEmpty()).forEach(f -> {
                    try {
                        String orig = StringUtils.cleanPath(f.getOriginalFilename());
                        AudioTrack t = new AudioTrack();
                        t.setOriginalName(orig);
                        t.setMusica(albumRef);
                        t = trackRepo.save(t);
                        String filename = storageService.computeFilenameWithId(orig, t.getId());
                        storageService.storeFileWithGivenName(albumIdForStorage, f, filename, "audio");
                        t.setFilename(filename);
                        trackRepo.save(t);
                        allStored.add(filename);
                    } catch (IOException e) {
                        logger.error("Error guardando archivo de audio: {}", e.getMessage());
                    }
                });
            }

            // external URLs (OneDrive/Dropbox) - multiple separated by newline or comma
            if (audioUrls != null && !audioUrls.isBlank()) {
                String[] parts = audioUrls.split("[\r\n,]+");
                for (String part : parts) {
                    String urlStr = part.trim();
                    if (urlStr.isEmpty()) continue;
                    // If caller requested to persist the link only, don't try to download the file;
                    // just store the URL string so it will be persisted in the database as a track reference.
                    if (storeLinksOnly) {
                        allStored.add(urlStr);
                        continue;
                    }
                    try {
                        String normalized = normalizeDownloadUrl(urlStr);
                        URL url = new URL(normalized);
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setInstanceFollowRedirects(true);
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(30000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                        int status = conn.getResponseCode();
                        if (status >= 300 && status < 400) {
                            String loc = conn.getHeaderField("Location");
                            if (loc != null && !loc.isBlank()) {
                                conn.disconnect();
                                url = new URL(loc);
                                conn = (HttpURLConnection) url.openConnection();
                                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                            }
                        }
                        String contentType = conn.getContentType();
                        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = in.read(buffer)) != -1) baos.write(buffer, 0, read);
                            byte[] data = baos.toByteArray();
                            String path = url.getPath();
                            String filename = null;
                            if (path != null && path.contains("/")) {
                                filename = URLDecoder.decode(path.substring(path.lastIndexOf('/') + 1), StandardCharsets.UTF_8.name());
                            }
                            if (filename == null || filename.isBlank()) filename = "from_url";
                            // create DB record first to obtain id
                            AudioTrack t = new AudioTrack();
                            t.setOriginalName(filename);
                            t.setMusica(albumFinal);
                            t = trackRepo.save(t);
                            String storedFilename = storageService.computeFilenameWithId(filename, t.getId());
                            storageService.storeBytesWithGivenName(albumIdForStorage, data, storedFilename, "audio", contentType);
                            t.setFilename(storedFilename);
                            trackRepo.save(t);
                            allStored.add(storedFilename);
                        }
                    } catch (Exception e) {
                        logger.error("Error descargando y guardando desde URL {}: {}", urlStr, e.getMessage());
                    }
                }
            }

            // tracks were persisted individually; nothing else required. Save album to update relationships if needed.
            if (!allStored.isEmpty()) musicaServicio.guardar(album);

            return "redirect:/musica/" + album.getId();
        } catch (Exception ex) {
            logger.error("Error al subir archivos al álbum: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error al subir archivos: " + ex.getMessage());
            model.addAttribute("albums", musicaServicio.listarTodos());
            return "admin/musica_upload";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/musica/import")
    public String mostrarImport(Model model) {
        model.addAttribute("albums", musicaServicio.listarTodos());
        return "admin/musica_import";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/import")
    public String importFromFolder(@RequestParam(value = "albumId", required = false) Long albumId,
                                   @RequestParam(value = "newFolder", required = false) String newFolder,
                                   @RequestParam("folderPath") String folderPath,
                                   Model model) {
        if (folderPath == null || folderPath.isBlank()) {
            model.addAttribute("errorMessage", "Debes indicar la ruta absoluta de la carpeta a importar.");
            model.addAttribute("albums", musicaServicio.listarTodos());
            return "admin/musica_import";
        }
        Musica album = null;
        try {
            if (newFolder != null && !newFolder.isBlank()) {
                Musica created = new Musica();
                created.setTitulo(newFolder.trim());
                album = musicaServicio.guardar(created);
            } else {
                if (albumId == null) {
                    model.addAttribute("errorMessage", "Debe seleccionar una carpeta o ingresar un nombre para crearla.");
                    model.addAttribute("albums", musicaServicio.listarTodos());
                    return "admin/musica_import";
                }
                Optional<Musica> opt = musicaServicio.porId(albumId);
                if (opt.isEmpty()) {
                    model.addAttribute("errorMessage", "Álbum no encontrado");
                    model.addAttribute("albums", musicaServicio.listarTodos());
                    return "admin/musica_import";
                }
                album = opt.get();
            }

            Path sourceDir = Paths.get(folderPath).toAbsolutePath().normalize();
            if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
                model.addAttribute("errorMessage", "La ruta indicada no existe o no es una carpeta: " + folderPath);
                model.addAttribute("albums", musicaServicio.listarTodos());
                return "admin/musica_import";
            }

            final Long albumIdForStorage = album.getId();
            final Musica albumFinal = album;
            List<String> storedList = new java.util.ArrayList<>();

            try (java.util.stream.Stream<Path> stream = Files.list(sourceDir)) {
                stream.filter(p -> Files.isRegularFile(p)).forEach(p -> {
                    try {
                        String filename = p.getFileName().toString();
                        String contentType = Files.probeContentType(p);
                        byte[] data = Files.readAllBytes(p);
                        // create DB record to get id
                        AudioTrack t = new AudioTrack();
                        t.setOriginalName(filename);
                        t.setMusica(albumFinal);
                        t = trackRepo.save(t);
                        String storedFilename = storageService.computeFilenameWithId(filename, t.getId());
                        storageService.storeBytesWithGivenName(albumIdForStorage, data, storedFilename, "audio", contentType);
                        t.setFilename(storedFilename);
                        trackRepo.save(t);
                        storedList.add(storedFilename);
                    } catch (Exception e) {
                        logger.error("Error importando archivo {} desde carpeta {}: {}", p.toString(), folderPath, e.getMessage());
                    }
                });
            }

            if (!storedList.isEmpty()) musicaServicio.guardar(album);

            return "redirect:/musica/" + album.getId();
        } catch (Exception ex) {
            logger.error("Error importando carpeta {}: {}", folderPath, ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error importando carpeta: " + ex.getMessage());
            model.addAttribute("albums", musicaServicio.listarTodos());
            return "admin/musica_import";
        }
    }

    // Normalize links from common providers into direct-download URLs when possible
    private String normalizeDownloadUrl(String url) {
        if (url == null) return null;
        String u = url.trim();
        // Dropbox: convert shared link to direct download if not already
        if (u.contains("dropbox.com") && !u.contains("dl=1") && !u.contains("dl=0")) {
            if (u.contains("www.dropbox.com")) {
                if (u.contains("?")) u = u + "&dl=1"; else u = u + "?dl=1";
            } else if (u.contains("dl.dropboxusercontent.com")) {
                // already direct
            }
        }
        // OneDrive: try to append download=1 for onedrive.live.com share links
        if (u.contains("onedrive.live.com") || u.contains("1drv.ms")) {
            if (u.contains("download") || u.contains("redir") || u.contains("action=download")) {
                // leave as-is; many 1drv links redirect to the real file
            } else {
                if (u.contains("?")) u = u + "&download=1"; else u = u + "?download=1";
            }
        }
        return u;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/musica/{id}/delete-file")
    public String eliminarArchivo(@PathVariable Long id, @RequestParam("filename") String filename, Model model) {
        Optional<Musica> opt = musicaServicio.porId(id);
        if (opt.isEmpty()) return "redirect:/musica";
        Musica album = opt.get();
        try {
            // If the filename is a URL (external link), treat specially
            if (filename.startsWith("http://") || filename.startsWith("https://")) {
                AudioTrack t = trackRepo.findByFilename(filename);
                if (t != null && t.getMusica() != null && t.getMusica().getId().equals(id)) {
                    trackRepo.delete(t);
                }
            } else {
                try { storageService.deleteFile(album.getId(), filename); } catch (IOException e) { logger.warn("No se pudo borrar archivo físico {}: {}", filename, e.getMessage()); }
                AudioTrack t = trackRepo.findByFilename(filename);
                if (t != null && t.getMusica() != null && t.getMusica().getId().equals(id)) {
                    trackRepo.delete(t);
                }
            }
        } catch (Exception ex) {
            logger.error("Error eliminando pista {} del álbum {}: {}", filename, id, ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error eliminando pista: " + ex.getMessage());
            model.addAttribute("musica", album);
            return "musica/detail";
        }
        return "redirect:/musica/" + id;
    }
}
