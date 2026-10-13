package com.medassist.security.user;

import com.medassist.security.annotation.RequireAdmin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin - Users")
@RequireAdmin
public class UserAdminController {

    private final UserDirectoryService userDirectoryService;

    public UserAdminController(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    public record UserResponse(String id, String username, String displayName, String email, List<String> roles,
                               boolean enabled, Instant firstSeenAt, Instant lastSeenAt) {
        static UserResponse from(UserAccount account) {
            return new UserResponse(account.getId(), account.getUsername(), account.getDisplayName(),
                    account.getEmail(), account.roleList(), account.isEnabled(), account.getFirstSeenAt(),
                    account.getLastSeenAt());
        }
    }

    public record UpdateUserAccessRequest(@NotNull Boolean enabled) {
    }

    @GetMapping
    @Operation(summary = "List users known to MedAssist (credentials and roles are managed in the identity provider)")
    public List<UserResponse> list() {
        return userDirectoryService.listUsers().stream().map(UserResponse::from).toList();
    }

    @PutMapping("/{userId}/access")
    @Operation(summary = "Enable or suspend a user's MedAssist access")
    public UserResponse updateAccess(@PathVariable String userId,
                                     @RequestBody @jakarta.validation.Valid UpdateUserAccessRequest request) {
        return UserResponse.from(userDirectoryService.setEnabled(userId, request.enabled()));
    }
}
