package vn.giapha.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Exception goc cua ung dung: mang theo HTTP status va ma loi (code) de client xu ly.
 * Cac exception nghiep vu cu the ke thua lop nay (xem {@link ResourceNotFoundException},
 * {@link DuplicateResourceException}, {@link BusinessRuleViolationException}).
 */
@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String message) {
        this(status, status.name(), message);
    }

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException notFound(String what) {
        return new ResourceNotFoundException(what);
    }

    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này");
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
