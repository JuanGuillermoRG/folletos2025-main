package cl.folletos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

// ...existing code...

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomAuthSuccessHandler successHandler;

    public SecurityConfig(CustomAuthSuccessHandler successHandler) {
        this.successHandler = successHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                // Public: home, listado y recursos estáticos
                .requestMatchers(HttpMethod.GET, "/", "/css/**", "/js/**", "/webjars/**", "/default-ui.css", "/favicon.ico", "/error", "/login").permitAll()
                // Lectura de detalle/listado pública (folletos y música)
                .requestMatchers(HttpMethod.GET, "/contactos/**", "/folletos/**", "/files/**", "/api/**", "/musica/**").permitAll()
                // Rutas de administración (solo ADMIN)
                .requestMatchers(HttpMethod.GET, "/agregar", "/editar/**", "/eliminar/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/agregar", "/editar", "/eliminar/**").hasRole("ADMIN")
                // Panel admin general
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )
            // Use explicit custom login page so unauthenticated access to /admin/** redirects to /login
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler)
                .permitAll()
            )
            .logout(logout -> logout.logoutSuccessUrl("/").permitAll())
            .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        return http.build();
    }

    @Bean
    public UserDetailsService users(PasswordEncoder passwordEncoder) {
        // Usuario por defecto para desarrollo: admin/admin (cambiar en producción)
        UserDetails user = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("admin"))
                .roles("ADMIN")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}