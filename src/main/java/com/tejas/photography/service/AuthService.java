package com.tejas.photography.service;

import com.tejas.photography.config.AppProperties;
import com.tejas.photography.dto.LoginRequest;
import com.tejas.photography.dto.RegisterRequest;
import com.tejas.photography.dto.SessionResponse;
import com.tejas.photography.entity.User;
import com.tejas.photography.enums.Role;
import com.tejas.photography.exception.ConflictException;
import com.tejas.photography.exception.ResourceNotFoundException;
import com.tejas.photography.repository.UserRepository;
import com.tejas.photography.security.JwtService;
import com.tejas.photography.security.UserPrincipal;
import com.tejas.photography.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppProperties props;

    /** Registers a customer (role USER) and logs them in straight away by returning a session. */
    @Transactional
    public SessionResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email already registered");
        }
        User user = userRepository.save(User.builder()
                .fullName(req.name().trim())
                .email(email)
                .phone(PhoneUtil.normalize(req.phone(), props.defaultCountryCode()))
                .password(passwordEncoder.encode(req.password()))
                .role(Role.USER)
                .enabled(true)
                .build());
        return SessionResponse.of(user, jwtService.generateToken(UserPrincipal.from(user)));
    }

    /**
     * The login screen has a User tab and an Admin tab. A customer account cannot log in through the Admin tab
     * and the admin account cannot use the User tab; both get the generic message the frontend shows.
     */
    @Transactional(readOnly = true)
    public SessionResponse login(LoginRequest req) {
        boolean adminTab = "admin".equalsIgnoreCase(req.role());
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email().trim().toLowerCase(), req.password()));
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        if (adminTab != (principal.getRole() == Role.ADMIN)) {
            throw new BadCredentialsException(adminTab ? "Invalid admin credentials" : "Invalid email or password");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return SessionResponse.of(user, jwtService.generateToken(principal));
    }

    @Transactional(readOnly = true)
    public SessionResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return SessionResponse.of(user, null);
    }
}
