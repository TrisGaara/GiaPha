package vn.giapha.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.giapha.common.ApiException;

public final class CurrentUser {
    private CurrentUser() {
    }

    public static UserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
    }

    public static Long id() {
        return get().id();
    }
}
