package cl.folletos.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;

@Component
public class CustomAuthSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private static final List<String> STATIC_PATH_SEGMENTS = Arrays.asList("/css/", "/js/", "/webjars/", "/static/");
    private static final List<String> STATIC_EXTENSIONS = Arrays.asList(
            ".css", ".js", ".png", ".jpg", ".jpeg", ".svg", ".ico", ".woff", ".woff2", ".map", ".ttf", ".eot"
    );

    private final RequestCache requestCache;

    public CustomAuthSuccessHandler(ObjectProvider<RequestCache> requestCacheProvider, @Value("/") String defaultTargetUrl) {
        // Obtain RequestCache if available; otherwise use a local HttpSessionRequestCache to avoid depending on a bean defined in SecurityConfig
        this.requestCache = requestCacheProvider.getIfAvailable(HttpSessionRequestCache::new);
        setDefaultTargetUrl(defaultTargetUrl);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null) {
            String target = savedRequest.getRedirectUrl();
            if (target != null) {
                String lower = target.toLowerCase();
                // If the saved request points to a static resource, remove it and let handler use defaultTargetUrl or continue handler behavior
                for (String seg : STATIC_PATH_SEGMENTS) {
                    if (lower.contains(seg)) {
                        requestCache.removeRequest(request, response);
                        super.onAuthenticationSuccess(request, response, authentication);
                        return;
                    }
                }
                for (String ext : STATIC_EXTENSIONS) {
                    if (lower.endsWith(ext)) {
                        requestCache.removeRequest(request, response);
                        super.onAuthenticationSuccess(request, response, authentication);
                        return;
                    }
                }
            }
        }
        // proceed normally
        super.onAuthenticationSuccess(request, response, authentication);
    }
}