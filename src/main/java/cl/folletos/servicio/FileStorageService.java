package cl.folletos.servicio;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path rootLocation;
    private final long maxSizeBytes;

    // allowed MIME types (expanded to support common fallbacks)
    private final Set<String> allowedAudio = new HashSet<>(Arrays.asList(
            "audio/mpeg", "audio/mp3", "audio/ogg", "audio/wav", "audio/x-wav", "audio/mp4", "audio/aac", "audio/x-m4a"
    ));
    private final Set<String> allowedPdf = new HashSet<>(Arrays.asList("application/pdf"));
    private final Set<String> allowedImages = new HashSet<>(Arrays.asList("image/jpeg", "image/png", "image/webp"));

    // helper characters allowed in filenames
    private static final String FILENAME_SAFE_REGEX = "[^A-Za-z0-9._-]";

    public FileStorageService(@Value("${file.storage.location:./uploads}") String location,
                              @Value("${file.upload.max-size-bytes:52428800}") long maxSizeBytes) throws IOException {
        this.rootLocation = Paths.get(location).toAbsolutePath().normalize();
        Files.createDirectories(this.rootLocation);
        this.maxSizeBytes = maxSizeBytes;
    }

    public String storeFile(Long folletoId, MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > maxSizeBytes) {
            throw new IOException("El archivo excede el tamaño máximo permitido: " + file.getSize());
        }

        String original = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();

        String contentType = file.getContentType();
        boolean ok = false;
        if ("audio".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedAudio.contains(contentType.toLowerCase())) ok = true;
            if (!ok && (".mp3".equals(ext) || ".ogg".equals(ext) || ".wav".equals(ext) || ".m4a".equals(ext) || ".aac".equals(ext))) ok = true; // fallback by extension
            if (!ok) throw new IOException("Tipo de archivo de audio no permitido: " + contentType);
        } else if ("pdf".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedPdf.contains(contentType.toLowerCase())) ok = true;
            if (!ok && ".pdf".equals(ext)) ok = true;
            if (!ok) throw new IOException("Tipo de archivo PDF no permitido: " + contentType);
        } else if ("cover".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedImages.contains(contentType.toLowerCase())) ok = true;
            if (!ok && (".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext) || ".webp".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de imagen no permitido: " + contentType);
        }

        // Build a safe filename using the original name, keeping the extension.
        String base = (dot >= 0) ? original.substring(0, dot) : original;
        base = base.replaceAll(FILENAME_SAFE_REGEX, "_");
        if (base.length() == 0) base = "file";

        Path dir = this.rootLocation.resolve(String.valueOf(folletoId));
        Files.createDirectories(dir);

        String candidate = prefix + "_" + base + ext;
        Path target = dir.resolve(candidate);
        int count = 1;
        while (Files.exists(target)) {
            candidate = prefix + "_" + base + "(" + count + ")" + ext;
            target = dir.resolve(candidate);
            count++;
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        try { Files.setAttribute(target, "dos:readonly", false); } catch (Exception ex) { /* ignore */ }

        return candidate;
    }

    public Resource loadAsResource(Long folletoId, String filename) throws MalformedURLException {
        if (filename == null) return null;
        Path file = this.rootLocation.resolve(String.valueOf(folletoId)).resolve(filename).normalize();
        if (!Files.exists(file)) return null;
        Resource resource = new UrlResource(file.toUri());
        return resource;
    }

    public boolean deleteFile(Long folletoId, String filename) throws IOException {
        if (filename == null) return false;
        Path file = this.rootLocation.resolve(String.valueOf(folletoId)).resolve(filename).normalize();
        return Files.deleteIfExists(file);
    }

    // New: store raw bytes (downloaded from URL or other source) as if it were an uploaded file
    public String storeBytes(Long folletoId, byte[] data, String originalFilename, String prefix, String contentType) throws IOException {
        if (data == null || data.length == 0) return null;
        if (data.length > maxSizeBytes) throw new IOException("El archivo excede el tamaño máximo permitido: " + data.length);

        String original = originalFilename == null ? "file" : StringUtils.cleanPath(originalFilename);
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();

        boolean ok = false;
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if ("audio".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedAudio.contains(ct)) ok = true;
            if (!ok && (".mp3".equals(ext) || ".ogg".equals(ext) || ".wav".equals(ext) || ".m4a".equals(ext) || ".aac".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de archivo de audio no permitido: " + contentType);
        } else if ("pdf".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedPdf.contains(ct)) ok = true;
            if (!ok && ".pdf".equals(ext)) ok = true;
            if (!ok) throw new IOException("Tipo de archivo PDF no permitido: " + contentType);
        } else if ("cover".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedImages.contains(ct)) ok = true;
            if (!ok && (".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext) || ".webp".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de imagen no permitido: " + contentType);
        }

        // Build a safe filename using the original name, keeping the extension.
        String base = (dot >= 0) ? original.substring(0, dot) : original;
        base = base.replaceAll(FILENAME_SAFE_REGEX, "_");
        if (base.length() == 0) base = "file";

        Path dir = this.rootLocation.resolve(String.valueOf(folletoId));
        Files.createDirectories(dir);

        String candidate = prefix + "_" + base + (ext.isEmpty() ? "" : ext);
        Path target = dir.resolve(candidate);
        int count = 1;
        while (Files.exists(target)) {
            candidate = prefix + "_" + base + "(" + count + ")" + (ext.isEmpty() ? "" : ext);
            target = dir.resolve(candidate);
            count++;
        }

        Files.write(target, data);

        try { Files.setAttribute(target, "dos:readonly", false); } catch (Exception ex) { /* ignore */ }

        return candidate;
    }

    // Compute a safe filename using the original name and a numeric id: base(id).ext
    public String computeFilenameWithId(String originalFilename, Long id) {
        String original = originalFilename == null ? "file" : StringUtils.cleanPath(originalFilename);
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();
        String base = (dot >= 0) ? original.substring(0, dot) : original;
        base = base.replaceAll(FILENAME_SAFE_REGEX, "_");
        if (base.length() == 0) base = "file";
        return base + "(" + id + ")" + (ext.isEmpty() ? "" : ext);
    }

    // Store a MultipartFile using a desired filename (no prefix added). Validates type using the provided prefix rules.
    public String storeFileWithGivenName(Long folletoId, MultipartFile file, String desiredFilename, String prefix) throws IOException {
        if (file == null || file.isEmpty()) return null;
        if (file.getSize() > maxSizeBytes) throw new IOException("El archivo excede el tamaño máximo permitido: " + file.getSize());

        String original = StringUtils.cleanPath(file.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot).toLowerCase();

        String contentType = file.getContentType();
        boolean ok = false;
        if ("audio".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedAudio.contains(contentType.toLowerCase())) ok = true;
            if (!ok && (".mp3".equals(ext) || ".ogg".equals(ext) || ".wav".equals(ext) || ".m4a".equals(ext) || ".aac".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de archivo de audio no permitido: " + contentType);
        } else if ("pdf".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedPdf.contains(contentType.toLowerCase())) ok = true;
            if (!ok && ".pdf".equals(ext)) ok = true;
            if (!ok) throw new IOException("Tipo de archivo PDF no permitido: " + contentType);
        } else if ("cover".equalsIgnoreCase(prefix)) {
            if (contentType != null && allowedImages.contains(contentType.toLowerCase())) ok = true;
            if (!ok && (".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext) || ".webp".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de imagen no permitido: " + contentType);
        }

        Path dir = this.rootLocation.resolve(String.valueOf(folletoId));
        Files.createDirectories(dir);
        Path target = dir.resolve(desiredFilename).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        try { Files.setAttribute(target, "dos:readonly", false); } catch (Exception ex) { /* ignore */ }
        return desiredFilename;
    }

    // Store raw bytes using a desired filename (no prefix added). Validates type using the provided prefix rules.
    public String storeBytesWithGivenName(Long folletoId, byte[] data, String desiredFilename, String prefix, String contentType) throws IOException {
        if (data == null || data.length == 0) return null;
        if (data.length > maxSizeBytes) throw new IOException("El archivo excede el tamaño máximo permitido: " + data.length);

        String ext = "";
        int dot = desiredFilename.lastIndexOf('.');
        if (dot >= 0) ext = desiredFilename.substring(dot).toLowerCase();

        boolean ok = false;
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if ("audio".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedAudio.contains(ct)) ok = true;
            if (!ok && (".mp3".equals(ext) || ".ogg".equals(ext) || ".wav".equals(ext) || ".m4a".equals(ext) || ".aac".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de archivo de audio no permitido: " + contentType);
        } else if ("pdf".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedPdf.contains(ct)) ok = true;
            if (!ok && ".pdf".equals(ext)) ok = true;
            if (!ok) throw new IOException("Tipo de archivo PDF no permitido: " + contentType);
        } else if ("cover".equalsIgnoreCase(prefix)) {
            if (ct != null && allowedImages.contains(ct)) ok = true;
            if (!ok && (".jpg".equals(ext) || ".jpeg".equals(ext) || ".png".equals(ext) || ".webp".equals(ext))) ok = true;
            if (!ok) throw new IOException("Tipo de imagen no permitido: " + contentType);
        }

        Path dir = this.rootLocation.resolve(String.valueOf(folletoId));
        Files.createDirectories(dir);
        Path target = dir.resolve(desiredFilename).normalize();
        Files.write(target, data);
        try { Files.setAttribute(target, "dos:readonly", false); } catch (Exception ex) { /* ignore */ }
        return desiredFilename;
    }

}