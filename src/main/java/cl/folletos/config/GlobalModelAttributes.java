package cl.folletos.config;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.web.csrf.CsrfToken;

@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute
    public void addCsrfToken(Model model, CsrfToken csrf) {
        if (csrf != null) {
            model.addAttribute("_csrf", csrf);
        }
    }
}
