package com.fitfood.permission;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Kiểm tra quyền dựa trên VaiTro - Quyen - PhanQuyen của SQL Server.
 * Role Java ADMIN ứng với MaVaiTroCode QUAN_TRI (không phụ thuộc MaVaiTro = 2).
 * Nếu bảng quyền chưa được tạo / SQL gặp lỗi: từ chối, KHÔNG tự cho qua.
 */
@Service("permissionService")
public class PermissionService {
    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);
    private static final String ADMIN_SQL_ROLE = "QUAN_TRI";

    private final JdbcTemplate jdbc;

    public PermissionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean allowed(Authentication authentication, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()
                || permissionCode == null || permissionCode.isBlank()
                || authentication.getAuthorities().stream()
                    .noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return false;
        }
        try {
            Integer n = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.PhanQuyen AS pq
                JOIN dbo.VaiTro AS vt ON vt.MaVaiTro = pq.MaVaiTro
                JOIN dbo.Quyen AS q ON q.MaQuyen = pq.MaQuyen
                WHERE vt.MaVaiTroCode = ? AND q.MaQuyenCode = ?
                """, Integer.class, ADMIN_SQL_ROLE, permissionCode);
            return n != null && n > 0;
        } catch (DataAccessException ex) {
            log.warn("Khong the kiem tra quyen SQL ({}): {}", permissionCode, ex.getMessage());
            return false;
        }
    }

    public List<PermissionView> adminPermissions() {
        try {
            return jdbc.query("""
                SELECT q.MaQuyenCode, q.TenQuyen
                FROM dbo.PhanQuyen AS pq
                JOIN dbo.VaiTro AS vt ON vt.MaVaiTro = pq.MaVaiTro
                JOIN dbo.Quyen AS q ON q.MaQuyen = pq.MaQuyen
                WHERE vt.MaVaiTroCode = ?
                ORDER BY q.MaQuyen
                """, (rs, rowNum) -> {
                    String code = rs.getString("MaQuyenCode");
                    String status = switch (code) {
                        case "DANG_NHAP", "QUEN_MAT_KHAU", "QUAN_LY_DANH_MUC" -> "Đã hoạt động";
                        case "XEM_THUC_DON", "XEM_DANH_MUC", "XEM_CHI_TIET_SAN_PHAM",
                             "TIM_KIEM_SAN_PHAM", "QUAN_LY_SAN_PHAM", "XEM_BAO_CAO" -> "Giao diện mẫu";
                        default -> "Chưa triển khai";
                    };
                    String css = switch (status) {
                        case "Đã hoạt động" -> "perm-live";
                        case "Giao diện mẫu" -> "perm-demo";
                        default -> "perm-pending";
                    };
                    return new PermissionView(code, rs.getString("TenQuyen"), status, css);
                }, ADMIN_SQL_ROLE);
        } catch (DataAccessException ex) {
            log.warn("Chua doc duoc bang phan quyen FITFOOD: {}", ex.getMessage());
            return List.of();
        }
    }

    public static class PermissionView {
        private final String code;
        private final String name;
        private final String status;
        private final String statusClass;

        public PermissionView(String code, String name, String status, String statusClass) {
            this.code = code;
            this.name = name;
            this.status = status;
            this.statusClass = statusClass;
        }
        public String getCode() { return code; }
        public String getName() { return name; }
        public String getStatus() { return status; }
        public String getStatusClass() { return statusClass; }
    }
}
