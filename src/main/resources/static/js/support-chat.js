
/*
 * FITFOOD - CHAT USER
 * - Ba cham ben canh tin nhan
 * - Chinh sua truc tiep trong o chat
 * - Bang xac nhan thu hoi theo giao dien FITFOOD
 */
(function () {
    'use strict';

    document.querySelectorAll('[data-support-chat]').forEach(initChat);

    function initChat(root) {
        const isWidget = root.dataset.mode === 'widget';

        const panel = isWidget
            ? root.querySelector('.ff-chat-panel')
            : root;

        const toggle = root.querySelector('[data-chat-toggle]');
        const closeButton = root.querySelector('[data-chat-close]');
        const refresh = root.querySelector('[data-chat-refresh]');
        const form = root.querySelector('[data-chat-form]');
        const input = root.querySelector('[data-chat-input]');
        const send = root.querySelector('[data-chat-send]');
        const list = root.querySelector('[data-chat-messages]');
        const status = root.querySelector('[data-chat-status]');
        const error = root.querySelector('[data-chat-error]');

        if (!panel || !form || !input || !send || !list) return;

        let busy = false;
        let loading = false;
        let poller = null;
        let snapshot = '';
        let activeMenu = null;
        let editingMessage = null;
        let previousDraft = '';

        const normalPlaceholder = input.placeholder;

        // CSS giao dien User mau xanh FITFOOD
        if (!document.getElementById('ff-chat-edit-ui-css')) {
            const style = document.createElement('style');
            style.id = 'ff-chat-edit-ui-css';

            style.textContent = `
                .ff-chat-message-line {
                    display:flex;
                    align-items:center;
                    gap:5px;
                    max-width:100%;
                    min-width:0;
                }
                .ff-chat-message.is-own .ff-chat-message-line {
                    flex-direction:row-reverse;
                }
                .ff-chat-message-line .ff-chat-bubble {
                    min-width:0;
                    overflow-wrap:anywhere;
                }
                .ff-chat-menu-wrap {
                    position:relative;
                    display:inline-flex;
                    flex:0 0 28px;
                    z-index:1;
                }
                .ff-chat-menu-wrap.is-open { z-index:30; }
                .ff-chat-menu-trigger {
                    display:grid;
                    place-items:center;
                    width:28px;
                    height:28px;
                    border:0;
                    border-radius:50%;
                    background:transparent;
                    color:#60766a;
                    font-size:23px;
                    cursor:pointer;
                }
                .ff-chat-menu-trigger:hover,
                .ff-chat-menu-trigger[aria-expanded="true"] {
                    background:#e9f1eb;
                    color:#14532d;
                }
                .ff-chat-menu-dropdown {
                    position:absolute;
                    right:0;
                    bottom:calc(100% + 5px);
                    min-width:150px;
                    padding:5px;
                    background:#fff;
                    border:1px solid #dce7df;
                    border-radius:11px;
                    box-shadow:0 10px 25px #173d2430;
                    z-index:31;
                }
                .ff-chat-menu-dropdown[hidden] {
                    display:none!important;
                }
                .ff-chat-menu-dropdown button {
                    display:block;
                    width:100%;
                    padding:10px;
                    text-align:left;
                    border:0;
                    border-radius:7px;
                    background:transparent;
                    color:#243b2c;
                    font:inherit;
                    font-size:13px;
                    cursor:pointer;
                }
                .ff-chat-menu-dropdown button:hover {
                    background:#edf7ef;
                }
                .ff-chat-menu-dropdown button.is-danger {
                    color:#b42318;
                }
                .ff-chat-menu-dropdown button.is-danger:hover {
                    background:#fff1ef;
                }

                /* THANH CHINH SUA PHIA TREN O CHAT */
                .ff-chat-edit-bar {
                    display:flex;
                    align-items:center;
                    justify-content:space-between;
                    gap:10px;
                    padding:10px 13px;
                    border-top:1px solid #d9ebdd;
                    border-left:3px solid #166534;
                    background:#f1f9f3;
                    color:#173b25;
                }
                .ff-chat-edit-bar[hidden] {
                    display:none!important;
                }
                .ff-chat-edit-label {
                    font-weight:700;
                    font-size:12px;
                    margin-bottom:3px;
                }
                .ff-chat-edit-text {
                    font-size:12px;
                    white-space:nowrap;
                    overflow:hidden;
                    text-overflow:ellipsis;
                    max-width:270px;
                    opacity:.85;
                }
                .ff-chat-edit-cancel {
                    flex:none;
                    border:1px solid #b9d6c1;
                    background:#fff;
                    color:#14532d;
                    border-radius:8px;
                    padding:7px 10px;
                    cursor:pointer;
                    font:inherit;
                    font-size:12px;
                }

                /* MODAL XAC NHAN THU HOI */
                .ff-chat-recall-overlay {
                    position:fixed;
                    inset:0;
                    z-index:5000;
                    background:#0b251ab0;
                    display:flex;
                    align-items:center;
                    justify-content:center;
                    padding:20px;
                }
                .ff-chat-recall-overlay[hidden] {
                    display:none!important;
                }
                .ff-chat-recall-dialog {
                    width:min(390px,100%);
                    background:#fff;
                    border:1px solid #d6e9db;
                    border-radius:18px;
                    padding:24px;
                    box-shadow:0 22px 65px #09201560;
                    color:#183527;
                    font-family:inherit;
                }
                .ff-chat-recall-icon {
                    width:48px;
                    height:48px;
                    border-radius:50%;
                    background:#fff0ed;
                    color:#bd392a;
                    display:grid;
                    place-items:center;
                    font-size:25px;
                    margin-bottom:12px;
                }
                .ff-chat-recall-dialog h3 {
                    font-size:19px;
                    margin:0 0 8px;
                    color:#153d29;
                }
                .ff-chat-recall-dialog p {
                    font-size:14px;
                    line-height:1.6;
                    color:#576b5f;
                    margin:0 0 20px;
                }
                .ff-chat-recall-actions {
                    display:flex;
                    justify-content:flex-end;
                    gap:10px;
                }
                .ff-chat-recall-actions button {
                    padding:10px 16px;
                    border-radius:9px;
                    font:inherit;
                    font-weight:600;
                    cursor:pointer;
                }
                .ff-chat-recall-cancel {
                    border:1px solid #cfe2d4;
                    background:#fff;
                    color:#245739;
                }
                .ff-chat-recall-ok {
                    border:1px solid #b42318;
                    background:#b42318;
                    color:#fff;
                }
                .ff-chat-recall-actions button:disabled {
                    opacity:.55;
                    cursor:wait;
                }
                .ff-chat-menu-trigger:focus-visible,
                .ff-chat-menu-dropdown button:focus-visible,
                .ff-chat-edit-cancel:focus-visible,
                .ff-chat-recall-actions button:focus-visible {
                    outline:2px solid #15803d;
                    outline-offset:2px;
                }
            `;

            document.head.append(style);
        }

        function make(tag, className, text) {
            const node = document.createElement(tag);

            if (className) {
                node.className = className;
            }

            if (text !== undefined) {
                node.textContent = text;
            }

            return node;
        }

        function setError(message) {
            if (!error) return;
            error.textContent = message || '';
            error.hidden = !message;
        }

        // THANH HIEN THI TIN NHAN DANG CHINH SUA
        const editBar = make('div', 'ff-chat-edit-bar');
        editBar.hidden = true;

        const editInfo = make('div', '');
        const editTitle = make(
            'div',
            'ff-chat-edit-label',
            '✎ Đang chỉnh sửa tin nhắn'
        );

        const editPreview = make(
            'div',
            'ff-chat-edit-text'
        );

        const cancelEditButton = make(
            'button',
            'ff-chat-edit-cancel',
            'Hủy'
        );

        cancelEditButton.type = 'button';

        editInfo.append(editTitle, editPreview);
        editBar.append(editInfo, cancelEditButton);

        // Chen thanh chinh sua ngay tren form chat
        form.parentNode.insertBefore(editBar, form);

        // TAO BANG XAC NHAN THU HOI
        const overlay = make(
            'div',
            'ff-chat-recall-overlay'
        );
        overlay.hidden = true;

        const dialog = make(
            'section',
            'ff-chat-recall-dialog'
        );

        dialog.setAttribute('role', 'dialog');
        dialog.setAttribute('aria-modal', 'true');

        const dialogTitleId =
            'ff-recall-title-' +
            Math.random().toString(36).slice(2);

        dialog.setAttribute(
            'aria-labelledby',
            dialogTitleId
        );

        const icon = make(
            'div',
            'ff-chat-recall-icon',
            '!'
        );

        icon.setAttribute('aria-hidden', 'true');

        const title = make(
            'h3',
            '',
            'Thu hồi tin nhắn?'
        );
        title.id = dialogTitleId;

        const detail = make(
            'p',
            '',
            'Tin nhắn sẽ được thay bằng “Tin nhắn đã được thu hồi” ở cả hai phía.'
        );

        const dialogActions = make(
            'div',
            'ff-chat-recall-actions'
        );

        const cancelRecallButton = make(
            'button',
            'ff-chat-recall-cancel',
            'Hủy bỏ'
        );

        const confirmRecallButton = make(
            'button',
            'ff-chat-recall-ok',
            'Thu hồi'
        );

        cancelRecallButton.type = 'button';
        confirmRecallButton.type = 'button';

        dialogActions.append(
            cancelRecallButton,
            confirmRecallButton
        );

        dialog.append(
            icon,
            title,
            detail,
            dialogActions
        );

        overlay.append(dialog);
        document.body.append(overlay);

        let recallMessage = null;
        let recallTrigger = null;

        // MENU BA CHAM
        function closeMenu() {
            if (!activeMenu) return;

            activeMenu.menu.hidden = true;

            activeMenu.trigger.setAttribute(
                'aria-expanded',
                'false'
            );

            activeMenu.wrap.classList.remove('is-open');
            activeMenu = null;
        }

        function toggleMenu(wrap, trigger, menu) {
            const same =
                activeMenu && activeMenu.menu === menu;

            closeMenu();

            if (same) return;

            menu.hidden = false;

            trigger.setAttribute(
                'aria-expanded',
                'true'
            );

            wrap.classList.add('is-open');

            activeMenu = {
                wrap,
                trigger,
                menu
            };
        }

        // CHUYEN TIN NHAN CAN SUA VAO O CHAT
        function beginEdit(message) {
            if (busy) return;

            if (!editingMessage) {
                previousDraft = input.value;
            }

            editingMessage = message;

            editPreview.textContent =
                message.content || '';

            editBar.hidden = false;
            input.value = message.content || '';

            input.placeholder =
                'Chỉnh sửa tin nhắn...';

            send.textContent = 'Lưu';

            setError('');
            input.focus();

            input.setSelectionRange(
                input.value.length,
                input.value.length
            );
        }

        // HUY CHE DO CHINH SUA
        function cancelEdit(restoreDraft = true) {
            editingMessage = null;
            editBar.hidden = true;
            editPreview.textContent = '';

            input.placeholder = normalPlaceholder;
            send.textContent = 'Gửi';

            input.value = restoreDraft
                ? previousDraft
                : '';

            previousDraft = '';
        }

        cancelEditButton.addEventListener(
            'click',
            () => cancelEdit()
        );

        // MO BANG XAC NHAN THU HOI
        function openRecall(message, trigger) {
            if (busy) return;

            recallMessage = message;
            recallTrigger = trigger;
            overlay.hidden = false;

            cancelRecallButton.focus();
        }

        function closeRecall() {
            if (busy) return;

            overlay.hidden = true;
            recallMessage = null;

            if (
                recallTrigger &&
                recallTrigger.isConnected
            ) {
                recallTrigger.focus();
            }

            recallTrigger = null;
        }

        cancelRecallButton.addEventListener(
            'click',
            closeRecall
        );

        overlay.addEventListener('click', e => {
            if (e.target === overlay) {
                closeRecall();
            }
        });

        // GIU BAN PHIM TRONG BANG XAC NHAN
        document.addEventListener('keydown', e => {
            if (
                overlay.hidden ||
                e.key !== 'Tab'
            ) {
                return;
            }

            const target = document.activeElement;

            if (
                e.shiftKey &&
                target === cancelRecallButton
            ) {
                e.preventDefault();
                confirmRecallButton.focus();
            } else if (
                !e.shiftKey &&
                target === confirmRecallButton
            ) {
                e.preventDefault();
                cancelRecallButton.focus();
            }
        });

        // BAM RA NGOAI DE DONG MENU BA CHAM
        document.addEventListener('pointerdown', e => {
            if (
                activeMenu &&
                !activeMenu.wrap.contains(e.target)
            ) {
                closeMenu();
            }
        });

        // ESC DE DONG CAC CHE DO DANG MO
        root.addEventListener('keydown', e => {
            if (e.key !== 'Escape') return;

            if (!overlay.hidden) {
                closeRecall();
                e.stopPropagation();
                return;
            }

            if (activeMenu) {
                const t = activeMenu.trigger;
                closeMenu();
                t.focus();
                e.stopPropagation();
                return;
            }

            if (editingMessage) {
                cancelEdit();
                input.focus();
                e.stopPropagation();
                return;
            }

            if (isWidget && !panel.hidden) {
                panel.hidden = true;

                if (toggle) {
                    toggle.setAttribute(
                        'aria-expanded',
                        'false'
                    );
                }

                stopPolling();
            }
        });

        overlay.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                e.preventDefault();
                closeRecall();
            }
        });

        // GOI API CHAT
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
                    'Không thể xử lý yêu cầu. Hãy kiểm tra kết nối và SQL Server.'
                );
            }

            const type =
                response.headers.get('content-type') || '';

            if (!type.includes('application/json')) {
                throw new Error(
                    'Máy chủ không trả về JSON.'
                );
            }

            return response.json();
        }

        // POST KEM CSRF
        function post(url, body) {
            const headers = {
                'Content-Type': 'application/json'
            };

            headers[
                root.dataset.csrfHeader || 'X-CSRF-TOKEN'
            ] = root.dataset.csrf || '';

            return api(url, {
                method: 'POST',
                headers,
                body: JSON.stringify(body)
            });
        }

        // VE DANH SACH TIN NHAN USER
        function draw(data) {
            const messages = Array.isArray(data.messages)
                ? data.messages
                : [];

            if (status) {
                status.textContent =
                    data.status ||
                    'Chưa có cuộc trò chuyện';
            }

            const next = JSON.stringify(messages);
            if (next === snapshot) return;

            snapshot = next;
            closeMenu();

            const oldScroll = list.scrollTop;
            const atBottom =
                list.scrollHeight -
                list.scrollTop -
                list.clientHeight < 90;

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

            for (const message of messages) {
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

                // BONG BONG TIN NHAN VA DAU 3 CHAM
                const messageLine = make(
                    'div',
                    'ff-chat-message-line'
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

                messageLine.append(bubble);

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
                        '✎ Chỉnh sửa'
                    );

                    const recall = make(
                        'button',
                        'is-danger',
                        '↶ Thu hồi'
                    );

                    edit.type = 'button';
                    recall.type = 'button';

                    edit.setAttribute('role', 'menuitem');
                    recall.setAttribute('role', 'menuitem');

                    trigger.addEventListener('click', e => {
                        e.stopPropagation();
                        toggleMenu(wrap, trigger, menu);
                    });

                    edit.addEventListener('click', () => {
                        closeMenu();
                        beginEdit(message);
                    });

                    recall.addEventListener('click', () => {
                        closeMenu();
                        openRecall(message, trigger);
                    });

                    menu.append(edit, recall);
                    wrap.append(trigger, menu);
                    messageLine.append(wrap);
                }

                row.append(messageLine);

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
            }

            // Neu tin dang sua da bi thu hoi o noi khac
            if (editingMessage) {
                const current = messages.find(
                    m => m.id === editingMessage.id
                );

                if (current && current.recalled) {
                    cancelEdit();

                    setError(
                        'Tin nhắn đang sửa đã được thu hồi.'
                    );
                }
            }

            list.scrollTop = atBottom
                ? list.scrollHeight
                : oldScroll;
        }

        // NUT XAC NHAN THU HOI
        confirmRecallButton.addEventListener(
            'click',
            async () => {
                if (busy || !recallMessage) return;

                busy = true;

                confirmRecallButton.disabled = true;
                cancelRecallButton.disabled = true;

                const message = recallMessage;

                try {
                    const data = await post(
                        '/api/support/messages/' +
                        encodeURIComponent(message.id) +
                        '/recall',
                        {}
                    );

                    overlay.hidden = true;
                    recallMessage = null;
                    recallTrigger = null;

                    if (
                        editingMessage &&
                        editingMessage.id === message.id
                    ) {
                        cancelEdit();
                    }

                    draw(data);
                    setError('');

                } catch (e) {
                    setError(
                        e.message ||
                        'Không thể thu hồi tin nhắn.'
                    );

                    overlay.hidden = true;
                    recallMessage = null;
                    recallTrigger = null;

                } finally {
                    busy = false;
                    confirmRecallButton.disabled = false;
                    cancelRecallButton.disabled = false;
                }
            }
        );

        // TAI LICH SU CHAT
        async function load(silent = false) {
            if (
                loading ||
                busy ||
                !overlay.hidden ||
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
            stopPolling();

            poller = setInterval(() => {
                if (!document.hidden) {
                    load(true);
                }
            }, 8000);
        }

        function stopPolling() {
            if (poller) clearInterval(poller);
            poller = null;
        }

        // MO CHAT
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

        // DONG CHAT
        if (closeButton) {
            closeButton.addEventListener('click', () => {
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

        if (refresh) {
            refresh.addEventListener('click', () => load());
        }

        // NUT GUI / LUU CHINH SUA
        form.addEventListener('submit', async e => {
            e.preventDefault();

            if (busy) return;

            const content = input.value.trim();

            if (!content || content.length > 1000) {
                setError(
                    'Tin nhắn phải có từ 1 đến 1000 ký tự.'
                );
                return;
            }

            busy = true;
            send.disabled = true;

            const editedId = editingMessage
                ? editingMessage.id
                : null;

            try {
                // Dang sua -> goi API edit
                // Gui moi -> goi API messages
                const url = editedId !== null
                    ? '/api/support/messages/' +
                      encodeURIComponent(editedId) +
                      '/edit'
                    : '/api/support/messages';

                const data = await post(url, {content});

                if (editedId !== null) {
                    cancelEdit();
                } else {
                    input.value = '';
                }

                draw(data);
                setError('');

            } catch (err) {
                setError(
                    err.message ||
                    'Không lưu được tin nhắn.'
                );

            } finally {
                busy = false;
                send.disabled = false;
                input.focus();
            }
        });

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
