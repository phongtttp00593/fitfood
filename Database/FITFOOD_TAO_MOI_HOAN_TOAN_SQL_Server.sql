/* ============================================================================
 FITFOOD - SQL SERVER - XOA VA TAO MOI DATABASE HOAN CHINH
 Phien ban: 2026-10-10 | SQL Server 2019+ / SSMS

 !!! CANH BAO XOA DU LIEU !!!
 File nay XOA HOAN TOAN database FITFOOD, bao gom tat ca tai khoan,
 mat khau da bam, don hang, san pham, danh muc va du lieu phan quyen cu.
 Sao luu database truoc, DUNG ung dung FITFOOD trong NetBeans truoc khi chay.
 CHI chay khi chap nhan mat toan bo du lieu cu.

 Tong hop theo project FITFOOD Spring Boot hien tai:
  - JPA Java: dbo.fitfood_users; dbo.fitfood_password_reset_tokens;
              dbo.fitfood_categories (chinh thuc; KHONG tao DanhMuc rieng)
  - Doi tuong kinh doanh: san pham, goi an, gio hang, don hang,
       thanh toan, khuyen mai, giao hang, hoa don, CSKH, kho hang.
  - Phan quyen: dbo.VaiTro, dbo.Quyen, dbo.PhanQuyen;
       28 chuc nang; Khach hang 21; Admin 18; Nhan vien 13; Vang lai 6.
  - Muc 08B chen 4 tai khoan khach hang TEST (mat khau Test@123) + 4 don hang mau;
    xoa muc 08B neu khong muon co du lieu test.
  - AdminBootstrap Java se tao lai ADMIN tu bien moi truong neu cau hinh.
  - UserRole.java hien chi co USER/ADMIN; NHAN_VIEN can code Java rieng.
  - SQL KHONG tu tao Controller/CRUD dang cho hay bien giao dien mau thanh that.

 HUONG DAN: Mo file trong SSMS, Ctrl+A, nhan F5, xem ket qua cuoi file.
 ============================================================================ */

USE master;
GO
SET NOCOUNT ON;
GO
IF DB_ID(N'FITFOOD') IS NOT NULL
BEGIN
    ALTER DATABASE [FITFOOD] SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE [FITFOOD];
END;
GO
CREATE DATABASE [FITFOOD];
GO
ALTER DATABASE [FITFOOD] SET MULTI_USER;
GO
USE [FITFOOD];
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
GO

/* 01. BANG SPRING BOOT/JPA DANG DUNG: KHONG TAO NguoiDung PHU */
CREATE TABLE dbo.fitfood_users (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_fitfood_users PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    [role] VARCHAR(12) NOT NULL CONSTRAINT DF_fitfood_user_role DEFAULT ('USER'),
    enabled BIT NOT NULL CONSTRAINT DF_fitfood_user_enabled DEFAULT (1),
    created_at DATETIMEOFFSET(6) NOT NULL CONSTRAINT DF_fitfood_user_created DEFAULT (SYSDATETIMEOFFSET()),
    CONSTRAINT UQ_fitfood_user_email UNIQUE (email),
    CONSTRAINT CK_fitfood_user_role CHECK ([role] IN ('USER', 'ADMIN'))
);
GO
CREATE TABLE dbo.fitfood_password_reset_tokens (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_ff_reset PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL CONSTRAINT UQ_ff_reset_token UNIQUE,
    user_id BIGINT NOT NULL,
    created_at DATETIMEOFFSET(6) NOT NULL,
    expires_at DATETIMEOFFSET(6) NOT NULL,
    used_at DATETIMEOFFSET(6) NULL,
    CONSTRAINT FK_ff_reset_user FOREIGN KEY (user_id) REFERENCES dbo.fitfood_users(id)
);
GO
CREATE INDEX IX_ff_reset_user_created ON dbo.fitfood_password_reset_tokens(user_id, created_at DESC);
GO

/* 02. DANH MUC - DUY NHAT MOT BANG DE WEBSITE VA SQL CUNG XEM */
CREATE TABLE dbo.fitfood_categories (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_fitfood_categories PRIMARY KEY,
    [name] NVARCHAR(120) NOT NULL,
    [description] NVARCHAR(500) NULL,
    active BIT NOT NULL CONSTRAINT DF_ff_category_active DEFAULT (1),
    created_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_category_created DEFAULT (SYSDATETIME()),
    CONSTRAINT uk_fitfood_categories_name UNIQUE ([name])
);
GO

/* 03. BANG VAI TRO / QUYEN / PHAN QUYEN */
CREATE TABLE dbo.VaiTro (
    MaVaiTro INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    MaVaiTroCode VARCHAR(50) NOT NULL UNIQUE,
    TenVaiTro NVARCHAR(100) NOT NULL
);
GO
CREATE TABLE dbo.Quyen (
    MaQuyen INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    MaQuyenCode VARCHAR(100) NOT NULL UNIQUE,
    TenQuyen NVARCHAR(255) NOT NULL
);
GO
CREATE TABLE dbo.PhanQuyen (
    MaVaiTro INT NOT NULL,
    MaQuyen INT NOT NULL,
    CONSTRAINT PK_PhanQuyen PRIMARY KEY (MaVaiTro, MaQuyen),
    CONSTRAINT FK_FF_PQ_Role FOREIGN KEY (MaVaiTro) REFERENCES dbo.VaiTro(MaVaiTro),
    CONSTRAINT FK_FF_PQ_Perm FOREIGN KEY (MaQuyen) REFERENCES dbo.Quyen(MaQuyen)
);
GO

/* 04. BANG NGHIEP VU: LIEN KET CHUNG 1 USER VA 1 DANH MUC */
IF OBJECT_ID(N'dbo.fitfood_customer_profiles', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_customer_profiles (
        user_id BIGINT NOT NULL PRIMARY KEY,
        phone VARCHAR(20) NULL,
        address NVARCHAR(255) NULL,
        birthday DATE NULL,
        gender NVARCHAR(20) NULL,
        CONSTRAINT FK_ff_profile_user FOREIGN KEY (user_id)
            REFERENCES dbo.fitfood_users(id)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_products', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_products (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        category_id BIGINT NOT NULL,
        name NVARCHAR(150) NOT NULL,
        description NVARCHAR(500) NULL,
        price DECIMAL(18,2) NOT NULL,
        image_url NVARCHAR(500) NULL,
        active BIT NOT NULL CONSTRAINT DF_ff_product_active DEFAULT (1),
        created_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_product_created DEFAULT (SYSDATETIME()),
        CONSTRAINT CK_ff_product_price CHECK (price >= 0),
        CONSTRAINT FK_ff_product_category FOREIGN KEY (category_id)
            REFERENCES dbo.fitfood_categories(id)
    );
    CREATE INDEX IX_ff_product_category ON dbo.fitfood_products(category_id);
END;
GO
IF OBJECT_ID(N'dbo.fitfood_meal_plans', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_meal_plans (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        name NVARCHAR(150) NOT NULL,
        description NVARCHAR(500) NULL,
        price DECIMAL(18,2) NOT NULL,
        number_of_days INT NULL,
        active BIT NOT NULL CONSTRAINT DF_ff_plan_active DEFAULT (1),
        CONSTRAINT CK_ff_plan_price CHECK (price >= 0),
        CONSTRAINT CK_ff_plan_days CHECK (number_of_days IS NULL OR number_of_days > 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_meal_plan_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_meal_plan_items (
        meal_plan_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        quantity INT NOT NULL CONSTRAINT DF_ff_planitem_qty DEFAULT (1),
        CONSTRAINT PK_ff_planitem PRIMARY KEY (meal_plan_id, product_id),
        CONSTRAINT FK_ff_planitem_plan FOREIGN KEY (meal_plan_id) REFERENCES dbo.fitfood_meal_plans(id),
        CONSTRAINT FK_ff_planitem_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
        CONSTRAINT CK_ff_planitem_qty CHECK (quantity > 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_carts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_carts (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL UNIQUE,
        created_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_cart_created DEFAULT (SYSDATETIME()),
        CONSTRAINT FK_ff_cart_user FOREIGN KEY (user_id) REFERENCES dbo.fitfood_users(id)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_cart_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_cart_items (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        cart_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        quantity INT NOT NULL CONSTRAINT DF_ff_cartitem_qty DEFAULT (1),
        unit_price DECIMAL(18,2) NOT NULL,
        CONSTRAINT UQ_ff_cartitem_product UNIQUE (cart_id, product_id),
        CONSTRAINT FK_ff_cartitem_cart FOREIGN KEY (cart_id) REFERENCES dbo.fitfood_carts(id),
        CONSTRAINT FK_ff_cartitem_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
        CONSTRAINT CK_ff_cartitem_qty CHECK (quantity > 0),
        CONSTRAINT CK_ff_cartitem_price CHECK (unit_price >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_promotions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_promotions (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        code VARCHAR(50) NOT NULL UNIQUE,
        name NVARCHAR(150) NOT NULL,
        percent_discount DECIMAL(5,2) NULL,
        amount_discount DECIMAL(18,2) NULL,
        starts_at DATETIME2(6) NOT NULL,
        ends_at DATETIME2(6) NOT NULL,
        minimum_order DECIMAL(18,2) NOT NULL CONSTRAINT DF_ff_promo_min DEFAULT (0),
        maximum_discount DECIMAL(18,2) NULL,
        active BIT NOT NULL CONSTRAINT DF_ff_promo_active DEFAULT (1),
        CONSTRAINT CK_ff_promo_dates CHECK (ends_at > starts_at),
        CONSTRAINT CK_ff_promo_percent CHECK (percent_discount IS NULL OR percent_discount BETWEEN 0 AND 100),
        CONSTRAINT CK_ff_promo_amount CHECK (amount_discount IS NULL OR amount_discount >= 0),
        CONSTRAINT CK_ff_promo_min CHECK (minimum_order >= 0),
        CONSTRAINT CK_ff_promo_max CHECK (maximum_discount IS NULL OR maximum_discount >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_orders', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_orders (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        promotion_id BIGINT NULL,
        placed_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_order_date DEFAULT (SYSDATETIME()),
        subtotal DECIMAL(18,2) NOT NULL CONSTRAINT DF_ff_order_subtotal DEFAULT (0),
        discount DECIMAL(18,2) NOT NULL CONSTRAINT DF_ff_order_discount DEFAULT (0),
        delivery_fee DECIMAL(18,2) NOT NULL CONSTRAINT DF_ff_order_shipping DEFAULT (0),
        total DECIMAL(18,2) NOT NULL CONSTRAINT DF_ff_order_total DEFAULT (0),
        status NVARCHAR(50) NOT NULL CONSTRAINT DF_ff_order_status DEFAULT (N'Chờ xác nhận'),
        note NVARCHAR(500) NULL,
        CONSTRAINT FK_ff_order_user FOREIGN KEY (user_id) REFERENCES dbo.fitfood_users(id),
        CONSTRAINT FK_ff_order_promotion FOREIGN KEY (promotion_id) REFERENCES dbo.fitfood_promotions(id),
        CONSTRAINT CK_ff_order_amount CHECK (subtotal >= 0 AND discount >= 0 AND delivery_fee >= 0 AND total >= 0)
    );
    CREATE INDEX IX_ff_order_user_date ON dbo.fitfood_orders(user_id, placed_at DESC);
END;
GO
IF OBJECT_ID(N'dbo.fitfood_order_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_order_items (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        product_name NVARCHAR(150) NOT NULL,
        quantity INT NOT NULL,
        unit_price DECIMAL(18,2) NOT NULL,
        line_total DECIMAL(18,2) NOT NULL,
        CONSTRAINT FK_ff_orderitem_order FOREIGN KEY (order_id) REFERENCES dbo.fitfood_orders(id),
        CONSTRAINT FK_ff_orderitem_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
        CONSTRAINT CK_ff_orderitem_values CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_payments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_payments (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL,
        payment_method NVARCHAR(50) NOT NULL,
        amount DECIMAL(18,2) NOT NULL,
        paid_at DATETIME2(6) NULL,
        status NVARCHAR(50) NOT NULL CONSTRAINT DF_ff_payment_status DEFAULT (N'Chưa thanh toán'),
        transaction_code VARCHAR(100) NULL,
        CONSTRAINT FK_ff_payment_order FOREIGN KEY (order_id) REFERENCES dbo.fitfood_orders(id),
        CONSTRAINT CK_ff_payment_amount CHECK (amount >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_deliveries', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_deliveries (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL UNIQUE,
        receiver_name NVARCHAR(100) NOT NULL,
        receiver_phone VARCHAR(20) NOT NULL,
        delivery_address NVARCHAR(255) NOT NULL,
        status NVARCHAR(50) NOT NULL CONSTRAINT DF_ff_delivery_status DEFAULT (N'Chờ giao'),
        sent_at DATETIME2(6) NULL,
        delivered_at DATETIME2(6) NULL,
        note NVARCHAR(255) NULL,
        CONSTRAINT FK_ff_delivery_order FOREIGN KEY (order_id) REFERENCES dbo.fitfood_orders(id)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_invoices', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_invoices (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        order_id BIGINT NOT NULL UNIQUE,
        invoice_number VARCHAR(50) NOT NULL UNIQUE,
        issued_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_invoice_date DEFAULT (SYSDATETIME()),
        customer_name NVARCHAR(100) NULL,
        customer_phone VARCHAR(20) NULL,
        customer_address NVARCHAR(255) NULL,
        total DECIMAL(18,2) NOT NULL,
        status NVARCHAR(50) NOT NULL CONSTRAINT DF_ff_invoice_status DEFAULT (N'Đã lập'),
        CONSTRAINT FK_ff_invoice_order FOREIGN KEY (order_id) REFERENCES dbo.fitfood_orders(id),
        CONSTRAINT CK_ff_invoice_total CHECK (total >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_invoice_items', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_invoice_items (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        invoice_id BIGINT NOT NULL,
        product_id BIGINT NOT NULL,
        product_name NVARCHAR(150) NOT NULL,
        quantity INT NOT NULL,
        unit_price DECIMAL(18,2) NOT NULL,
        line_total DECIMAL(18,2) NOT NULL,
        CONSTRAINT FK_ff_invoiceitem_invoice FOREIGN KEY (invoice_id) REFERENCES dbo.fitfood_invoices(id),
        CONSTRAINT FK_ff_invoiceitem_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
        CONSTRAINT CK_ff_invoiceitem_values CHECK (quantity > 0 AND unit_price >= 0 AND line_total >= 0)
    );
END;
GO
IF OBJECT_ID(N'dbo.fitfood_support_tickets', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.fitfood_support_tickets (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        user_id BIGINT NOT NULL,
        assigned_staff_id BIGINT NULL,
        title NVARCHAR(200) NOT NULL,
        content NVARCHAR(1000) NULL,
        status NVARCHAR(50) NOT NULL CONSTRAINT DF_ff_support_status DEFAULT (N'Chưa xử lý'),
        created_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_support_created DEFAULT (SYSDATETIME()),
        resolved_at DATETIME2(6) NULL,
        CONSTRAINT FK_ff_support_user FOREIGN KEY (user_id) REFERENCES dbo.fitfood_users(id),
        CONSTRAINT FK_ff_support_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.fitfood_users(id)
    );
END;
GO


/* 05. KHO HANG (TRUOC MAT LA CAU TRUC DATA, CAN LAP TRINH JAVA THEM) */
CREATE TABLE dbo.fitfood_inventory (
    product_id BIGINT NOT NULL CONSTRAINT PK_ff_inventory PRIMARY KEY,
    stock_quantity INT NOT NULL CONSTRAINT DF_ff_stock DEFAULT (0),
    reserved_quantity INT NOT NULL CONSTRAINT DF_ff_reserved DEFAULT (0),
    reorder_level INT NOT NULL CONSTRAINT DF_ff_reorder DEFAULT (5),
    updated_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_inv_update DEFAULT (SYSDATETIME()),
    CONSTRAINT FK_ff_inventory_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
    CONSTRAINT CK_ff_inventory_qty CHECK (
        stock_quantity >= 0 AND reserved_quantity >= 0
        AND reserved_quantity <= stock_quantity AND reorder_level >= 0)
);
GO
CREATE TABLE dbo.fitfood_stock_movements (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    changed_by_user_id BIGINT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity INT NOT NULL,
    reason NVARCHAR(300) NULL,
    created_at DATETIME2(6) NOT NULL CONSTRAINT DF_ff_stockmov_time DEFAULT (SYSDATETIME()),
    CONSTRAINT FK_ff_stockmov_product FOREIGN KEY (product_id) REFERENCES dbo.fitfood_products(id),
    CONSTRAINT FK_ff_stockmov_user FOREIGN KEY (changed_by_user_id) REFERENCES dbo.fitfood_users(id),
    CONSTRAINT CK_ff_stockmov_qty CHECK (quantity > 0),
    CONSTRAINT CK_ff_stockmov_type CHECK (movement_type IN ('IN','OUT','ADJUST'))
);
GO
CREATE INDEX IX_ff_stockmov_product ON dbo.fitfood_stock_movements(product_id, created_at DESC);
GO

/* 06. CAP 28 QUYEN CHINH XAC THEO BANG NGUOI DUNG */
/* 06A. MA TRAN 28 QUYEN */
BEGIN TRY
    BEGIN TRANSACTION;

    DECLARE @Roles TABLE (
        MaVaiTroCode VARCHAR(50) PRIMARY KEY,
        TenVaiTro NVARCHAR(100) NOT NULL
    );
    INSERT INTO @Roles VALUES
        ('KHACH_HANG', N'Khách hàng'),
        ('QUAN_TRI', N'Quản trị viên'),
        ('NHAN_VIEN', N'Nhân viên'),
        ('KHACH_VANG_LAI', N'Khách vãng lai');

    INSERT INTO dbo.VaiTro (MaVaiTroCode, TenVaiTro)
    SELECT r.MaVaiTroCode, r.TenVaiTro FROM @Roles r
    WHERE NOT EXISTS (SELECT 1 FROM dbo.VaiTro v WHERE v.MaVaiTroCode = r.MaVaiTroCode);

    UPDATE v SET TenVaiTro = r.TenVaiTro
    FROM dbo.VaiTro v JOIN @Roles r ON r.MaVaiTroCode = v.MaVaiTroCode;

    DECLARE @Matrix TABLE (
        STT INT PRIMARY KEY,
        MaQuyenCode VARCHAR(100) NOT NULL UNIQUE,
        TenQuyen NVARCHAR(255) NOT NULL,
        KhachHang BIT NOT NULL,
        [Admin] BIT NOT NULL,
        NhanVien BIT NOT NULL,
        KhachVangLai BIT NOT NULL
    );

    --                          KhachHang  Admin  NhanVien  KhachVangLai
    INSERT INTO @Matrix VALUES
    ( 1, 'DANG_NHAP',                N'Đăng nhập',                       1,1,1,1),
    ( 2, 'DANG_KY',                  N'Đăng ký tài khoản',               1,0,0,1),
    ( 3, 'QUEN_MAT_KHAU',            N'Quên mật khẩu',                   1,1,1,0),
    ( 4, 'XEM_THUC_DON',             N'Xem thực đơn',                    1,1,1,1),
    ( 5, 'XEM_DANH_MUC',             N'Xem danh mục / loại sản phẩm',    1,1,1,1),
    ( 6, 'XEM_CHI_TIET_SAN_PHAM',    N'Xem chi tiết sản phẩm',          1,1,1,1),
    ( 7, 'TIM_KIEM_SAN_PHAM',        N'Tìm kiếm sản phẩm',               1,1,1,1),
    ( 8, 'CHON_GOI_AN',              N'Chọn gói ăn',                     1,0,0,0),
    ( 9, 'THEM_GIO_HANG',            N'Thêm vào giỏ hàng',               1,0,0,0),
    (10, 'XEM_SUA_GIO_HANG',         N'Xem / chỉnh sửa giỏ hàng',        1,0,0,0),
    (11, 'DAT_HANG',                 N'Đặt hàng',                        1,0,0,0),
    (12, 'THANH_TOAN',               N'Thanh toán',                      1,0,0,0),
    (13, 'AP_DUNG_KHUYEN_MAI',      N'Nhập mã khuyến mãi',              1,0,0,0),
    (14, 'CHON_DOI_MON',             N'Chọn / đổi món ăn',               1,0,0,0),
    (15, 'XEM_LICH_SU_DON_HANG',     N'Xem lịch sử đơn hàng',            1,1,1,0),
    (16, 'THEO_DOI_DON_HANG',        N'Theo dõi trạng thái đơn hàng',    1,1,1,0),
    (17, 'QUAN_LY_DON_HANG',         N'Quản lý đơn hàng',                0,1,0,0),
    (18, 'QUAN_LY_SAN_PHAM',         N'Quản lý sản phẩm',                0,1,0,0),
    (19, 'QUAN_LY_DANH_MUC',         N'Quản lý danh mục',                0,1,0,0),
    (20, 'QUAN_LY_KHUYEN_MAI',       N'Quản lý khuyến mãi',              0,1,0,0),
    (21, 'QUAN_LY_KHACH_HANG',       N'Quản lý khách hàng',              0,1,1,0),
    (22, 'QUAN_LY_GIAO_HANG',        N'Quản lý giao hàng',               0,1,1,0),
    (23, 'XEM_BAO_CAO',             N'Xem báo cáo / thống kê',          0,1,0,0),
    (24, 'HUY_DON_HANG',             N'Hủy Đặt Hàng',                    1,0,0,0),
    (25, 'SUA_THONG_TIN_KHACH_HANG', N'Chỉnh sửa thông tin khách hàng', 1,0,0,0),
    (26, 'XEM_THONG_TIN_KHACH_HANG', N'Xem thông tin khách hàng',        1,1,1,0),
    (27, 'HO_TRO_KHACH_HANG',        N'Chăm sóc khách hàng',             1,1,1,0),
    (28, 'HOA_DON',                  N'Hóa Đơn',                          1,1,1,0);

    IF (SELECT COUNT(*) FROM @Matrix) <> 28
        OR (SELECT COUNT(*) FROM @Matrix WHERE KhachHang = 1) <> 21
        OR (SELECT COUNT(*) FROM @Matrix WHERE [Admin] = 1) <> 18
        OR (SELECT COUNT(*) FROM @Matrix WHERE NhanVien = 1) <> 13
        OR (SELECT COUNT(*) FROM @Matrix WHERE KhachVangLai = 1) <> 6
    BEGIN
        THROW 51003, N'So quyen trong ma tran khong dung bang goc, khong cap nhat.', 1;
    END;

    INSERT INTO dbo.Quyen (MaQuyenCode, TenQuyen)
    SELECT m.MaQuyenCode, m.TenQuyen FROM @Matrix m
    WHERE NOT EXISTS (SELECT 1 FROM dbo.Quyen q WHERE q.MaQuyenCode = m.MaQuyenCode);

    UPDATE q SET TenQuyen = m.TenQuyen
    FROM dbo.Quyen q JOIN @Matrix m ON m.MaQuyenCode = q.MaQuyenCode;

    DECLARE @Expected TABLE (
        MaVaiTroCode VARCHAR(50) NOT NULL,
        MaQuyenCode VARCHAR(100) NOT NULL,
        PRIMARY KEY (MaVaiTroCode, MaQuyenCode)
    );

    INSERT INTO @Expected (MaVaiTroCode, MaQuyenCode)
    SELECT x.MaVaiTroCode, m.MaQuyenCode
    FROM @Matrix AS m
    CROSS APPLY (VALUES
        ('KHACH_HANG', m.KhachHang),
        ('QUAN_TRI', m.[Admin]),
        ('NHAN_VIEN', m.NhanVien),
        ('KHACH_VANG_LAI', m.KhachVangLai)
    ) AS x(MaVaiTroCode, DuocPhep)
    WHERE x.DuocPhep = 1;

    -- Xoa CHI cac lien ket quyen CU sai bang, KHONG xoa vai tro/tai khoan.
    DELETE pq
    FROM dbo.PhanQuyen pq
    JOIN dbo.VaiTro vt ON vt.MaVaiTro = pq.MaVaiTro
    JOIN dbo.Quyen q ON q.MaQuyen = pq.MaQuyen
    JOIN @Roles r ON r.MaVaiTroCode = vt.MaVaiTroCode
    WHERE NOT EXISTS (
        SELECT 1 FROM @Expected e
        WHERE e.MaVaiTroCode = vt.MaVaiTroCode AND e.MaQuyenCode = q.MaQuyenCode
    );

    -- Them dung nhung lien ket con thieu.
    INSERT INTO dbo.PhanQuyen (MaVaiTro, MaQuyen)
    SELECT vt.MaVaiTro, q.MaQuyen
    FROM @Expected e
    JOIN dbo.VaiTro vt ON vt.MaVaiTroCode = e.MaVaiTroCode
    JOIN dbo.Quyen q ON q.MaQuyenCode = e.MaQuyenCode
    WHERE NOT EXISTS (
        SELECT 1 FROM dbo.PhanQuyen pq
        WHERE pq.MaVaiTro = vt.MaVaiTro AND pq.MaQuyen = q.MaQuyen
    );

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('KHACH_HANG', 21), ('QUAN_TRI', 18),
            ('NHAN_VIEN', 13), ('KHACH_VANG_LAI', 6)
        ) AS expected(MaVaiTroCode, SoQuyen)
        OUTER APPLY (
            SELECT COUNT(*) AS SoThucTe
            FROM dbo.PhanQuyen pq
            JOIN dbo.VaiTro vt ON pq.MaVaiTro = vt.MaVaiTro
            WHERE vt.MaVaiTroCode = expected.MaVaiTroCode
        ) AS actual
        WHERE expected.SoQuyen <> actual.SoThucTe
    )
    BEGIN
        THROW 51004, N'Ket qua phan quyen khong dung 21/18/13/6. Da huy giao dich.', 1;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* 07. DU LIEU MAU (KHONG TAO TAI KHOAN VOI MAT KHAU YEU) */
BEGIN TRY
    BEGIN TRANSACTION;

    INSERT INTO dbo.fitfood_categories ([name], [description], active)
    VALUES
       (N'Cơm dinh dưỡng', N'Các món cơm cân bằng dinh dưỡng', 1),
       (N'Salad', N'Salad rau củ và thịt', 1),
       (N'Đồ uống', N'Sinh tố và nước ép tươi', 1),
       (N'Gói ăn', N'Gói ăn theo ngày', 1);

    INSERT INTO dbo.fitfood_products (category_id, [name], [description], price, image_url, active)
    SELECT c.id, s.product_name, s.detail, s.price, s.image_url, 1
    FROM (VALUES
       (N'Cơm dinh dưỡng', N'Cơm gà sốt tiêu đen', N'Cơm gạo lứt cùng ức gà và sốt tiêu đen', 65000.00, N'/images/chicken-bowl.jpg'),
       (N'Cơm dinh dưỡng', N'Cơm bò xào rau củ', N'Cơm gạo lứt, thịt bò và rau củ', 75000.00, N'/images/beef-bowl.jpg'),
       (N'Cơm dinh dưỡng', N'Cơm cá hồi áp chảo', N'Cá hồi ăn kèm cơm và rau xanh', 95000.00, N'/images/salmon-bowl.jpg'),
       (N'Salad', N'Salad ức gà', N'Rau xanh, cà chua, dưa chuột và ức gà', 60000.00, N'/images/chicken-salad.jpg'),
       (N'Salad', N'Salad cá ngừ', N'Cá ngừ với rau xanh và sốt', 65000.00, N'/images/green-bowl.jpg'),
       (N'Salad', N'Salad tôm', N'Tôm tươi với rau củ', 70000.00, N'/images/seafood-bowl.jpg'),
       (N'Đồ uống', N'Sinh tố bơ', N'Sinh tố bơ nguyên chất', 40000.00, NULL),
       (N'Đồ uống', N'Sinh tố xoài', N'Sinh tố xoài tươi', 40000.00, NULL),
       (N'Đồ uống', N'Nước ép cam', N'Nước ép cam tươi', 35000.00, NULL),
       (N'Đồ uống', N'Nước ép dứa', N'Nước ép dứa tươi', 35000.00, NULL)
    ) AS s(category_name, product_name, detail, price, image_url)
    JOIN dbo.fitfood_categories AS c ON c.[name] = s.category_name;

    INSERT INTO dbo.fitfood_inventory (product_id, stock_quantity, reserved_quantity, reorder_level)
    SELECT id, 50, 0, 10 FROM dbo.fitfood_products;

    INSERT INTO dbo.fitfood_meal_plans ([name], [description], price, number_of_days, active)
    VALUES (N'Gói ăn 7 ngày', N'Chế độ dinh dưỡng 7 ngày', 450000, 7, 1),
           (N'Gói ăn 14 ngày', N'Chế độ dinh dưỡng 14 ngày', 850000, 14, 1),
           (N'Gói ăn 30 ngày', N'Chế độ dinh dưỡng 30 ngày', 1500000, 30, 1);

    INSERT INTO dbo.fitfood_meal_plan_items(meal_plan_id, product_id, quantity)
    SELECT m.id, p.id, 1
    FROM (VALUES
       (N'Gói ăn 7 ngày', N'Cơm gà sốt tiêu đen'),
       (N'Gói ăn 7 ngày', N'Salad ức gà'),
       (N'Gói ăn 14 ngày', N'Cơm bò xào rau củ'),
       (N'Gói ăn 14 ngày', N'Salad cá ngừ'),
       (N'Gói ăn 30 ngày', N'Cơm cá hồi áp chảo'),
       (N'Gói ăn 30 ngày', N'Salad tôm')
    ) AS s(plan_name, product_name)
    JOIN dbo.fitfood_meal_plans m ON m.[name] = s.plan_name
    JOIN dbo.fitfood_products p ON p.[name] = s.product_name;

    INSERT INTO dbo.fitfood_promotions
       (code, [name], percent_discount, amount_discount, starts_at, ends_at,
        minimum_order, maximum_discount, active)
    VALUES
       ('FITFOOD10', N'Giảm 10%', 10, NULL, SYSDATETIME(), DATEADD(DAY,30,SYSDATETIME()), 100000, 100000, 1),
       ('CHAOMUNG50', N'Giảm 50.000đ', NULL, 50000, SYSDATETIME(), DATEADD(DAY,30,SYSDATETIME()), 200000, 50000, 1),
       ('DINHDUONG20', N'Giảm 20%', 20, NULL, SYSDATETIME(), DATEADD(DAY,30,SYSDATETIME()), 300000, 200000, 1);

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

/* 08. BAO CAO/VIEW CO SAN; DON HANG CHUA CO CHO DEN KHI KHACH DAT */
CREATE VIEW dbo.v_fitfood_order_summary
AS
SELECT o.id AS order_id, o.user_id, u.email, o.placed_at, o.[status],
       o.subtotal, o.discount, o.delivery_fee, o.total
FROM dbo.fitfood_orders AS o
JOIN dbo.fitfood_users AS u ON u.id = o.user_id;
GO

/* 08B. DU LIEU MAU DE TEST: KHACH HANG, SAN PHAM, KHUYEN MAI, DON HANG
 Tai khoan khach hang mau (mat khau deu la Test@123, da bam bcrypt):
   khach1@test.com (Nguyễn Văn An)    khach2@test.com (Trần Thị Bình)
   khach3@test.com (Lê Minh Cường)    khach4@test.com (Phạm Thị Dung)
 4 don hang mau du trang thai: Chờ xác nhận, Đang giao, Hoàn thành, Đã hủy.
 XOA phan nay neu khong muon co du lieu test (vi du khi trien khai that). */

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
    VALUES (@o4, N'Phạm Thị Dung', '0934567890', N'23 Trần Phú, Quận Ngô Quyền, Hải Phòng', N'Giao thất bại', N'Khách yêu cầu hủy đơn');
END;
GO

SELECT o.id, u.full_name, o.status, o.subtotal, o.discount, o.delivery_fee, o.total, o.note
FROM dbo.fitfood_orders o
JOIN dbo.fitfood_users u ON u.id = o.user_id
WHERE o.note LIKE N'[[]SAMPLE-ORDER-%'
ORDER BY o.id;
GO

/* 09. KIEM TRA SO LUONG BANG, DANH MUC, SAN PHAM, MA TRAN QUYEN */
SELECT COUNT(*) AS TongBang
FROM sys.tables WHERE schema_id = SCHEMA_ID('dbo');
GO
SELECT vt.MaVaiTroCode, vt.TenVaiTro, COUNT(*) AS SoQuyen
FROM dbo.PhanQuyen pq
JOIN dbo.VaiTro vt ON pq.MaVaiTro = vt.MaVaiTro
GROUP BY vt.MaVaiTroCode, vt.TenVaiTro
ORDER BY CASE vt.MaVaiTroCode WHEN 'KHACH_HANG' THEN 1
    WHEN 'QUAN_TRI' THEN 2 WHEN 'NHAN_VIEN' THEN 3 ELSE 4 END;
GO
SELECT q.MaQuyenCode, q.TenQuyen,
       MAX(CASE WHEN vt.MaVaiTroCode = 'KHACH_HANG' THEN 1 ELSE 0 END) AS KhachHang,
       MAX(CASE WHEN vt.MaVaiTroCode = 'QUAN_TRI' THEN 1 ELSE 0 END) AS [Admin],
       MAX(CASE WHEN vt.MaVaiTroCode = 'NHAN_VIEN' THEN 1 ELSE 0 END) AS NhanVien,
       MAX(CASE WHEN vt.MaVaiTroCode = 'KHACH_VANG_LAI' THEN 1 ELSE 0 END) AS KhachVangLai
FROM dbo.Quyen q
LEFT JOIN dbo.PhanQuyen pq ON pq.MaQuyen = q.MaQuyen
LEFT JOIN dbo.VaiTro vt ON vt.MaVaiTro = pq.MaVaiTro
GROUP BY q.MaQuyenCode, q.TenQuyen
ORDER BY q.MaQuyenCode;
GO
SELECT id, [name], [description], active FROM dbo.fitfood_categories ORDER BY id;
GO
SELECT p.id, p.[name], c.[name] AS DanhMuc, p.price, p.active
FROM dbo.fitfood_products p
JOIN dbo.fitfood_categories c ON c.id = p.category_id
ORDER BY p.id;
GO
SELECT COUNT(*) AS SoTaiKhoan FROM dbo.fitfood_users;
GO
PRINT N'HOAN THANH: DATABASE FITFOOD DA DUOC TAO MOI. HAY CHAY LAI SPRING BOOT.';
GO


USE FITFOOD;
GO

-- Xóa bảng cũ nếu đã tạo nháp trước đó để tránh trùng lặp
IF OBJECT_ID('dbo.food_variant_options', 'U') IS NOT NULL
    DROP TABLE dbo.food_variant_options;
IF OBJECT_ID('dbo.food_variant_groups', 'U') IS NOT NULL
    DROP TABLE dbo.food_variant_groups;
GO

-- 1. Bảng Nhóm biến thể của món ăn (VD: Chọn Size, Chọn Sốt, Thêm Topping)
CREATE TABLE dbo.food_variant_groups (
    id INT IDENTITY(1,1) PRIMARY KEY,
    food_id INT NOT NULL,
    group_name NVARCHAR(100) NOT NULL,      -- VD: N'Chọn Khẩu Phần', N'Loại Sốt'
    is_required BIT NOT NULL DEFAULT 1,     -- 1: Bắt buộc chọn, 0: Tùy chọn
    allow_multiple BIT NOT NULL DEFAULT 0,  -- 0: Chọn 1 (Radio), 1: Chọn nhiều (Checkbox)
    max_select INT NOT NULL DEFAULT 1,      -- Số lượng tối đa được chọn
    sort_order INT NOT NULL DEFAULT 0
);
GO

-- 2. Bảng Các tùy chọn bên trong nhóm (VD: Size S, Size L, Sốt mè rang...)
CREATE TABLE dbo.food_variant_options (
    id INT IDENTITY(1,1) PRIMARY KEY,
    group_id INT NOT NULL,
    option_name NVARCHAR(100) NOT NULL,     -- VD: N'Size L (+150g ức gà)', N'Sốt Chanh Dây'
    extra_price DECIMAL(10, 2) NOT NULL DEFAULT 0, -- Giá cộng thêm (VD: 15000, hoặc 0)
    extra_calories INT NOT NULL DEFAULT 0,  -- Calo cộng/trừ thêm (VD: +120 Kcal)
    is_default BIT NOT NULL DEFAULT 0,      -- 1: Mặc định chọn sẵn
    is_available BIT NOT NULL DEFAULT 1,    -- 1: Còn hàng, 0: Hết hàng
    CONSTRAINT FK_VariantOptions_Group FOREIGN KEY (group_id) 
        REFERENCES dbo.food_variant_groups(id) ON DELETE CASCADE
);
GO

-- 3. Thêm cột lưu biến thể vào bảng chi tiết đơn hàng (nếu bảng order_items đã tồn tại)
IF OBJECT_ID('dbo.order_items', 'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.order_items', 'variants_json') IS NULL
    BEGIN
        ALTER TABLE dbo.order_items
        ADD variants_json NVARCHAR(MAX) NULL,
            unit_final_price DECIMAL(10, 2) NOT NULL DEFAULT 0;
    END
END
GO

-- Tạo nhóm biến thể mẫu cho món ăn có food_id = 1
INSERT INTO dbo.food_variant_groups (food_id, group_name, is_required, allow_multiple, max_select, sort_order)
VALUES 
(1, N'1. Chọn Khẩu Phần (Size)', 1, 0, 1, 1),
(1, N'2. Chọn Nền Tinh Bột (Carb)', 1, 0, 1, 2),
(1, N'3. Topping & Sốt thêm', 0, 1, 2, 3);

-- Lấy ID của 3 nhóm vừa tạo
DECLARE @GroupSize INT = (SELECT TOP 1 id FROM dbo.food_variant_groups WHERE food_id = 1 AND sort_order = 1 ORDER BY id DESC);
DECLARE @GroupCarb INT = (SELECT TOP 1 id FROM dbo.food_variant_groups WHERE food_id = 1 AND sort_order = 2 ORDER BY id DESC);
DECLARE @GroupTopping INT = (SELECT TOP 1 id FROM dbo.food_variant_groups WHERE food_id = 1 AND sort_order = 3 ORDER BY id DESC);

-- Thêm các tùy chọn cho nhóm Size
INSERT INTO dbo.food_variant_options (group_id, option_name, extra_price, extra_calories, is_default, is_available)
VALUES
(@GroupSize, N'Size S - Giảm cân (Low Carb)', -10000, -90, 0, 1),	
(@GroupSize, N'Size M - Tiêu chuẩn (Balance)', 0, 0, 1, 1),
(@GroupSize, N'Size L - Tăng cơ (+100g ức gà)', 25000, 165, 0, 1);

-- Thêm các tùy chọn cho nhóm Tinh bột
INSERT INTO dbo.food_variant_options (group_id, option_name, extra_price, extra_calories, is_default, is_available)
VALUES
(@GroupCarb, N'Gạo lứt huyết rồng', 0, 110, 1, 1),
(@GroupCarb, N'Khoai lang mật nướng', 5000, 95, 0, 1),
(@GroupCarb, N'100% Rau xanh (Không tinh bột)', 0, 25, 0, 1);

-- Thêm các tùy chọn cho nhóm Topping & Sốt
INSERT INTO dbo.food_variant_options (group_id, option_name, extra_price, extra_calories, is_default, is_available)
VALUES
(@GroupTopping, N'Trứng lòng đào ngâm tương', 12000, 75, 0, 1),
(@GroupTopping, N'Thêm Bơ sáp tươi (50g)', 18000, 80, 0, 1),
(@GroupTopping, N'Sốt mè rang Kewpie', 8000, 60, 0, 1);
GO

-- Kiểm tra kết quả
SELECT g.group_name, o.option_name, o.extra_price, o.extra_calories, o.is_default
FROM dbo.food_variant_groups g
JOIN dbo.food_variant_options o ON g.id = o.group_id
WHERE g.food_id = 1
ORDER BY g.sort_order, o.id;
