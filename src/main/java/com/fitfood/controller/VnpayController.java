
package com.fitfood.controller;

import com.fitfood.payment.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class VnpayController {

    private final JdbcTemplate jdbc;
    private final VnpayService vnpayService;

    @Value("${app.local-base-url:http://localhost:8081}")
    private String localBaseUrl;

    public VnpayController(JdbcTemplate jdbc, VnpayService vnpayService) {
        this.jdbc = jdbc;
        this.vnpayService = vnpayService;
    }

    /**
     * VNPAY IPN - Server to server callback.
     */
    @Transactional
    @GetMapping(
            value = "/payment/vnpay/ipn",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseBody
    public Map<String, String> ipn(HttpServletRequest request) {

        // 1. Kiểm tra chữ ký VNPAY
        if (!vnpayService.isValidSignature(request.getParameterMap())) {
            return response("97", "Invalid signature");
        }

        String txnRef = request.getParameter("vnp_TxnRef");
        String responseCode = request.getParameter("vnp_ResponseCode");
        String transactionStatus = request.getParameter("vnp_TransactionStatus");
        String amountText = request.getParameter("vnp_Amount");

        // 2. Tìm thông tin thanh toán
        PaymentRecord payment = findPayment(txnRef);

        if (payment == null) {
            return response("01", "Order not found");
        }

        // 3. Kiểm tra số tiền
        if (!isAmountMatched(payment.amount(), amountText)) {
            return response("04", "Invalid amount");
        }

        // 4. Chỉ xóa giỏ hàng khi VNPAY xác nhận thành công
        boolean gatewaySuccess = "00".equals(responseCode)
                && "00".equals(transactionStatus);

        if (gatewaySuccess) {

            // Cập nhật trạng thái thanh toán
            updatePaymentStatus(
                    payment.id(),
                    responseCode,
                    transactionStatus
            );

            // Xóa các món thuộc đơn hàng khỏi giỏ hàng
            clearPurchasedCartItems(payment.orderId());

            return response("00", "Confirm Success");
        }

        // Thanh toán thất bại: không xóa giỏ hàng
        updatePaymentStatus(
                payment.id(),
                responseCode,
                transactionStatus
        );

        return response("00", "Confirm Success");
    }

    /**
     * Người dùng được chuyển về website sau khi thanh toán.
     */
    @Transactional
    @GetMapping("/payment/vnpay/return")
    public String paymentReturn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {

        String baseUrl = localBaseUrl == null
                ? "http://localhost:8081"
                : localBaseUrl.trim();

        baseUrl = baseUrl.replaceAll("/+$", "");

        // 1. Kiểm tra chữ ký
        if (!vnpayService.isValidSignature(request.getParameterMap())) {
            return "redirect:" + baseUrl
                    + "/cart?paymentError=invalid";
        }

        // 2. Tìm đơn hàng
        String txnRef = params.getOrDefault("vnp_TxnRef", "");

        PaymentRecord payment = findPayment(txnRef);

        if (payment == null
                || !isAmountMatched(
                        payment.amount(),
                        params.get("vnp_Amount")
                )) {

            return "redirect:" + baseUrl
                    + "/cart?paymentError=invalid";
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");

        boolean gatewaySuccess = "00".equals(responseCode)
                && "00".equals(transactionStatus);

        // 3. Cập nhật trạng thái thanh toán
        updatePaymentStatus(
                payment.id(),
                responseCode,
                transactionStatus
        );

        if (gatewaySuccess) {

            // 4. Xóa món đã mua khỏi giỏ hàng
            clearPurchasedCartItems(payment.orderId());

            return "redirect:" + baseUrl
                    + "/tai-khoan/don-hang/" + payment.orderId()
                    + "?paymentSuccess=1";
        }

        // Thanh toán thất bại: giữ nguyên giỏ hàng
        return "redirect:" + baseUrl
                + "/cart?recheckout=1&paymentError=1";
    }

    /**
     * Tìm thông tin thanh toán bằng mã giao dịch VNPAY.
     */
    private PaymentRecord findPayment(String txnRef) {

        if (txnRef == null || !txnRef.matches("[0-9]+_[0-9]+")) {
            return null;
        }

        var rows = jdbc.query(
                """
                SELECT p.id, p.order_id, p.amount
                FROM dbo.fitfood_payments p
                WHERE p.transaction_code = ?
                  AND p.payment_method = N'VNPAY'
                """,
                (rs, rowNum) -> new PaymentRecord(
                        rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getBigDecimal("amount")
                ),
                txnRef
        );

        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * Kiểm tra số tiền VNPAY gửi về.
     */
    private boolean isAmountMatched(
            BigDecimal expectedAmount,
            String amountText) {

        if (expectedAmount == null || amountText == null) {
            return false;
        }

        try {
            long receivedAmount = Long.parseLong(amountText);

            return expectedAmount
                    .movePointRight(2)
                    .longValueExact() == receivedAmount;

        } catch (NumberFormatException | ArithmeticException ex) {
            return false;
        }
    }

    /**
     * Cập nhật trạng thái thanh toán.
     */
    private void updatePaymentStatus(
            long paymentId,
            String responseCode,
            String transactionStatus) {

        if ("00".equals(responseCode)
                && "00".equals(transactionStatus)) {

            jdbc.update(
                    """
                    UPDATE dbo.fitfood_payments
                    SET status = N'Đã thanh toán',
                        paid_at = COALESCE(paid_at, SYSDATETIME())
                    WHERE id = ?
                    """,
                    paymentId
            );

        } else {

            jdbc.update(
                    """
                    UPDATE dbo.fitfood_payments
                    SET status = N'Thanh toán thất bại'
                    WHERE id = ?
                      AND status <> N'Đã thanh toán'
                    """,
                    paymentId
            );
        }
    }

    /**
     * Xóa các sản phẩm thuộc đơn hàng đã thanh toán
     * khỏi giỏ hàng của người đặt đơn.
     *
     * Không xóa giỏ hàng của người dùng khác.
     * Không xóa toàn bộ bảng fitfood_cart_items.
     */
    private void clearPurchasedCartItems(long orderId) {

        jdbc.update(
                """
                DELETE ci
                FROM dbo.fitfood_cart_items ci
                INNER JOIN dbo.fitfood_carts c
                    ON c.id = ci.cart_id
                INNER JOIN dbo.fitfood_orders o
                    ON o.user_id = c.user_id
                INNER JOIN dbo.fitfood_order_items oi
                    ON oi.order_id = o.id
                   AND oi.product_id = ci.product_id
                WHERE o.id = ?
                """,
                orderId
        );
    }

    /**
     * Trả kết quả cho VNPAY.
     */
    private Map<String, String> response(
            String code,
            String message) {

        return Map.of(
                "RspCode", code,
                "Message", message
        );
    }

    private record PaymentRecord(
            long id,
            long orderId,
            BigDecimal amount) {
    }
}
