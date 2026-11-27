package com.fitfood.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Cấp địa chỉ cửa hàng và điểm ghim bản đồ cho mọi template (chân trang, trang chủ). */
@ControllerAdvice
public class SiteConfigAdvice {

    private final String storeAddress;
    private final String storeMapQuery;

    public SiteConfigAdvice(
            @Value("${fitfood.store.address:TD2.23 K\u0110T Golden Point, P. \u0110\u1ed3ng Ho\u00e0, TP. H\u1ea3i Ph\u00f2ng}") String storeAddress,
            @Value("${fitfood.store.map-query:}") String storeMapQuery) {
        this.storeAddress = storeAddress;
        // Điểm ghim trên bản đồ: toạ độ "vĩ độ,kinh độ" hoặc từ khoá; để trống thì dùng địa chỉ.
        this.storeMapQuery = (storeMapQuery == null || storeMapQuery.isBlank())
                ? storeAddress : storeMapQuery.trim();
    }

    @ModelAttribute("storeAddress")
    public String storeAddress() {
        return storeAddress;
    }

    @ModelAttribute("storeMapQuery")
    public String storeMapQuery() {
        return storeMapQuery;
    }
}
