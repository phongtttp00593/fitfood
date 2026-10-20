/* ============================================================================
 FITFOOD - DU LIEU MAU DE TEST DON HANG
 Phien ban: 2026-10-18 | SQL Server 2019+ / SSMS

 Chi them du lieu (KHONG xoa gi het). Co the chay lai nhieu lan an toan:
 moi INSERT deu co IF NOT EXISTS / kiem tra truoc, dua theo email, ten,
 ma khuyen mai hoac ghi chu don hang.

 Yeu cau truoc khi chay: database FITFOOD da co cac bang fitfood_* (chay
 FITFOOD_TAO_MOI_HOAN_TOAN_SQL_Server.sql truoc do it nhat 1 lan).

 Tai khoan khach hang mau de dang nhap test (mat khau deu la Test@123, da bam bcrypt,
 khop voi BCryptPasswordEncoder cua Spring Security):
   email: khach1@test.com (Nguyễn Văn An)
   email: khach2@test.com (Trần Thị Bình)
   email: khach3@test.com (Lê Minh Cường)
   email: khach4@test.com (Phạm Thị Dung)
 ============================================================================ */

USE [FITFOOD];
GO
SET NOCOUNT ON;
GO

/* 1. KHACH HANG MAU */
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_users WHERE email = N'khach1@test.com')
    INSERT INTO dbo.fitfood_users (email, password_hash, full_name, [role], enabled)
    VALUES (N'khach1@test.com', '$2b$10$4JYnOx2IJIxqaRFp13LAKeYiJgQK9HJVwBchj190iRHzFB5HU3fE2', N'Nguyễn Văn An', 'USER', 1);
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_users WHERE email = N'khach2@test.com')
    INSERT INTO dbo.fitfood_users (email, password_hash, full_name, [role], enabled)
    VALUES (N'khach2@test.com', '$2b$10$4JYnOx2IJIxqaRFp13LAKeYiJgQK9HJVwBchj190iRHzFB5HU3fE2', N'Trần Thị Bình', 'USER', 1);
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_users WHERE email = N'khach3@test.com')
    INSERT INTO dbo.fitfood_users (email, password_hash, full_name, [role], enabled)
    VALUES (N'khach3@test.com', '$2b$10$4JYnOx2IJIxqaRFp13LAKeYiJgQK9HJVwBchj190iRHzFB5HU3fE2', N'Lê Minh Cường', 'USER', 1);
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_users WHERE email = N'khach4@test.com')
    INSERT INTO dbo.fitfood_users (email, password_hash, full_name, [role], enabled)
    VALUES (N'khach4@test.com', '$2b$10$4JYnOx2IJIxqaRFp13LAKeYiJgQK9HJVwBchj190iRHzFB5HU3fE2', N'Phạm Thị Dung', 'USER', 1);
GO

IF NOT EXISTS (
    SELECT 1 FROM dbo.fitfood_customer_profiles p
    JOIN dbo.fitfood_users u ON u.id = p.user_id WHERE u.email = N'khach1@test.com')
    INSERT INTO dbo.fitfood_customer_profiles (user_id, phone, address, birthday, gender)
    SELECT id, '0901234567', N'12 Nguyễn Trãi, Quận 1, TP.HCM', '1998-05-10', N'Nam'
    FROM dbo.fitfood_users WHERE email = N'khach1@test.com';
GO
IF NOT EXISTS (
    SELECT 1 FROM dbo.fitfood_customer_profiles p
    JOIN dbo.fitfood_users u ON u.id = p.user_id WHERE u.email = N'khach2@test.com')
    INSERT INTO dbo.fitfood_customer_profiles (user_id, phone, address, birthday, gender)
    SELECT id, '0912345678', N'45 Lê Lợi, Quận 3, TP.HCM', '2000-11-22', N'Nữ'
    FROM dbo.fitfood_users WHERE email = N'khach2@test.com';
GO
IF NOT EXISTS (
    SELECT 1 FROM dbo.fitfood_customer_profiles p
    JOIN dbo.fitfood_users u ON u.id = p.user_id WHERE u.email = N'khach3@test.com')
    INSERT INTO dbo.fitfood_customer_profiles (user_id, phone, address, birthday, gender)
    SELECT id, '0923456789', N'78 Hai Bà Trưng, Quận Hải Châu, Đà Nẵng', '1995-03-15', N'Nam'
    FROM dbo.fitfood_users WHERE email = N'khach3@test.com';
GO
IF NOT EXISTS (
    SELECT 1 FROM dbo.fitfood_customer_profiles p
    JOIN dbo.fitfood_users u ON u.id = p.user_id WHERE u.email = N'khach4@test.com')
    INSERT INTO dbo.fitfood_customer_profiles (user_id, phone, address, birthday, gender)
    SELECT id, '0934567890', N'23 Trần Phú, Quận Ngô Quyền, Hải Phòng', '1999-07-08', N'Nữ'
    FROM dbo.fitfood_users WHERE email = N'khach4@test.com';
GO

/* 2. DANH MUC + SAN PHAM MAU */
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_categories WHERE [name] = N'Món mẫu test')
    INSERT INTO dbo.fitfood_categories ([name], [description], active)
    VALUES (N'Món mẫu test', N'Danh mục tạo riêng để test luồng đặt hàng', 1);
GO

IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_products WHERE name = N'Salad gà áp chảo')
    INSERT INTO dbo.fitfood_products (category_id, name, description, price, active)
    SELECT id, N'Salad gà áp chảo', N'Salad rau củ với gà áp chảo, ít dầu', 59000, 1
    FROM dbo.fitfood_categories WHERE [name] = N'Món mẫu test';
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_products WHERE name = N'Cơm gạo lứt thịt bò')
    INSERT INTO dbo.fitfood_products (category_id, name, description, price, active)
    SELECT id, N'Cơm gạo lứt thịt bò', N'Cơm gạo lứt kèm thịt bò xào rau củ', 65000, 1
    FROM dbo.fitfood_categories WHERE [name] = N'Món mẫu test';
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_products WHERE name = N'Sinh tố bơ chuối')
    INSERT INTO dbo.fitfood_products (category_id, name, description, price, active)
    SELECT id, N'Sinh tố bơ chuối', N'Sinh tố bơ chuối sữa hạt, không đường', 35000, 1
    FROM dbo.fitfood_categories WHERE [name] = N'Món mẫu test';
GO
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_products WHERE name = N'Ức gà sốt tiêu đen')
    INSERT INTO dbo.fitfood_products (category_id, name, description, price, active)
    SELECT id, N'Ức gà sốt tiêu đen', N'Ức gà áp chảo sốt tiêu đen kèm rau củ hấp', 69000, 1
    FROM dbo.fitfood_categories WHERE [name] = N'Món mẫu test';
GO

/* 3. KHUYEN MAI MAU */
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_promotions WHERE code = N'TEST10')
    INSERT INTO dbo.fitfood_promotions (code, name, percent_discount, amount_discount, starts_at, ends_at, minimum_order, maximum_discount, active)
    VALUES (N'TEST10', N'Giảm 10% - mã test', 10, NULL, '2026-01-01', '2026-12-31', 50000, 20000, 1);
GO

/* 4. DON HANG MAU (4 don, du trang thai de test loc/hien thi) */

-- Don 1: khach1 - Cho xac nhan - COD - chua thanh toan - chua giao
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_orders WHERE note = N'[SAMPLE-ORDER-1]')
BEGIN
    DECLARE @u1 BIGINT = (SELECT id FROM dbo.fitfood_users WHERE email = N'khach1@test.com');
    DECLARE @p1 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Salad gà áp chảo');
    DECLARE @p3 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Sinh tố bơ chuối');
    DECLARE @sub1 DECIMAL(18,2) = 2 * 59000 + 1 * 35000; -- 153000
    DECLARE @fee1 DECIMAL(18,2) = 15000;
    DECLARE @total1 DECIMAL(18,2) = @sub1 + @fee1;

    INSERT INTO dbo.fitfood_orders (user_id, promotion_id, subtotal, discount, delivery_fee, total, status, note)
    VALUES (@u1, NULL, @sub1, 0, @fee1, @total1, N'Chờ xác nhận', N'[SAMPLE-ORDER-1]');
    DECLARE @o1 BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.fitfood_order_items (order_id, product_id, product_name, quantity, unit_price, line_total)
    VALUES
        (@o1, @p1, N'Salad gà áp chảo', 2, 59000, 118000),
        (@o1, @p3, N'Sinh tố bơ chuối', 1, 35000, 35000);

    INSERT INTO dbo.fitfood_payments (order_id, payment_method, amount, paid_at, status)
    VALUES (@o1, N'COD', @total1, NULL, N'Chưa thanh toán');

    INSERT INTO dbo.fitfood_deliveries (order_id, receiver_name, receiver_phone, delivery_address, status)
    VALUES (@o1, N'Nguyễn Văn An', '0901234567', N'12 Nguyễn Trãi, Quận 1, TP.HCM', N'Chờ giao');
END;
GO

-- Don 2: khach2 - Dang giao - chuyen khoan - da thanh toan - co khuyen mai
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_orders WHERE note = N'[SAMPLE-ORDER-2]')
BEGIN
    DECLARE @u2 BIGINT = (SELECT id FROM dbo.fitfood_users WHERE email = N'khach2@test.com');
    DECLARE @p2 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Cơm gạo lứt thịt bò');
    DECLARE @p4 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Ức gà sốt tiêu đen');
    DECLARE @promo BIGINT = (SELECT id FROM dbo.fitfood_promotions WHERE code = N'TEST10');
    DECLARE @sub2 DECIMAL(18,2) = 65000 + 69000; -- 134000
    DECLARE @disc2 DECIMAL(18,2) = 13400; -- 10%, duoi muc giam toi da 20000
    DECLARE @fee2 DECIMAL(18,2) = 15000;
    DECLARE @total2 DECIMAL(18,2) = @sub2 - @disc2 + @fee2;

    INSERT INTO dbo.fitfood_orders (user_id, promotion_id, subtotal, discount, delivery_fee, total, status, note)
    VALUES (@u2, @promo, @sub2, @disc2, @fee2, @total2, N'Đang giao', N'[SAMPLE-ORDER-2]');
    DECLARE @o2 BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.fitfood_order_items (order_id, product_id, product_name, quantity, unit_price, line_total)
    VALUES
        (@o2, @p2, N'Cơm gạo lứt thịt bò', 1, 65000, 65000),
        (@o2, @p4, N'Ức gà sốt tiêu đen', 1, 69000, 69000);

    INSERT INTO dbo.fitfood_payments (order_id, payment_method, amount, paid_at, status, transaction_code)
    VALUES (@o2, N'Chuyển khoản', @total2, SYSDATETIME(), N'Đã thanh toán', N'TXN-SAMPLE-002');

    INSERT INTO dbo.fitfood_deliveries (order_id, receiver_name, receiver_phone, delivery_address, status, sent_at)
    VALUES (@o2, N'Trần Thị Bình', '0912345678', N'45 Lê Lợi, Quận 3, TP.HCM', N'Đang giao', SYSDATETIME());
END;
GO

-- Don 3: khach3 - Hoan thanh - da thanh toan - da giao xong
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_orders WHERE note = N'[SAMPLE-ORDER-3]')
BEGIN
    DECLARE @u3 BIGINT = (SELECT id FROM dbo.fitfood_users WHERE email = N'khach3@test.com');
    DECLARE @p1 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Salad gà áp chảo');
    DECLARE @p4 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Ức gà sốt tiêu đen');
    DECLARE @sub3 DECIMAL(18,2) = 59000 + 2 * 69000; -- 197000
    DECLARE @total3 DECIMAL(18,2) = @sub3; -- mien phi ship

    INSERT INTO dbo.fitfood_orders (user_id, promotion_id, subtotal, discount, delivery_fee, total, status, note)
    VALUES (@u3, NULL, @sub3, 0, 0, @total3, N'Hoàn thành', N'[SAMPLE-ORDER-3]');
    DECLARE @o3 BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.fitfood_order_items (order_id, product_id, product_name, quantity, unit_price, line_total)
    VALUES
        (@o3, @p1, N'Salad gà áp chảo', 1, 59000, 59000),
        (@o3, @p4, N'Ức gà sốt tiêu đen', 2, 69000, 138000);

    INSERT INTO dbo.fitfood_payments (order_id, payment_method, amount, paid_at, status, transaction_code)
    VALUES (@o3, N'Chuyển khoản', @total3, DATEADD(DAY, -3, SYSDATETIME()), N'Đã thanh toán', N'TXN-SAMPLE-003');

    INSERT INTO dbo.fitfood_deliveries (order_id, receiver_name, receiver_phone, delivery_address, status, sent_at, delivered_at)
    VALUES (@o3, N'Lê Minh Cường', '0923456789', N'78 Hai Bà Trưng, Quận Hải Châu, Đà Nẵng', N'Đã giao', DATEADD(DAY, -3, SYSDATETIME()), DATEADD(DAY, -2, SYSDATETIME()));
END;
GO

-- Don 4: khach4 - Da huy - chua thanh toan
IF NOT EXISTS (SELECT 1 FROM dbo.fitfood_orders WHERE note = N'[SAMPLE-ORDER-4]')
BEGIN
    DECLARE @u4 BIGINT = (SELECT id FROM dbo.fitfood_users WHERE email = N'khach4@test.com');
    DECLARE @p2 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Cơm gạo lứt thịt bò');
    DECLARE @p3 BIGINT = (SELECT id FROM dbo.fitfood_products WHERE name = N'Sinh tố bơ chuối');
    DECLARE @sub4 DECIMAL(18,2) = 65000 + 35000; -- 100000
    DECLARE @fee4 DECIMAL(18,2) = 15000;
    DECLARE @total4 DECIMAL(18,2) = @sub4 + @fee4;

    INSERT INTO dbo.fitfood_orders (user_id, promotion_id, subtotal, discount, delivery_fee, total, status, note)
    VALUES (@u4, NULL, @sub4, 0, @fee4, @total4, N'Đã hủy', N'[SAMPLE-ORDER-4] Khách đổi ý, hủy trước khi giao');
    DECLARE @o4 BIGINT = SCOPE_IDENTITY();

    INSERT INTO dbo.fitfood_order_items (order_id, product_id, product_name, quantity, unit_price, line_total)
    VALUES
        (@o4, @p2, N'Cơm gạo lứt thịt bò', 1, 65000, 65000),
        (@o4, @p3, N'Sinh tố bơ chuối', 1, 35000, 35000);

    INSERT INTO dbo.fitfood_payments (order_id, payment_method, amount, paid_at, status)
    VALUES (@o4, N'COD', @total4, NULL, N'Đã hủy');

    INSERT INTO dbo.fitfood_deliveries (order_id, receiver_name, receiver_phone, delivery_address, status, note)
    VALUES (@o4, N'Phạm Thị Dung', '0934567890', N'23 Trần Phú, Quận Ngô Quyền, Hải Phòng', N'Đã hủy', N'Khách yêu cầu hủy đơn');
END;
GO

/* 5. XEM KET QUA */
SELECT o.id, u.full_name, o.status, o.subtotal, o.discount, o.delivery_fee, o.total, o.note
FROM dbo.fitfood_orders o
JOIN dbo.fitfood_users u ON u.id = o.user_id
WHERE o.note LIKE N'[SAMPLE-ORDER-%'
ORDER BY o.id;
GO
