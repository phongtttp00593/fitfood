
package com.fitfood.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping
public class ProductVariantController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // =========================================
    // TRANG QUAN LY BIEN THE
    // GET /admin/variants
    // =========================================
    @GetMapping("/admin/variants")
    public String openAdminVariantsPage() {
        // Render giao diện quản trị thật; API JSON vẫn được giữ ở các endpoint bên dưới.
        return "admin-variants";
    }

    // =========================================
    // 1. LAY DANH SACH MON AN TU SQL SERVER
    // GET /api/admin/variants/foods
    // =========================================
    @ResponseBody
    @GetMapping("/api/admin/variants/foods")
    public ResponseEntity<?> getFoodsForAdmin() {
        try {
            String sql = """
                SELECT id, name, price
                FROM dbo.fitfood_products
                ORDER BY id
                """;

            List<Map<String, Object>> foods =
                    jdbcTemplate.queryForList(sql);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "foods", foods
            ));
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 2. HAM LAY CAC NHOM BIEN THE
    // =========================================
    private List<Map<String, Object>> loadVariantGroups(Long foodId) {
        String groupSql = """
            SELECT id, group_name,
                   is_required, allow_multiple,
                   max_select, sort_order
            FROM dbo.food_variant_groups
            WHERE food_id = ?
            ORDER BY sort_order, id
            """;

        List<Map<String, Object>> groups =
                jdbcTemplate.queryForList(groupSql, foodId);

        String optionSql = """
            SELECT id, option_name, extra_price,
                   extra_calories, is_default,
                   is_available
            FROM dbo.food_variant_options
            WHERE group_id = ?
            ORDER BY id
            """;

        for (Map<String, Object> group : groups) {
            Long groupId = ((Number) group.get("id")).longValue();

            List<Map<String, Object>> options =
                    jdbcTemplate.queryForList(optionSql, groupId);

            group.put("options", options);
        }

        return groups;
    }

    // =========================================
    // 3. API BIEN THE CHO KHACH HANG
    // GET /api/foods/{id}/variants
    // =========================================
    @ResponseBody
    @GetMapping("/api/foods/{id}/variants")
    public ResponseEntity<?> getFoodVariants(@PathVariable Long id) {
        try {
            List<Map<String, Object>> groups = loadVariantGroups(id);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "food_id", id,
                    "variant_groups", groups
            ));
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 4. API BIEN THE CHO ADMIN
    // GET /api/admin/variants/food/{foodId}
    // =========================================
    @ResponseBody
    @GetMapping("/api/admin/variants/food/{foodId}")
    public ResponseEntity<?> getVariantsForAdmin(@PathVariable Long foodId) {
        try {
            List<Map<String, Object>> groups = loadVariantGroups(foodId);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "variant_groups", groups
            ));
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 5. THEM NHOM BIEN THE
    // POST /api/admin/variants/group/add
    // =========================================
    @ResponseBody
    @PostMapping("/api/admin/variants/group/add")
    public ResponseEntity<?> addVariantGroup(
            @RequestBody Map<String, Object> payload) {
        try {
            Long foodId = Long.valueOf(payload.get("food_id").toString());
            String groupName = payload.get("group_name").toString().trim();

            boolean isRequired = Boolean.parseBoolean(
                    payload.get("is_required").toString());

            boolean allowMultiple = Boolean.parseBoolean(
                    payload.get("allow_multiple").toString());

            int maxSelect = Integer.parseInt(
                    payload.get("max_select").toString());

            if (groupName.isEmpty() || maxSelect < 1) {
                throw new IllegalArgumentException(
                        "Tên nhóm hoặc số lượng chọn không hợp lệ");
            }

            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.fitfood_products WHERE id = ?",
                    Integer.class, foodId);

            if (count == null || count == 0) {
                throw new IllegalArgumentException("Món ăn không tồn tại");
            }

            String sql = """
                INSERT INTO dbo.food_variant_groups
                (food_id, group_name, is_required,
                 allow_multiple, max_select)
                VALUES (?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(
                    sql, foodId, groupName,
                    isRequired, allowMultiple, maxSelect);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đã thêm nhóm biến thể thành công!"
            ));
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 6. THEM TUY CHON BIEN THE
    // POST /api/admin/variants/option/add
    // =========================================
    @ResponseBody
    @PostMapping("/api/admin/variants/option/add")
    public ResponseEntity<?> addVariantOption(
            @RequestBody Map<String, Object> payload) {
        try {
            Long groupId = Long.valueOf(payload.get("group_id").toString());
            String optionName = payload.get("option_name").toString().trim();

            BigDecimal extraPrice = new BigDecimal(
                    payload.get("extra_price").toString());

            int extraCalories = Integer.parseInt(
                    payload.get("extra_calories").toString());

            boolean isDefault = Boolean.parseBoolean(
                    payload.get("is_default").toString());

            if (optionName.isEmpty()) {
                throw new IllegalArgumentException(
                        "Tên tùy chọn không được để trống");
            }

            Integer groupCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM dbo.food_variant_groups WHERE id = ?",
                    Integer.class, groupId);

            if (groupCount == null || groupCount == 0) {
                throw new IllegalArgumentException(
                        "Nhóm biến thể không tồn tại");
            }

            String sql = """
                INSERT INTO dbo.food_variant_options
                (group_id, option_name, extra_price,
                 extra_calories, is_default)
                VALUES (?, ?, ?, ?, ?)
                """;

            jdbcTemplate.update(
                    sql, groupId, optionName,
                    extraPrice, extraCalories, isDefault);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đã thêm tùy chọn thành công!"
            ));
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 7. TINH GIA MON AN KEM BIEN THE
    // POST /api/foods/add-to-cart
    // =========================================
    @ResponseBody
    @PostMapping("/api/foods/add-to-cart")
    public ResponseEntity<?> addToCartWithVariants(
            @RequestBody Map<String, Object> payload) {
        try {
            Long foodId = Long.valueOf(payload.get("food_id").toString());
            int quantity = Integer.parseInt(payload.get("quantity").toString());

            if (quantity <= 0) {
                throw new IllegalArgumentException(
                        "Số lượng phải lớn hơn 0");
            }

            BigDecimal basePrice = jdbcTemplate.queryForObject(
                    """
                    SELECT price
                    FROM dbo.fitfood_products
                    WHERE id = ?
                    """,
                    BigDecimal.class, foodId);

            if (basePrice == null) {
                throw new IllegalArgumentException(
                        "Không tìm thấy giá món ăn");
            }

            Object rawOptions = payload.get("options");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> selectedOptions =
                    rawOptions instanceof List
                            ? (List<Map<String, Object>>) rawOptions
                            : List.of();

            BigDecimal totalExtraPrice = BigDecimal.ZERO;
            int totalExtraCalories = 0;
            Set<Long> usedOptionIds = new HashSet<>();

            for (Map<String, Object> selected : selectedOptions) {
                Long optionId = Long.valueOf(
                        selected.get("option_id").toString());

                if (!usedOptionIds.add(optionId)) {
                    throw new IllegalArgumentException(
                            "Tùy chọn bị trùng lặp");
                }

                String optionSql = """
                    SELECT o.extra_price, o.extra_calories
                    FROM dbo.food_variant_options o
                    JOIN dbo.food_variant_groups g
                      ON g.id = o.group_id
                    WHERE o.id = ?
                      AND g.food_id = ?
                      AND o.is_available = 1
                    """;

                Map<String, Object> option =
                        jdbcTemplate.queryForMap(
                                optionSql, optionId, foodId);

                totalExtraPrice = totalExtraPrice.add(
                        new BigDecimal(option.get("extra_price").toString()));

                totalExtraCalories += ((Number)
                        option.get("extra_calories")).intValue();
            }

            BigDecimal unitPrice = basePrice.add(totalExtraPrice);

            if (unitPrice.signum() < 0) {
                throw new IllegalArgumentException(
                        "Giá món ăn không hợp lệ");
            }

            BigDecimal totalPrice = unitPrice.multiply(
                    BigDecimal.valueOf(quantity));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "success");
            result.put("message", "Đã tính giá. Chưa lưu giỏ hàng.");
            result.put("food_id", foodId);
            result.put("quantity", quantity);
            result.put("unit_price", unitPrice);
            result.put("total_price", totalPrice);
            result.put("total_calories", null);
            result.put("extra_calories_total",
                    totalExtraCalories * quantity);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error(e);
        }
    }

    // =========================================
    // 8. TRA VE LOI API
    // =========================================
    private ResponseEntity<?> error(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", String.valueOf(e.getMessage())
        ));
    }
}
