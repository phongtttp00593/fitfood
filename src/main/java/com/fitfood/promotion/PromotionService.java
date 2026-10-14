/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */


package com.fitfood.promotion;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionService {

    private final PromotionRepository repository;

    public PromotionService(PromotionRepository repository) {
        this.repository = repository;
    }

    public List<Promotion> findAll(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return repository.findAllByOrderByIdDesc();
        }

        String q = keyword.trim();

        return repository
                .findByCodeContainingIgnoreCaseOrNameContainingIgnoreCaseOrderByIdDesc(q, q);
    }

    public Promotion findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Không tìm thấy khuyến mãi có ID: " + id));
    }

    public PromotionForm createForm() {
        return new PromotionForm();
    }

    public PromotionForm toForm(Promotion p) {
        PromotionForm f = new PromotionForm();

        f.setId(p.getId());
        f.setCode(p.getCode());
        f.setName(p.getName());
        f.setPercentDiscount(p.getPercentDiscount());
        f.setAmountDiscount(p.getAmountDiscount());
        f.setStartsAt(p.getStartsAt());
        f.setEndsAt(p.getEndsAt());
        f.setMinimumOrder(p.getMinimumOrder());
        f.setMaximumDiscount(p.getMaximumDiscount());
        f.setActive(p.isActive());

        return f;
    }

    private void validate(PromotionForm f) {

        if (f.getCode() == null || f.getCode().isBlank()) {
            throw new IllegalArgumentException(
                "Mã khuyến mãi không được để trống");
        }

        if (f.getName() == null || f.getName().isBlank()) {
            throw new IllegalArgumentException(
                "Tên khuyến mãi không được để trống");
        }

        if (f.getStartsAt() == null || f.getEndsAt() == null) {
            throw new IllegalArgumentException(
                "Vui lòng chọn thời gian bắt đầu và kết thúc");
        }

        // Cho phép ngày bắt đầu nằm trong tương lai
        // Chỉ bắt buộc ngày kết thúc sau ngày bắt đầu
        if (!f.getEndsAt().isAfter(f.getStartsAt())) {
            throw new IllegalArgumentException(
                "Ngày kết thúc phải sau ngày bắt đầu");
        }

        boolean hasPercent = f.getPercentDiscount() != null;
        boolean hasAmount = f.getAmountDiscount() != null;

        if (hasPercent == hasAmount) {
            throw new IllegalArgumentException(
                "Chỉ chọn một hình thức giảm: phần trăm hoặc số tiền");
        }

        if (hasPercent) {
            if (f.getPercentDiscount().compareTo(BigDecimal.ZERO) <= 0
                    || f.getPercentDiscount()
                        .compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException(
                    "Phần trăm giảm phải lớn hơn 0 và tối đa 100%");
            }
        }

        if (hasAmount
                && f.getAmountDiscount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Số tiền giảm phải lớn hơn 0");
        }

        if (f.getMinimumOrder() == null
                || f.getMinimumOrder().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Đơn hàng tối thiểu không hợp lệ");
        }

        if (f.getMaximumDiscount() != null
                && f.getMaximumDiscount()
                    .compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                "Giảm tối đa không được âm");
        }

        if (f.getCode().length() > 50
                || f.getName().length() > 150) {
            throw new IllegalArgumentException(
                "Mã hoặc tên khuyến mãi quá dài");
        }
    }

    @Transactional
    public Promotion save(PromotionForm form) {

        validate(form);

        String code = form.getCode()
                .trim()
                .toUpperCase(Locale.ROOT);

        List<Promotion> all = repository.findAll();

        boolean duplicate = all.stream().anyMatch(p ->
                p.getCode().equalsIgnoreCase(code)
                && !p.getId().equals(form.getId())
        );

        if (duplicate) {
            throw new IllegalArgumentException(
                "Mã khuyến mãi đã tồn tại");
        }

        Promotion p;

        if (form.getId() == null) {
            p = new Promotion();
        } else {
            p = findById(form.getId());
        }

        p.setCode(code);
        p.setName(form.getName().trim());
        p.setPercentDiscount(form.getPercentDiscount());
        p.setAmountDiscount(form.getAmountDiscount());
        p.setStartsAt(form.getStartsAt());
        p.setEndsAt(form.getEndsAt());
        p.setMinimumOrder(form.getMinimumOrder());
        p.setMaximumDiscount(form.getMaximumDiscount());
        p.setActive(form.isActive());

        return repository.save(p);
    }

    @Transactional
    public void toggle(Long id) {
        Promotion p = findById(id);
        p.setActive(!p.isActive());
        repository.save(p);
    }

    @Transactional
    public void delete(Long id) {
        Promotion p = findById(id);

        try {
            repository.delete(p);
            repository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                "Không thể xóa khuyến mãi đã được sử dụng trong đơn hàng. "
                + "Hãy tắt khuyến mãi thay vì xóa.");
        }
    }
}

