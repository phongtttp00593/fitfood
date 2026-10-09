
package com.fitfood.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    @GetMapping("/admin/products")
    public String products() {
        return "admin-products";
    }
}
