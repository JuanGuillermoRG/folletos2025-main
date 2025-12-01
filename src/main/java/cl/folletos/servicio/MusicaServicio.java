package cl.folletos.servicio;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cl.folletos.modelo.Musica;
import cl.folletos.repositorio.MusicaRepositorio;

@Service
public class MusicaServicio {

    private static final Logger logger = LoggerFactory.getLogger(MusicaServicio.class);

    @Autowired
    private MusicaRepositorio repo;

    public List<Musica> listarTodos() {
        return repo.findAllByOrderByAnoAscTituloAsc();
    }

    public Musica guardar(Musica m) {
        try {
            Musica saved = repo.save(m);
            logger.info("Musica guardada en servicio: id={}, titulo={}, ano={}", saved.getId(), saved.getTitulo(), saved.getAno());
            return saved;
        } catch (Exception ex) {
            logger.error("Error al guardar musica (titulo={}): {}", m != null ? m.getTitulo() : null, ex.getMessage(), ex);
            throw ex;
        }
    }

    public Optional<Musica> porId(Long id) {
        return repo.findById(id);
    }

    public void eliminar(Musica m) {
        repo.delete(m);
    }
}
