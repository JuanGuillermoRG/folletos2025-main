package cl.folletos;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
public class IntegrationControllerEditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FolletoServicio folletoServicio;

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testEditEndpointUpdatesTitle() throws Exception {
        Folleto f = new Folleto();
        f.setTitulo("BeforeTitle");
        f.setAno(2025);
        f = folletoServicio.guardar(f);
        Long id = f.getId();

        // perform multipart POST with fields (no files)
        MockMultipartFile emptyFile = new MockMultipartFile("dummy", "", MediaType.TEXT_PLAIN_VALUE, new byte[0]);

        mockMvc.perform(multipart("/admin/folletos/edit")
                .file(emptyFile)
                .param("id", id.toString())
                .param("titulo", "10")
                .param("ano", "2025")
                .param("categoria", f.getCategoria() == null ? "FOLLETOS" : f.getCategoria())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/folletos/*"));

        Folleto updated = folletoServicio.porId(id).orElseThrow();
        assertEquals("10", updated.getTitulo());
    }
}
