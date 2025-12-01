package cl.folletos.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Folleto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String titulo;
    private Integer ano;

    // Rutas/keys relativas a files almacenados (legacy single-file fields kept for compatibility)
    private String coverFilename;
    private String pdfFilename;
    private String audioFilename;

    private String descripcion;

    // Categoria para distinguir entre folletos locales y combinados (COMBINADO, LOCAL)
    @Column(length = 30)
    private String categoria;

    // New: related files (multiple PDFs or audio tracks). Cascade so saving Folleto persists these.
    @OneToMany(mappedBy = "folleto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<FolletoFile> files = new ArrayList<>();

    public Folleto() {}

    // getters y setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public Integer getAno() { return ano; }
    public void setAno(Integer ano) { this.ano = ano; }

    public String getCoverFilename() { return coverFilename; }
    public void setCoverFilename(String coverFilename) { this.coverFilename = coverFilename; }

    public String getPdfFilename() { return pdfFilename; }
    public void setPdfFilename(String pdfFilename) { this.pdfFilename = pdfFilename; }

    public String getAudioFilename() { return audioFilename; }
    public void setAudioFilename(String audioFilename) { this.audioFilename = audioFilename; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public List<FolletoFile> getFiles() { return files; }
    public void setFiles(List<FolletoFile> files) { this.files = files; }
}