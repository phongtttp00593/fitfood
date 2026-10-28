
package com.fitfood.support;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportChatService {

    private static final String CHAT_TITLE =
            "Chat hỗ trợ FITFOOD";

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern(
                    "HH:mm dd/MM/yyyy",
                    Locale.forLanguageTag("vi-VN")
            );

    private final JdbcTemplate jdbc;

    public SupportChatService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Du lieu cua mot tin nhan
    public record ChatMessage(
            long id,
            String sender,
            String content,
            String sentAt
    ) {}

    // Du lieu lich su hoi thoai
    public record ChatHistory(
            String status,
            List<ChatMessage> messages
    ) {}

    // Lay lich su chat cua User
    @Transactional(readOnly = true)
    public ChatHistory history(long userId) {

        Long ticketId = findTicket(userId);

        if (ticketId == null) {
            return new ChatHistory(
                    "Chưa có cuộc trò chuyện",
                    List.of()
            );
        }

        String status = jdbc.queryForObject(
                """
                SELECT status
                FROM dbo.fitfood_support_tickets
                WHERE id = ? AND user_id = ?
                """,
                String.class,
                ticketId,
                userId
        );

        return new ChatHistory(
                status,
                listMessages(ticketId)
        );
    }

    // Gui tin nhan
    @Transactional
    public ChatHistory send(
            long userId,
            String rawContent) {

        String content = rawContent == null
                ? ""
                : rawContent.strip();

        // Kiem tra noi dung tin nhan
        if (content.isEmpty()
                || content.length() > MAX_MESSAGE_LENGTH) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tin nhắn phải có từ 1 đến 1000 ký tự."
            );
        }

        // Khoa dong user de tranh tao trung ticket
        List<Long> lockedUsers = jdbc.query(
                """
                SELECT id
                FROM dbo.fitfood_users
                WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId
        );

        if (lockedUsers.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Tài khoản không tồn tại."
            );
        }

        Long ticketId = findTicket(userId);

        // Tin nhan dau tien thi tao ticket
        if (ticketId == null) {

            ticketId = jdbc.queryForObject(
                    """
                    INSERT INTO dbo.fitfood_support_tickets
                        (user_id, title, content, status)
                    OUTPUT INSERTED.id
                    VALUES (?, ?, ?, N'Chờ xử lý')
                    """,
                    Long.class,
                    userId,
                    CHAT_TITLE,
                    content
            );

        } else {

            // Neu ticket da dong thi mo lai
            jdbc.update(
                    """
                    UPDATE dbo.fitfood_support_tickets
                    SET status = N'Chờ xử lý',
                        resolved_at = NULL
                    WHERE id = ?
                      AND user_id = ?
                      AND status IN (
                          N'Đã đóng',
                          N'Đã xử lý'
                      )
                    """,
                    ticketId,
                    userId
            );
        }

        // Luu tin nhan vao SQL Server
        jdbc.update(
                """
                INSERT INTO dbo.fitfood_support_messages
                    (
                        ticket_id,
                        sender_user_id,
                        sender_role,
                        content
                    )
                VALUES (?, ?, 'USER', ?)
                """,
                ticketId,
                userId,
                content
        );

        String status = jdbc.queryForObject(
                """
                SELECT status
                FROM dbo.fitfood_support_tickets
                WHERE id = ?
                """,
                String.class,
                ticketId
        );

        return new ChatHistory(
                status,
                listMessages(ticketId)
        );
    }

    // Tim hoi thoai cua User
    private Long findTicket(long userId) {

        List<Long> ids = jdbc.query(
                """
                SELECT TOP (1) id
                FROM dbo.fitfood_support_tickets
                WHERE user_id = ?
                  AND title = ?
                ORDER BY id DESC
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                CHAT_TITLE
        );

        return ids.isEmpty()
                ? null
                : ids.get(0);
    }

    // Lay 100 tin nhan gan nhat
    private List<ChatMessage> listMessages(
            long ticketId) {

        List<ChatMessage> latest =
                new ArrayList<>(
                    jdbc.query(
                        """
                        SELECT TOP (100)
                            id,
                            sender_role,
                            content,
                            created_at
                        FROM dbo.fitfood_support_messages
                        WHERE ticket_id = ?
                        ORDER BY id DESC
                        """,
                        (rs, rowNum) -> {

                            Timestamp at = rs.getTimestamp(
                                    "created_at"
                            );

                            String time = at == null
                                    ? ""
                                    : at.toLocalDateTime()
                                        .format(DATE_FORMAT);

                            return new ChatMessage(
                                    rs.getLong("id"),
                                    rs.getString("sender_role"),
                                    rs.getString("content"),
                                    time
                            );
                        },
                        ticketId
                    )
                );

        Collections.reverse(latest);

        return latest;
    }
}
