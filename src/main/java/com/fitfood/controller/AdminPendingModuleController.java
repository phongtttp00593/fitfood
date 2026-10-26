
package com.fitfood.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Tao trang gioi thieu cho nhung module chua lap trinh.
 */
@Controller
@RequestMapping("/admin")
public class AdminPendingModuleController {


    // ================= HO TRO KHACH HANG =================

    @PreAuthorize("@permissionService.allowed(authentication, 'HO_TRO_KHACH_HANG')")
    @GetMapping("/support")
    public String support(Model model) {
        return show(model, "support", "Hỗ trợ khách hàng", "HO_TRO_KHACH_HANG",
            "Danh sách yêu cầu hỗ trợ",
            "Phân công nhân viên xử lý",
            "Theo dõi tiến độ và nội dung phản hồi",
            "HoTroKhachHang");
    }

    /*
     * Chuc nang Thong ke & Bao cao da duoc chuyen sang
     * StatisticsController, xu ly tai /admin/reports.
     * Chuc nang Quan ly don hang da duoc chuyen sang
     * OrderController, xu ly tai /admin/orders.
     * Chuc nang Quan ly giao hang da duoc chuyen sang
     * DeliveryController, xu ly tai /admin/deliveries.
     * Chuc nang Quan ly khuyen mai da duoc chuyen sang
     * AdminPromotionController, xu ly tai /admin/promotions.
     * Chuc nang Quan ly khach hang da duoc chuyen sang
     * KhachHangController, xu ly tai /admin/customers.
     * Khong khai bao cac mapping nay tai day de tranh trung URL.
     */

    // ================= HAM DUNG CHUNG =================

    private String show(Model model, String module, String title,
                        String permission, String step1, String step2,
                        String step3, String tables) {

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
