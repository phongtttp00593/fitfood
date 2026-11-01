package com.fitfood.payment;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VnpayService {
    private static final DateTimeFormatter VNP_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${vnpay.tmn-code:}") private String tmnCode;
    @Value("${vnpay.hash-secret:}") private String hashSecret;
    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") private String payUrl;
    @Value("${vnpay.return-url:http://localhost:8081/payment/vnpay/return}") private String returnUrl;
    @Value("${vnpay.version:2.1.0}") private String version;
    @Value("${vnpay.command:pay}") private String command;
    @Value("${vnpay.order-type:other}") private String orderType;

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank()
                && hashSecret != null && !hashSecret.isBlank();
    }

    public String createPaymentUrl(String txnRef, BigDecimal amount, HttpServletRequest request) {
        if (!isConfigured()) throw new IllegalStateException("Chưa cấu hình VNPAY Sandbox (TMN_CODE/HASH_SECRET).");
        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", version);
        params.put("vnp_Command", command);
        params.put("vnp_TmnCode", tmnCode);
        if (amount == null || amount.signum() <= 0) {
    throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ.");
}

long vnpAmount = amount
        .setScale(2, java.math.RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact();

params.put("vnp_Amount", String.valueOf(vnpAmount));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_OrderInfo", "Thanh toan don hang FITFOOD " + txnRef);
        params.put("vnp_OrderType", orderType);
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl);
        params.put("vnp_IpAddr", clientIp(request));
        params.put("vnp_CreateDate", LocalDateTime.now().format(VNP_DATE));
        params.put("vnp_ExpireDate", LocalDateTime.now().plusMinutes(15).format(VNP_DATE));

        String query = buildQuery(params);
        String secureHash = hmacSHA512(hashSecret, query);
        return payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    public boolean isValidSignature(Map<String, String[]> requestParams) {
        String receivedHash = first(requestParams, "vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank() || !isConfigured()) return false;
        TreeMap<String, String> params = new TreeMap<>();
        requestParams.forEach((key, values) -> {
            if (key.startsWith("vnp_") && !key.equals("vnp_SecureHash") && !key.equals("vnp_SecureHashType")
                    && values != null && values.length > 0 && values[0] != null) {
                params.put(key, values[0]);
            }
        });
        String expected = hmacSHA512(hashSecret, buildQuery(params));
        return constantTimeEquals(expected, receivedHash);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String first(Map<String, String[]> values, String key) {
        String[] value = values.get(key);
        return value == null || value.length == 0 ? null : value[0];
    }

    private static String buildQuery(TreeMap<String, String> params) {
        StringBuilder result = new StringBuilder();
        params.forEach((key, value) -> {
            if (value == null || value.isEmpty()) return;
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(key, StandardCharsets.US_ASCII));
            result.append('=');
            result.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    private static String hmacSHA512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo chữ ký VNPAY.", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return java.security.MessageDigest.isEqual(a.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                b.toLowerCase().getBytes(StandardCharsets.US_ASCII));
    }
}
