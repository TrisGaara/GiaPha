package vn.giapha.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private String phone;
    @Enumerated(EnumType.STRING)
    private SystemRole systemRole = SystemRole.USER;
    private boolean enabled = true;
    private Instant createdAt = Instant.now();
}
