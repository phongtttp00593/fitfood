package com.fitfood.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {
    @GetMapping("/") public String home() { return "index"; }
    @GetMapping("/menu") public String menu() { return "menu"; }
    @GetMapping("/product") public String product() { return "product"; }
    @GetMapping("/cart") public String cart() { return "cart"; }
    // Đăng nhập /dang-nhap được xử lý ở AuthController, KHÔNG khai báo ở đây.
}
