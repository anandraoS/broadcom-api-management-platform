package io.apiplatform.controlplane.security;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Small accessor over the SecurityContext (actor name, roles, team claim). */
public final class CurrentUser {
    private CurrentUser() {}

    public static String name() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? "system" : a.getName();
    }

    public static Set<String> roles() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return Set.of();
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    public static boolean hasRole(String role) {
        return roles().contains("ROLE_" + role);
    }

    public static String team() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof Jwt jwt) {
            Object t = jwt.getClaims().get("team");
            return t == null ? null : t.toString();
        }
        return null;
    }
}
