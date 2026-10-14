USE master;
GO

/* =========================================================
   XÓA DATABASE CŨ
   ========================================================= */

IF DB_ID(N'FITFOOD') IS NOT NULL
BEGIN
    ALTER DATABASE FITFOOD SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE FITFOOD;
END
GO

/* =========================================================
   TẠO DATABASE FITFOOD
   ========================================================= */

CREATE DATABASE FITFOOD;
GO

USE FITFOOD;
GO

/* =========================================================
   1. BẢNG VAI TRÒ
   ========================================================= */

CREATE TABLE VaiTro (
    MaVaiTro INT IDENTITY(1,1) PRIMARY KEY,
    MaVaiTroCode VARCHAR(50) NOT NULL UNIQUE,
    TenVaiTro NVARCHAR(100) NOT NULL
);

INSERT INTO VaiTro (MaVaiTroCode, TenVaiTro)
VALUES
('KHACH_HANG', N'Khách hàng'),
('QUAN_TRI', N'Quản trị viên'),
('NHAN_VIEN', N'Nhân viên'),
('KHACH_VANG_LAI', N'Khách vãng lai');


/* =========================================================
   2. BẢNG QUYỀN
   ========================================================= */

CREATE TABLE Quyen (
    MaQuyen INT IDENTITY(1,1) PRIMARY KEY,
    MaQuyenCode VARCHAR(100) NOT NULL UNIQUE,
    TenQuyen NVARCHAR(255) NOT NULL
);

INSERT INTO Quyen (MaQuyenCode, TenQuyen)
VALUES
('DANG_NHAP', N'Đăng nhập'),
('DANG_KY', N'Đăng ký tài khoản'),
('QUEN_MAT_KHAU', N'Quên mật khẩu'),
('XEM_THUC_DON', N'Xem thực đơn'),
('XEM_DANH_MUC', N'Xem danh mục'),
('XEM_CHI_TIET_SAN_PHAM', N'Xem chi tiết sản phẩm'),
('TIM_KIEM_SAN_PHAM', N'Tìm kiếm sản phẩm'),
('CHON_GOI_AN', N'Chọn gói ăn'),
('THEM_GIO_HANG', N'Thêm vào giỏ hàng'),
('XEM_SUA_GIO_HANG', N'Xem và chỉnh sửa giỏ hàng'),
('DAT_HANG', N'Đặt hàng'),
('THANH_TOAN', N'Thanh toán'),
('AP_DUNG_KHUYEN_MAI', N'Áp dụng khuyến mãi'),
('CHON_DOI_MON', N'Chọn hoặc đổi món'),
('XEM_LICH_SU_DON_HANG', N'Xem lịch sử đơn hàng'),
('THEO_DOI_DON_HANG', N'Theo dõi đơn hàng'),
('QUAN_LY_DON_HANG', N'Quản lý đơn hàng'),
('QUAN_LY_SAN_PHAM', N'Quản lý sản phẩm'),
('QUAN_LY_DANH_MUC', N'Quản lý danh mục'),
('QUAN_LY_KHUYEN_MAI', N'Quản lý khuyến mãi'),
('QUAN_LY_KHACH_HANG', N'Quản lý khách hàng'),
('QUAN_LY_GIAO_HANG', N'Quản lý giao hàng'),
('XEM_BAO_CAO', N'Xem báo cáo và thống kê'),
('HUY_DON_HANG', N'Hủy đơn hàng'),
('SUA_THONG_TIN_KHACH_HANG', N'Chỉnh sửa thông tin khách hàng'),
('XEM_THONG_TIN_KHACH_HANG', N'Xem thông tin khách hàng'),
('HO_TRO_KHACH_HANG', N'Chăm sóc khách hàng'),
('HOA_DON', N'Hóa đơn'),
('CHI_TIET_HOA_DON', N'Chi tiết hóa đơn');


/* =========================================================
   3. BẢNG PHÂN QUYỀN
   ========================================================= */

CREATE TABLE PhanQuyen (
    MaVaiTro INT NOT NULL,
    MaQuyen INT NOT NULL,

    PRIMARY KEY (MaVaiTro, MaQuyen),

    FOREIGN KEY (MaVaiTro)
        REFERENCES VaiTro(MaVaiTro),

    FOREIGN KEY (MaQuyen)
        REFERENCES Quyen(MaQuyen)
);


/* =========================================================
   QUYỀN KHÁCH HÀNG
   ========================================================= */

INSERT INTO PhanQuyen (MaVaiTro, MaQuyen)
SELECT 1, MaQuyen
FROM Quyen
WHERE MaQuyenCode IN (
    'DANG_NHAP',
    'DANG_KY',
    'QUEN_MAT_KHAU',
    'XEM_THUC_DON',
    'XEM_DANH_MUC',
    'XEM_CHI_TIET_SAN_PHAM',
    'TIM_KIEM_SAN_PHAM',
    'CHON_GOI_AN',
    'THEM_GIO_HANG',
    'XEM_SUA_GIO_HANG',
    'DAT_HANG',
    'THANH_TOAN',
    'AP_DUNG_KHUYEN_MAI',
    'CHON_DOI_MON',
    'XEM_LICH_SU_DON_HANG',
    'THEO_DOI_DON_HANG',
    'HUY_DON_HANG',
    'SUA_THONG_TIN_KHACH_HANG',
    'XEM_THONG_TIN_KHACH_HANG',
    'HOA_DON',
    'CHI_TIET_HOA_DON'
);


/* =========================================================
   QUYỀN QUẢN TRỊ VIÊN
   ========================================================= */

INSERT INTO PhanQuyen (MaVaiTro, MaQuyen)
SELECT 2, MaQuyen
FROM Quyen
WHERE MaQuyenCode IN (
    'DANG_NHAP',
    'QUEN_MAT_KHAU',
    'XEM_THUC_DON',
    'XEM_DANH_MUC',
    'XEM_CHI_TIET_SAN_PHAM',
    'TIM_KIEM_SAN_PHAM',
    'XEM_LICH_SU_DON_HANG',
    'THEO_DOI_DON_HANG',
    'QUAN_LY_DON_HANG',
    'QUAN_LY_SAN_PHAM',
    'QUAN_LY_DANH_MUC',
    'QUAN_LY_KHUYEN_MAI',
    'QUAN_LY_KHACH_HANG',
    'QUAN_LY_GIAO_HANG',
    'XEM_BAO_CAO',
    'HUY_DON_HANG',
    'SUA_THONG_TIN_KHACH_HANG',
    'XEM_THONG_TIN_KHACH_HANG',
    'HO_TRO_KHACH_HANG',
    'HOA_DON',
    'CHI_TIET_HOA_DON'
);


/* =========================================================
   QUYỀN NHÂN VIÊN
   ========================================================= */

INSERT INTO PhanQuyen (MaVaiTro, MaQuyen)
SELECT 3, MaQuyen
FROM Quyen
WHERE MaQuyenCode IN (
    'DANG_NHAP',
    'QUEN_MAT_KHAU',
    'XEM_THUC_DON',
    'XEM_DANH_MUC',
    'XEM_CHI_TIET_SAN_PHAM',
    'TIM_KIEM_SAN_PHAM',
    'XEM_LICH_SU_DON_HANG',
    'THEO_DOI_DON_HANG',
    'QUAN_LY_SAN_PHAM',
    'QUAN_LY_DANH_MUC',
    'QUAN_LY_KHACH_HANG',
    'QUAN_LY_GIAO_HANG',
    'HUY_DON_HANG',
    'SUA_THONG_TIN_KHACH_HANG',
    'XEM_THONG_TIN_KHACH_HANG',
    'HO_TRO_KHACH_HANG',
    'HOA_DON',
    'CHI_TIET_HOA_DON'
);


/* =========================================================
   QUYỀN KHÁCH VÃNG LAI
   ========================================================= */

INSERT INTO PhanQuyen (MaVaiTro, MaQuyen)
SELECT 4, MaQuyen
FROM Quyen
WHERE MaQuyenCode IN (
    'DANG_NHAP',
    'DANG_KY',
    'QUEN_MAT_KHAU',
    'XEM_THUC_DON',
    'XEM_DANH_MUC',
    'XEM_CHI_TIET_SAN_PHAM',
    'TIM_KIEM_SAN_PHAM'
);


/* =========================================================
   4. NGƯỜI DÙNG
   ========================================================= */

CREATE TABLE NguoiDung (
    MaNguoiDung INT IDENTITY(1,1) PRIMARY KEY,
    TenDangNhap VARCHAR(50) NOT NULL UNIQUE,
    MatKhau VARCHAR(255) NOT NULL,
    Email VARCHAR(100) UNIQUE,
    MaVaiTro INT NOT NULL,
    TrangThai BIT NOT NULL DEFAULT 1,
    NgayTao DATETIME DEFAULT GETDATE(),

    FOREIGN KEY (MaVaiTro)
        REFERENCES VaiTro(MaVaiTro)
);


/* =========================================================
   5. THÔNG TIN KHÁCH HÀNG
   ========================================================= */

CREATE TABLE ThongTinKhachHang (
    MaThongTin INT IDENTITY(1,1) PRIMARY KEY,
    MaNguoiDung INT NOT NULL UNIQUE,
    HoTen NVARCHAR(100) NOT NULL,
    SoDienThoai VARCHAR(20),
    DiaChi NVARCHAR(255),
    NgaySinh DATE,
    GioiTinh NVARCHAR(10),

    FOREIGN KEY (MaNguoiDung)
        REFERENCES NguoiDung(MaNguoiDung)
);


/* =========================================================
   6. DANH MỤC
   ========================================================= */

CREATE TABLE DanhMuc (
    MaDanhMuc INT IDENTITY(1,1) PRIMARY KEY,
    TenDanhMuc NVARCHAR(100) NOT NULL,
    MoTa NVARCHAR(255),
    TrangThai BIT DEFAULT 1
);


/* =========================================================
   7. SẢN PHẨM
   ========================================================= */

CREATE TABLE SanPham (
    MaSanPham INT IDENTITY(1,1) PRIMARY KEY,
    MaDanhMuc INT NOT NULL,
    TenSanPham NVARCHAR(150) NOT NULL,
    MoTa NVARCHAR(500),
    DonGia DECIMAL(18,2) NOT NULL,
    HinhAnh VARCHAR(500),
    TrangThai BIT DEFAULT 1,
    NgayTao DATETIME DEFAULT GETDATE(),

    FOREIGN KEY (MaDanhMuc)
        REFERENCES DanhMuc(MaDanhMuc)
);


/* =========================================================
   8. GÓI ĂN
   ========================================================= */

CREATE TABLE GoiAn (
    MaGoiAn INT IDENTITY(1,1) PRIMARY KEY,
    TenGoiAn NVARCHAR(150) NOT NULL,
    MoTa NVARCHAR(500),
    Gia DECIMAL(18,2) NOT NULL,
    SoNgay INT,
    TrangThai BIT DEFAULT 1
);


/* =========================================================
   9. SẢN PHẨM TRONG GÓI ĂN
   ========================================================= */

CREATE TABLE SanPhamGoiAn (
    MaGoiAn INT NOT NULL,
    MaSanPham INT NOT NULL,
    SoLuong INT NOT NULL DEFAULT 1,

    PRIMARY KEY (MaGoiAn, MaSanPham),

    FOREIGN KEY (MaGoiAn)
        REFERENCES GoiAn(MaGoiAn),

    FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)
);


/* =========================================================
   10. GIỎ HÀNG
   ========================================================= */

CREATE TABLE GioHang (
    MaGioHang INT IDENTITY(1,1) PRIMARY KEY,
    MaNguoiDung INT NOT NULL UNIQUE,
    NgayTao DATETIME DEFAULT GETDATE(),

    FOREIGN KEY (MaNguoiDung)
        REFERENCES NguoiDung(MaNguoiDung)
);


/* =========================================================
   11. CHI TIẾT GIỎ HÀNG
   ========================================================= */

CREATE TABLE ChiTietGioHang (
    MaChiTiet INT IDENTITY(1,1) PRIMARY KEY,
    MaGioHang INT NOT NULL,
    MaSanPham INT NOT NULL,
    SoLuong INT NOT NULL DEFAULT 1,
    DonGia DECIMAL(18,2) NOT NULL,

    FOREIGN KEY (MaGioHang)
        REFERENCES GioHang(MaGioHang),

    FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)
);


/* =========================================================
   12. KHUYẾN MÃI
   ========================================================= */

CREATE TABLE KhuyenMai (
    MaKhuyenMai INT IDENTITY(1,1) PRIMARY KEY,
    MaKhuyenMaiCode VARCHAR(50) NOT NULL UNIQUE,
    TenKhuyenMai NVARCHAR(150) NOT NULL,
    PhanTramGiam DECIMAL(5,2),
    SoTienGiam DECIMAL(18,2),
    NgayBatDau DATETIME NOT NULL,
    NgayKetThuc DATETIME NOT NULL,
    DonHangToiThieu DECIMAL(18,2) DEFAULT 0,
    GiamToiDa DECIMAL(18,2),
    TrangThai BIT DEFAULT 1
);


/* =========================================================
   13. ĐƠN HÀNG
   ========================================================= */

CREATE TABLE DonHang (
    MaDonHang INT IDENTITY(1,1) PRIMARY KEY,
    MaNguoiDung INT NOT NULL,
    MaKhuyenMai INT NULL,

    NgayDat DATETIME DEFAULT GETDATE(),

    TongTienSanPham DECIMAL(18,2) NOT NULL DEFAULT 0,
    TienGiam DECIMAL(18,2) DEFAULT 0,
    PhiGiaoHang DECIMAL(18,2) DEFAULT 0,
    TongTien DECIMAL(18,2) NOT NULL DEFAULT 0,

    TrangThai NVARCHAR(50) NOT NULL
        DEFAULT N'Chờ xác nhận',

    GhiChu NVARCHAR(500),

    FOREIGN KEY (MaNguoiDung)
        REFERENCES NguoiDung(MaNguoiDung),

    FOREIGN KEY (MaKhuyenMai)
        REFERENCES KhuyenMai(MaKhuyenMai)
);


/* =========================================================
   14. CHI TIẾT ĐƠN HÀNG
   ========================================================= */

CREATE TABLE ChiTietDonHang (
    MaChiTiet INT IDENTITY(1,1) PRIMARY KEY,
    MaDonHang INT NOT NULL,
    MaSanPham INT NOT NULL,

    TenSanPham NVARCHAR(150),
    SoLuong INT NOT NULL,
    DonGia DECIMAL(18,2) NOT NULL,
    ThanhTien DECIMAL(18,2) NOT NULL,

    FOREIGN KEY (MaDonHang)
        REFERENCES DonHang(MaDonHang),

    FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)
);



/* =========================================================
   16. THANH TOÁN
   ========================================================= */

CREATE TABLE ThanhToan (
    MaThanhToan INT IDENTITY(1,1) PRIMARY KEY,
    MaDonHang INT NOT NULL,
    PhuongThucThanhToan NVARCHAR(50) NOT NULL,
    SoTien DECIMAL(18,2) NOT NULL,
    NgayThanhToan DATETIME DEFAULT GETDATE(),

    TrangThai NVARCHAR(50)
        DEFAULT N'Chưa thanh toán',

    MaGiaoDich VARCHAR(100),

    FOREIGN KEY (MaDonHang)
        REFERENCES DonHang(MaDonHang)
);


/* =========================================================
   17. GIAO HÀNG
   ========================================================= */

CREATE TABLE GiaoHang (
    MaGiaoHang INT IDENTITY(1,1) PRIMARY KEY,
    MaDonHang INT NOT NULL UNIQUE,

    TenNguoiNhan NVARCHAR(100) NOT NULL,
    SoDienThoai VARCHAR(20) NOT NULL,
    DiaChiGiaoHang NVARCHAR(255) NOT NULL,

    TrangThaiGiaoHang NVARCHAR(50)
        DEFAULT N'Chờ giao',

    NgayGui DATETIME NULL,
    NgayGiao DATETIME NULL,

    GhiChu NVARCHAR(255),

    FOREIGN KEY (MaDonHang)
        REFERENCES DonHang(MaDonHang)
);


/* =========================================================
   18. HÓA ĐƠN
   ========================================================= */

CREATE TABLE HoaDon (
    MaHoaDon INT IDENTITY(1,1) PRIMARY KEY,
    MaDonHang INT NOT NULL UNIQUE,

    SoHoaDon VARCHAR(50) NOT NULL UNIQUE,

    NgayLap DATETIME DEFAULT GETDATE(),

    TenKhachHang NVARCHAR(100),
    SoDienThoai VARCHAR(20),
    DiaChi NVARCHAR(255),

    TongTien DECIMAL(18,2) NOT NULL,

    TrangThai NVARCHAR(50)
        DEFAULT N'Đã lập',

    FOREIGN KEY (MaDonHang)
        REFERENCES DonHang(MaDonHang)
);


/* =========================================================
   19. CHI TIẾT HÓA ĐƠN
   ========================================================= */

CREATE TABLE ChiTietHoaDon (
    MaChiTiet INT IDENTITY(1,1) PRIMARY KEY,

    MaHoaDon INT NOT NULL,
    MaSanPham INT NOT NULL,

    TenSanPham NVARCHAR(150),
    SoLuong INT NOT NULL,
    DonGia DECIMAL(18,2) NOT NULL,
    ThanhTien DECIMAL(18,2) NOT NULL,

    FOREIGN KEY (MaHoaDon)
        REFERENCES HoaDon(MaHoaDon),

    FOREIGN KEY (MaSanPham)
        REFERENCES SanPham(MaSanPham)
);


/* =========================================================
   20. HỖ TRỢ KHÁCH HÀNG
   ========================================================= */

CREATE TABLE HoTroKhachHang (
    MaHoTro INT IDENTITY(1,1) PRIMARY KEY,

    MaNguoiDung INT NOT NULL,

    TieuDe NVARCHAR(200),
    NoiDung NVARCHAR(1000),

    TrangThai NVARCHAR(50)
        DEFAULT N'Chưa xử lý',

    MaNhanVien INT NULL,

    NgayTao DATETIME DEFAULT GETDATE(),
    NgayXuLy DATETIME NULL,

    FOREIGN KEY (MaNguoiDung)
        REFERENCES NguoiDung(MaNguoiDung),

    FOREIGN KEY (MaNhanVien)
        REFERENCES NguoiDung(MaNguoiDung)
);


/* =========================================================
   21. DỮ LIỆU NGƯỜI DÙNG
   ========================================================= */

INSERT INTO NguoiDung
(TenDangNhap, MatKhau, Email, MaVaiTro)
VALUES
('quantri', '123456', 'quantri@fitfood.vn', 2),
('nhanvien', '123456', 'nhanvien@fitfood.vn', 3),
('test', '123456', 'test@gmail.com', 1),
('khachvanglai', '123456', 'khachvanglai@gmail.com', 4);


/* =========================================================
   22. DỮ LIỆU KHÁCH HÀNG
   ========================================================= */

INSERT INTO ThongTinKhachHang
(MaNguoiDung, HoTen, SoDienThoai, DiaChi, GioiTinh)
VALUES
(
    3,
    N'Nguyễn Hoàng Đạt',
    '0912345678',
    N'Hải Phòng',
    N'Nam'
);


/* =========================================================
   23. DỮ LIỆU DANH MỤC
   ========================================================= */

INSERT INTO DanhMuc
(TenDanhMuc, MoTa)
VALUES
(
    N'Cơm dinh dưỡng',
    N'Các món cơm đầy đủ dinh dưỡng'
),
(
    N'Salad',
    N'Các món salad rau củ và thịt'
),
(
    N'Đồ uống',
    N'Nước ép và sinh tố tươi'
),
(
    N'Gói ăn',
    N'Các gói ăn theo ngày'
);


/* =========================================================
   24. DỮ LIỆU SẢN PHẨM
   ========================================================= */

INSERT INTO SanPham
(MaDanhMuc, TenSanPham, MoTa, DonGia, HinhAnh)
VALUES
(
    1,
    N'Cơm gà sốt tiêu đen',
    N'Cơm gạo lứt kết hợp với ức gà và sốt tiêu đen',
    65000,
    'com-ga-tieu-den.jpg'
),
(
    1,
    N'Cơm bò xào rau củ',
    N'Cơm gạo lứt kết hợp thịt bò và rau củ tươi',
    75000,
    'com-bo-rau-cu.jpg'
),
(
    1,
    N'Cơm cá hồi áp chảo',
    N'Cá hồi áp chảo dùng kèm cơm và rau xanh',
    95000,
    'com-ca-hoi.jpg'
),
(
    2,
    N'Salad ức gà',
    N'Rau xanh, cà chua, dưa chuột và ức gà',
    60000,
    'salad-uc-ga.jpg'
),
(
    2,
    N'Salad cá ngừ',
    N'Cá ngừ kết hợp rau xanh và sốt đặc biệt',
    65000,
    'salad-ca-ngu.jpg'
),
(
    2,
    N'Salad tôm',
    N'Tôm tươi kết hợp rau củ và nước sốt',
    70000,
    'salad-tom.jpg'
),
(
    3,
    N'Sinh tố bơ',
    N'Sinh tố bơ nguyên chất thơm ngon',
    40000,
    'sinh-to-bo.jpg'
),
(
    3,
    N'Sinh tố xoài',
    N'Sinh tố xoài tươi nguyên chất',
    40000,
    'sinh-to-xoai.jpg'
),
(
    3,
    N'Nước ép cam',
    N'Nước ép cam tươi nguyên chất',
    35000,
    'nuoc-ep-cam.jpg'
),
(
    3,
    N'Nước ép dứa',
    N'Nước ép dứa tươi nguyên chất',
    35000,
    'nuoc-ep-dua.jpg'
);


/* =========================================================
   25. DỮ LIỆU GÓI ĂN
   ========================================================= */

INSERT INTO GoiAn
(TenGoiAn, MoTa, Gia, SoNgay)
VALUES
(
    N'Gói ăn 7 ngày',
    N'Gói ăn dinh dưỡng dành cho 7 ngày',
    450000,
    7
),
(
    N'Gói ăn 14 ngày',
    N'Gói ăn dinh dưỡng dành cho 14 ngày',
    850000,
    14
),
(
    N'Gói ăn 30 ngày',
    N'Gói ăn dinh dưỡng dành cho 30 ngày',
    1500000,
    30
);


/* =========================================================
   26. SẢN PHẨM TRONG GÓI
   ========================================================= */

INSERT INTO SanPhamGoiAn
(MaGoiAn, MaSanPham, SoLuong)
VALUES
(1, 1, 1),
(1, 4, 1),
(1, 7, 1),

(2, 1, 1),
(2, 2, 1),
(2, 5, 1),
(2, 8, 1),

(3, 1, 1),
(3, 2, 1),
(3, 3, 1),
(3, 4, 1),
(3, 5, 1),
(3, 6, 1),
(3, 7, 1),
(3, 8, 1),
(3, 9, 1);


/* =========================================================
   27. DỮ LIỆU KHUYẾN MÃI
   ========================================================= */

INSERT INTO KhuyenMai
(
    MaKhuyenMaiCode,
    TenKhuyenMai,
    PhanTramGiam,
    NgayBatDau,
    NgayKetThuc,
    DonHangToiThieu,
    GiamToiDa
)
VALUES
(
    'FITFOOD10',
    N'Giảm 10 phần trăm',
    10,
    GETDATE(),
    DATEADD(DAY, 30, GETDATE()),
    100000,
    100000
),
(
    'CHAOMUNG50',
    N'Giảm 50.000 đồng cho khách hàng mới',
    0,
    GETDATE(),
    DATEADD(DAY, 30, GETDATE()),
    200000,
    50000
),
(
    'DINHDUONG20',
    N'Giảm 20 phần trăm cho gói ăn',
    20,
    GETDATE(),
    DATEADD(DAY, 30, GETDATE()),
    300000,
    200000
);


/* =========================================================
   28. KIỂM TRA VAI TRÒ
   ========================================================= */

SELECT
    MaVaiTro AS N'Mã vai trò',
    MaVaiTroCode AS N'Mã vai trò',
    TenVaiTro AS N'Tên vai trò'
FROM VaiTro;


/* =========================================================
   29. KIỂM TRA QUYỀN
   ========================================================= */

SELECT
    MaQuyen AS N'Mã quyền',
    MaQuyenCode AS N'Mã chức năng',
    TenQuyen AS N'Tên chức năng'
FROM Quyen;


/* =========================================================
   30. KIỂM TRA PHÂN QUYỀN
   ========================================================= */

SELECT
    vt.TenVaiTro AS N'Vai trò',
    q.TenQuyen AS N'Chức năng'
FROM PhanQuyen pq
JOIN VaiTro vt
    ON pq.MaVaiTro = vt.MaVaiTro
JOIN Quyen q
    ON pq.MaQuyen = q.MaQuyen
ORDER BY
    vt.MaVaiTro,
    q.MaQuyen;


/* =========================================================
   31. KIỂM TRA NGƯỜI DÙNG
   ========================================================= */

SELECT
    nd.MaNguoiDung AS N'Mã người dùng',
    nd.TenDangNhap AS N'Tên đăng nhập',
    nd.Email AS N'Email',
    vt.TenVaiTro AS N'Vai trò',
    CASE
        WHEN nd.TrangThai = 1 THEN N'Hoạt động'
        ELSE N'Khóa'
    END AS N'Trạng thái'
FROM NguoiDung nd
JOIN VaiTro vt
    ON nd.MaVaiTro = vt.MaVaiTro;


/* =========================================================
   32. KIỂM TRA DANH MỤC
   ========================================================= */

SELECT
    MaDanhMuc AS N'Mã danh mục',
    TenDanhMuc AS N'Tên danh mục',
    MoTa AS N'Mô tả'
FROM DanhMuc;


/* =========================================================
   33. KIỂM TRA SẢN PHẨM
   ========================================================= */

SELECT
    sp.MaSanPham AS N'Mã sản phẩm',
    sp.TenSanPham AS N'Tên sản phẩm',
    dm.TenDanhMuc AS N'Danh mục',
    sp.DonGia AS N'Đơn giá',
    sp.MoTa AS N'Mô tả'
FROM SanPham sp
JOIN DanhMuc dm
    ON sp.MaDanhMuc = dm.MaDanhMuc;


/* =========================================================
   34. KIỂM TRA GÓI ĂN
   ========================================================= */

SELECT
    MaGoiAn AS N'Mã gói ăn',
    TenGoiAn AS N'Tên gói ăn',
    Gia AS N'Giá',
    SoNgay AS N'Số ngày'
FROM GoiAn;


/* =========================================================
   35. KIỂM TRA KHUYẾN MÃI
   ========================================================= */

SELECT
    MaKhuyenMai AS N'Mã khuyến mãi',
    MaKhuyenMaiCode AS N'Mã khuyến mãi',
    TenKhuyenMai AS N'Tên khuyến mãi',
    PhanTramGiam AS N'Phần trăm giảm',
    GiamToiDa AS N'Giảm tối đa'
FROM KhuyenMai;
