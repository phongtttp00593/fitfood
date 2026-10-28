/* 
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Other/javascript.js to edit this template
 */


/*
 FITFOOD - CHAT HO TRO USER
 Gui tin nhan va lay lich su tu SQL Server.
*/

(function () {
    'use strict';

    function initChat(root) {

        var isWidget = root.dataset.mode === 'widget';

        var panel = isWidget
            ? root.querySelector('.ff-chat-panel')
            : root;

        var toggle = root.querySelector('[data-chat-toggle]');
        var closeButton = root.querySelector('[data-chat-close]');
        var refresh = root.querySelector('[data-chat-refresh]');
        var form = root.querySelector('[data-chat-form]');
        var input = root.querySelector('[data-chat-input]');
        var send = root.querySelector('[data-chat-send]');
        var list = root.querySelector('[data-chat-messages]');
        var status = root.querySelector('[data-chat-status]');
        var error = root.querySelector('[data-chat-error]');

        var poller = null;
        var loading = false;
        var lastSnapshot = '';

        if (!form || !input || !list) return;

        // Hien thi thong bao loi
        function setError(message) {
            error.textContent = message || '';
            error.hidden = !message;
        }

        // Ve danh sach tin nhan
        function draw(data) {

            var messages = Array.isArray(data.messages)
                ? data.messages
                : [];

            var snapshot = JSON.stringify(messages);

            status.textContent =
                data.status || 'Chưa có cuộc trò chuyện';

            if (snapshot === lastSnapshot) return;

            lastSnapshot = snapshot;

            list.replaceChildren();

            // Chua co tin nhan
            if (!messages.length) {

                var intro = document.createElement('div');
                intro.className = 'ff-chat-empty';

                var heading = document.createElement('strong');
                heading.textContent = 'Xin chào!';

                var text = document.createElement('p');

                text.textContent =
                    'Hãy để lại câu hỏi. ' +
                    'Bộ phận hỗ trợ sẽ đọc được tin nhắn ' +
                    'khi hệ thống quản trị được hoàn thiện.';

                intro.append(heading, text);
                list.appendChild(intro);

                return;
            }

            // Hien thi tung tin nhan
            messages.forEach(function (item) {

                var own = item.sender === 'USER';

                var row = document.createElement('div');

                row.className =
                    'ff-chat-message ' +
                    (own ? 'is-own' : 'is-staff');

                var who = document.createElement('span');
                who.className = 'ff-chat-sender';

                who.textContent = own
                    ? 'Bạn'
                    : 'Nhân viên FITFOOD';

                var body = document.createElement('div');
                body.className = 'ff-chat-bubble';

                // textContent de tranh chay HTML khong an toan
                body.textContent = item.content || '';

                var time = document.createElement('small');
                time.className = 'ff-chat-time';

                time.textContent = item.sentAt || '';

                row.append(who, body, time);
                list.appendChild(row);
            });

            list.scrollTop = list.scrollHeight;
        }

        // Goi API
        async function request(url, options) {

            var response = await fetch(
                url,
                Object.assign({
                    credentials: 'same-origin',
                    cache: 'no-store',
                    headers: {
                        'Accept': 'application/json'
                    }
                }, options || {})
            );

            if (
                response.redirected ||
                response.status === 401 ||
                response.status === 403
            ) {
                throw new Error(
                    'Phiên đăng nhập đã hết hạn. ' +
                    'Hãy đăng nhập lại.'
                );
            }

            if (!response.ok) {

                if (response.status === 400) {
                    throw new Error(
                        'Tin nhắn phải có từ 1 đến 1000 ký tự.'
                    );
                }

                throw new Error(
                    'Không thể kết nối hệ thống chat. ' +
                    'Kiểm tra SQL và thử lại.'
                );
            }

            return await response.json();
        }

        // Tai lich su chat
        async function load(silent) {

            if (loading || (isWidget && panel.hidden)) {
                return;
            }

            loading = true;

            try {

                var data = await request(
                    '/api/support/messages'
                );

                draw(data);

                if (!silent) {
                    setError('');
                }

            } catch (err) {

                if (!silent) {
                    setError(
                        err.message || 'Không thể tải tin nhắn.'
                    );
                }

            } finally {
                loading = false;
            }
        }

        // Tu tai tin nhan moi moi 8 giay
        function startPolling() {

            if (poller) {
                clearInterval(poller);
            }

            poller = setInterval(function () {

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

        // Mo / dong cua so chat
        if (toggle) {

            toggle.addEventListener('click', function () {

                panel.hidden = !panel.hidden;

                toggle.setAttribute(
                    'aria-expanded',
                    String(!panel.hidden)
                );

                if (!panel.hidden) {

                    load(false);
                    startPolling();
                    input.focus();

                } else {
                    stopPolling();
                }
            });
        }

        // Nut dong chat
        if (closeButton) {

            closeButton.addEventListener(
                'click',
                function () {

                    panel.hidden = true;

                    toggle.setAttribute(
                        'aria-expanded',
                        'false'
                    );

                    stopPolling();
                    toggle.focus();
                }
            );
        }

        // Dong chat bang Escape
        root.addEventListener('keydown', function (event) {

            if (
                event.key === 'Escape' &&
                isWidget &&
                !panel.hidden
            ) {

                panel.hidden = true;

                toggle.setAttribute(
                    'aria-expanded',
                    'false'
                );

                stopPolling();
                toggle.focus();
            }
        });

        // Tai lai thu cong
        if (refresh) {

            refresh.addEventListener('click', function () {
                load(false);
            });
        }

        // Gui tin nhan
        form.addEventListener(
            'submit',
            async function (event) {

                event.preventDefault();

                var content = input.value.trim();

                if (!content || content.length > 1000) {

                    setError(
                        'Vui lòng nhập từ 1 đến 1000 ký tự.'
                    );

                    return;
                }

                setError('');
                send.disabled = true;

                var csrfHeader =
                    root.dataset.csrfHeader ||
                    'X-CSRF-TOKEN';

                var headers = {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                };

                headers[csrfHeader] =
                    root.dataset.csrf || '';

                try {

                    var data = await request(
                        '/api/support/messages',
                        {
                            method: 'POST',
                            headers: headers,
                            body: JSON.stringify({
                                content: content
                            })
                        }
                    );

                    input.value = '';

                    draw(data);

                } catch (err) {

                    setError(
                        err.message ||
                        'Không gửi được tin nhắn.'
                    );

                } finally {

                    send.disabled = false;
                    input.focus();
                }
            }
        );

        // Enter: Gui | Shift + Enter: Xuong dong
        input.addEventListener(
            'keydown',
            function (event) {

                if (
                    event.key === 'Enter' &&
                    !event.shiftKey &&
                    !event.isComposing
                ) {

                    event.preventDefault();
                    form.requestSubmit();
                }
            }
        );

        // Trang chat rieng luon tai lich su
        if (!isWidget) {
            load(false);
            startPolling();
        }
    }

    document
        .querySelectorAll('[data-support-chat]')
        .forEach(initChat);

})();

