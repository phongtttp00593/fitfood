/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitfood.controller;

import com.fitfood.promotion.PromotionForm;
import com.fitfood.promotion.PromotionService;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/promotions")
@PreAuthorize("@permissionService.allowed(authentication, 'QUAN_LY_KHUYEN_MAI')")
public class AdminPromotionController {

    private final PromotionService service;

    public AdminPromotionController(PromotionService service) {
        this.service = service;
    }

    @GetMapping
    public String index(
            @RequestParam(required = false) String keyword,
            Model model) {

        model.addAttribute("promotions", service.findAll(keyword));
        model.addAttribute("keyword", keyword);

        return "admin-promotions";
    }

    @GetMapping("/new")
    public String create(Model model) {

        model.addAttribute("promotionForm", service.createForm());
        model.addAttribute("isEdit", false);

        return "admin-promotion-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            Model model,
            RedirectAttributes redirect) {

        try {
            model.addAttribute(
                    "promotionForm",
                    service.toForm(service.findById(id)));

            model.addAttribute("isEdit", true);

            return "admin-promotion-form";

        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/promotions";
        }
    }

    @PostMapping("/save")
    public String save(
            @Valid @ModelAttribute("promotionForm") PromotionForm form,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {

        if (result.hasErrors()) {
            model.addAttribute("isEdit", form.getId() != null);
            return "admin-promotion-form";
        }

        try {
            service.save(form);

            redirect.addFlashAttribute(
                    "success",
                    form.getId() == null
                            ? "Thêm khuyến mãi thành công!"
                            : "Cập nhật khuyến mãi thành công!");

            return "redirect:/admin/promotions";

        } catch (IllegalArgumentException e) {

            model.addAttribute("error", e.getMessage());
            model.addAttribute("isEdit", form.getId() != null);

            return "admin-promotion-form";
        }
    }

    @PostMapping("/{id}/toggle")
    public String toggle(
            @PathVariable Long id,
            RedirectAttributes redirect) {

        try {
            service.toggle(id);
            redirect.addFlashAttribute(
                    "success", "Cập nhật trạng thái thành công!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/promotions";
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @PathVariable Long id,
            RedirectAttributes redirect) {

        try {
            service.delete(id);
            redirect.addFlashAttribute(
                    "success", "Xóa khuyến mãi thành công!");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/promotions";
    }
}
