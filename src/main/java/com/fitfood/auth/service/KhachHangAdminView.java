package com.fitfood.auth.service;

import com.fitfood.auth.model.AppUser;
import com.fitfood.auth.model.CustomerProfile;

public class KhachHangAdminView {
    private final AppUser account;
    private final CustomerProfile profile;

    public KhachHangAdminView(AppUser account, CustomerProfile profile) {
        this.account = account;
        this.profile = profile;
    }

    public AppUser getAccount() { return account; }
    public CustomerProfile getProfile() { return profile; }
}
