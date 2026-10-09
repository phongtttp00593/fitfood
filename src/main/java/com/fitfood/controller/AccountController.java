package com.fitfood.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AccountController {
    // SecurityConfig da co .anyRequest().authenticated(), nen khach chua dang nhap
    // se duoc chuyen den /dang-nhap.
    @GetMapping("/tai-khoan")
    public String myAccount() {
        return "account";
    }
}
