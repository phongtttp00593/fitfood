
package com.fitfood.controller;

import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.support.AdminSupportChatService;
import com.fitfood.support.ChatMessageMutationService;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/support/api")
@PreAuthorize(
    "@permissionService.allowed(authentication, 'HO_TRO_KHACH_HANG')"
)
public class AdminSupportChatApiController {

    private final AdminSupportChatService chat;
    private final AppUserRepository users;
    private final ChatMessageMutationService mutation;

    public AdminSupportChatApiController(
            AdminSupportChatService chat,
            AppUserRepository users,
            ChatMessageMutationService mutation) {

        this.chat = chat;
        this.users = users;
        this.mutation = mutation;
    }

    public record ReplyRequest(String content) {}

    @GetMapping("/me")
    @ResponseBody
    public Map<String, Long> me(Authentication auth) {
        return Map.of("id", currentAdminId(auth));
    }

    @GetMapping("/conversations")
    @ResponseBody
    public List<AdminSupportChatService.Conversation>
            conversations() {

        return chat.conversations();
    }

    @GetMapping("/conversations/{ticketId}")
    @ResponseBody
    public AdminSupportChatService.ConversationDetail conversation(
            @PathVariable long ticketId) {

        return chat.conversation(ticketId);
    }

    @PostMapping("/conversations/{ticketId}/reply")
    @ResponseBody
    public AdminSupportChatService.ConversationDetail reply(
            @PathVariable long ticketId,
            @RequestBody ReplyRequest request,
            Authentication authentication) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Thiếu tin nhắn."
            );
        }

        return chat.reply(
                ticketId,
                currentAdminId(authentication),
                request.content()
        );
    }

    // ADMIN CHINH SUA TIN NHAN MINH GUI
    @PostMapping(
        "/conversations/{ticketId}/messages/{messageId}/edit"
    )
    @ResponseBody
    public AdminSupportChatService.ConversationDetail edit(
            @PathVariable long ticketId,
            @PathVariable long messageId,
            Authentication authentication,
            @RequestBody ReplyRequest request) {

        verifyTicketMessage(ticketId, messageId);

        mutation.change(
                messageId,
                currentAdminId(authentication),
                "ADMIN",
                request == null ? null : request.content(),
                false
        );

        return chat.conversation(ticketId);
    }

    // ADMIN THU HOI TIN NHAN MINH GUI
    @PostMapping(
        "/conversations/{ticketId}/messages/{messageId}/recall"
    )
    @ResponseBody
    public AdminSupportChatService.ConversationDetail recall(
            @PathVariable long ticketId,
            @PathVariable long messageId,
            Authentication authentication) {

        verifyTicketMessage(ticketId, messageId);

        mutation.change(
                messageId,
                currentAdminId(authentication),
                "ADMIN",
                null,
                true
        );

        return chat.conversation(ticketId);
    }

    @PostMapping("/conversations/{ticketId}/close")
    @ResponseBody
    public AdminSupportChatService.ConversationDetail close(
            @PathVariable long ticketId) {

        return chat.close(ticketId);
    }

    private void verifyTicketMessage(
            long ticketId,
            long messageId) {

        if (!chat.messageBelongsToTicket(ticketId, messageId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Tin nhắn không thuộc hội thoại."
            );
        }
    }

    private long currentAdminId(
            Authentication authentication) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Chưa đăng nhập."
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
