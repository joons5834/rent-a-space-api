package rent_a_space_api_clone.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import rent_a_space_api_clone.dto.AddRoleRequest;
import rent_a_space_api_clone.dto.LoginRequest;
import rent_a_space_api_clone.dto.LoginResponse;
import rent_a_space_api_clone.dto.SignupRequest;
import rent_a_space_api_clone.dto.UserResponse;
import rent_a_space_api_clone.enums.Role;
import rent_a_space_api_clone.service.UserService;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.ArrayList;
import java.util.Collection;

@RestController
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    public UserController(AuthenticationManager authenticationManager,
                          UserService userService,
                          SecurityContextRepository securityContextRepository,
                          SecurityContextHolderStrategy securityContextHolderStrategy) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.securityContextRepository = securityContextRepository;
        this.securityContextHolderStrategy = securityContextHolderStrategy;
    }

    @PostMapping("/v0/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        Authentication authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password());
        Authentication authentication =
                this.authenticationManager.authenticate(authenticationRequest);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        // After successful authentication, get user data
        return userService.getUserLoginData(loginRequest.email())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/v0/users")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest request) {
        UserResponse user = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/v0/users/{id}/roles")
    @PreAuthorize("hasRole('ADMIN') or @userService.isUserOwner(#id, authentication.name)")
    public ResponseEntity<UserResponse> addUserRole(
        @PathVariable Long id,
        @Valid @RequestBody AddRoleRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) {
        UserResponse userResponse = userService.addUserRole(id, request);

        // Update security context if user is adding role to themselves
        Authentication currentAuth = securityContextHolderStrategy.getContext().getAuthentication();
        if (currentAuth != null
                && currentAuth.getName().equals(userResponse.getEmail())
                && !request.role().equals(Role.ADMIN)) {
            // Copy existing authorities and add the new role
            Collection<GrantedAuthority> updatedAuthorities = new ArrayList<>(currentAuth.getAuthorities());
            System.out.println("[DEBUG]: authorities before: " +updatedAuthorities);
            updatedAuthorities.add(new SimpleGrantedAuthority("ROLE_"+ request.role().name()));
            System.out.println("[DEBUG]: authorities after: " +updatedAuthorities);


            // Create new authentication with updated authorities
            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    currentAuth.getPrincipal(),
                    currentAuth.getCredentials(),
                    updatedAuthorities
            );

            // Update security context
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(newAuth);
            System.out.println("[DEBUG] new SecutrityContext: " + context);
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, httpRequest, httpResponse);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }
}