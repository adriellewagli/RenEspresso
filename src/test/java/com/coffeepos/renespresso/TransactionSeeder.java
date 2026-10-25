package com.coffeepos.renespresso;

import com.coffeepos.renespresso.config.DatabaseManager;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Seeds realistic transaction data for the last 14 days (including foods, coffee, milk tea, juices).
 * Enforces all RenEspresso business rules:
 * - Cash-Only payments (payment_method = 'Cash', tendered >= total_amount)
 * - Atomic order & line item consistency
 * - Philippine Senior/PWD VAT & discount formulas
 * - Realistic peak hours and high food inclusion (~65%+ orders with pastries/food)
 */
public class TransactionSeeder {

    record Variant(int variantId, int productId, String productName, String category, String size, double price) {}
    record Staff(int userId, String name, String role) {}
    record CartLine(Variant variant, int qty) {}

    public static void main(String[] args) {
        System.out.println("Starting 14-day transaction seeder for RenEspresso...");
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            // 1. Fetch available variants
            List<Variant> drinks = new ArrayList<>();
            List<Variant> foods = new ArrayList<>();
            String varSql = """
                SELECT pv.variant_id, p.id AS product_id, p.name AS product_name, c.name AS category_name, pv.size_name, pv.price
                FROM product_variants pv
                JOIN products p ON pv.product_id = p.id
                JOIN categories c ON p.category_id = c.category_id
                ORDER BY pv.variant_id
            """;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(varSql)) {
                while (rs.next()) {
                    Variant v = new Variant(
                            rs.getInt("variant_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_name"),
                            rs.getString("category_name"),
                            rs.getString("size_name"),
                            rs.getDouble("price")
                    );
                    if ("Bread & Pastries".equalsIgnoreCase(v.category())) {
                        foods.add(v);
                    } else {
                        drinks.add(v);
                    }
                }
            }
            System.out.println("Loaded " + drinks.size() + " drink variants and " + foods.size() + " food variants.");

            // 2. Fetch cashiers
            List<Staff> staffList = new ArrayList<>();
            String userSql = "SELECT user_id, full_name, role FROM users WHERE role IN ('CASHIER', 'MANAGER', 'SUPERVISOR', 'ADMIN')";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(userSql)) {
                while (rs.next()) {
                    staffList.add(new Staff(rs.getInt("user_id"), rs.getString("full_name"), rs.getString("role")));
                }
            }
            if (staffList.isEmpty()) {
                staffList.add(new Staff(2, "Allan Miguelle V. Oiga", "Cashier"));
            }

            // 3. Clean existing transaction records for a fresh, consistent 14-day history
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("DELETE FROM transaction_items");
                st.executeUpdate("DELETE FROM transactions");
                st.executeUpdate("ALTER TABLE transactions AUTO_INCREMENT = 1");
            }
            System.out.println("Cleared existing transaction tables.");

            // 4. Generate transactions for the last 14 days
            Random rng = new Random(42); // fixed seed for repeatable realistic distribution
            LocalDate today = LocalDate.now();
            LocalDate start = today.minusDays(13); // 14 days total up to today

            String insertTxSql = """
                INSERT INTO transactions (
                    transaction_id, cashier_id, cashier_name, cashier_role, order_id, ticket_number, total_amount,
                    subtotal, vat, discount, tendered, payment_method, payment_status,
                    status, receipt_text, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'Cash', ?, ?, ?, ?, ?)
            """;

            String insertItemSql = """
                INSERT INTO transaction_items (transaction_id, variant_id, quantity, price)
                VALUES (?, ?, ?, ?)
            """;

            PreparedStatement psTx = conn.prepareStatement(insertTxSql);
            PreparedStatement psItem = conn.prepareStatement(insertItemSql);

            int globalTxId = 1;
            int orderNumber = 8401;
            int totalOrdersGenerated = 0;
            double totalRevenue = 0.0;

            for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
                boolean isWeekend = (date.getDayOfWeek().getValue() >= 6);
                int dailyOrders = isWeekend ? (24 + rng.nextInt(9)) : (16 + rng.nextInt(7)); // 24-32 on weekends, 16-22 on weekdays
                int dailyTicket = 1;

                List<LocalTime> times = generateTimes(rng, dailyOrders, date.equals(today));
                Collections.sort(times);

                for (LocalTime time : times) {
                    LocalDateTime createdAt = LocalDateTime.of(date, time);
                    String orderId = "TXN-" + orderNumber++;
                    String ticketNum = String.format("#%03d", dailyTicket++);

                    // Select cashier
                    Staff cashier = pickCashier(staffList, rng);

                    // Pick items (1 to 4 items)
                    List<CartLine> cart = pickCartItems(drinks, foods, rng, time.getHour());

                    // Compute gross
                    double gross = 0.0;
                    List<Double> itemPrices = new ArrayList<>();
                    for (CartLine cl : cart) {
                        gross += cl.variant().price() * cl.qty();
                        for (int q = 0; q < cl.qty(); q++) {
                            itemPrices.add(cl.variant().price());
                        }
                    }

                    // Sort descending for Senior/PWD highest meal discount
                    itemPrices.sort(Collections.reverseOrder());

                    // 12% probability of Senior/PWD discount
                    boolean isSenior = rng.nextDouble() < 0.12 && !itemPrices.isEmpty();
                    int seniorCount = isSenior ? (rng.nextDouble() < 0.20 ? 2 : 1) : 0;

                    double discount = 0.0;
                    double vatableGross = 0.0;
                    double exemptGross = 0.0;

                    if (seniorCount > 0) {
                        int discountedUnits = Math.min(seniorCount, itemPrices.size());
                        for (int i = 0; i < itemPrices.size(); i++) {
                            double p = itemPrices.get(i);
                            if (i < discountedUnits) {
                                double base = p / 1.12;
                                discount += base * 0.20;
                                exemptGross += p;
                            } else {
                                vatableGross += p;
                            }
                        }
                    } else {
                        vatableGross = gross;
                    }

                    double subtotal = round2((vatableGross / 1.12) + (exemptGross / 1.12));
                    double vat = round2(vatableGross - (vatableGross / 1.12));
                    discount = round2(discount);
                    double total = round2(gross - discount);

                    // Cash tendered
                    double tendered = pickCashTendered(total, rng);

                    // Status
                    boolean isToday = date.equals(today);
                    String status = "COMPLETED";
                    String payStatus = "Paid";

                    if (!isToday && rng.nextDouble() < 0.03) {
                        status = "CANCELLED";
                        payStatus = "Refunded";
                    } else if (isToday && time.getHour() >= 21 && rng.nextDouble() < 0.35) {
                        status = "PREPARING";
                    }

                    if ("COMPLETED".equals(status)) {
                        totalRevenue += total;
                    }

                    // Build Receipt Text
                    String receipt = buildReceiptText(orderId, ticketNum, createdAt, cashier.name(), cashier.role(),
                            cart, subtotal, vat, discount, total, tendered, (tendered - total), isSenior, seniorCount);

                    // Insert transaction
                    psTx.setInt(1, globalTxId);
                    psTx.setInt(2, cashier.userId());
                    psTx.setString(3, cashier.name());
                    psTx.setString(4, cashier.role());
                    psTx.setString(5, orderId);
                    psTx.setString(6, ticketNum);
                    psTx.setDouble(7, total);
                    psTx.setDouble(8, subtotal);
                    psTx.setDouble(9, vat);
                    psTx.setDouble(10, discount);
                    psTx.setDouble(11, tendered);
                    psTx.setString(12, payStatus);
                    psTx.setString(13, status);
                    psTx.setString(14, receipt);
                    Timestamp ts = Timestamp.valueOf(createdAt);
                    psTx.setTimestamp(15, ts);
                    psTx.setTimestamp(16, ts);
                    psTx.addBatch();

                    // Insert transaction items
                    for (CartLine cl : cart) {
                        psItem.setInt(1, globalTxId);
                        psItem.setInt(2, cl.variant().variantId());
                        psItem.setInt(3, cl.qty());
                        psItem.setDouble(4, cl.variant().price());
                        psItem.addBatch();
                    }

                    globalTxId++;
                    totalOrdersGenerated++;
                }
            }

            psTx.executeBatch();
            psItem.executeBatch();
            conn.commit();

            System.out.printf("Successfully seeded %d transactions across 14 days! Total Revenue: ₱%,.2f%n",
                    totalOrdersGenerated, totalRevenue);
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static List<LocalTime> generateTimes(Random rng, int count, boolean isToday) {
        List<LocalTime> list = new ArrayList<>();
        int maxHour = isToday ? 22 : 21;
        for (int i = 0; i < count; i++) {
            // Weighted peaks: 07:30-10:00 (breakfast/coffee), 11:30-13:30 (lunch), 14:30-17:30 (afternoon snacks), 18:00-21:30 (evening)
            double r = rng.nextDouble();
            int hour;
            int minute = rng.nextInt(60);

            if (r < 0.25) {
                // Morning coffee & pastry peak
                hour = 7 + rng.nextInt(4); // 7, 8, 9, 10
            } else if (r < 0.50) {
                // Lunch refresh peak
                hour = 11 + rng.nextInt(3); // 11, 12, 13
            } else if (r < 0.85) {
                // Afternoon boba & pastry rush
                hour = 14 + rng.nextInt(4); // 14, 15, 16, 17
            } else {
                // Evening
                hour = 18 + rng.nextInt(Math.max(1, maxHour - 17)); // 18..21
            }
            if (hour < 7) hour = 7;
            if (hour > maxHour) hour = maxHour;
            list.add(LocalTime.of(hour, minute, rng.nextInt(60)));
        }
        return list;
    }

    private static Staff pickCashier(List<Staff> staff, Random rng) {
        // Miguelle (Allan) handles ~60% of register work, Andrei ~25%, manager ~15%
        double r = rng.nextDouble();
        if (r < 0.60 && staff.size() > 1) {
            return staff.stream().filter(s -> s.userId() == 2).findFirst().orElse(staff.get(0));
        } else if (r < 0.85 && staff.size() > 2) {
            return staff.stream().filter(s -> s.userId() == 3).findFirst().orElse(staff.get(0));
        }
        return staff.get(rng.nextInt(staff.size()));
    }

    private static List<CartLine> pickCartItems(List<Variant> drinks, List<Variant> foods, Random rng, int hour) {
        List<CartLine> cart = new ArrayList<>();
        Set<Integer> usedVariants = new HashSet<>();

        // Order composition:
        // ~65% drink + food combo
        // ~15% food only
        // ~20% drink only
        double r = rng.nextDouble();
        boolean includeFood;
        boolean includeDrink;

        if (r < 0.65) {
            includeFood = true;
            includeDrink = true;
        } else if (r < 0.80) {
            includeFood = true;
            includeDrink = false;
        } else {
            includeFood = false;
            includeDrink = true;
        }

        // How many items
        int drinkCount = includeDrink ? (rng.nextDouble() < 0.70 ? 1 : (rng.nextDouble() < 0.90 ? 2 : 3)) : 0;
        int foodCount = includeFood ? (rng.nextDouble() < 0.65 ? 1 : 2) : 0;

        if (drinkCount == 0 && foodCount == 0) {
            drinkCount = 1;
            includeDrink = true;
        }

        // Add drinks
        for (int i = 0; i < drinkCount; i++) {
            Variant v = pickDrinkForHour(drinks, rng, hour);
            if (!usedVariants.contains(v.variantId())) {
                usedVariants.add(v.variantId());
                int qty = rng.nextDouble() < 0.75 ? 1 : (rng.nextDouble() < 0.92 ? 2 : 3);
                cart.add(new CartLine(v, qty));
            }
        }

        // Add foods
        for (int i = 0; i < foodCount; i++) {
            Variant v = foods.get(rng.nextInt(foods.size()));
            if (!usedVariants.contains(v.variantId())) {
                usedVariants.add(v.variantId());
                int qty = rng.nextDouble() < 0.70 ? 1 : (rng.nextDouble() < 0.90 ? 2 : 3);
                cart.add(new CartLine(v, qty));
            }
        }

        return cart;
    }

    private static Variant pickDrinkForHour(List<Variant> drinks, Random rng, int hour) {
        // Morning -> favors Hot Coffee (cat 2) and Iced Coffee (cat 3)
        // Afternoon -> favors Milk Tea (cat 1), Shakes (cat 4), Iced Coffee (cat 3)
        // Evening -> favors Fruit Juice (cat 5), Tea, Coffee
        if (hour <= 10) {
            List<Variant> morning = drinks.stream()
                    .filter(d -> d.category().contains("Coffee"))
                    .toList();
            if (!morning.isEmpty() && rng.nextDouble() < 0.75) {
                return morning.get(rng.nextInt(morning.size()));
            }
        } else if (hour >= 14 && hour <= 18) {
            List<Variant> afternoon = drinks.stream()
                    .filter(d -> d.category().contains("Milk Tea") || d.category().contains("Shake") || d.category().contains("Iced"))
                    .toList();
            if (!afternoon.isEmpty() && rng.nextDouble() < 0.75) {
                return afternoon.get(rng.nextInt(afternoon.size()));
            }
        }
        return drinks.get(rng.nextInt(drinks.size()));
    }

    private static double pickCashTendered(double total, Random rng) {
        // 35% exact cash
        if (rng.nextDouble() < 0.35) return total;

        double[] bills = {100.0, 200.0, 500.0, 1000.0};
        for (double b : bills) {
            if (b >= total) {
                // If it's close or random pick
                if (rng.nextDouble() < 0.75) return b;
            }
        }

        // If total > 1000 or larger bill
        long ceil100 = (long) Math.ceil(total / 100.0) * 100;
        if (ceil100 < total) ceil100 += 100;
        return (double) ceil100;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String buildReceiptText(String orderId, String ticket, LocalDateTime dt,
                                           String cashierName, String role, List<CartLine> cart,
                                           double subtotal, double vat, double discount, double total,
                                           double tendered, double change, boolean isSenior, int seniorCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================================\n");
        sb.append("              RenEspresso               \n");
        sb.append("   Phase 5A Package 5 Block 8 Lot 2     \n");
        sb.append("         TIN: 111-111-111-111           \n");
        sb.append("========================================\n");
        sb.append(String.format("Order: %-16s Ticket: %s\n", orderId, ticket));
        sb.append(String.format("Date: %s\n", dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        sb.append(String.format("Cashier: %s (%s)\n", cashierName, role));
        sb.append("----------------------------------------\n");
        for (CartLine cl : cart) {
            String item = cl.qty() + "x " + cl.variant().productName() + " (" + cl.variant().size() + ")";
            double lineTotal = cl.variant().price() * cl.qty();
            sb.append(String.format("%-30s ₱%7.2f\n", item.length() > 30 ? item.substring(0, 30) : item, lineTotal));
        }
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-28s ₱%9.2f\n", "Subtotal:", subtotal));
        sb.append(String.format("%-28s ₱%9.2f\n", "12% VAT:", vat));
        if (isSenior) {
            sb.append(String.format("%-28s-₱%9.2f\n", "Senior/PWD (" + seniorCount + "x):", discount));
        } else {
            sb.append(String.format("%-28s ₱%9.2f\n", "Discount:", 0.0));
        }
        sb.append(String.format("%-28s ₱%9.2f\n", "TOTAL:", total));
        sb.append(String.format("%-28s %10s\n", "Payment Method:", "Cash"));
        sb.append(String.format("%-28s ₱%9.2f\n", "Cash Tendered:", tendered));
        sb.append(String.format("%-28s ₱%9.2f\n", "Change:", Math.max(0, change)));
        sb.append("========================================\n");
        sb.append("        Thank you for visiting!         \n");
        sb.append("========================================\n");
        return sb.toString();
    }
}
