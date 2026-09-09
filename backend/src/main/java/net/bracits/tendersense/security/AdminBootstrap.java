package net.bracits.tendersense.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminBootstrap {
    @Bean
    CommandLineRunner createAdmin(JdbcTemplate jdbc, PasswordEncoder encoder,
        @Value("${app.admin-email}") String email, @Value("${app.admin-password}") String password) {
        return args -> jdbc.update("""
            INSERT INTO users(name,email,password_hash,role) VALUES ('TenderSense Admin',?,?, 'ADMIN')
            ON CONFLICT (email) DO NOTHING
            """, email, encoder.encode(password));
    }
}
