package vn.giapha.common;

import org.springframework.http.HttpStatus;

/** Du lieu da ton tai (vd: quan he hon nhan bi trung) -> 409 */
public class DuplicateResourceException extends ApiException {
    public DuplicateResourceException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
