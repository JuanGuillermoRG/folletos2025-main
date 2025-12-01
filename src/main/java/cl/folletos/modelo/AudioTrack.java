package cl.folletos.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class AudioTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalName;
    private String filename; // nombre real en disco (incluye id)

    @ManyToOne
    @JoinColumn(name = "musica_id")
    private Musica musica;

    public AudioTrack() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public Musica getMusica() { return musica; }
    public void setMusica(Musica musica) { this.musica = musica; }
}
