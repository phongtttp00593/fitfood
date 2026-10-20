package com.fitfood.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class KhachHangForm {
    @NotBlank(message = "Vui lòng nhập họ và tên.")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự.")
    private String hoTen;

    @Pattern(regexp = "^$|^[0-9+() .-]{8,20}$", message = "Số điện thoại không hợp lệ.")
    private String soDienThoai;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự.")
    private String diaChi;

    @Past(message = "Ngày sinh phải trước ngày hôm nay.")
    private LocalDate ngaySinh;

    @Pattern(regexp = "^$|Nam|Nữ|Khác$", message = "Vui lòng chọn giới tính hợp lệ.")
    private String gioiTinh;

    public String getHoTen() { return hoTen; }
    public void setHoTen(String hoTen) { this.hoTen = hoTen; }
    public String getSoDienThoai() { return soDienThoai; }
    public void setSoDienThoai(String soDienThoai) { this.soDienThoai = soDienThoai; }
    public String getDiaChi() { return diaChi; }
    public void setDiaChi(String diaChi) { this.diaChi = diaChi; }
    public LocalDate getNgaySinh() { return ngaySinh; }
    public void setNgaySinh(LocalDate ngaySinh) { this.ngaySinh = ngaySinh; }
    public String getGioiTinh() { return gioiTinh; }
    public void setGioiTinh(String gioiTinh) { this.gioiTinh = gioiTinh; }
}
