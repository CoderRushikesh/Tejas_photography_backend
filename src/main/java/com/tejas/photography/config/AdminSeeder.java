package com.tejas.photography.config;

import com.tejas.photography.entity.User;
import com.tejas.photography.enums.Role;
import com.tejas.photography.repository.UserRepository;
import com.tejas.photography.util.PhoneUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Creates the first ADMIN account from app.admin.* properties if it does not exist yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        AppProperties.Admin cfg = props.admin();
        if (cfg == null || !StringUtils.hasText(cfg.email()) || !StringUtils.hasText(cfg.password())) {
            log.warn("No admin credentials configured (app.admin.*); skipping admin seeding");
            return;
        }
        String email = cfg.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        String phone = StringUtils.hasText(cfg.phone())
                ? PhoneUtil.normalize(cfg.phone(), props.defaultCountryCode())
                : "+910000000000";
        User admin = User.builder()
                .fullName(StringUtils.hasText(cfg.name()) ? cfg.name() : "Admin")
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(cfg.password()))
                .role(Role.ADMIN)
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Seeded admin account: {}", email);
    }
}
