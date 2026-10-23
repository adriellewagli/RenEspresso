# RenEspresso POS - Agent Memory & Business Rules

This document records architectural conventions, established business logic, domain rules, and backlog items for this codebase.

---

## ☕ 1. Business Logic & F&B Domain Rules

### 1.1 Food Spoilage & Order Cancellation Rule
- **Rule:** When an order is cancelled or refunded, **inventory stock is NOT restored**.
- **Rationale:** In a food and beverage / coffee shop setting, prepared drinks and heated dishes cannot be poured back into bean hoppers or returned to stock; cancelled items are discarded as **waste / spoilage**.

### 1.2 Philippine Senior Citizen / PWD Discount (RA 9994 / RA 10754)
- **Personal Consumption Rule:** Discount applies strictly to the meal/beverage of the cardholder.
- **Multiple IDs / Highest Meal Deduction:**
  - Cashiers can specify the count of Senior/PWD IDs presented (`boxSeniorCount` stepper in `PosView.fxml`).
  - The discount applies to the **$N$ highest-priced individual items/meals** in the cart.
  - The remaining items are charged regular VAT-inclusive prices.
- **Tax & Discount Math:**
  - For qualifying Senior items:
    - VAT-exempt: $\text{Base} = \frac{\text{Item Gross}}{1.12}$
    - 20% Discount: $\text{Discount} = \text{Base} \times 0.20$
    - Amount Paid: $\text{Base} - \text{Discount}$ (VAT = ₱0.00).
  - For non-discounted regular items:
    - Retains standard 12% VAT and regular menu price.
  - Total Formula: $\text{Total} = \text{Subtotal} + \text{VAT} - \text{Discount}$.
- **Audit & Receipt:**
  - `TransactionRecord` stores `discountApplied` (e.g. `"Senior/PWD (20%)"` or `"Senior/PWD (2x)"`).
  - Printed receipt itemizes Subtotal, 12% VAT, and Senior discount line.

### 1.3 Atomic Inventory Stock Deduction
- Stock deduction is executed **atomically inside `TransactionDAO.saveTransaction()`** within the same database transaction (`conn.setAutoCommit(false)`):
  ```sql
  UPDATE products 
  SET quantity = GREATEST(0, quantity - ?),
      available = IF(quantity - ? <= 0, 0, available)
  WHERE id = ?;
  ```
- Cart item quantities are grouped by `productId` before batch update to prevent duplicate update locks.
- Never deduct stock in a separate connection before `saveTransaction()`.

### 1.4 User Session & Active Status Lifecycle
- `users.is_active` tracks whether a staff account is currently online/logged in:
  - **Login:** Sets `is_active = 1` and updates `last_login = NOW()`.
  - **Logout / Exit:** Sets `is_active = 0` via `SessionManager.logout()`. Handled on manual logout, stage window close (`setOnCloseRequest`), application termination (`App.stop()`), and JVM shutdown hook.
### 1.5 Cash-Only Limitation Rule
- **Rule:** RenEspresso strictly operates as a **Cash-Only** establishment. Non-cash payment methods (e.g. GCash, credit/debit cards) are **not accepted**.
- **Implementation:**
  - `UserSession.PAYMENT_METHODS` is constrained to `List.of("Cash")`.
  - The POS payment modal locks the payment method to "Cash" (`cmbPaymentMethod.setDisable(true)`), enforcing cash tendered input, quick cash buttons, and change calculation before finalizing any transaction.

---

## 🏛️ 2. Tech Stack & Environment

- **Java Version:** Java 21 (`jdk-21.0.12.101-hotspot` at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`).
- **Frameworks:** JavaFX 21, MaterialFX 11.17.0, ControlsFX, BootstrapFX.
- **Database:** MariaDB / MySQL with HikariCP connection pool (`com.coffeepos.renespresso.config.DatabaseManager`).
- **Build Tool:** Maven (`.\mvnw.cmd clean compile`). Note: set `$env:JAVA_HOME` if not set in environment.
- **Security:** jBCrypt password hashing (`PasswordUtil`).

---

## 📋 3. Backlog for Next Work Session

1. **[FIXED] Menu Item Availability Toggle Bug:**
   - In `MenuConfigureController.handleSave()`, fixed line 398 to respect `availableBtn.isSelected() && (qty > 0)`.
2. **[FIXED] Null-Safe Transaction Search Filters:**
   - In `TransactionController.applyFilters()` and `OrderManagementController.applyFilters()`, added null checks to prevent `NullPointerException` on `orderId`, `staffName`, `staffRole`, `paymentStatus`, `paymentMethod`, `status`, `ticketNumber`, `items`, and `createdAt`. Also applied defensive null checks across queue and menu search filters.
3. **[COMPLETED] Standardized AlertUtil Confirmation Across User & Admin:**
   - Standardized all critical and state-changing confirmations across User (`UserViewController`, `OrderQueueController`) and Admin (`SettingsController`, `AccountController`, `MenuController`, `MenuConfigureController`) to use `AlertUtil` modal dialogs (`AlertUtil.showYesNo`, `AlertUtil.showSuccess`, etc.).
4. **Low-Stock Alerting & Badges:**
   - Add visual warning badges when product `quantity <= 5` in both POS menu cards and Admin menu view.
5. **[CANCELLED / OBSOLETE] Payment Reference Number for Non-Cash:**
   - Cancelled: Business operates strictly on Cash Only; non-cash payment options removed.
