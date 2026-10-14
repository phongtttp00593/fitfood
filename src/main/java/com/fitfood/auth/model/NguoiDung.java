/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.fitfood.auth.model;

import jakarta.persistence.*;
import java.util.Date;

@Entity
@Table(name = "NguoiDung")
public class NguoiDung {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "MaNguoiDung")
    private Integer maNguoiDung;

    @Column(name = "TenDangNhap")
    private String tenDangNhap;

    @Column(name = "MatKhau")
    private String matKhau;

    @Column(name = "Email")
    private String email;

    @Column(name = "MaVaiTro")
    private Integer maVaiTro;

    @Column(name = "TrangThai")
    private Boolean trangThai;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "NgayTao")
    private Date ngayTao;

    public Integer getMaNguoiDung() {
        return maNguoiDung;
    }

    public String getTenDangNhap() {
        return tenDangNhap;
    }

    public String getEmail() {
        return email;
    }

    public Integer getMaVaiTro() {
        return maVaiTro;
    }

    public Boolean getTrangThai() {
        return trangThai;
    }

    public void setTenDangNhap(String tenDangNhap) {
        this.tenDangNhap = tenDangNhap;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setTrangThai(Boolean trangThai) {
        this.trangThai = trangThai;
    }
}