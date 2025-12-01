package cl.folletos.controlador;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.access.prepost.PreAuthorize;

import cl.folletos.modelo.Folleto;
import cl.folletos.servicio.FileStorageService;
import cl.folletos.servicio.FolletoServicio;

@Controller
public class FolletoControlador {

    private static final Logger logger = LoggerFactory.getLogger(FolletoControlador.class);

    @Autowired
    private FolletoServicio folletoServicio;

    @Autowired
    private FileStorageService storageService;

    @Value("${file.upload.max-size-bytes:52428800}")
    private long maxUploadBytes;

    @GetMapping({"/folletos","/folletos/list"})
    public String listar(@RequestParam(required = false) Integer ano, @RequestParam(required = false) String titulo, Model model) {
        // default to FOLLETOS category for the main list
        List<Folleto> lista = folletoServicio.buscarPorCategoria("FOLLETOS", ano, titulo);
        model.addAttribute("folletos", lista);
        model.addAttribute("ano", ano);
        model.addAttribute("titulo", titulo);
        // If user requested a specific year but there are no folletos, show a friendly message
        if (ano != null && (lista == null || lista.isEmpty())) {
            model.addAttribute("noResultsMessage", "No se encontraron folletos para el año " + ano + ".");
        }
        model.addAttribute("categoria", "FOLLETOS");
        model.addAttribute("categoriaLabel", "Folletos");
        return "folletos/list";
    }

    @GetMapping("/folletos/combinados")
    public String listarCombinados(@RequestParam(required = false) Integer ano, @RequestParam(required = false) String titulo, Model model) {
        List<Folleto> lista = folletoServicio.buscarPorCategoria("COMPAGINADOS", ano, titulo);
        model.addAttribute("folletos", lista);
        model.addAttribute("ano", ano);
        model.addAttribute("titulo", titulo);
        model.addAttribute("categoria", "COMPAGINADOS");
        model.addAttribute("categoriaLabel", "Folletos Compaginados");
        if (ano != null && (lista == null || lista.isEmpty())) {
            model.addAttribute("noResultsMessage", "No se encontraron folletos compaginados para el año " + ano + ".");
        }
        return "folletos/list";
    }

    @GetMapping("/folletos/locales")
    public String listarLocales(@RequestParam(required = false) Integer ano, @RequestParam(required = false) String titulo, Model model) {
        List<Folleto> lista = folletoServicio.buscarPorCategoria("LOCALES", ano, titulo);
        model.addAttribute("folletos", lista);
        model.addAttribute("ano", ano);
        model.addAttribute("titulo", titulo);
        model.addAttribute("categoria", "LOCALES");
        model.addAttribute("categoriaLabel", "Folletos Locales");
        if (ano != null && (lista == null || lista.isEmpty())) {
            model.addAttribute("noResultsMessage", "No se encontraron folletos locales para el año " + ano + ".");
        }
        return "folletos/list";
    }

    // Debug endpoint: lista todos los folletos en JSON (útil para verificar persistencia)
    @GetMapping("/api/folletos")
    @ResponseBody
    public ResponseEntity<List<Folleto>> apiList() {
        try {
            List<Folleto> all = folletoServicio.listarTodos();
            return ResponseEntity.ok(all);
        } catch (Exception ex) {
            logger.error("Error al obtener folletos para API: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/folletos/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        Optional<Folleto> f = folletoServicio.porId(id);
        if (f.isEmpty()) return "redirect:/folletos";
        model.addAttribute("folleto", f.get());
        return "folletos/detail";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/folletos/add")
    public String mostrarAgregar(Model model) {
        model.addAttribute("folleto", new Folleto());
        model.addAttribute("maxUploadBytes", maxUploadBytes);
        return "admin/folletos_form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/folletos/add")
    public String agregar(@ModelAttribute Folleto folleto,
            @RequestParam(value = "coverFile", required = false) MultipartFile cover,
            @RequestParam(value = "pdfFile", required = false) MultipartFile pdf,
            @RequestParam(value = "audioFile", required = false) MultipartFile audio,
            Model model) {
        logger.info("Solicitud de creación recibida");

        // Validate year
        if (folleto.getAno() == null || folleto.getAno() <= 0) {
            model.addAttribute("errorMessage", "El año ingresado no es válido.");
            model.addAttribute("folleto", folleto);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }

        // Server-side category validation: LOCALES requires PDF and must not include audio
        if (folleto.getCategoria() != null && "LOCALES".equalsIgnoreCase(folleto.getCategoria())) {
            if (pdf == null || pdf.isEmpty()) {
                model.addAttribute("errorMessage", "Los folletos locales requieren un archivo PDF.");
                model.addAttribute("folleto", folleto);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
            if (audio != null && !audio.isEmpty()) {
                model.addAttribute("errorMessage", "Los folletos locales no permiten archivos de audio.");
                model.addAttribute("folleto", folleto);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
        }

        Folleto saved = null;
        try {
            saved = folletoServicio.guardar(folleto);
            logger.info("Folleto inicialmente guardado (id={}, titulo={})", saved.getId(), saved.getTitulo());
        } catch (Exception ex) {
            logger.error("Error al guardar folleto inicial: {}", ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error al guardar folleto: " + ex.getMessage());
            model.addAttribute("folleto", folleto);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }

        Long id = saved.getId();
        try {
            if (cover != null && !cover.isEmpty()) {
                String fn = storageService.storeFile(id, cover, "cover");
                saved.setCoverFilename(fn);
            }
            if (pdf != null && !pdf.isEmpty()) {
                String fn = storageService.storeFile(id, pdf, "pdf");
                saved.setPdfFilename(fn);
            }
            if (audio != null && !audio.isEmpty()) {
                String fn = storageService.storeFile(id, audio, "audio");
                saved.setAudioFilename(fn);
            }
            folletoServicio.guardar(saved);
            logger.info("Folleto final guardado con archivos (id={}, titulo={})", saved.getId(), saved.getTitulo());
            return "redirect:/folletos/" + id;
        } catch (IOException ex) {
            logger.error("Error al guardar archivos para folleto id={}: {}", id, ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error al guardar archivos: " + ex.getMessage());
            model.addAttribute("folleto", saved);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }
        catch (Exception ex) {
            logger.error("Error inesperado al procesar creación de folleto id={}: {}", id, ex.getMessage(), ex);
            model.addAttribute("errorMessage", "Error inesperado: " + ex.getMessage());
            model.addAttribute("folleto", saved);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/folletos/edit/{id}")
    public String mostrarEditar(@PathVariable Long id, Model model) {
        Optional<Folleto> f = folletoServicio.porId(id);
        if (f.isEmpty()) return "redirect:/folletos";
        model.addAttribute("folleto", f.get());
        model.addAttribute("maxUploadBytes", maxUploadBytes);
        return "admin/folletos_form";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/folletos/edit")
    public String editar(@ModelAttribute Folleto folleto,
            @RequestParam(value = "coverFile", required = false) MultipartFile cover,
            @RequestParam(value = "pdfFile", required = false) MultipartFile pdf,
            @RequestParam(value = "audioFile", required = false) MultipartFile audio,
            Model model) {
        Optional<Folleto> opt = folletoServicio.porId(folleto.getId());
        if (opt.isEmpty()) return "redirect:/folletos";
        Folleto existing = opt.get();
        existing.setTitulo(folleto.getTitulo());
        // copy category from form
        existing.setCategoria(folleto.getCategoria());
        existing.setAno(folleto.getAno());
        existing.setDescripcion(folleto.getDescripcion());

        // Validate year
        if (existing.getAno() == null || existing.getAno() <= 0) {
            model.addAttribute("errorMessage", "El año ingresado no es válido.");
            model.addAttribute("folleto", existing);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }
        // Server-side category validation: LOCAL requires PDF and must not include audio
        if (existing.getCategoria() != null && "LOCALES".equalsIgnoreCase(existing.getCategoria())) {
            // If there's already a PDF stored, allow keeping it when editing (no need to re-subir)
            boolean hasExistingPdf = existing.getPdfFilename() != null && !existing.getPdfFilename().isBlank();
            if (!hasExistingPdf && (pdf == null || pdf.isEmpty())) {
                model.addAttribute("errorMessage", "Los folletos locales requieren un archivo PDF.");
                model.addAttribute("folleto", existing);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
            if (audio != null && !audio.isEmpty()) {
                model.addAttribute("errorMessage", "Los folletos locales no permiten archivos de audio.");
                model.addAttribute("folleto", existing);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
        }
        try {
            if (cover != null && !cover.isEmpty()) {
                String fn = storageService.storeFile(existing.getId(), cover, "cover");
                existing.setCoverFilename(fn);
            }
            if (pdf != null && !pdf.isEmpty()) {
                String fn = storageService.storeFile(existing.getId(), pdf, "pdf");
                existing.setPdfFilename(fn);
            }
            if (audio != null && !audio.isEmpty()) {
                String fn = storageService.storeFile(existing.getId(), audio, "audio");
                existing.setAudioFilename(fn);
            }
            folletoServicio.guardar(existing);
            return "redirect:/folletos/" + existing.getId();
        } catch (IOException ex) {
            logger.error("Error al guardar archivos para folleto id={}: {}", existing.getId(), ex.getMessage());
            model.addAttribute("errorMessage", "Error al guardar archivos: " + ex.getMessage());
            model.addAttribute("folleto", existing);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/folletos/delete/{id}")
    public String eliminar(@PathVariable Long id) throws IOException {
        Optional<Folleto> opt = folletoServicio.porId(id);
        if (opt.isPresent()) {
            Folleto f = opt.get();
            storageService.deleteFile(f.getId(), f.getCoverFilename());
            storageService.deleteFile(f.getId(), f.getPdfFilename());
            storageService.deleteFile(f.getId(), f.getAudioFilename());
            folletoServicio.eliminar(f);
        }
        return "redirect:/folletos";
    }

    @GetMapping("/files/{id}/{type}")
    public ResponseEntity<Resource> servirArchivo(@PathVariable Long id, @PathVariable String type,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) throws IOException {
        Optional<Folleto> opt = folletoServicio.porId(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Folleto f = opt.get();
        String filename = null;
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        boolean inline = true; // default show inline
        if ("pdf".equalsIgnoreCase(type)) {
            filename = f.getPdfFilename();
            contentType = MediaType.APPLICATION_PDF_VALUE;
        } else if ("audio".equalsIgnoreCase(type)) {
            filename = f.getAudioFilename();
            contentType = "audio/mpeg";
        } else if ("cover".equalsIgnoreCase(type)) {
            filename = f.getCoverFilename();
            // leave contentType to be determined after we have the actual file path
            contentType = MediaType.IMAGE_JPEG_VALUE;
        }
        if (filename == null) return ResponseEntity.notFound().build();
        Resource resource = storageService.loadAsResource(id, filename);
        if (resource == null) return ResponseEntity.notFound().build();

        Path filePath = Paths.get(resource.getURI());
        long fileLength = Files.size(filePath);

        // Determine content-type for cover using the real file path (probe may return null)
        if ("cover".equalsIgnoreCase(type)) {
            try {
                String probed = Files.probeContentType(filePath);
                if (probed != null && !probed.isBlank()) {
                    contentType = probed;
                } else {
                    // fallback to common image types based on extension
                    String name = filePath.getFileName().toString().toLowerCase();
                    if (name.endsWith(".png")) contentType = "image/png";
                    else if (name.endsWith(".webp")) contentType = "image/webp";
                    else contentType = MediaType.IMAGE_JPEG_VALUE;
                }
            } catch (IOException ex) {
                // keep default contentType (image/jpeg) if probe fails
            }
        }

        // inline unless download=true
        inline = !download;

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

    // Admin-only helper to seed sample folletos per category when missing
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/folletos/seed")
    public String seedSamples(Model model, Principal principal) {
        if (principal == null) return "redirect:/login";
        List<Folleto> all = folletoServicio.listarTodos();
        boolean hasF = false, hasC = false, hasL = false;
        for (Folleto f : all) {
            if (f.getCategoria() == null || "FOLLETOS".equalsIgnoreCase(f.getCategoria())) hasF = true;
            if ("COMPAGINADOS".equalsIgnoreCase(f.getCategoria())) hasC = true;
            if ("LOCALES".equalsIgnoreCase(f.getCategoria())) hasL = true;
        }
        if (!hasF) {
            Folleto a = new Folleto(); a.setTitulo("Folleto F1"); a.setAno(2000); a.setDescripcion("Seed FOLLETOS 1"); a.setCategoria("FOLLETOS"); folletoServicio.guardar(a);
            Folleto b = new Folleto(); b.setTitulo("Folleto F2"); b.setAno(2001); b.setDescripcion("Seed FOLLETOS 2"); b.setCategoria("FOLLETOS"); folletoServicio.guardar(b);
        }
        if (!hasC) {
            Folleto a = new Folleto(); a.setTitulo("Folleto C1"); a.setAno(1990); a.setDescripcion("Seed COMPAGINADOS 1"); a.setCategoria("COMPAGINADOS"); folletoServicio.guardar(a);
            Folleto b = new Folleto(); b.setTitulo("Folleto C2"); b.setAno(1995); b.setDescripcion("Seed COMPAGINADOS 2"); b.setCategoria("COMPAGINADOS"); folletoServicio.guardar(b);
        }
        if (!hasL) {
            Folleto a = new Folleto(); a.setTitulo("Folleto L1"); a.setAno(1980); a.setDescripcion("Seed LOCALES 1"); a.setCategoria("LOCALES"); folletoServicio.guardar(a);
            Folleto b = new Folleto(); b.setTitulo("Folleto L2"); b.setAno(1985); b.setDescripcion("Seed LOCALES 2"); b.setCategoria("LOCALES"); folletoServicio.guardar(b);
        }
        return "redirect:/folletos";
    }
}
