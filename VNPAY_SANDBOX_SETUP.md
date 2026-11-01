# FITFOOD - VNPAY Sandbox setup

This integration uses the VNPAY hosted checkout page. The customer chooses VNPAY-QR / a supported bank app on the VNPAY page. Sandbox transactions are test transactions and do not transfer real money.

## 1. Get Sandbox credentials

Register at https://sandbox.vnpayment.vn/devreg/ and wait for VNPAY to issue:

- `vnp_TmnCode` (merchant/site code)
- `vnp_HashSecret` (signing secret)

Do not commit these values to Git or send the HashSecret in chat.

## 2. Set local environment variables (PowerShell)

This project reads database and mail credentials from environment variables instead of storing passwords in `application.properties`. Set your existing SQL Server password and your rotated Gmail App Password as well as VNPAY credentials. Open the terminal that will start FITFOOD and run:

```powershell
$env:DB_PASSWORD="YOUR_SQL_SERVER_PASSWORD"
$env:SPRING_MAIL_USERNAME="YOUR_GMAIL_ADDRESS"
$env:SPRING_MAIL_PASSWORD="YOUR_NEW_GMAIL_APP_PASSWORD"
$env:VNPAY_TMN_CODE="YOUR_TMN_CODE"
$env:VNPAY_HASH_SECRET="YOUR_HASH_SECRET"
$env:VNPAY_RETURN_URL="http://localhost:8081/payment/vnpay/return"
```

Then start the application on port 8081. Environment variables set in a terminal are inherited only by processes started from that terminal. If starting from NetBeans, configure the same variables in the Run Configuration / environment for that project. The SQL Server username remains `sa` by default; set `DB_USERNAME` too if yours differs.

## 3. Expose the local callback with HTTPS

VNPAY cannot call `localhost` on your computer. Install and authenticate ngrok, then run:

```powershell
ngrok http 8081
```

Copy the HTTPS forwarding address, for example `https://example.ngrok-free.app`, then update the environment variables and restart FITFOOD:

```powershell
$env:VNPAY_RETURN_URL="https://example.ngrok-free.app/payment/vnpay/return"
```

In the VNPAY Sandbox merchant configuration, register this IPN URL:

```text
https://example.ngrok-free.app/payment/vnpay/ipn
```

The ngrok address is an example only. Use the exact HTTPS address displayed by your ngrok session. If the address changes, update both the environment variable and VNPAY IPN configuration.

## 4. Test flow

1. Start SQL Server and FITFOOD (`http://localhost:8081`).
2. Sign in with a customer account and add products to the cart.
3. Open checkout and choose `Thanh toán trực tuyến qua VNPAY`.
4. VNPAY opens its Sandbox payment page. Choose the QR / bank payment option available in the test environment.
5. Complete a Sandbox test transaction.
6. Confirm the VNPAY IPN request reaches `/payment/vnpay/ipn` and the row in `dbo.fitfood_payments` changes to `Đã thanh toán`.

## 5. Implementation notes

- `VnpayService` creates the payment URL and HMAC-SHA512 signature, and validates callback signatures.
- `/payment/vnpay/ipn` validates the signature, transaction reference and amount before updating payment status.
- `/payment/vnpay/return` is display-only. It does not mark an order paid; IPN is authoritative.
- Payment rows reuse `dbo.fitfood_payments.transaction_code`; no schema migration is required for the current SQL schema.
- This is a Sandbox integration. Production credentials, production URL, merchant approval and settlement configuration must come from VNPAY before real payments are accepted.

## 6. Important follow-up before production

The existing checkout workflow in this codebase currently deducts stock and clears the cart during order creation. For production, change that lifecycle so stock is reserved safely and cart cleanup / final fulfillment happen only after verified payment, with expiry and cancellation handling. This integration intentionally does not claim to solve inventory reservation or payment reconciliation for production.
