
package com.fitfood.support;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChatMessageMutationService {

    private static final String CHAT_TITLE = "Chat hỗ trợ FITFOOD";
    private final JdbcTemplate jdbc;

    public ChatMessageMutationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void change(
            long messageId,
            long actorId,
            String role,
            String rawContent,
            boolean recall) {

        String content = rawContent == null
                ? ""
                : rawContent.strip();

        if (messageId <= 0 || actorId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "ID không hợp lệ."
            );
        }

        if (!recall
                && (content.isEmpty() || content.length() > 1000)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nội dung phải từ 1 đến 1000 ký tự."
            );
        }

        int updated;

        if (recall) {
            updated = jdbc.update("""
                UPDATE m
                SET m.content = N'Tin nhắn đã được thu hồi',
                    m.is_recalled = 1,
                    m.edited_at = SYSDATETIME()
                FROM dbo.fitfood_support_messages AS m
                JOIN dbo.fitfood_support_tickets AS t
                    ON t.id = m.ticket_id
                WHERE m.id = ?
                  AND m.sender_user_id = ?
                  AND m.sender_role = ?
                  AND m.is_recalled = 0
                  AND t.title = ?
                """,
                messageId,
                actorId,
                role,
                CHAT_TITLE
            );
        } else {
            updated = jdbc.update("""
                UPDATE m
                SET m.content = ?,
                    m.edited_at = SYSDATETIME()
                FROM dbo.fitfood_support_messages AS m
                JOIN dbo.fitfood_support_tickets AS t
                    ON t.id = m.ticket_id
                WHERE m.id = ?
                  AND m.sender_user_id = ?
                  AND m.sender_role = ?
                  AND m.is_recalled = 0
                  AND t.title = ?
                """,
                content,
                messageId,
                actorId,
                role,
                CHAT_TITLE
            );
        }

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy tin nhắn hoặc bạn không có quyền."
            );
        }
    }
}
