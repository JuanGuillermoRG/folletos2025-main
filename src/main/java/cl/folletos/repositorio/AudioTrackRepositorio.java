package cl.folletos.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import cl.folletos.modelo.AudioTrack;

public interface AudioTrackRepositorio extends JpaRepository<AudioTrack, Long> {
    AudioTrack findByFilename(String filename);
    List<AudioTrack> findByMusicaIdOrderByIdAsc(Long musicaId);
}
