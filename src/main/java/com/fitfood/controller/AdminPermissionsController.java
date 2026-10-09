package com.fitfood.controller;

import com.fitfood.permission.PermissionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPermissionsController {
    private final PermissionService permissions;

    public AdminPermissionsController(PermissionService permissions) {
        this.permissions = permissions;
    }

    // /admin/** luôn phải có ROLE_ADMIN theo SecurityConfig.
    @GetMapping("/admin/permissions")
    public String list(Model model) {
        var rows = permissions.adminPermissions();
        model.addAttribute("permissionRows", rows);
        model.addAttribute("permissionCount", rows.size());
        model.addAttribute("sqlReady", !rows.isEmpty());
        return "admin-permissions";
    }
}
