
/*
 * FITFOOD - Chat User
 * Menu ba cham nam BEN CANH bong bong tin nhan.
 */
(function () {
    'use strict';

    document.querySelectorAll('[data-support-chat]')
        .forEach(initChat);

    function initChat(root) {
        const isWidget = root.dataset.mode === 'widget';

        const panel = isWidget
            ? root.querySelector('.ff-chat-panel')
            : root;

        const toggle = root.querySelector('[data-chat-toggle]');
        const close = root.querySelector('[data-chat-close]');
        const refresh = root.querySelector('[data-chat-refresh]');
        const form = root.querySelector('[data-chat-form]');
        const input = root.querySelector('[data-chat-input]');
        const send = root.querySelector('[data-chat-send]');
        const list = root.querySelector('[data-chat-messages]');
        const status = root.querySelector('[data-chat-status]');
        const error = root.querySelector('[data-chat-error]');

        if (!form || !input || !list || !panel) return;

        let poller = null;
        let busy = false;
        let loading = false;
        let snapshot = '';
        let activeMenu = null;

        // CSS: dau ba cham va bubble cung mot hang
        if (!document.getElementById('ff-chat-dropdown-style')) {
            const style = document.createElement('style');
            style.id = 'ff-chat-dropdown-style';

            style.textContent = `
                .ff-chat-message-line {
                    display: flex;
                    align-items: center;
                    gap: 5px;
                    max-width: 100%;
                    min-width: 0;
                }

                .ff-chat-message.is-own .ff-chat-message-line {
                    flex-direction: row-reverse;
                }

                .ff-chat-message-line .ff-chat-bubble {
                    min-width: 0;
                    overflow-wrap: anywhere;
                }

                .ff-chat-menu-wrap {
                    position: relative;
                    display: inline-flex;
                    align-self: center;
                    flex: 0 0 28px;
                    z-index: 1;
                }

                .ff-chat-menu-wrap.is-open {
                    z-index: 30;
                }

                .ff-chat-menu-trigger {
                    display: grid;
                    place-items: center;
                    width: 28px;
                    height: 28px;
                    padding: 0;
                    border: 0;
                    border-radius: 50%;
                    background: transparent;
                    color: #60766a;
                    font-size: 22px;
                    line-height: 1;
                    cursor: pointer;
                }

                .ff-chat-menu-trigger:hover,
                .ff-chat-menu-trigger[aria-expanded="true"] {
                    background: #e9f1eb;
                    color: #14532d;
                }

                .ff-chat-menu-trigger:focus-visible,
                .ff-chat-menu-dropdown button:focus-visible {
                    outline: 2px solid #15803d;
                    outline-offset: 2px;
                }

                .ff-chat-menu-dropdown {
                    position: absolute;
                    right: 0;
                    bottom: calc(100% + 5px);
                    min-width: 146px;
                    padding: 5px;
                    background: #fff;
                    color: #1f2937;
                    border: 1px solid #dce7df;
                    border-radius: 10px;
                    box-shadow: 0 8px 25px rgba(0,0,0,.16);
                    z-index: 31;
                }

                .ff-chat-menu-dropdown[hidden] {
                    display: none !important;
                }

                .ff-chat-menu-dropdown button {
                    display: block;
                    width: 100%;
                    padding: 9px 10px;
                    text-align: left;
                    background: transparent;
                    border: 0;
                    border-radius: 6px;
                    font: inherit;
                    font-size: 13px;
                    color: #243b2c;
                    cursor: pointer;
                }

                .ff-chat-menu-dropdown button:hover {
                    background: #edf7ef;
                }

                .ff-chat-menu-dropdown button.is-danger {
                    color: #b42318;
                }

                .ff-chat-menu-dropdown button.is-danger:hover {
                    background: #fff1ef;
                }
            `;

            document.head.append(style);
        }

        function closeMenu() {
            if (!activeMenu) return;

            activeMenu.panel.hidden = true;

            activeMenu.trigger.setAttribute(
                'aria-expanded',
                'false'
            );

            activeMenu.wrap.classList.remove('is-open');
            activeMenu = null;
        }

        function toggleMenu(wrap, trigger, menu) {
            const alreadyOpen =
                activeMenu && activeMenu.panel === menu;

            closeMenu();

            if (alreadyOpen) return;

            menu.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            wrap.classList.add('is-open');

            activeMenu = {
                wrap,
                trigger,
                panel: menu
            };
        }

        document.addEventListener('pointerdown', e => {
            if (
                activeMenu &&
                !activeMenu.wrap.contains(e.target)
            ) {
                closeMenu();
            }
        });

        function setError(message) {
            if (!error) return;

            error.textContent = message || '';
            error.hidden = !message;
        }

        function make(tag, className, text) {
            const element = document.createElement(tag);

            if (className) {
                element.className = className;
            }

            if (text !== undefined) {
                element.textContent = text;
            }

            return element;
        }

        async function api(url, options = {}) {
            const response = await fetch(url, {
                credentials: 'same-origin',
                cache: 'no-store',
                ...options,
                headers: {
                    Accept: 'application/json',
                    ...(options.headers || {})
                }
            });

            const type =
                response.headers.get('content-type') || '';

            if (
                response.status === 401 ||
                response.status === 403 ||
                response.redirected
            ) {
                throw new Error(
                    'Phiên đăng nhập hết hạn hoặc bạn không có quyền.'
                );
            }

            if (!response.ok) {
                if (response.status === 400) {
                    throw new Error(
                        'Tin nhắn phải có từ 1 đến 1000 ký tự.'
                    );
                }

                if (response.status === 404) {
                    throw new Error(
                        'Tin nhắn không tồn tại hoặc bạn không có quyền.'
                    );
                }

                throw new Error(
                    'Không thể xử lý yêu cầu. Kiểm tra kết nối và SQL Server.'
                );
            }

            if (!type.includes('application/json')) {
                throw new Error(
                    'Máy chủ không trả về dữ liệu JSON.'
                );
            }

            return response.json();
        }

        function post(url, body) {
            const header =
                root.dataset.csrfHeader || 'X-CSRF-TOKEN';

            const headers = {
                'Content-Type': 'application/json'
            };

            headers[header] = root.dataset.csrf || '';

            return api(url, {
                method: 'POST',
                headers,
                body: JSON.stringify(body)
            });
        }

        // Hien thi tin nhan
        function draw(data) {
            const messages = Array.isArray(data.messages)
                ? data.messages
                : [];

            if (status) {
                status.textContent =
                    data.status || 'Chưa có cuộc trò chuyện';
            }

            const next = JSON.stringify(messages);

            if (next === snapshot) return;

            snapshot = next;

            closeMenu();
            list.replaceChildren();

            if (!messages.length) {
                const intro = make(
                    'div',
                    'ff-chat-empty'
                );

                intro.append(
                    make('strong', '', 'Xin chào!'),
                    make(
                        'p',
                        '',
                        'Hãy để lại câu hỏi. FITFOOD sẽ phản hồi ngay khi có thể.'
                    )
                );

                list.append(intro);
                return;
            }

            messages.forEach(message => {
                const own = message.sender === 'USER';
                const recalled = !!message.recalled;

                const row = make(
                    'div',
                    'ff-chat-message ' +
                    (own ? 'is-own' : 'is-staff')
                );

                row.append(
                    make(
                        'span',
                        'ff-chat-sender',
                        own ? 'Bạn' : 'Nhân viên FITFOOD'
                    )
                );

                const bubble = make(
                    'div',
                    'ff-chat-bubble',
                    recalled
                        ? 'Tin nhắn đã được thu hồi'
                        : (message.content || '')
                );

                if (recalled) {
                    bubble.style.fontStyle = 'italic';
                }

                // QUAN TRONG:
                // Bubble va dau ba cham nam tren cung mot hang.
                const messageLine = make(
                    'div',
                    'ff-chat-message-line'
                );

                messageLine.append(bubble);

                // Chi hien menu cho tin cua User
                if (
                    own &&
                    message.canManage &&
                    !recalled
                ) {
                    const wrap = make(
                        'div',
                        'ff-chat-menu-wrap'
                    );

                    const trigger = make(
                        'button',
                        'ff-chat-menu-trigger',
                        '⋮'
                    );

                    trigger.type = 'button';

                    trigger.setAttribute(
                        'aria-label',
                        'Tùy chọn tin nhắn'
                    );

                    trigger.setAttribute(
                        'title',
                        'Tùy chọn tin nhắn'
                    );

                    trigger.setAttribute(
                        'aria-haspopup',
                        'menu'
                    );

                    trigger.setAttribute(
                        'aria-expanded',
                        'false'
                    );

                    const menu = make(
                        'div',
                        'ff-chat-menu-dropdown'
                    );

                    menu.hidden = true;
                    menu.setAttribute('role', 'menu');

                    const edit = make(
                        'button',
                        '',
                        'Chỉnh sửa'
                    );

                    const recall = make(
                        'button',
                        'is-danger',
                        'Thu hồi'
                    );

                    edit.type = 'button';
                    recall.type = 'button';

                    edit.setAttribute('role', 'menuitem');
                    recall.setAttribute('role', 'menuitem');

                    trigger.addEventListener(
                        'click',
                        e => {
                            e.stopPropagation();

                            toggleMenu(
                                wrap,
                                trigger,
                                menu
                            );
                        }
                    );

                    edit.addEventListener(
                        'click',
                        () => {
                            closeMenu();
                            changeMessage(message, false);
                        }
                    );

                    recall.addEventListener(
                        'click',
                        () => {
                            closeMenu();
                            changeMessage(message, true);
                        }
                    );

                    menu.append(edit, recall);
                    wrap.append(trigger, menu);

                    // THEM MENU VAO CANH BUBBLE,
                    // KHONG THEM VAO CUOI TIN NHAN.
                    messageLine.append(wrap);
                }

                row.append(messageLine);

                // Thoi gian van nam duoi tin nhan
                row.append(
                    make(
                        'small',
                        'ff-chat-time',
                        (message.sentAt || '') +
                        (
                            message.edited && !recalled
                                ? ' · Đã chỉnh sửa'
                                : ''
                        )
                    )
                );

                list.append(row);
            });

            list.scrollTop = list.scrollHeight;
        }

        // CHINH SUA VA THU HOI
        async function changeMessage(message, recall) {
            if (busy) return;

            let content = '';

            if (recall) {
                if (!window.confirm(
                    'Bạn muốn thu hồi tin nhắn này?'
                )) {
                    return;
                }
            } else {
                const changed = window.prompt(
                    'Chỉnh sửa tin nhắn:',
                    message.content || ''
                );

                if (changed === null) return;

                content = changed.trim();

                if (!content || content.length > 1000) {
                    setError(
                        'Tin nhắn phải có từ 1 đến 1000 ký tự.'
                    );
                    return;
                }
            }

            busy = true;

            try {
                const path =
                    '/api/support/messages/' +
                    encodeURIComponent(message.id) +
                    (recall ? '/recall' : '/edit');

                const data = await post(
                    path,
                    recall ? {} : {content}
                );

                draw(data);
                setError('');

            } catch (e) {
                setError(
                    e.message ||
                    'Không thể thay đổi tin nhắn.'
                );
            } finally {
                busy = false;
            }
        }

        // Tai lich su chat
        async function load(silent = false) {
            if (
                loading ||
                busy ||
                (isWidget && panel.hidden)
            ) {
                return;
            }

            loading = true;

            try {
                draw(
                    await api('/api/support/messages')
                );

                if (!silent) {
                    setError('');
                }

            } catch (e) {
                if (!silent) {
                    setError(
                        e.message ||
                        'Không tải được lịch sử chat.'
                    );
                }
            } finally {
                loading = false;
            }
        }

        function startPolling() {
            if (poller) {
                clearInterval(poller);
            }

            poller = setInterval(() => {
                if (!document.hidden) {
                    load(true);
                }
            }, 8000);
        }

        function stopPolling() {
            if (poller) {
                clearInterval(poller);
            }

            poller = null;
        }

        if (toggle) {
            toggle.addEventListener('click', () => {
                closeMenu();

                panel.hidden = !panel.hidden;

                toggle.setAttribute(
                    'aria-expanded',
                    String(!panel.hidden)
                );

                if (!panel.hidden) {
                    load();
                    startPolling();
                    input.focus();
                } else {
                    stopPolling();
                }
            });
        }

        if (close) {
            close.addEventListener('click', () => {
                closeMenu();
                panel.hidden = true;

                if (toggle) {
                    toggle.setAttribute(
                        'aria-expanded',
                        'false'
                    );

                    toggle.focus();
                }

                stopPolling();
            });
        }

        root.addEventListener('keydown', e => {
            if (e.key === 'Escape' && activeMenu) {
                const trigger = activeMenu.trigger;

                closeMenu();
                trigger.focus();
                e.stopPropagation();
                return;
            }

            if (
                e.key === 'Escape' &&
                isWidget &&
                !panel.hidden
            ) {
                closeMenu();
                panel.hidden = true;

                if (toggle) {
                    toggle.setAttribute(
                        'aria-expanded',
                        'false'
                    );

                    toggle.focus();
                }

                stopPolling();
            }
        });

        if (refresh) {
            refresh.addEventListener('click', () => load());
        }

        // Gui tin nhan User
        form.addEventListener('submit', async e => {
            e.preventDefault();

            if (busy) return;

            const content = input.value.trim();

            if (!content || content.length > 1000) {
                setError(
                    'Vui lòng nhập từ 1 đến 1000 ký tự.'
                );
                return;
            }

            busy = true;

            if (send) {
                send.disabled = true;
            }

            try {
                const result = await post(
                    '/api/support/messages',
                    {content}
                );

                input.value = '';

                draw(result);
                setError('');

            } catch (err) {
                setError(
                    err.message ||
                    'Không gửi được tin nhắn.'
                );
            } finally {
                busy = false;

                if (send) {
                    send.disabled = false;
                }

                input.focus();
            }
        });

        // Enter gui, Shift + Enter xuong dong
        input.addEventListener('keydown', e => {
            if (
                e.key === 'Enter' &&
                !e.shiftKey &&
                !e.isComposing
            ) {
                e.preventDefault();
                form.requestSubmit();
            }
        });

        if (!isWidget) {
            load();
            startPolling();
        }
    }
})();
