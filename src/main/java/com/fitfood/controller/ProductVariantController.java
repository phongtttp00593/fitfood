package com.fitfood.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/foods")
public class ProductVariantController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // API trả về danh sách các nhóm biến thể và tùy chọn của 1 món ăn để load lên giao diện chi tiết sản phẩm
    @GetMapping("/{id}/variants")
    public ResponseEntity<Map<String, Object>> getFoodVariants(@PathVariable Long id) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 1. Lấy danh sách nhóm biến thể (Size, Sốt, Topping...)
            String groupsQuery = "SELECT id, group_name, is_required, allow_multiple, max_select FROM food_variant_groups WHERE food_id = ?";
            List<Map<String, Object>> groups = jdbcTemplate.queryForList(groupsQuery, id);

            // 2. Lấy các tùy chọn con cho từng nhóm
            for (Map<String, Object> group : groups) {
                Long groupId = ((Number) group.get("id")).longValue();
                String optionsQuery = "SELECT id, option_name, extra_price, extra_calories, is_default FROM food_variant_options WHERE group_id = ?";
                List<Map<String, Object>> options = jdbcTemplate.queryForList(optionsQuery, groupId);
                group.put("options", options);
            }

            response.put("food_id", id);
            response.put("variant_groups", groups);
            response.put("status", "success");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // API nhận yêu cầu thêm sản phẩm kèm biến thể vào giỏ hàng
    @PostMapping("/add-to-cart")
    public ResponseEntity<?> addToCartWithVariants(@RequestBody Map<String, Object> payload) {
        try {
            Long foodId = Long.valueOf(payload.get("food_id").toString());
            int quantity = Integer.parseInt(payload.get("quantity").toString());
            List<Map<String, Object>> selectedOptions = (List<Map<String, Object>>) payload.get("options");
            String note = (String) payload.get("note");

            double totalExtraPrice = 0;
            int totalExtraCalo = 0;

            // Kiểm tra và tính tiền, calo cộng thêm từ database một cách an toàn ở server
            if (selectedOptions != null && !selectedOptions.isEmpty()) {
                for (Map<String, Object> opt : selectedOptions) {
                    Long optionId = Long.valueOf(opt.get("option_id").toString());
                    
                    Map<String, Object> optionData = jdbcTemplate.queryForMap(
                        "SELECT extra_price, extra_calories FROM food_variant_options WHERE id = ?", optionId
                    );
                    
                    totalExtraPrice += ((Number) optionData.get("extra_price")).doubleValue();
                    totalExtraCalo += ((Number) optionData.get("extra_calories")).intValue();
                }
            }

            // Lấy giá gốc món ăn
            Map<String, Object> foodData = jdbcTemplate.queryForMap("SELECT price, calories FROM foods WHERE id = ?", foodId);
            double basePrice = ((Number) foodData.get("price")).doubleValue();
            int baseCalo = ((Number) foodData.get("calories")).intValue();

            double finalUnitPrice = basePrice + totalExtraPrice;
            double finalTotalPrice = finalUnitPrice * quantity;
            int finalTotalCalo = (baseCalo + totalExtraCalo) * quantity;

            // TODO: Lưu vào bảng giỏ hàng (Cart / CartItems) trong Database tại đây

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Đã thêm vào giỏ hàng thành công!",
                "unit_price", finalUnitPrice,
                "total_price", finalTotalPrice,
                "total_calories", finalTotalCalo
            ));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}