package cl.folletos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import cl.folletos.modelo.Folleto;
import cl.folletos.servicio.FolletoServicio;

@SpringBootTest
@AutoConfigureMockMvc
public class IntegrationEditExistingWithFilesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolletoServicio folletoServicio;

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void editExistingFolletoWithFiles() throws Exception {
        List<Folleto> all = folletoServicio.listarTodos();
        Folleto target = null;
        for (Folleto f : all) {
            if (f.getFiles() != null && !f.getFiles().isEmpty()) { target = f; break; }
        }
        if (target == null) {
            // fallback: create a new folleto with no files and fail the test to ask user to ensure one exists
            Folleto f = new Folleto(); f.setTitulo("TestNoFiles"); f.setAno(2025); f.setCategoria("FOLLETOS");
            target = folletoServicio.guardar(f);
            throw new RuntimeException("No existing Folleto with files found in DB. Created fallback id=" + target.getId());
        }

        Long id = target.getId();

        MockMultipartFile dummy = new MockMultipartFile("dummy", "", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/admin/folletos/edit")
                .file(dummy)
                .param("id", id.toString())
                .param("titulo", target.getTitulo() + " - EDITADO")
                .param("ano", String.valueOf(target.getAno() == null ? 2025 : target.getAno()))
                .param("categoria", target.getCategoria() == null ? "FOLLETOS" : target.getCategoria())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/folletos/*"));

        Folleto updated = folletoServicio.porId(id).orElseThrow();
        assertEquals(target.getTitulo() + " - EDITADO", updated.getTitulo());
    }
}
