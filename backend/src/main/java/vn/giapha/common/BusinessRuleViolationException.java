package vn.giapha.common;

import org.springframework.http.HttpStatus;

/**
 * Vi pham quy tac nghiep vu (du lieu dung dinh dang nhung khong hop le ve mat gia pha) -> 422.
 * Vi du: me duoc chon khong phai vo cua cha, hai nguoi cung gioi ket hon...
 */
public class BusinessRuleViolationException extends ApiException {
    public BusinessRuleViolationException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message);
    }
}
