package io.apiplatform.controlplane.security;

import io.apiplatform.controlplane.domain.ApiDefinition;
import io.apiplatform.controlplane.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Resource-level (object-level) authorization. Role checks alone are insufficient: an API_OWNER may only
 * modify APIs owned by their own team (OWASP API1: Broken Object Level Authorization).
 * The check lives in the service layer, so it applies to REST and GraphQL alike.
 */
@Component
public class AccessGuard {
    public void assertCanModify(ApiDefinition api) {
        if (CurrentUser.hasRole("ADMIN")) return;
        if (CurrentUser.hasRole("API_OWNER") && api.getOwnerTeam().equals(CurrentUser.team())) return;
        // 404 would hide existence; for management users we return 403 so the UI can explain.
        throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not own API " + api.getSlug());
    }
}
