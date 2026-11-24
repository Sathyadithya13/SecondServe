
package com.secondserve.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

import com.secondserve.controller.dto.LoginRequest;
import com.secondserve.controller.dto.RegisterRequest;
import com.secondserve.entity.User;
import com.secondserve.repository.UserRepository;
import com.secondserve.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
// import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            UserService userService,
            UserRepository userRepository,
            AuthenticationManager authenticationManager) {

        this.userService = userService;
        this.userRepository = userRepository;
        this.authenticationManager = authenticationManager;

        this.securityContextRepository =
                new HttpSessionSecurityContextRepository();
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @Valid @RequestBody RegisterRequest request) {

        User user = userService.registerUser(
                request.getFullName(),
                request.getEmail(),
                request.getPassword(),
                request.getRole()
        );

        Map<String, Object> response = Map.of(
                "message", "Account created successfully",
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().name()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {

        try {
            String normalizedEmail = request.getEmail()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            Authentication authentication =
                    authenticationManager.authenticate(
                            UsernamePasswordAuthenticationToken.unauthenticated(
                                    normalizedEmail,
                                    request.getPassword()
                            )
                    );

            // Establish a session and rotate its ID to help prevent
            // session-fixation attacks.
            httpRequest.getSession(true);
            httpRequest.changeSessionId();

            SecurityContext securityContext =
                    SecurityContextHolder.createEmptyContext();

            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            // Explicitly persist authentication so it remains available
            // to subsequent requests using the session cookie.
            securityContextRepository.saveContext(
                    securityContext,
                    httpRequest,
                    httpResponse
            );

            User user = userRepository.findByEmail(normalizedEmail)
                    .orElseThrow(() ->
                            new IllegalStateException(
                                    "Authenticated account could not be loaded."
                            )
                    );

            Map<String, Object> response = Map.of(
                    "message", "Login successful",
                    "id", user.getId(),
                    "fullName", user.getFullName(),
                    "email", user.getEmail(),
                    "role", user.getRole().name()
            );

            return ResponseEntity.ok(response);

        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "message", "Invalid email or password."
                    ));
        }
    }
    @GetMapping("/me")
public ResponseEntity<Map<String, Object>> getCurrentUser() {

    Authentication authentication =
            SecurityContextHolder.getContext().getAuthentication();

    User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() ->
                    new IllegalStateException("User account not found.")
            );

    Map<String, Object> response = Map.of(
            "id", user.getId(),
            "fullName", user.getFullName(),
            "email", user.getEmail(),
            "role", user.getRole().name()
    );

    return ResponseEntity.ok(response);
}

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        new SecurityContextLogoutHandler()
                .logout(
                        request,
                        response,
                        SecurityContextHolder.getContext().getAuthentication()
                );

        return ResponseEntity.ok(Map.of(
                "message", "Logged out successfully"
        ));
    }
}
