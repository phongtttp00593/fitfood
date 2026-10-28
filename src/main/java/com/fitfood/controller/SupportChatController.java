
package com.fitfood.controller;

import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.support.SupportChatService;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SupportChatController {

    private final SupportChatService chat;
    private final AppUserRepository users;

    public SupportChatController(
            SupportChatService chat,
            AppUserRepository users) {

        this.chat = chat;
        this.users = users;
    }

    // Trang chat cho User
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/ho-tro")
    public String supportPage(Model model) {

        model.addAttribute("supportFullPage", true);

        return "support-chat";
    }

    // Lay lich su tin nhan
    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/support/messages")
    @ResponseBody
    public SupportChatService.ChatHistory getMessages(
            Authentication authentication) {

        return chat.history(
                currentUserId(authentication)
        );
    }

    // Gui tin nhan moi
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/api/support/messages")
    @ResponseBody
    public SupportChatService.ChatHistory sendMessage(
            Authentication authentication,
            @RequestBody Map<String, String> body) {

        return chat.send(
                currentUserId(authentication),
                body.get("content")
        );
    }

    // Xac dinh User dang dang nhap
    private long currentUserId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Vui lòng đăng nhập."
            );
        }

        return users.findByEmailIgnoreCase(
                authentication.getName()
        )
        .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Tài khoản không tồn tại."
        ))
        .getId();
    }
}
