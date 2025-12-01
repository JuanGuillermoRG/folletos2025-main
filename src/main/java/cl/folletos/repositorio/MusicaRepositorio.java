package cl.folletos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import cl.folletos.modelo.Musica;

import java.util.List;

public interface MusicaRepositorio extends JpaRepository<Musica, Long> {
    List<Musica> findAllByOrderByAnoAscTituloAsc();
}
