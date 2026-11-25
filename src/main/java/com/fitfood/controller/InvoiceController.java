
package com.fitfood.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/invoices")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_DON_HANG')")
public class InvoiceController {

    @GetMapping
    public String list() {
        return "redirect:/admin/orders";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable long id) {
        return "redirect:/admin/orders/" + id;
    }
}
