    
package com.fitfood.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping
    public String admin() {
        return "admin";
    }

    @PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_SAN_PHAM')")
    @GetMapping("/products")
    public String products() {
        return "admin-products";
    }
}
