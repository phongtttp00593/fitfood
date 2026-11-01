package com.fitfood.controller;

import com.fitfood.payment.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
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

    /** VNPAY server-to-server callback. */
    @GetMapping(value = "/payment/vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, String> ipn(HttpServletRequest request) {
        if (!vnpayService.isValidSignature(request.getParameterMap())) {
            return response("97", "Invalid signature");
        }

        String txnRef = request.getParameter("vnp_TxnRef");
        String responseCode = request.getParameter("vnp_ResponseCode");
        String transactionStatus = request.getParameter("vnp_TransactionStatus");
        String amountText = request.getParameter("vnp_Amount");

        PaymentRecord payment = findPayment(txnRef);
        if (payment == null) {
            return response("01", "Order not found");
        }
        if (!isAmountMatched(payment.amount(), amountText)) {
            return response("04", "Invalid amount");
        }

        updatePaymentStatus(payment.id(), responseCode, transactionStatus);
        return response("00", "Confirm Success");
    }

    /**
     * Browser return is signature- and amount-verified as well.
     * It updates the same payment row as IPN, so the order page can immediately
     * show the confirmed status even if the IPN request arrives a little later.
     * IPN remains enabled and idempotently confirms the server-to-server result.
     */
    @GetMapping("/payment/vnpay/return")
    public String paymentReturn(
            @RequestParam Map<String, String> params,
            HttpServletRequest request) {

        String baseUrl = localBaseUrl == null ? "http://localhost:8081" : localBaseUrl.trim();
        baseUrl = baseUrl.replaceAll("/+$", "");

        if (!vnpayService.isValidSignature(request.getParameterMap())) {
            return "redirect:" + baseUrl + "/cart?paymentError=invalid";
        }

        String txnRef = params.getOrDefault("vnp_TxnRef", "");
        PaymentRecord payment = findPayment(txnRef);
        if (payment == null || !isAmountMatched(payment.amount(), params.get("vnp_Amount"))) {
            return "redirect:" + baseUrl + "/cart?paymentError=invalid";
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        boolean gatewaySuccess = "00".equals(responseCode)
                && "00".equals(transactionStatus);

        updatePaymentStatus(payment.id(), responseCode, transactionStatus);

        if (gatewaySuccess) {
            return "redirect:" + baseUrl
                    + "/tai-khoan/don-hang/" + payment.orderId()
                    + "?paymentSuccess=1";
        }

        return "redirect:" + baseUrl + "/cart?recheckout=1&paymentError=1";
    }

    private PaymentRecord findPayment(String txnRef) {
        if (txnRef == null || !txnRef.matches("[0-9]+_[0-9]+")) {
            return null;
        }

        var rows = jdbc.query("""
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

    private boolean isAmountMatched(BigDecimal expectedAmount, String amountText) {
        if (expectedAmount == null || amountText == null) {
            return false;
        }
        try {
            long receivedAmount = Long.parseLong(amountText);
            return expectedAmount.movePointRight(2).longValueExact() == receivedAmount;
        } catch (NumberFormatException | ArithmeticException ex) {
            return false;
        }
    }

    private void updatePaymentStatus(long paymentId, String responseCode, String transactionStatus) {
        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            jdbc.update("""
                    UPDATE dbo.fitfood_payments
                    SET status = N'Đã thanh toán',
                        paid_at = COALESCE(paid_at, SYSDATETIME())
                    WHERE id = ?
                    """, paymentId);
        } else {
            jdbc.update("""
                    UPDATE dbo.fitfood_payments
                    SET status = N'Thanh toán thất bại'
                    WHERE id = ?
                      AND status <> N'Đã thanh toán'
                    """, paymentId);
        }
    }

    private Map<String, String> response(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private record PaymentRecord(long id, long orderId, BigDecimal amount) {
    }
}
