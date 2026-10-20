package com.fitfood.controller;

import com.fitfood.auth.model.KhachHangForm;
import com.fitfood.auth.service.KhachHangService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/customers")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHACH_HANG')")
public class KhachHangController {

    private final KhachHangService khachHangService;

    public KhachHangController(KhachHangService khachHangService) {
        this.khachHangService = khachHangService;
    }

    @GetMapping
    public String danhSach(@RequestParam(defaultValue = "") String keyword,
                           @RequestParam(defaultValue = "all") String status,
                           Model model) {
        String normalizedStatus = switch (status) {
            case "active", "inactive" -> status;
            default -> "all";
        };
        model.addAttribute("customers", khachHangService.timKiemKhachHang(keyword, normalizedStatus));
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", normalizedStatus);
        model.addAttribute("total", khachHangService.demKhachHang("all"));
        model.addAttribute("activeCount", khachHangService.demKhachHang("active"));
        model.addAttribute("inactiveCount", khachHangService.demKhachHang("inactive"));
        return "admin-customers";
    }

    @GetMapping("/{id}/edit")
    public String suaHoSo(@PathVariable Long id, Model model) {
        var customer = khachHangService.timKhachHang(id);
        KhachHangForm form = new KhachHangForm();
        form.setHoTen(customer.getAccount().getFullName());
        if (customer.getProfile() != null) {
            form.setSoDienThoai(customer.getProfile().getPhone());
            form.setDiaChi(customer.getProfile().getAddress());
            form.setNgaySinh(customer.getProfile().getBirthday());
            form.setGioiTinh(customer.getProfile().getGender());
        }
        model.addAttribute("customer", customer);
        model.addAttribute("customerForm", form);
        return "admin-customer-form";
    }

    @PostMapping("/{id}/edit")
    public String capNhatHoSo(@PathVariable Long id,
                              @Valid @ModelAttribute("customerForm") KhachHangForm form,
                              BindingResult errors,
                              Model model,
                              RedirectAttributes redirect) {
        var customer = khachHangService.timKhachHang(id);
        if (errors.hasErrors()) {
            model.addAttribute("customer", customer);
            return "admin-customer-form";
        }
        khachHangService.capNhatHoSo(id, form.getHoTen(), form.getSoDienThoai(),
                form.getDiaChi(), form.getNgaySinh(), form.getGioiTinh());
        redirect.addFlashAttribute("success", "Đã cập nhật hồ sơ khách hàng.");
        return "redirect:/admin/customers";
    }

    @PostMapping("/{id}/toggle")
    public String doiTrangThai(@PathVariable Long id, RedirectAttributes redirect) {
        boolean active = khachHangService.doiTrangThaiTaiKhoan(id);
        redirect.addFlashAttribute("success", active
                ? "Đã mở lại tài khoản khách hàng."
                : "Đã khóa tài khoản khách hàng.");
        return "redirect:/admin/customers";
    }
}
