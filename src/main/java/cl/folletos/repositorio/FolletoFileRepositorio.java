package cl.folletos.repositorio;

import org.springframework.data.jpa.repository.JpaRepository;
import cl.folletos.modelo.FolletoFile;
import java.util.List;

public interface FolletoFileRepositorio extends JpaRepository<FolletoFile, Long> {
    List<FolletoFile> findByFolletoId(Long folletoId);
}
