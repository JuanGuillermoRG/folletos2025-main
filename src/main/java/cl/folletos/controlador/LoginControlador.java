package cl.folletos.controlador;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.web.csrf.CsrfToken;

@Controller
public class LoginControlador {

    @GetMapping("/login")
    public String login(Principal principal, Model model, CsrfToken csrf) {
        // If already authenticated, redirect to home instead of showing login page
        if (principal != null) {
            return "redirect:/";
        }
        // Expose the CSRF token to Thymeleaf as _csrf (some requests may not have it otherwise)
        if (csrf != null) {
            model.addAttribute("_csrf", csrf);
        }
        return "login";
    }
}