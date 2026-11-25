
(function () {
    'use strict';

    window.flyToCart = function (sourceImage, callback) {

        const cartIcon = Array.from(
            document.querySelectorAll('a[href]')
        ).find(function (link) {

            const path = new URL(link.href, location.href)
                .pathname.replace(/\/$/, '');

            const rect = link.getBoundingClientRect();

            return path.endsWith('/cart')
                && rect.width > 0
                && rect.height > 0
                && rect.top >= 0
                && rect.bottom <= window.innerHeight;

        });

        let completed = false;

        function finish() {
            if (completed) return;
            completed = true;

            if (typeof callback === 'function') {
                callback();
            }
        }

        if (!sourceImage || !cartIcon) {
            console.warn('FITFOOD: Không tìm thấy ảnh hoặc giỏ hàng.');
            finish();
            return;
        }

        const start = sourceImage.getBoundingClientRect();
        const end = cartIcon.getBoundingClientRect();

        const startX = start.left + start.width / 2;
        const startY = start.top + start.height / 2;

        const endX = end.left + end.width / 2;
        const endY = end.top + end.height / 2;

        const flyingImage = document.createElement('img');

        flyingImage.src =
            sourceImage.currentSrc || sourceImage.src;

        Object.assign(flyingImage.style, {
            position: 'fixed',
            left: (startX - 60) + 'px',
            top: (startY - 60) + 'px',
            width: '120px',
            height: '120px',
            objectFit: 'cover',
            borderRadius: '50%',
            border: '4px solid white',
            boxShadow: '0 10px 35px rgba(0,0,0,0.3)',
            pointerEvents: 'none',
            zIndex: '2147483647',
            margin: '0'
        });

        document.body.appendChild(flyingImage);

        const duration = 850;
        const startTime = performance.now();

        function animateFrame(now) {

            const progress = Math.min(
                (now - startTime) / duration, 1
            );

            const x = (endX - startX) * progress;

            const y =
                (endY - startY) * progress
                - Math.sin(progress * Math.PI) * 130;

            const scale = 1 - progress * 0.9;

            flyingImage.style.transform =
                'translate(' + x + 'px,' + y + 'px)' +
                ' scale(' + scale + ')' +
                ' rotate(' + progress * 25 + 'deg)';

            flyingImage.style.opacity =
                String(1 - progress * 0.25);

            if (progress < 1) {

                requestAnimationFrame(animateFrame);

            } else {

                flyingImage.remove();

                if (cartIcon.animate) {

                    cartIcon.animate([
                        { transform: 'scale(1)' },
                        { transform: 'scale(1.4)' },
                        { transform: 'scale(0.9)' },
                        { transform: 'scale(1)' }
                    ], {
                        duration: 350,
                        easing: 'ease-in-out'
                    });

                }

                setTimeout(finish, 350);
            }
        }

        requestAnimationFrame(animateFrame);

        // Dự phòng nếu trình duyệt ngừng animation
        setTimeout(function () {
            flyingImage.remove();
            finish();
        }, 1800);
    };

    console.log('FITFOOD: Cart animation loaded');

})();
