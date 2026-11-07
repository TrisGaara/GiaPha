package vn.giapha.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * "Che do nguoi xem" (xem thu duoi goc nhin Khach).
 * <p>
 * Client gui header {@code X-View-As: VIEWER} -> PermissionService HA quyen request do xuong thanh
 * mot Khach (VIEWER) an danh: khong gan voi nut nao tren pha he, khong co nhanh quan ly.
 * Moi du lieu tra ve vi the di qua DUNG logic quyen rieng tu ma tai khoan Khach that dang dung.
 * <p>
 * An toan: header chi co the HA quyen, khong bao gio nang quyen, va chi co tac dung sau khi nguoi dung
 * da qua buoc kiem tra la thanh vien dong ho. Ngoai request HTTP (job chay nen) luon tra ve false.
 */
public final class ViewAs {
    public static final String HEADER = "X-View-As";
    public static final String GUEST = "VIEWER";

    private ViewAs() {
    }

    /** Request hien tai co yeu cau xem duoi goc nhin Khach khong */
    public static boolean isGuestPreview() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            return GUEST.equalsIgnoreCase(request.getHeader(HEADER));
        }
        return false;
    }
}
