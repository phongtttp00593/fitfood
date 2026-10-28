
package com.fitfood.support;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Doc va phan hoi chat tren hai bang da co o giai doan User.
 * Chi thao tac ticket mang tieu de Chat ho tro FITFOOD.
 */
@Service
public class AdminSupportChatService {

    private static final String CHAT_TITLE = "Chat hỗ trợ FITFOOD";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final JdbcTemplate jdbc;

    public AdminSupportChatService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Conversation(
            long id,
            String customerName,
            String customerEmail,
            String status,
            String preview,
            String lastAt
    ) {}

    public record ChatMessage(
            long id,
            String sender,
            String content,
            String sentAt
    ) {}

    public record ConversationDetail(
            Conversation conversation,
            List<ChatMessage> messages
    ) {}

    /**
     * Lay 200 cuoc chat moi nhat.
     */
    @Transactional(readOnly = true)
    public List<Conversation> conversations() {
        return jdbc.query("""
                SELECT TOP (200)
                    t.id,
                    u.full_name AS customer_name,
                    u.email AS customer_email,
                    t.status,
                    COALESCE(last_message.content, t.content) AS preview,
                    COALESCE(
                        last_message.created_at,
                        t.created_at
                    ) AS last_activity
                FROM dbo.fitfood_support_tickets AS t
                JOIN dbo.fitfood_users AS u
                    ON u.id = t.user_id
                OUTER APPLY (
                    SELECT TOP (1)
                        m.content,
                        m.created_at
                    FROM dbo.fitfood_support_messages AS m
                    WHERE m.ticket_id = t.id
                    ORDER BY m.id DESC
                ) AS last_message
                WHERE t.title = ?
                ORDER BY
                    COALESCE(
                        last_message.created_at,
                        t.created_at
                    ) DESC,
                    t.id DESC
                """,
                (rs, rowNum) -> new Conversation(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getString("status"),
                        rs.getString("preview"),
                        format(rs.getTimestamp("last_activity"))
                ),
                CHAT_TITLE
        );
    }

    @Transactional(readOnly = true)
    public ConversationDetail conversation(long ticketId) {
        return loadConversation(ticketId);
    }

    /**
     * Admin gui tin nhan cho khach hang.
     */
    @Transactional
    public ConversationDetail reply(
            long ticketId,
            long adminId,
            String rawMessage) {

        String message = rawMessage == null
                ? ""
                : rawMessage.strip();

        if (message.isEmpty() || message.length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tin nhắn phải có từ 1 đến 1000 ký tự."
            );
        }

        // Kiem tra cuoc chat co ton tai hay khong
        List<Long> ids = jdbc.query("""
                SELECT id
                FROM dbo.fitfood_support_tickets
                WITH (UPDLOCK, HOLDLOCK)
                WHERE id = ?
                  AND title = ?
                """,
                (rs, rowNum) -> rs.getLong("id"),
                ticketId,
                CHAT_TITLE
        );

        if (ids.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy cuộc trò chuyện."
            );
        }

        // Luu tin nhan cua Admin
        jdbc.update("""
                INSERT INTO dbo.fitfood_support_messages
                    (
                        ticket_id,
                        sender_user_id,
                        sender_role,
                        content
                    )
                VALUES (?, ?, 'ADMIN', ?)
                """,
                ticketId,
                adminId,
                message
        );

        // Cap nhat trang thai dang xu ly
        jdbc.update("""
                UPDATE dbo.fitfood_support_tickets
                SET status = N'Đang xử lý',
                    resolved_at = NULL
                WHERE id = ?
                  AND title = ?
                """,
                ticketId,
                CHAT_TITLE
        );

        return loadConversation(ticketId);
    }

    /**
     * Dong cuoc tro chuyen.
     */
    @Transactional
    public ConversationDetail close(long ticketId) {
        int updated = jdbc.update("""
                UPDATE dbo.fitfood_support_tickets
                SET status = N'Đã đóng',
                    resolved_at = SYSDATETIME()
                WHERE id = ?
                  AND title = ?
                """,
                ticketId,
                CHAT_TITLE
        );

        if (updated == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy cuộc trò chuyện."
            );
        }

        return loadConversation(ticketId);
    }

    /**
     * Lay chi tiet va lich su tin nhan.
     */
    private ConversationDetail loadConversation(long ticketId) {

        List<Conversation> found = jdbc.query("""
                SELECT
                    t.id,
                    u.full_name AS customer_name,
                    u.email AS customer_email,
                    t.status,
                    t.content AS preview,
                    t.created_at AS last_activity
                FROM dbo.fitfood_support_tickets AS t
                JOIN dbo.fitfood_users AS u
                    ON u.id = t.user_id
                WHERE t.id = ?
                  AND t.title = ?
                """,
                (rs, rowNum) -> new Conversation(
                        rs.getLong("id"),
                        rs.getString("customer_name"),
                        rs.getString("customer_email"),
                        rs.getString("status"),
                        rs.getString("preview"),
                        format(rs.getTimestamp("last_activity"))
                ),
                ticketId,
                CHAT_TITLE
        );

        if (found.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy cuộc trò chuyện."
            );
        }

        // Lay 200 tin nhan gan nhat
        List<ChatMessage> messages = new ArrayList<>(
                jdbc.query("""
                        SELECT TOP (200)
                            id,
                            sender_role,
                            content,
                            created_at
                        FROM dbo.fitfood_support_messages
                        WHERE ticket_id = ?
                        ORDER BY id DESC
                        """,
                        (rs, rowNum) -> new ChatMessage(
                                rs.getLong("id"),
                                rs.getString("sender_role"),
                                rs.getString("content"),
                                format(rs.getTimestamp("created_at"))
                        ),
                        ticketId
                )
        );

        Collections.reverse(messages);

        return new ConversationDetail(
                found.get(0),
                messages
        );
    }

    private String format(Timestamp value) {
        return value == null
                ? ""
                : value.toLocalDateTime().format(TIME_FORMAT);
    }
}
