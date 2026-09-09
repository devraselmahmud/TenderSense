package net.bracits.tendersense.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;

    public AuthController(AuthenticationManager authenticationManager, JwtEncoder encoder) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        Instant now = Instant.now();
        List<String> roles = auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_", "")).toList();
        var claims = JwtClaimsSet.builder().issuer("tendersense").issuedAt(now)
            .expiresAt(now.plus(8, ChronoUnit.HOURS)).subject(auth.getName()).claim("roles", roles).build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        return Map.of("token", encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), "email", auth.getName(), "roles", roles);
    }

    record LoginRequest(@Email String email, @NotBlank String password) {}
}
