package com.medassist.dashboard;

import com.medassist.security.CurrentUserProvider;
import com.medassist.security.annotation.RequireClinician;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Dashboard")
@RequireClinician
public class DashboardController {

    private final DashboardQueryService queryService;
    private final CurrentUserProvider currentUser;
    private final Clock clock;

    public DashboardController(DashboardQueryService queryService, CurrentUserProvider currentUser, Clock clock) {
        this.queryService = queryService;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @GetMapping("/api/dashboard")
    @Operation(summary = "Clinician dashboard: today's encounters, recent patients, incomplete assessments, "
            + "critical safety alerts and recent assessments")
    public DashboardQueryService.Dashboard dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        Instant from = since == null ? Instant.now(clock).minus(Duration.ofHours(24)) : since;
        return queryService.dashboard(currentUser.currentUserId(), from);
    }
}
