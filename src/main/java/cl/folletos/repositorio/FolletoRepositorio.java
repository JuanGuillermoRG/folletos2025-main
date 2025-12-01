package cl.folletos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.folletos.modelo.Folleto;

import java.util.List;

public interface FolletoRepositorio extends JpaRepository<Folleto, Long> {
    List<Folleto> findByAno(Integer ano);
    List<Folleto> findByTituloContainingIgnoreCase(String titulo);

    // Ordered variants: order by year ascending, then title ascending
    List<Folleto> findAllByOrderByAnoAscTituloAsc();
    List<Folleto> findByAnoOrderByAnoAscTituloAsc(Integer ano);
    List<Folleto> findByTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(String titulo);
    List<Folleto> findByAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(Integer ano, String titulo);

    // Category-specific queries
    List<Folleto> findByCategoriaOrderByAnoAscTituloAsc(String categoria);
    List<Folleto> findByCategoriaIsNullOrderByAnoAscTituloAsc();
    List<Folleto> findByCategoriaAndAnoOrderByAnoAscTituloAsc(String categoria, Integer ano);
    List<Folleto> findByCategoriaAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(String categoria, String titulo);
    List<Folleto> findByCategoriaAndAnoAndTituloContainingIgnoreCaseOrderByAnoAscTituloAsc(String categoria, Integer ano, String titulo);
}