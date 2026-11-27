
(function () {

    'use strict';

    function initMenu() {

        const grid = document.getElementById('menuGrid');

        if (!grid) return;

        const cards = Array.from(grid.children);

        const searchInput = document.getElementById('q');
        const sortSelect = document.getElementById('sort');
        const resultCount = document.getElementById('resultCount');
        const emptyMessage = document.getElementById('menuEmpty');
        const resetButton = document.getElementById('resetMenuFilters');

        const tabs = Array.from(
            document.querySelectorAll('#menuApp .cat-list .tab:not(.tab-link)')
        );

        const priceOptions = Array.from(
            document.querySelectorAll(
                '#menuApp input[name="priceRange"]'
            )
        );

        let selectedCategory = '';
        let selectedPrice = 'all';

        // Tìm kiếm tiếng Việt có dấu và không dấu
        function normalize(value) {

            return String(value || '')
                .toLowerCase()
                .trim()
                .normalize('NFD')
                .replace(/[\u0300-\u036f]/g, '')
                .replace(/đ/g, 'd');

        }

        // Kiểm tra khoảng giá
        function matchesPrice(price) {

            switch (selectedPrice) {

                case 'under50':
                    return price < 50000;

                case '50to100':
                    return price >= 50000 && price <= 100000;

                case '100to200':
                    return price > 100000 && price <= 200000;

                case 'over200':
                    return price > 200000;

                default:
                    return true;
            }

        }

        // Áp dụng toàn bộ điều kiện lọc
        function applyFilters() {

            const keyword = normalize(
                searchInput ? searchInput.value : ''
            );

            let visibleCards = cards.filter(function (card) {

                const name = normalize(card.dataset.name);
                const category = normalize(card.dataset.cat);
                const description = normalize(card.dataset.description);

                const price = Number(card.dataset.price || 0);

                const categoryMatched =
                    !selectedCategory ||
                    category === normalize(selectedCategory);

                const searchableText =
                    name + ' ' + category + ' ' + description;

                const keywordMatched =
                    !keyword ||
                    keyword.split(/\s+/).every(function (word) {
                        return searchableText.includes(word);
                    });

                const priceMatched = matchesPrice(price);

                return categoryMatched &&
                       keywordMatched &&
                       priceMatched;

            });

            const sortValue = sortSelect ? sortSelect.value : '';

            // Sắp xếp theo giá
            if (sortValue === 'price_asc') {

                visibleCards.sort(function (a, b) {

                    return Number(a.dataset.price || 0)
                         - Number(b.dataset.price || 0);

                });

            } else if (sortValue === 'price_desc') {

                visibleCards.sort(function (a, b) {

                    return Number(b.dataset.price || 0)
                         - Number(a.dataset.price || 0);

                });

            // Sắp xếp theo tên
            } else if (sortValue === 'name_asc') {

                visibleCards.sort(function (a, b) {

                    return String(a.dataset.name || '')
                        .localeCompare(
                            String(b.dataset.name || ''),
                            'vi'
                        );

                });

            } else if (sortValue === 'name_desc') {

                visibleCards.sort(function (a, b) {

                    return String(b.dataset.name || '')
                        .localeCompare(
                            String(a.dataset.name || ''),
                            'vi'
                        );

                });

            }

            // Ẩn tất cả sản phẩm
            cards.forEach(function (card) {

                card.hidden = true;

            });

            // Hiện sản phẩm phù hợp
            visibleCards.forEach(function (card) {

                card.hidden = false;

                grid.appendChild(card);

            });

            // Cập nhật số lượng kết quả
            if (resultCount) {

                resultCount.textContent =
                    'Hiển thị ' + visibleCards.length
                    + ' / ' + cards.length + ' món ăn';

            }

            // Thông báo không tìm thấy sản phẩm
            if (emptyMessage) {

                emptyMessage.hidden =
                    visibleCards.length !== 0;

                emptyMessage.textContent =
                    'Không tìm thấy món ăn phù hợp. '
                    + 'Bạn hãy thử thay đổi bộ lọc.';

            }

        }

        // Chọn danh mục
        function selectCategory(tab) {

            selectedCategory = tab.dataset.filter || '';

            tabs.forEach(function (item) {

                item.classList.remove('on', 'active');

                item.setAttribute(
                    'aria-pressed',
                    'false'
                );

            });

            tab.classList.add('on', 'active');

            tab.setAttribute(
                'aria-pressed',
                'true'
            );

            applyFilters();

        }

        tabs.forEach(function (tab) {

            tab.addEventListener('click', function () {

                selectCategory(tab);

            });

        });

        // Lọc theo giá
        priceOptions.forEach(function (option) {

            option.addEventListener('change', function () {

                if (option.checked) {

                    selectedPrice = option.value;

                    applyFilters();

                }

            });

        });

        // Tìm kiếm
        if (searchInput) {

            searchInput.addEventListener(
                'input',
                applyFilters
            );

        }

        // Sắp xếp
        if (sortSelect) {

            sortSelect.addEventListener(
                'change',
                applyFilters
            );

        }

        // Đặt lại toàn bộ bộ lọc
        if (resetButton) {

            resetButton.addEventListener('click', function () {

                selectedCategory = '';
                selectedPrice = 'all';

                if (searchInput) {

                    searchInput.value = '';

                }

                if (sortSelect) {

                    sortSelect.value = '';

                }

                priceOptions.forEach(function (option) {

                    option.checked = option.value === 'all';

                });

                const allTab = tabs.find(function (tab) {

                    return !tab.dataset.filter;

                });

                if (allTab) {

                    selectCategory(allTab);

                } else {

                    applyFilters();

                }

                // Xóa danh mục cũ trên URL, giữ các tham số khác
                const url = new URL(window.location.href);

                url.searchParams.delete('category');

                window.history.replaceState(
                    {},
                    '',
                    url.toString()
                );

            });

        }

        // Nhận danh mục khi chuyển từ trang chủ sang
        const initialCategory =
            new URLSearchParams(window.location.search)
                .get('category');

        const initialTab = tabs.find(function (tab) {

            return normalize(tab.dataset.filter)
                === normalize(initialCategory);

        });

        if (initialTab) {

            selectCategory(initialTab);

        } else {

            applyFilters();

        }

    }

    if (document.readyState === 'loading') {

        document.addEventListener(
            'DOMContentLoaded',
            initMenu
        );

    } else {

        initMenu();

    }

})();
