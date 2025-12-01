package cl.folletos.servicio;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cl.folletos.modelo.Folleto;
import cl.folletos.repositorio.FolletoRepositorio;

@Service
public class FolletoServicio {

    private static final Logger logger = LoggerFactory.getLogger(FolletoServicio.class);

    @Autowired
    private FolletoRepositorio repo;

    public List<Folleto> listarTodos() {
        return repo.findAll();
    }

    public List<Folleto> listarPorAno(Integer ano) {
        if (ano == null) return repo.findAll();
        return repo.findByAno(ano);
    }

    public List<Folleto> buscarPorTitulo(String texto) {
        if (texto == null || texto.isBlank()) return repo.findAll();
        return repo.findByTituloContainingIgnoreCase(texto);
    }

    public List<Folleto> buscar(Integer ano, String titulo) {
        boolean hasAno = ano != null;
        boolean hasTitulo = titulo != null && !titulo.isBlank();
        if (hasAno && hasTitulo) {
            return repo.findByAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(ano, titulo);
        } else if (hasAno) {
            return repo.findByAnoOrderByAnoAscTituloAsc(ano);
        } else if (hasTitulo) {
            return repo.findByTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(titulo);
        } else {
            return repo.findAllByOrderByAnoAscTituloAsc();
        }
    }

    // Search within a specific category (FOLLETOS/COMPAGINADOS/LOCALES)
    public List<Folleto> buscarPorCategoria(String categoria, Integer ano, String titulo) {
        boolean hasAno = ano != null;
        boolean hasTitulo = titulo != null && !titulo.isBlank();
        // Special handling: category FOLLETOS should include existing records with null categoria
        if (categoria != null && "FOLLETOS".equalsIgnoreCase(categoria)) {
            List<Folleto> result = new ArrayList<>();
            if (hasAno && hasTitulo) {
                result.addAll(repo.findByCategoriaAndAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc("FOLLETOS", ano, titulo));
                result.addAll(repo.findByAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(ano, titulo));
                return result;
            } else if (hasAno) {
                result.addAll(repo.findByCategoriaAndAnoOrderByAnoAscTituloAsc("FOLLETOS", ano));
                result.addAll(repo.findByAnoOrderByAnoAscTituloAsc(ano));
                return result;
            } else if (hasTitulo) {
                result.addAll(repo.findByCategoriaAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc("FOLLETOS", titulo));
                result.addAll(repo.findByTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(titulo));
                return result;
            } else {
                result.addAll(repo.findByCategoriaOrderByAnoAscTituloAsc("FOLLETOS"));
                result.addAll(repo.findByCategoriaIsNullOrderByAnoAscTituloAsc());
                return result;
            }
        }

        if (hasAno && hasTitulo) {
            return repo.findByCategoriaAndAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(categoria, ano, titulo);
        } else if (hasAno) {
            return repo.findByCategoriaAndAnoOrderByAnoAscTituloAsc(categoria, ano);
        } else if (hasTitulo) {
            return repo.findByCategoriaAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(categoria, titulo);
        } else {
            return repo.findByCategoriaOrderByAnoAscTituloAsc(categoria);
        }
    }

    public Folleto guardar(Folleto f) {
        try {
            Folleto saved = repo.save(f);
            logger.info("Folleto guardado en servicio: id={}, titulo={}, ano={}", saved.getId(), saved.getTitulo(), saved.getAno());
            return saved;
        } catch (Exception ex) {
            logger.error("Error al guardar folleto (titulo={}): {}", f != null ? f.getTitulo() : null, ex.getMessage(), ex);
            throw ex;
        }
    }

    public Optional<Folleto> porId(Long id) {
        return repo.findById(id);
    }

    public void eliminar(Folleto f) {
        repo.delete(f);
    }
}