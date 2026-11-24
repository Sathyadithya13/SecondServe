
package com.secondserve.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.http.HttpMethod;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
            .csrf(csrf -> csrf
                // Keep the existing JSON registration/login requests working.
                // CSRF remains enabled for donation creation and other requests.
                .ignoringRequestMatchers(
                    "/api/auth/register",
                    "/api/auth/login"
                )
            )
            .authorizeHttpRequests(auth -> auth

                // Public pages and authentication endpoints.
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/login.html",
                    "/css/**",
                    "/js/**",
                    "/api/auth/register",
                    "/api/auth/login",
                    "/error"
                ).permitAll()

                // Only donors may create donations.
                .requestMatchers(
                    org.springframework.http.HttpMethod.POST,
                    "/api/donations"
                ).hasRole("DONOR")

                // Donors may view their own donations and statistics.
                .requestMatchers(
                    "/api/donations/mine",
                    "/api/donations/stats"
                ).hasRole("DONOR")

                // Only recipients may browse available donations.
                .requestMatchers(
                    "/api/donations/available"
                ).hasRole("RECIPIENT")

                                // Only recipients can create reservations.
                .requestMatchers(
                    HttpMethod.POST,
                    "/api/reservations"
                ).hasRole("RECIPIENT")

                // Only recipients can view their reservation history.
                .requestMatchers(
                    HttpMethod.GET,
                    "/api/reservations/mine"
                ).hasRole("RECIPIENT")

                // Only recipients can cancel their reservations.
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/reservations/*/cancel"
                ).hasRole("RECIPIENT")

                // Both donors and recipients can confirm pickup.
                .requestMatchers(
                    HttpMethod.PATCH,
                    "/api/reservations/*/confirm-pickup"
                ).hasAnyRole("DONOR", "RECIPIENT")

                // Every other endpoint requires authentication.
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration)
            throws Exception {

        return configuration.getAuthenticationManager();
    }
}
