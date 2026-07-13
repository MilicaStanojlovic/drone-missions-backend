package com.project.drone_missions.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns a 401 with a JSON body shaped like the API's {@code ErrorResponse}, for
 * requests that reach a protected endpoint without valid authentication (missing
 * or invalid bearer token). Keeps unauthorized responses consistent with the rest
 * of the API instead of Spring Security's default blank/HTML body. The body is a
 * fixed constant, so it is written directly rather than through a JSON mapper.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String BODY =
            "{\"data\":null,\"status\":\"UNAUTHORIZED\",\"message\":\"Authentication required\"}";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(BODY);
    }
}
