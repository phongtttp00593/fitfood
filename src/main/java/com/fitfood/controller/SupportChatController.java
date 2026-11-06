
package com.fitfood.controller;

import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.support.ChatMessageMutationService;
import com.fitfood.support.SupportChatService;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SupportChatController {

    private final SupportChatService chat;
    private final AppUserRepository users;
    private final ChatMessageMutationService mutation;

    public SupportChatController(
            SupportChatService chat,
            AppUserRepository users,
            ChatMessageMutationService mutation) {

        this.chat = chat;
        this.users = users;
        this.mutation = mutation;
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/ho-tro")
    public String supportPage(Model model) {
        model.addAttribute("supportFullPage", true);
        return "support-chat";
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/api/support/messages")
    @ResponseBody
    public SupportChatService.ChatHistory getMessages(
            Authentication authentication) {

        return chat.history(
                currentUserId(authentication)
        );
    }

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

    // USER CHINH SUA TIN NHAN
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/api/support/messages/{messageId}/edit")
    @ResponseBody
    public SupportChatService.ChatHistory edit(
            @PathVariable long messageId,
            Authentication authentication,
            @RequestBody Map<String, String> body) {

        long userId = currentUserId(authentication);

        mutation.change(
                messageId,
                userId,
                "USER",
                body == null ? null : body.get("content"),
                false
        );

        return chat.history(userId);
    }

    // USER THU HOI TIN NHAN
    @PreAuthorize("hasRole('USER')")
    @PostMapping("/api/support/messages/{messageId}/recall")
    @ResponseBody
    public SupportChatService.ChatHistory recall(
            @PathVariable long messageId,
            Authentication authentication) {

        long userId = currentUserId(authentication);

        mutation.change(
                messageId,
                userId,
                "USER",
                null,
                true
        );

        return chat.history(userId);
    }

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
