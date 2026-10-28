let selectedProductPrice = 0;

function openQuickAddModal(button) {
    const id = button.getAttribute('data-id');
    const name = button.getAttribute('data-name');
    const price = parseFloat(button.getAttribute('data-price')) || 0;

    selectedProductPrice = price;
    
    const inputId = document.getElementById('modalProductId');
    const txtName = document.getElementById('modalName');
    const txtPrice = document.getElementById('modalUnitPriceDisplay');
    const inputQty = document.getElementById('modalQtyInput');
    const modal = document.getElementById('quickAddModal');

    if (inputId) inputId.value = id;
    if (txtName) txtName.innerText = name;
    if (txtPrice) txtPrice.innerText = price.toLocaleString('vi-VN');
    if (inputQty) inputQty.value = 1;

    calculateModalTotal();
    if (modal) modal.style.display = 'flex';
}

function closeQuickAddModal() {
    const modal = document.getElementById('quickAddModal');
    if (modal) modal.style.display = 'none';
}

function modifyQty(amount) {
    const qtyInput = document.getElementById('modalQtyInput');
    if (!qtyInput) return;
    let current = parseInt(qtyInput.value) || 1;
    current += amount;
    if (current < 1) current = 1;
    qtyInput.value = current;
    calculateModalTotal();
}

function calculateModalTotal() {
    const qtyInput = document.getElementById('modalQtyInput');
    const totalDisplay = document.getElementById('modalTotalDisplay');
    if (!qtyInput || !totalDisplay) return;

    const qty = parseInt(qtyInput.value) || 1;
    const total = selectedProductPrice * qty;
    totalDisplay.innerText = total.toLocaleString('vi-VN');
}

// Bấm ra ngoài hộp thoại thì đóng popup
window.addEventListener('click', function(event) {
    const modal = document.getElementById('quickAddModal');
    if (event.target === modal) {
        closeQuickAddModal();
    }
});