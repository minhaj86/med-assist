package com.medassist.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Session")
public class MeController {

    private final CurrentUserProvider currentUserProvider;

    public MeController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    public record MeResponse(String id, String username, String displayName, List<String> roles) {
    }

    @GetMapping("/api/me")
    @Operation(summary = "The authenticated user and their application roles")
    public MeResponse me() {
        CurrentUser user = currentUserProvider.current();
        return new MeResponse(user.id(), user.username(), user.displayName(), user.roles().stream().sorted().toList());
    }
}
