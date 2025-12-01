package cl.folletos.controlador;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
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
import cl.folletos.modelo.FolletoFile;
import cl.folletos.servicio.FileStorageService;
import cl.folletos.servicio.FolletoServicio;
import cl.folletos.repositorio.FolletoFileRepositorio;
import jakarta.servlet.http.HttpServletRequest;

@Controller
public class FolletoControlador {

    private static final Logger logger = LoggerFactory.getLogger(FolletoControlador.class);

    @Autowired
    private FolletoServicio folletoServicio;

    @Autowired
    private FileStorageService storageService;

    @Autowired
    private FolletoFileRepositorio folletoFileRepo;

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
            @RequestParam(value = "pdfFiles", required = false) MultipartFile[] pdfFiles,
            @RequestParam(value = "audioFiles", required = false) MultipartFile[] audioFiles,
            Model model, RedirectAttributes redirectAttrs) {
        logger.info("Solicitud de creación recibida");

        // Validate year
        if (folleto.getAno() == null || folleto.getAno() <= 0) {
            model.addAttribute("errorMessage", "El año ingresado no es válido.");
            model.addAttribute("folleto", folleto);
            model.addAttribute("maxUploadBytes", maxUploadBytes);
            return "admin/folletos_form";
        }

        // Server-side category validation: LOCALES requires at least one PDF and must not include audio
        if (folleto.getCategoria() != null && "LOCALES".equalsIgnoreCase(folleto.getCategoria())) {
            boolean hasPdfUpload = pdfFiles != null && pdfFiles.length>0;
            if (!hasPdfUpload) {
                model.addAttribute("errorMessage", "Los folletos locales requieren al menos un archivo PDF.");
                model.addAttribute("folleto", folleto);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
            // Only consider an audio upload present if any MultipartFile is non-empty.
            boolean hasAudioUpload = false;
            if (audioFiles != null) {
                for (MultipartFile a : audioFiles) {
                    if (a != null && !a.isEmpty()) { hasAudioUpload = true; break; }
                }
            }
            if (hasAudioUpload) {
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

            // debug: log uploaded audio/pdf counts and names
            if (audioFiles != null) {
                StringBuilder sb = new StringBuilder();
                for (MultipartFile m : audioFiles) {
                    if (m == null) continue;
                    sb.append(m.getOriginalFilename()).append(",");
                }
                logger.info("agregar: audioFiles count={} names={}", audioFiles.length, sb.toString());
            } else {
                logger.info("agregar: audioFiles is null");
            }
            if (pdfFiles != null) {
                StringBuilder sbp = new StringBuilder();
                for (MultipartFile m : pdfFiles) { if (m==null) continue; sbp.append(m.getOriginalFilename()).append(","); }
                logger.info("agregar: pdfFiles count={} names={}", pdfFiles.length, sbp.toString());
            }

            // store any uploaded pdf files
            if (pdfFiles != null) {
                boolean firstPdfSet = false;
                for (MultipartFile pf : pdfFiles) {
                    if (pf == null || pf.isEmpty()) continue;
                    String fn = storageService.storeFile(id, pf, "pdf");
                    FolletoFile ff = new FolletoFile();
                    // Use the uploaded original filename as base for display; if it already exists among this folleto's files,
                    // append a part counter so multiple uploads with the same name are shown distinctly.
                    String displayName = pf.getOriginalFilename();
                    if (displayName == null || displayName.isBlank()) displayName = fn;
                    String baseDisplay = displayName;
                    int part = 1;
                    while (originalNameExists(saved.getFiles(), displayName)) {
                        // append part suffix until unique
                         displayName = baseDisplay + " (parte " + part + ")";
                         part++;
                    }
                    ff.setOriginalName(displayName);
                    ff.setFilename(fn);
                    ff.setType("pdf");
                    ff.setFolleto(saved);
                    saved.getFiles().add(ff);
                    if (!firstPdfSet) { saved.setPdfFilename(fn); firstPdfSet = true; }
                }
            }

            // store any uploaded audio files
            if (audioFiles != null) {
                boolean firstAudioSet = false;
                for (MultipartFile af : audioFiles) {
                    if (af == null || af.isEmpty()) continue;
                    String fn = storageService.storeFile(id, af, "audio");
                    FolletoFile ff = new FolletoFile();
                    // ensure display name is unique among this folleto's files
                    String displayName = af.getOriginalFilename();
                    if (displayName == null || displayName.isBlank()) displayName = fn;
                    String baseDisplay = displayName;
                    int part = 1;
                    while (originalNameExists(saved.getFiles(), displayName)) {
                        displayName = baseDisplay + " (parte " + part + ")";
                        part++;
                    }
                    ff.setOriginalName(displayName);
                    ff.setFilename(fn);
                    ff.setType("audio");
                    ff.setFolleto(saved);
                    saved.getFiles().add(ff);
                    if (!firstAudioSet) { saved.setAudioFilename(fn); firstAudioSet = true; }
                }
            }

            folletoServicio.guardar(saved);
            logger.info("Folleto final guardado con archivos (id={}, titulo={})", saved.getId(), saved.getTitulo());
            redirectAttrs.addFlashAttribute("successMessage", "Folleto guardado correctamente.");
            // Redirect to the folleto detail page so the user can see the result of their edits
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
              @RequestParam(value = "pdfFiles", required = false) MultipartFile[] pdfFiles,
              @RequestParam(value = "audioFiles", required = false) MultipartFile[] audioFiles,
             Model model, RedirectAttributes redirectAttrs, HttpServletRequest request) {
          // debug incoming form data binding
         // TEMP DIAGNOSTIC: log request content type and parts to troubleshoot missing multipart files
         try {
             logger.info("[DIAG] Request contentType={}", request.getContentType());
             try {
                 var parts = request.getParts();
                 if (parts != null) {
                     StringBuilder sb = new StringBuilder();
                     parts.forEach(p -> sb.append(p.getName()).append("(").append(p.getSize()).append("),"));
                     logger.info("[DIAG] request.getParts(): {}", sb.toString());
                 }
             } catch (Exception e) {
                 logger.info("[DIAG] request.getParts() not available or failed: {}", e.getMessage());
             }
         } catch (Exception e) {
             logger.warn("[DIAG] unable to log request parts: {}", e.getMessage());
         }
          // normalize file arrays: remove empty entries to avoid false positives from empty inputs
          pdfFiles = filterNonEmpty(pdfFiles);
          audioFiles = filterNonEmpty(audioFiles);
          try {
             logger.info("editar: incoming Folleto from form - id={}, titulo='{}', ano={}, categoria='{}', descripcion='{}'",
                     folleto == null ? null : folleto.getId(), folleto == null ? null : folleto.getTitulo(),
                     folleto == null ? null : folleto.getAno(), folleto == null ? null : folleto.getCategoria(),
                     folleto == null ? null : folleto.getDescripcion());
            // log request parameter map for debugging client submissions
            try {
                StringBuilder pm = new StringBuilder();
                request.getParameterMap().forEach((k,v)-> {
                    pm.append(k).append("=");
                    for (int i=0;i<v.length;i++) { pm.append(v[i]); if (i<v.length-1) pm.append(','); }
                    pm.append(';');
                });
                logger.info("editar: request.params={}", pm.toString());
            } catch (Exception e) {
                logger.warn("editar: unable to dump request params: {}", e.getMessage());
            }
            // log counts and filenames of multipart arrays
            if (pdfFiles != null) {
                StringBuilder sbp = new StringBuilder();
                for (MultipartFile m : pdfFiles) {
                    sbp.append(m.getOriginalFilename()).append("(").append(m.getSize()).append("),");
                }
                logger.info("editar: pdfFiles filtered count={} namesSizes={}", pdfFiles.length, sbp.toString());
            } else {
                logger.info("editar: pdfFiles filtered is null");
            }
            if (audioFiles != null) {
                StringBuilder sba = new StringBuilder();
                for (MultipartFile m : audioFiles) {
                    sba.append(m.getOriginalFilename()).append("(").append(m.getSize()).append("),");
                }
                logger.info("editar: audioFiles filtered count={} namesSizes={}", audioFiles.length, sba.toString());
            } else {
                logger.info("editar: audioFiles filtered is null");
            }
         } catch (Exception e) { logger.warn("editar: failed to log incoming folleto: {}", e.getMessage()); }
         Optional<Folleto> opt = folletoServicio.porId(folleto.getId());
        if (opt.isEmpty()) {
            logger.warn("editar: no existing Folleto found for id={}", folleto == null ? null : folleto.getId());
            return "redirect:/folletos";
        }
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
            boolean hasExistingPdf = (existing.getPdfFilename() != null && !existing.getPdfFilename().isBlank());
            // also check related files for pdf
            if (!hasExistingPdf) {
                for (FolletoFile f : existing.getFiles()) {
                    if ("pdf".equalsIgnoreCase(f.getType())) { hasExistingPdf = true; break; }
                }
            }
            boolean hasPdfUpload = pdfFiles != null && pdfFiles.length>0;
            if (!hasExistingPdf && !hasPdfUpload) {
                model.addAttribute("errorMessage", "Los folletos locales requieren un archivo PDF.");
                model.addAttribute("folleto", existing);
                model.addAttribute("maxUploadBytes", maxUploadBytes);
                return "admin/folletos_form";
            }
            // Consider audio upload present only if any MultipartFile is non-empty.
            boolean hasAudioUpload = false;
            if (audioFiles != null) {
                for (MultipartFile a : audioFiles) {
                    if (a != null && !a.isEmpty()) { hasAudioUpload = true; break; }
                }
            }
            if (hasAudioUpload) {
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

            // debug: log uploaded audio/pdf counts and names for edit
            if (audioFiles != null) {
                StringBuilder sba = new StringBuilder();
                for (MultipartFile m : audioFiles) { if (m==null) continue; sba.append(m.getOriginalFilename()).append(","); }
                logger.info("editar: audioFiles count={} names={}", audioFiles.length, sba.toString());
            } else {
                logger.info("editar: audioFiles is null");
            }
            if (pdfFiles != null) {
                StringBuilder sbp2 = new StringBuilder();
                for (MultipartFile m : pdfFiles) { if (m==null) continue; sbp2.append(m.getOriginalFilename()).append(","); }
                logger.info("editar: pdfFiles count={} names={}", pdfFiles.length, sbp2.toString());
            }

            if (pdfFiles != null) {
                boolean firstPdfSet = (existing.getPdfFilename() != null && !existing.getPdfFilename().isBlank());
                for (MultipartFile pf : pdfFiles) {
                    if (pf == null || pf.isEmpty()) continue;
                    String fn = storageService.storeFile(existing.getId(), pf, "pdf");
                    FolletoFile ff = new FolletoFile();
                    // Use the uploaded original filename as base for display; if it already exists among this folleto's files,
                    // append a part counter so multiple uploads with the same name are shown distinctly.
                    String displayName = pf.getOriginalFilename();
                    if (displayName == null || displayName.isBlank()) displayName = fn;
                    String baseDisplay = displayName;
                    int part = 1;
                    while (originalNameExists(existing.getFiles(), displayName)) {
                        displayName = baseDisplay + " (parte " + part + ")";
                        part++;
                    }
                    ff.setOriginalName(displayName);
                    ff.setFilename(fn);
                    ff.setType("pdf");
                    ff.setFolleto(existing);
                    existing.getFiles().add(ff);
                    if (!firstPdfSet) { existing.setPdfFilename(fn); firstPdfSet = true; }
                }
            }

            if (audioFiles != null) {
                boolean firstAudioSet = (existing.getAudioFilename() != null && !existing.getAudioFilename().isBlank());
                for (MultipartFile af : audioFiles) {
                    if (af == null || af.isEmpty()) continue;
                    String fn = storageService.storeFile(existing.getId(), af, "audio");
                    FolletoFile ff = new FolletoFile();
                    // ensure display name is unique among this folleto's files
                    String displayName = af.getOriginalFilename();
                    if (displayName == null || displayName.isBlank()) displayName = fn;
                    String baseDisplay = displayName;
                    int part = 1;
                    while (originalNameExists(existing.getFiles(), displayName)) {
                        displayName = baseDisplay + " (parte " + part + ")";
                        part++;
                    }
                    ff.setOriginalName(displayName);
                    ff.setFilename(fn);
                    ff.setType("audio");
                    ff.setFolleto(existing);
                    existing.getFiles().add(ff);
                    if (!firstAudioSet) { existing.setAudioFilename(fn); firstAudioSet = true; }
                }
            }
            folletoServicio.guardar(existing);
            redirectAttrs.addFlashAttribute("successMessage", "Folleto guardado correctamente.");
            // After editing, redirect back to the admin edit page so the user can continue editing and see updated files
            return "redirect:/admin/folletos/edit/" + existing.getId();
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
            // delete cover
            storageService.deleteFile(f.getId(), f.getCoverFilename());
            // delete legacy single files
            storageService.deleteFile(f.getId(), f.getPdfFilename());
            storageService.deleteFile(f.getId(), f.getAudioFilename());
            // delete all related FolletoFile entries and their physical files
            for (FolletoFile ff : f.getFiles()) {
                try { storageService.deleteFile(f.getId(), ff.getFilename()); } catch (Exception ex) { /* ignore */ }
            }
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

        // if there are multiple files of this type, return the first one (compatibility)
        String filename = null;
        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        boolean inline = true; // default show inline
        if ("pdf".equalsIgnoreCase(type)) {
            // pick first related pdf if present
            for (FolletoFile ff : f.getFiles()) { if ("pdf".equalsIgnoreCase(ff.getType())) { filename = ff.getFilename(); break; } }
            if (filename == null) filename = f.getPdfFilename();
            contentType = MediaType.APPLICATION_PDF_VALUE;
        } else if ("audio".equalsIgnoreCase(type)) {
            for (FolletoFile ff : f.getFiles()) { if ("audio".equalsIgnoreCase(ff.getType())) { filename = ff.getFilename(); break; } }
            if (filename == null) filename = f.getAudioFilename();
            contentType = "audio/mpeg";
        } else if ("cover".equalsIgnoreCase(type)) {
            filename = f.getCoverFilename();
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
                    String name = filePath.getFileName().toString().toLowerCase();
                    if (name.endsWith(".png")) contentType = "image/png";
                    else if (name.endsWith(".webp")) contentType = "image/webp";
                    else contentType = MediaType.IMAGE_JPEG_VALUE;
                }
            } catch (IOException ex) {
                // keep default contentType (image/jpeg) if probe fails
            }
        }

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

    // New: serve a specific FolletoFile by its id
    @GetMapping("/files/{id}/file/{fileId}")
    public ResponseEntity<Resource> servirArchivoByFileId(@PathVariable Long id, @PathVariable Long fileId,
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestParam(value = "download", required = false, defaultValue = "false") boolean download) throws IOException {
        Optional<Folleto> opt = folletoServicio.porId(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        Optional<FolletoFile> off = folletoFileRepo.findById(fileId);
        if (off.isEmpty()) return ResponseEntity.notFound().build();
        FolletoFile ff = off.get();
        if (ff.getFolleto() == null || !ff.getFolleto().getId().equals(id)) return ResponseEntity.notFound().build();

        String filename = ff.getFilename();
        Resource resource = storageService.loadAsResource(id, filename);
        if (resource == null) return ResponseEntity.notFound().build();

        Path filePath = Paths.get(resource.getURI());
        long fileLength = Files.size(filePath);

        String contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        if ("pdf".equalsIgnoreCase(ff.getType())) contentType = MediaType.APPLICATION_PDF_VALUE;
        else if ("audio".equalsIgnoreCase(ff.getType())) contentType = "audio/mpeg";
        else if ("cover".equalsIgnoreCase(ff.getType())) {
            try { String probed = Files.probeContentType(filePath); if (probed != null && !probed.isBlank()) contentType = probed; } catch (IOException ex) {}
        }

        boolean inline = !download;

        if (rangeHeader == null) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, contentType);
            if (inline) headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ff.getOriginalName() + "\"");
            else headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ff.getOriginalName() + "\"");
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
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ff.getOriginalName() + "\"");
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).contentLength(rangeLength).body(body);
        } else {
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + ff.getOriginalName() + "\"");
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

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/folletos/{id}/files/delete/{fileId}")
    public String eliminarArchivoIndividual(@PathVariable Long id, @PathVariable Long fileId, RedirectAttributes redirectAttrs) throws IOException {
        Optional<Folleto> opt = folletoServicio.porId(id);
        if (opt.isEmpty()) return "redirect:/folletos";
        Optional<FolletoFile> off = folletoFileRepo.findById(fileId);
        if (off.isEmpty()) return "redirect:/admin/folletos/edit/" + id;
        FolletoFile ff = off.get();
        // ensure file belongs to the folleto
        if (ff.getFolleto() == null || ff.getFolleto().getId() == null || !ff.getFolleto().getId().equals(id)) {
            return "redirect:/admin/folletos/edit/" + id;
        }
        Folleto f = opt.get();
        // delete physical file if present
        try { storageService.deleteFile(id, ff.getFilename()); } catch (Exception ex) { /* ignore */ }
        // remove from parent's collection (orphanRemoval should remove DB entry on save)
        f.getFiles().removeIf(x -> x.getId() != null && x.getId().equals(fileId));
        // clear legacy single-file pointers if they referenced this filename
        if ("pdf".equalsIgnoreCase(ff.getType()) && ff.getFilename() != null && ff.getFilename().equals(f.getPdfFilename())) {
            f.setPdfFilename(null);
        }
        if ("audio".equalsIgnoreCase(ff.getType()) && ff.getFilename() != null && ff.getFilename().equals(f.getAudioFilename())) {
            f.setAudioFilename(null);
        }
        folletoServicio.guardar(f);
        // ensure repository does not keep orphan (safe delete)
        try { folletoFileRepo.deleteById(fileId); } catch (Exception ex) { /* ignore */ }
        // After deleting a file, return to the admin edit page so the user can continue editing
        redirectAttrs.addFlashAttribute("successMessage", "Archivo eliminado correctamente.");
        return "redirect:/admin/folletos/edit/" + id;
    }

    // New helper to check whether a display/original name already exists among a Folleto's files.
    private boolean originalNameExists(List<FolletoFile> files, String name) {
        if (name == null || files == null) return false;
        for (FolletoFile f : files) {
            if (f.getOriginalName() != null && f.getOriginalName().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    // helper to remove empty MultipartFile entries
    private MultipartFile[] filterNonEmpty(MultipartFile[] files) {
        if (files == null || files.length == 0) return null;
        List<MultipartFile> res = new ArrayList<>();
        for (MultipartFile f : files) {
            if (f != null && !f.isEmpty()) res.add(f);
        }
        if (res.isEmpty()) return null;
        return res.toArray(new MultipartFile[0]);
    }
}
