package com.fitfood.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Chi tao trang gioi thieu cho nhung module chua lap trinh.
 * Moi endpoint yeu cau ROLE_ADMIN (SecurityConfig) VA quyen SQL tuong ung.
 */
@Controller
public class AdminPendingModuleController {

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_DON_HANG')")
    @GetMapping("/admin/orders")
    public String orders(Model model) {
        return show(model, "orders", "Đơn hàng", "QUAN_LY_DON_HANG",
            "Danh sách đơn hàng", "Xem chi tiết, cập nhật trạng thái, hủy đơn hàng", "Theo dõi tiến trình và lịch sử", "DonHang, ChiTietDonHang");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHUYEN_MAI')")
    @GetMapping("/admin/promotions")
    public String promotions(Model model) {
        return show(model, "promotions", "Khuyến mãi", "QUAN_LY_KHUYEN_MAI",
            "Danh sách mã giảm giá", "Thêm, sửa, ngừng kích hoạt mã", "Kiểm tra thời hạn và điều kiện giảm", "KhuyenMai");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHACH_HANG')")
    @GetMapping("/admin/customers")
    public String customers(Model model) {
        return show(model, "customers", "Khách hàng", "QUAN_LY_KHACH_HANG",
            "Danh sách khách hàng", "Xem, tìm và cập nhật hồ sơ", "Kiểm soát trạng thái tài khoản", "NguoiDung, ThongTinKhachHang");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_GIAO_HANG')")
    @GetMapping("/admin/deliveries")
    public String deliveries(Model model) {
        return show(model, "deliveries", "Giao hàng", "QUAN_LY_GIAO_HANG",
            "Danh sách giao hàng", "Cập nhật người nhận và địa chỉ", "Theo dõi trạng thái giao và thời điểm giao", "GiaoHang, DonHang");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'HOA_DON')")
    @GetMapping("/admin/invoices")
    public String invoices(Model model) {
        return show(model, "invoices", "Hóa đơn", "HOA_DON",
            "Danh sách hóa đơn", "Xem và lập hóa đơn", "Chi tiết sản phẩm, giá và tổng tiền", "HoaDon, ChiTietHoaDon");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'HO_TRO_KHACH_HANG')")
    @GetMapping("/admin/support")
    public String support(Model model) {
        return show(model, "support", "Hỗ trợ khách hàng", "HO_TRO_KHACH_HANG",
            "Danh sách yêu cầu hỗ trợ", "Phân công nhân viên xử lý", "Theo dõi tiến độ và nội dung phản hồi", "HoTroKhachHang");
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'XEM_BAO_CAO')")
    @GetMapping("/admin/reports")
    public String reports(Model model) {
        return show(model, "reports", "Thống kê & báo cáo", "XEM_BAO_CAO",
            "Tổng hợp doanh thu", "Phân tích đơn hàng và sản phẩm", "Báo cáo theo mốc thời gian", "DonHang, ThanhToan");
    }

    private String show(Model model, String module, String title, String permission,
                        String step1, String step2, String step3, String tables) {
        model.addAttribute("module", module);
        model.addAttribute("moduleTitle", title);
        model.addAttribute("modulePermission", permission);
        model.addAttribute("moduleStep1", step1);
        model.addAttribute("moduleStep2", step2);
        model.addAttribute("moduleStep3", step3);
        model.addAttribute("moduleTables", tables);
        return "admin-pending-module";
    }
}
