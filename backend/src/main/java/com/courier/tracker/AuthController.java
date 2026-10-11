package com.courier.tracker;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.*;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
class AuthController {
  private final AuthenticationManager auth;
  private final SecurityContextRepository contextRepo;
  private final UserRepository users;

  AuthController(
      AuthenticationManager auth, SecurityContextRepository contextRepo, UserRepository users) {
    this.auth = auth;
    this.contextRepo = contextRepo;
    this.users = users;
  }

  @GetMapping("/health")
  Map<String, String> health() {
    return Map.of("status", "UP");
  }

  @GetMapping("/auth/csrf")
  Map<String, String> csrf(CsrfToken token) {
    return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
  }

  @PostMapping("/auth/login")
  Dtos.UserView login(
      @Valid @RequestBody Dtos.Login input, HttpServletRequest req, HttpServletResponse res) {
    Authentication result;
    try {
      result =
          auth.authenticate(
              UsernamePasswordAuthenticationToken.unauthenticated(
                  input.email().trim().toLowerCase(), input.password()));
    } catch (AuthenticationException e) {
      throw new ApiException(401, "Invalid email or password.");
    }
    req.getSession(true);
    req.changeSessionId();
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    contextRepo.saveContext(context, req, res);
    return Dtos.UserView.of(users.findByEmail(result.getName()).orElseThrow());
  }

  @GetMapping("/auth/me")
  Dtos.UserView me(Authentication auth) {
    return Dtos.UserView.of(users.findByEmail(auth.getName()).orElseThrow());
  }
}
