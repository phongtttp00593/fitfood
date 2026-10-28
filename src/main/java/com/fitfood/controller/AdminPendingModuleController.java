
package com.fitfood.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Trang ho tro Admin da duoc thay bang giao dien chat that.
 * Cac module admin khac do controller rieng xu ly.
 */
@Controller
@RequestMapping("/admin")
public class AdminPendingModuleController {

    @PreAuthorize("@permissionService.allowed(authentication, 'HO_TRO_KHACH_HANG')")
    @GetMapping("/support")
    public String support() {
        return "admin-support-chat";
    }
}
