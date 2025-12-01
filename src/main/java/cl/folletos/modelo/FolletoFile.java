package cl.folletos.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class FolletoFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String originalName;
    private String filename; // stored filename
    private String type; // pdf, audio, cover

    @ManyToOne
    @JoinColumn(name = "folleto_id")
    private Folleto folleto;

    public FolletoFile() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Folleto getFolleto() { return folleto; }
    public void setFolleto(Folleto folleto) { this.folleto = folleto; }
}
