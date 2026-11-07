package vn.giapha.security;

import vn.giapha.user.SystemRole;

public record UserPrincipal(Long id, String username, SystemRole role) {
    public boolean isSuperAdmin() {
        return role == SystemRole.SUPER_ADMIN;
    }
}
