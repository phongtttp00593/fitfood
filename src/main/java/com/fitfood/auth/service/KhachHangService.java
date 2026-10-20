package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.CustomerProfile;
import com.fitfood.auth.model.UserRole;
import com.fitfood.auth.repository.AppUserRepository;
import com.fitfood.auth.repository.CustomerProfileRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KhachHangService {
    private final AppUserRepository users;
    private final CustomerProfileRepository profiles;

    public KhachHangService(AppUserRepository users, CustomerProfileRepository profiles) {
        this.users = users;
        this.profiles = profiles;
    }

    @Transactional(readOnly = true)
    public List<KhachHangAdminView> timKiemKhachHang(String keyword, String status) {
        String key = keyword == null ? "" : keyword.strip().toLowerCase(Locale.ROOT);
        String filter = status == null ? "all" : status;
        return users.findByRoleOrderByCreatedAtDesc(UserRole.USER).stream()
                .map(this::toView)
                .filter(customer -> key.isEmpty()
                        || contains(customer.getAccount().getFullName(), key)
                        || contains(customer.getAccount().getEmail(), key)
                        || contains(customer.getProfile() == null ? null : customer.getProfile().getPhone(), key)
                        || contains(customer.getProfile() == null ? null : customer.getProfile().getAddress(), key))
                .filter(customer -> switch (filter) {
                    case "active" -> customer.getAccount().isEnabled();
                    case "inactive" -> !customer.getAccount().isEnabled();
                    default -> true;
                }).toList();
    }

    @Transactional(readOnly = true)
    public long demKhachHang(String status) {
        return users.findByRoleOrderByCreatedAtDesc(UserRole.USER).stream()
                .filter(user -> switch (status) {
                    case "active" -> user.isEnabled();
                    case "inactive" -> !user.isEnabled();
                    default -> true;
                }).count();
    }

    @Transactional(readOnly = true)
    public KhachHangAdminView timKhachHang(Long id) {
        return toView(layTaiKhoanKhachHang(id));
    }

    @Transactional
    public void capNhatHoSo(Long id, String hoTen, String soDienThoai,
            String diaChi, LocalDate ngaySinh, String gioiTinh) {
        AppUser account = layTaiKhoanKhachHang(id);
        account.setFullName(hoTen.strip());
        users.save(account);

        CustomerProfile profile = profiles.findById(id).orElseGet(() -> {
            CustomerProfile created = new CustomerProfile();
            created.setUserId(id);
            return created;
        });
        profile.setPhone(blankToNull(soDienThoai));
        profile.setAddress(blankToNull(diaChi));
        profile.setBirthday(ngaySinh);
        profile.setGender(blankToNull(gioiTinh));
        profiles.save(profile);
    }

    @Transactional
    public boolean doiTrangThaiTaiKhoan(Long id) {
        AppUser account = layTaiKhoanKhachHang(id);
        account.setEnabled(!account.isEnabled());
        users.save(account);
        return account.isEnabled();
    }

    private KhachHangAdminView toView(AppUser account) {
        return new KhachHangAdminView(account, profiles.findById(account.getId()).orElse(null));
    }

    private AppUser layTaiKhoanKhachHang(Long id) {
        AppUser account = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (account.getRole() != UserRole.USER) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return account;
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
