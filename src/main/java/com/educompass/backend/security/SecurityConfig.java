package com.educompass.backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.LocalDateTime;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:5174,http://localhost:5175,http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write(String.format(
                                    "{\"timestamp\":\"%s\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required to access this resource\"}",
                                    LocalDateTime.now()
                            ));
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        // Public Authentication Endpoints
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/google", "/api/auth/logout").permitAll()
                        
                        // Protected Profile Endpoints
                        .requestMatchers("/api/auth/me", "/api/auth/profile").authenticated()

                        // Public Reference & Guidance APIs (Read-Only for catalog and discussions)
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/pathways/**", "/api/careers/**", "/api/exams/**", "/api/threads/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/recommendations").permitAll()

                        // Reference Data Mutations restricted to ADMIN role (prevents student or anonymous tampering)
                        .requestMatchers(HttpMethod.POST, "/api/careers/**", "/api/exams/**", "/api/pathways/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/careers/**", "/api/exams/**", "/api/pathways/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/careers/**", "/api/exams/**", "/api/pathways/**").hasRole("ADMIN")

                        // Authenticated student forum endpoints (POST /api/threads, POST replies, PUT, DELETE)
                        .requestMatchers("/api/threads/**").authenticated()

                        // Any other request
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
