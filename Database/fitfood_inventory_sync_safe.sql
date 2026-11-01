/*
    FITFOOD - Dong bo du lieu kho an toan
    - Tao inventory cho san pham chua co ban ghi.
    - Neu products.stock va inventory.stock_quantity lech nhau va khong co hang dang giu,
      dong bo theo so nho hon de tranh hien thi ton kho cao hon thuc te.
    - Khong tao stock movement gia cho lich su cu.
    - Chay thu cong sau khi backup database FITFOOD.
*/
USE FITFOOD;
GO

SET XACT_ABORT ON;
BEGIN TRY
    BEGIN TRANSACTION;

    -- Tao ban ghi kho con thieu, lay ton hien tai tu bang san pham.
    INSERT INTO dbo.fitfood_inventory
        (product_id, stock_quantity, reserved_quantity, reorder_level)
    SELECT
        p.id,
        CASE WHEN p.stock IS NULL OR p.stock < 0 THEN 0 ELSE p.stock END,
        0,
        10
    FROM dbo.fitfood_products p
    WHERE NOT EXISTS (
        SELECT 1
        FROM dbo.fitfood_inventory i
        WHERE i.product_id = p.id
    );

    -- Neu chua co hang dang giu, chon so ton thap hon va dong bo ca hai bang.
    UPDATE i
    SET i.stock_quantity = CASE
            WHEN p.stock IS NULL THEN i.stock_quantity
            WHEN p.stock < i.stock_quantity THEN p.stock
            ELSE i.stock_quantity
        END,
        i.updated_at = SYSDATETIME()
    FROM dbo.fitfood_inventory i
    INNER JOIN dbo.fitfood_products p ON p.id = i.product_id
    WHERE i.reserved_quantity = 0
      AND p.stock IS NOT NULL
      AND i.stock_quantity <> p.stock;

    UPDATE p
    SET p.stock = i.stock_quantity
    FROM dbo.fitfood_products p
    INNER JOIN dbo.fitfood_inventory i ON i.product_id = p.id
    WHERE i.reserved_quantity = 0
      AND p.stock IS NOT NULL
      AND p.stock <> i.stock_quantity;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO

-- Kiem tra sau khi dong bo
SELECT
    p.id,
    p.name,
    p.stock AS product_stock,
    i.stock_quantity,
    i.reserved_quantity,
    i.stock_quantity - i.reserved_quantity AS available_stock
FROM dbo.fitfood_products p
LEFT JOIN dbo.fitfood_inventory i ON i.product_id = p.id
ORDER BY p.id;
GO
