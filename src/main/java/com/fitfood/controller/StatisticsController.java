
package com.fitfood.controller;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin")
public class StatisticsController {

    private final JdbcTemplate jdbcTemplate;

    public StatisticsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/reports")
    public String reports(
            @RequestParam(defaultValue = "month") String period,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            Model model) {

        LocalDate today = LocalDate.now();
        LocalDateTime from;
        LocalDateTime to;

        // Giá trị mặc định cho bộ lọc
        if (date == null || date.isBlank()) {
            date = today.toString();
        }

        if (month == null || month.isBlank()) {
            month = YearMonth.now().toString();
        }

        if (year == null || year.isBlank()) {
            year = String.valueOf(today.getYear());
        }

        if (startDate == null || startDate.isBlank()) {
            startDate = today.withDayOfMonth(1).toString();
        }

        if (endDate == null || endDate.isBlank()) {
            endDate = today.toString();
        }

        String filterError = null;

        // Xác định khoảng thời gian lọc
        try {
            switch (period) {
                case "day": {
                    LocalDate selectedDate = LocalDate.parse(date);
                    from = selectedDate.atStartOfDay();
                    to = selectedDate.plusDays(1).atStartOfDay();
                    break;
                }

                case "year": {
                    int selectedYearValue = Integer.parseInt(year);

                    if (selectedYearValue < 2000
                            || selectedYearValue > 2100) {
                        throw new IllegalArgumentException();
                    }

                    LocalDate firstDay =
                            LocalDate.of(selectedYearValue, 1, 1);

                    from = firstDay.atStartOfDay();
                    to = firstDay.plusYears(1).atStartOfDay();
                    break;
                }

                case "range": {
                    LocalDate start = LocalDate.parse(startDate);
                    LocalDate end = LocalDate.parse(endDate);

                    if (end.isBefore(start)) {
                        throw new IllegalArgumentException();
                    }

                    from = start.atStartOfDay();
                    to = end.plusDays(1).atStartOfDay();
                    break;
                }

                default: {
                    period = "month";
                    YearMonth selectedMonth = YearMonth.parse(month);

                    from = selectedMonth.atDay(1).atStartOfDay();
                    to = selectedMonth.plusMonths(1)
                            .atDay(1).atStartOfDay();
                    break;
                }
            }
        } catch (Exception ex) {
            period = "month";
            month = YearMonth.now().toString();
            filterError = "Khoảng thời gian không hợp lệ. "
                    + "Hệ thống đang hiển thị dữ liệu tháng hiện tại.";

            YearMonth currentMonth = YearMonth.now();
            from = currentMonth.atDay(1).atStartOfDay();
            to = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        }

        Timestamp startTime = Timestamp.valueOf(from);
        Timestamp endTime = Timestamp.valueOf(to);

        // 1. Tổng số đơn hàng
        Integer totalOrders = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM dbo.fitfood_orders
                WHERE placed_at >= ? AND placed_at < ?
                """,
                Integer.class, startTime, endTime);

        // 2. Tổng doanh thu, loại trừ đơn đã hủy
        BigDecimal totalRevenue = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(total), 0)
                FROM dbo.fitfood_orders
                WHERE placed_at >= ? AND placed_at < ?
                  AND LOWER(LTRIM(RTRIM(COALESCE([status], ''))))
                      NOT IN (N'đã hủy', N'đã huỷ',
                              N'cancelled', N'canceled')
                """,
                BigDecimal.class, startTime, endTime);

        // 3. Tổng số lượng sản phẩm đã bán
        Integer totalProductsSold = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(oi.quantity), 0)
                FROM dbo.fitfood_order_items oi
                INNER JOIN dbo.fitfood_orders o
                    ON o.id = oi.order_id
                WHERE o.placed_at >= ? AND o.placed_at < ?
                  AND LOWER(LTRIM(RTRIM(COALESCE(o.[status], ''))))
                      NOT IN (N'đã hủy', N'đã huỷ',
                              N'cancelled', N'canceled')
                """,
                Integer.class, startTime, endTime);

        // 4. Số khách hàng đã đặt hàng
        Integer totalCustomers = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT user_id)
                FROM dbo.fitfood_orders
                WHERE placed_at >= ? AND placed_at < ?
                  AND user_id IS NOT NULL
                """,
                Integer.class, startTime, endTime);

        // 5. Đếm trạng thái đơn hàng
        Map<String, Object> statusCounts =
                jdbcTemplate.queryForMap(
                """
                SELECT
                    COALESCE(SUM(CASE WHEN LOWER(LTRIM(RTRIM(
                        COALESCE([status], '')))) IN
                        (N'chờ xử lý', N'chờ xác nhận',
                         N'chờ thanh toán', N'pending')
                        THEN 1 ELSE 0 END), 0) AS CHO_XU_LY,

                    COALESCE(SUM(CASE WHEN LOWER(LTRIM(RTRIM(
                        COALESCE([status], '')))) IN
                        (N'đang xử lý', N'đang giao',
                         N'đang giao hàng',
                         N'processing', N'shipping')
                        THEN 1 ELSE 0 END), 0) AS DANG_XU_LY,

                    COALESCE(SUM(CASE WHEN LOWER(LTRIM(RTRIM(
                        COALESCE([status], '')))) IN
                        (N'hoàn thành', N'completed', N'delivered')
                        THEN 1 ELSE 0 END), 0) AS HOAN_THANH,

                    COALESCE(SUM(CASE WHEN LOWER(LTRIM(RTRIM(
                        COALESCE([status], '')))) IN
                        (N'đã hủy', N'đã huỷ',
                         N'cancelled', N'canceled')
                        THEN 1 ELSE 0 END), 0) AS DA_HUY
                FROM dbo.fitfood_orders
                WHERE placed_at >= ? AND placed_at < ?
                """,
                startTime, endTime);

        model.addAttribute("waitingOrders",
                statusCounts.get("CHO_XU_LY"));
        model.addAttribute("processingOrders",
                statusCounts.get("DANG_XU_LY"));
        model.addAttribute("completedOrders",
                statusCounts.get("HOAN_THANH"));
        model.addAttribute("cancelledOrders",
                statusCounts.get("DA_HUY"));

        // 6. Doanh thu theo từng ngày để hiển thị biểu đồ
        List<Map<String, Object>> rawRevenue =
                jdbcTemplate.queryForList(
                """
                SELECT
                    CONVERT(varchar(10), CAST(placed_at AS date), 103)
                        AS reportDate,
                    COALESCE(SUM(CASE
                        WHEN LOWER(LTRIM(RTRIM(
                            COALESCE([status], '')))) IN
                            (N'đã hủy', N'đã huỷ',
                             N'cancelled', N'canceled')
                        THEN 0 ELSE total
                    END), 0) AS revenue
                FROM dbo.fitfood_orders
                WHERE placed_at >= ? AND placed_at < ?
                GROUP BY CAST(placed_at AS date)
                ORDER BY CAST(placed_at AS date)
                """,
                startTime, endTime);

        BigDecimal maxRevenue = BigDecimal.ZERO;

        for (Map<String, Object> item : rawRevenue) {
            BigDecimal revenue =
                    new BigDecimal(item.get("revenue").toString());

            if (revenue.compareTo(maxRevenue) > 0) {
                maxRevenue = revenue;
            }
        }

        List<Map<String, Object>> revenueByDay = new ArrayList<>();

        for (Map<String, Object> item : rawRevenue) {
            BigDecimal revenue =
                    new BigDecimal(item.get("revenue").toString());

            int barHeight = 0;

            if (maxRevenue.compareTo(BigDecimal.ZERO) > 0
                    && revenue.compareTo(BigDecimal.ZERO) > 0) {
                barHeight = revenue.multiply(BigDecimal.valueOf(160))
                        .divide(maxRevenue, 0,
                                java.math.RoundingMode.HALF_UP)
                        .intValue();

                if (barHeight < 6) {
                    barHeight = 6;
                }
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reportDate", item.get("reportDate"));
            row.put("revenue", revenue);
            row.put("barHeight", barHeight);

            revenueByDay.add(row);
        }

        model.addAttribute("revenueByDay", revenueByDay);
        model.addAttribute("maxRevenue", maxRevenue);

        // 7. Danh sách đơn hàng gần đây
        List<Map<String, Object>> recentOrders =
                jdbcTemplate.queryForList(
                """
                SELECT TOP 10
                    o.id AS orderId,
                    u.full_name AS customerName,
                    o.placed_at AS placedAt,
                    o.total AS total,
                    o.[status] AS status
                FROM dbo.fitfood_orders o
                LEFT JOIN dbo.fitfood_users u
                    ON u.id = o.user_id
                WHERE o.placed_at >= ? AND o.placed_at < ?
                ORDER BY o.placed_at DESC, o.id DESC
                """,
                startTime, endTime);

        // 8. Truyền dữ liệu sang HTML
        model.addAttribute("totalOrders",
                totalOrders == null ? 0 : totalOrders);

        model.addAttribute("totalRevenue",
                totalRevenue == null
                        ? BigDecimal.ZERO : totalRevenue);

        model.addAttribute("totalProductsSold",
                totalProductsSold == null ? 0 : totalProductsSold);

        model.addAttribute("totalCustomers",
                totalCustomers == null ? 0 : totalCustomers);

        model.addAttribute("recentOrders", recentOrders);

        model.addAttribute("selectedPeriod", period);
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedMonth", month);
        model.addAttribute("selectedYear", year);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("filterError", filterError);

        return "reports";
    }
}
