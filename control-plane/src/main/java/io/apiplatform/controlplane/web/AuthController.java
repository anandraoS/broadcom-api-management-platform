package io.apiplatform.controlplane.web;

import io.apiplatform.controlplane.security.DevAuthProperties;
import io.apiplatform.controlplane.security.SecurityProperties;
import io.apiplatform.controlplane.web.Dtos.TokenRequest;
import io.apiplatform.controlplane.web.Dtos.TokenResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * DEVELOPMENT-ONLY identity provider (app.dev-auth.enabled=true). It issues short-lived HS256 tokens with the same
 * claims an OIDC provider would (iss, sub, exp, roles, team) so the resource-server side is production-shaped.
 * In production, disable this controller and configure an OIDC issuer; no code changes are needed elsewhere.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(prefix = "app.dev-auth", name = "enabled", havingValue = "true")
public class AuthController {
    private final DevAuthProperties dev;
    private final SecurityProperties props;
    private final JwtEncoder encoder;

    public AuthController(DevAuthProperties dev, SecurityProperties props, JwtEncoder encoder) {
        this.dev = dev;
        this.props = props;
        this.encoder = encoder;
    }

    @PostMapping("/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest r) {
        var user = dev.users().stream().filter(u -> u.username().equals(r.username())).findFirst().orElse(null);
        boolean passwordOk = MessageDigest.isEqual(
                dev.password() == null ? new byte[0] : dev.password().getBytes(StandardCharsets.UTF_8),
                r.password().getBytes(StandardCharsets.UTF_8));
        if (user == null || !passwordOk) {
            // same message for unknown user and bad password: no user enumeration
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password");
        }
        Instant now = Instant.now();
        JwtClaimsSet.Builder b = JwtClaimsSet.builder().issuer(props.issuer()).subject(user.username())
                .issuedAt(now).expiresAt(now.plusSeconds(dev.tokenTtlSeconds()))
                .claim("roles", user.roles());
        if (user.team() != null) b.claim("team", user.team());
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), b.build())).getTokenValue();
        return new TokenResponse(jwt, "Bearer", dev.tokenTtlSeconds(), user.roles(), user.team());
    }
}
