/* 
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/javascript.js to edit this template
 */


/**
 * FITFOOD - ĐỊA CHỈ GIAO HÀNG VIỆT NAM
 * Chọn tỉnh/thành -> phường/xã -> số nhà, đường
 * Sử dụng API miễn phí, không cần API Key.
 */

(() => {
    'use strict';

    const API = 'https://provinces.open-api.vn/api/v2';

    const $ = id => document.getElementById(id);

    let requestId = 0;

    // HIỂN THỊ THÔNG BÁO
    function showStatus(message, error = false) {
        const element = $('checkoutAddressStatus');

        if (element) {
            element.textContent = message;
            element.style.color = error
                ? '#c53030'
                : '#718096';
        }
    }

    // GỌI API
    async function getData(url) {
        const response = await fetch(url, {
            headers: {
                'Accept': 'application/json'
            }
        });

        if (!response.ok) {
            throw new Error('Không thể lấy dữ liệu địa chỉ.');
        }

        return await response.json();
    }

    // LẤY DANH SÁCH TỈNH THÀNH
    async function loadProvinces() {
        const select = $('checkoutProvinceSelect');

        select.disabled = true;
        select.replaceChildren(
            new Option('Đang tải tỉnh/thành...', '')
        );

        try {
            const provinces = await getData(API + '/');

            provinces.sort((a, b) =>
                a.name.localeCompare(b.name, 'vi')
            );

            select.replaceChildren(
                new Option('-- Chọn tỉnh/thành phố --', '')
            );

            for (const province of provinces) {
                select.add(
                    new Option(province.name, province.code)
                );
            }

            select.disabled = false;

            showStatus(
                'Đã tải danh sách tỉnh/thành phố.'
            );

        } catch (error) {
            select.replaceChildren(
                new Option('Không tải được tỉnh/thành', '')
            );

            showStatus(
                'Không thể tải tỉnh/thành. Vui lòng kiểm tra Internet và tải lại trang.',
                true
            );

            console.error(error);
        }
    }

    // LẤY PHƯỜNG XÃ THEO TỈNH
    async function loadWards() {
        const province = $('checkoutProvinceSelect');
        const ward = $('checkoutWardSelect');

        const code = province.value;
        const current = ++requestId;

        ward.disabled = true;
        ward.replaceChildren(
            new Option(
                code ? 'Đang tải phường/xã...' :
                '-- Chọn tỉnh/thành trước --',
                ''
            )
        );

        updateAddress();

        if (!code) {
            return;
        }

        try {
            const data = await getData(
                API + '/p/' + encodeURIComponent(code) + '?depth=2'
            );

            if (current !== requestId) {
                return;
            }

            const wards = data.wards || [];

            wards.sort((a, b) =>
                a.name.localeCompare(b.name, 'vi')
            );

            ward.replaceChildren(
                new Option('-- Chọn phường/xã --', '')
            );

            for (const item of wards) {
                ward.add(
                    new Option(item.name, item.code)
                );
            }

            if (wards.length === 0) {
                throw new Error('Danh sách phường/xã trống.');
            }

            ward.disabled = false;

            showStatus(
                'Đã tải ' + wards.length + ' phường/xã/đặc khu.'
            );

        } catch (error) {
            if (current !== requestId) {
                return;
            }

            ward.disabled = true;
            ward.replaceChildren(
                new Option('Không tải được phường/xã', '')
            );

            showStatus(
                'Lỗi tải phường/xã. Vui lòng chọn lại tỉnh/thành.',
                true
            );

            console.error(error);
        }
    }

    // TỰ ĐỘNG GHÉP ĐỊA CHỈ
    function updateAddress() {
        const province = $('checkoutProvinceSelect');
        const ward = $('checkoutWardSelect');
        const street = $('checkoutStreetAddress');

        const fullAddress = $('checkoutDeliveryAddress');
        const preview = $('checkoutAddressPreview');

        const provinceName = province.value
            ? province.selectedOptions[0].textContent
            : '';

        const wardName = ward.value
            ? ward.selectedOptions[0].textContent
            : '';

        const streetName = street.value.trim();

        fullAddress.value = '';

        if (provinceName && wardName && streetName) {
            const address =
                streetName + ', ' +
                wardName + ', ' +
                provinceName;

            if (address.length <= 255) {
                fullAddress.value = address;
                preview.textContent = address;
            } else {
                preview.textContent =
                    'Địa chỉ quá dài, tối đa 255 ký tự.';
            }
        } else {
            preview.textContent =
                'Chưa chọn đủ thông tin địa chỉ.';
        }
    }

    // KIỂM TRA KHI ĐẶT HÀNG
    function validateCheckout() {
        const province = $('checkoutProvinceSelect');
        const ward = $('checkoutWardSelect');
        const street = $('checkoutStreetAddress');

        updateAddress();

        if (!province.value) {
            showStatus('Vui lòng chọn tỉnh/thành.', true);
            province.focus();
            return false;
        }

        if (!ward.value || ward.disabled) {
            showStatus('Vui lòng chọn phường/xã.', true);
            ward.focus();
            return false;
        }

        if (street.value.trim().length < 3) {
            showStatus(
                'Vui lòng nhập số nhà và tên đường.',
                true
            );
            street.focus();
            return false;
        }

        if (!$('checkoutDeliveryAddress').value) {
            showStatus('Địa chỉ không hợp lệ.', true);
            return false;
        }

        return true;
    }

    // KHỞI TẠO
    function initialize() {
        if (!$('checkoutProvinceSelect')) {
            return;
        }

        $('checkoutProvinceSelect').addEventListener(
            'change',
            loadWards
        );

        $('checkoutWardSelect').addEventListener(
            'change',
            updateAddress
        );

        $('checkoutStreetAddress').addEventListener(
            'input',
            updateAddress
        );

        loadProvinces();
        updateAddress();
    }

    window.FitfoodAddressPicker = {
        validateCheckout
    };

    if (document.readyState === 'loading') {
        document.addEventListener(
            'DOMContentLoaded',
            initialize
        );
    } else {
        initialize();
    }
})();

