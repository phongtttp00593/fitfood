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
  - KHONG chen tai khoan demo co mat khau de doan.
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
