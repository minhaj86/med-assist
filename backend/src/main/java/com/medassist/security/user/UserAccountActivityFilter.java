package com.medassist.security.user;

import com.medassist.common.web.ProblemDetailsWriter;
import com.medassist.common.web.ProblemTypes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs after bearer-token authentication: keeps the local user directory current and blocks users whose
 * application access has been suspended by an administrator. Registered only inside the security chain.
 */
public class UserAccountActivityFilter extends OncePerRequestFilter {

    private final UserDirectoryService userDirectoryService;
    private final ProblemDetailsWriter problemWriter;

    public UserAccountActivityFilter(UserDirectoryService userDirectoryService, ProblemDetailsWriter problemWriter) {
        this.userDirectoryService = userDirectoryService;
        this.problemWriter = problemWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token
                && !userDirectoryService.recordActivityAndCheckEnabled(token.getToken())) {
            problemWriter.write(request, response, HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN, "Account suspended",
                    "Your MedAssist access is suspended. Contact an administrator.");
            return;
        }
        chain.doFilter(request, response);
    }
}
