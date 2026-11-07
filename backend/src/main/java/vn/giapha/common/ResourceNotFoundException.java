package vn.giapha.common;

import org.springframework.http.HttpStatus;

/** Khong tim thay tai nguyen (hoac tai nguyen khong thuoc dong ho dang thao tac) -> 404 */
public class ResourceNotFoundException extends ApiException {
    public ResourceNotFoundException(String what) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy " + what);
    }
}
