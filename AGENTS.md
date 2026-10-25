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

### 1.6 Admin Authorization for Order Cancellation
- **Rule:** Cashiers cannot unilaterally cancel active orders. Cancelling any preparing/active order strictly requires **Admin or Manager authorization**.
- **Implementation:**
  - Modal prompt via `AlertUtil.requestAdminAuthorization()` backed by `AdminAuthModalView.fxml` and `AdminAuthController.java`.
  - Credentials validated with jBCrypt via `UserDAO.validateAdminCredentials(username, password)`.
  - Prevents rogue cancellations and enforces audit trail while complying with Rule 1.1 (spoilage / no stock restoration).

### 1.7 Role-Based Account Management & Non-Deletable Admin Rule
- **Admin Visibility & Rights:**
  - Admin accounts cannot be deleted under any circumstance.
  - **Admin Role is Fixed to Admin Only:** Admin accounts cannot have their role changed/demoted to Manager or Cashier (locked at UI and DAO layers).
  - Existing staff accounts (Manager, Cashier) cannot be elevated to Admin.
  - **Admin Cannot Create Admin:** The Admin cannot create or add another Admin account. In the "Add Account" dialog, allowed roles are strictly restricted to `Manager` and `Cashier` (`List.of("Manager", "Cashier")`), validated upon form submission in `AccountController.validate()`, and blocked at the database DAO layer in `UserDAO.registerUser()`.
  - Admins can see everything (all accounts: Admin, Manager, Cashier) and manage staff accounts between Manager and Cashier.
  - **Role-Based Listing Order:** The account list is ordered hierarchically based on roles: `Admin` -> `Manager` -> `Cashier` (with newest accounts first within each role, matching `HomeDAO`).
- **Manager Access Constraints:**
  - Managers **cannot see Admin accounts** and can **only see Cashiers** in the account list, search filter, and role dropdown.
  - **Managers Cannot Elevate:** Managers cannot elevate Cashier accounts to Manager or Admin (role ComboBox locked to "Cashier", enforced at UI and DAO layers).
  - **Managers Cannot Create Co-Managers:** Managers can strictly add Cashier accounts only (button "+ Add Cashier", role locked to "Cashier", enforced at UI and DAO layers).
  - Home dashboard staff widget for Managers only displays Cashier staff metrics.

---

### 1.8 Transaction History Status & Payment Filter Conventions
- **No "Unpaid" Transactions:** In RenEspresso's cash-only counter-service model, orders are paid at checkout upon order creation (or marked Refunded upon authorized cancellation). No "Unpaid" transaction state exists in transaction history.
- **No "Pending" Order Status:** Orders move directly from "Preparing" to "Completed" (or "Cancelled"). "Pending" is removed from transaction history filters across both Admin (`TransactionController.java`) and Staff (`OrderManagementController.java`).

---

### 1.9 Account Archiving, Restoration, and Hard Delete Lifecycle
- **Soft-Delete by Default (Archive):**
  - Active staff accounts cannot be deleted directly from the active account management table. Clicking the action row archives the account (`is_archived = 1, is_active = 0`).
  - Archived accounts cannot log in to the system or authorize actions via `UserDAO.authenticate()` and `UserDAO.validateAdminCredentials()`.
  - Archived accounts are excluded from the active accounts table and Home dashboard staff statistics.
- **Archived Accounts Modal:**
  - Accessible via the "Archived Accounts (N)" header button in `AccountView.fxml`.
  - Lists all archived staff accounts with search filtering.
  - **Restoration:** Clicking "Restore" sets `is_archived = 0`, reactivating the account and returning it to the active staff list.
- **Hard Delete & Former Staff Name Preservation:**
  - Clicking "Delete Permanently" issues a physical `DELETE FROM users WHERE user_id = ?`.
  - **Rule: Former staff names MUST still be seen in transaction history.**
  - Before unlinking past sales with `UPDATE transactions SET cashier_id = NULL WHERE cashier_id = ?`, `UserDAO.hardDeleteAccount()` permanently snapshots `t.cashier_name = u.full_name` and `t.cashier_role = 'Former Staff'` in the same database transaction.
  - Transactions also store `cashier_name` and `cashier_role` on order creation (`TransactionDAO.saveTransaction`).
  - Queries in `TransactionDAO`, `HomeDAO`, and `SalesDAO` use `COALESCE(u.full_name, t.cashier_name, 'Former Staff')` and fall back to parsing `receipt_text` if legacy records lack the snapshot, ensuring former staff members' authentic names remain visible forever with the `Former Staff` indicator.
- **Role Hierarchy & Non-Archivable Admin:**
  - Admin accounts can never be archived or deleted (enforced at UI and DAO layers).
  - Managers can only archive, view, restore, and delete Cashier accounts.

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
4. **[COMPLETED] Low-Stock Alerting & Badges:**
   - Added visual warning badges and distinct styling (`dish-qty-low`, amber `#D97706`) when product `quantity <= 5` in both POS menu cards (`PosController.java`, `user-dashboard.css`) and Admin menu view (`MenuController.java`, `menu.css`).
5. **[CANCELLED / OBSOLETE] Payment Reference Number for Non-Cash:**
   - Cancelled: Business operates strictly on Cash Only; non-cash payment options removed.
6. **[COMPLETED] Admin Non-Deletable & Manager Role Restrictions:**
   - Admin accounts can never be deleted (protected at UI and SQL DAO layers).
   - Admins can see everything (Admin, Manager, Cashier).
   - Managers cannot see Admin accounts and can only see Cashier accounts in account list, search, role filters, and staff dashboard.
   - Managers cannot elevate accounts to Admin or Manager roles; they can only add or manage Cashiers.
7. **[COMPLETED] Squeezed Icons & Aspect Ratio Fix:**
   - JavaFX `Region` scales `-fx-shape` independently across width and height (`scaleX != scaleY` when shape path bounds are not 1:1 square), resulting in severe stretching/squeezing for non-square paths (e.g. `icon-sales` 20x12, `icon-accounts` 22x14, `icon-history` 21x18, `icon-delete` 14x18).
   - Normalized all SVG paths across `modern.css`, `user-dashboard.css`, `account.css`, and `menu.css` by prepending `M0 0 L0 0 M24 24 L24 24 ` to lock the shape bounding box to a perfect 24x24 square viewBox, preserving authentic 1:1 aspect ratio across all scale factors.
   - Harmonized navbar button padding in `dashboard.css` from `14 14` down to `8` to eliminate horizontal clipping and icon squeezing inside the 85px sidebar.
   - Explicitly locked glyph and container sizing (`20x20` glyph in `37x37` box) and toggled `ContentDisplay.GRAPHIC_ONLY` when collapsed in `AdminDashboardController.java` and `UserViewController.java`.
8. **[COMPLETED] Transaction History Cleaned Filters:**
   - Removed `"Payment: Unpaid"` from Payment filter (RenEspresso is cash-only; all recorded orders are Paid or Refunded).
   - Removed `"Status: Pending"` from Order Status filter (orders in the queue move from Preparing to Completed or Cancelled).
   - Applied consistently across both Admin (`TransactionController.java`) and Staff (`OrderManagementController.java`).
9. **[COMPLETED] Harmonized Account Management UI Consistency:**
   - Replaced custom yellow-tinted `.card` (`#FFFDFA`) with standard crisp `#FFFFFF` card styling and shadow matching Transaction, Sales, and Menu views.
   - Refactored filter bar to use `.filter-toolbar-card`, `.filter-search-box`, standard `.filter-combo` (with consistent button cells and right-aligned button layout), and `.btn-clear-filters`.
   - Upgraded table to `.modern-audit-table, .account-table` with standard header typography, center-aligned role pills (`.role-pill`, 22px), status badges (`.status-pill`, green/amber dots), centered timestamps, and centered icon action buttons with tooltips.
10. **[COMPLETED] Account Archiving, Restoration, and Hard Delete System with UI Polish:**
   - **Soft-Delete by Default:** Replaced "Delete" action on active accounts with "Archive" (`icon-archive`, `#895737` default, `#B45309` amber hover), deactivating account and moving it to archives.
   - **Solid Archive Icon:** Replaced hollow SVG path with a solid filled archive box shape (solid rounded lid, solid box body, counter-clockwise handle cutout), matching the visual weight and style of the pencil edit icon.
   - **Archived Accounts Modal Redesign:** Created `ArchivedAccountsModalView.fxml` and `ArchivedAccountsModalController.java` accessed via "Archived Accounts (N)" header button. Added full `.modern-audit-table` column header styling to eliminate default grey JavaFX gradient buttons, widened modal to 920px to prevent `CREATED DATE` truncation, added header badge icon, streamlined table container, added friendly empty state with archive illustration, and refined Close button.
   - **Restoration & Hard Delete:** Allows restoring accounts back to active list or permanently deleting them from the database with foreign-key safety handling for transactions.
   - **Filter Text & Badge Fixes:** Refactored ComboBoxes to use direct prefixed items (`"Role: All Roles"`, etc.) identical to `TransactionController`, fixing invisible filter text. Added complete `.status-pill` CSS styles (`#DEF7EC` active, `#FEF3C7` inactive) in `account.css`. Enforced default role hierarchy sorting (`colRole`).
11. **[COMPLETED] Home Dashboard Recent Transactions & Status Badge Standardization:**
   - **Status Pill Radius & Styling Inconsistency:** Replaced ad-hoc `.pill` and legacy status classes in `home.css` with the standardized `.status-pill` badge system matching `transact.css` and `account.css`. Added 22px height, 20px border radius, 4px colored circle dot indicators (`#10B981` Completed, `#D97706` Preparing, `#EF4444` Cancelled), and exact font and padding specs.
   - **Missing & Blank Status Fallback:** Fixed `colStatus` cell factory in `HomeController.java` to safely parse and normalize any status, defaulting null, empty, or legacy statuses to `"COMPLETED"` so transactions like `#302` never show empty status cells.
   - **Header Clipping & Row Height (Fixed Cell Size):** Fixed top-row vertical slicing/clipping (row `#306`) by setting `recentTable.setFixedCellSize(44.0);` and resetting scroll position to 0 (`recentTable.scrollTo(0)`) on data load. Changed table header background to solid `#FFFFFF` to prevent scrolled row transparency artifacts.
   - **Cashier Name Fallback:** In `HomeDAO.java`, unlinked transactions now display as `"Former Staff"` instead of a bare dash `"-"`.
   - **Card & Scrollbar Polish:** Standardized dashboard `.card` background to `#FFFFFF` (removing `#FFFDFA` tint), styled TableView vertical scrollbar with a sleek 8px pill thumb, added card subtitle to align with other panels, and defined explicit column preferred widths in `HomeView.fxml`.
12. **[COMPLETED] Sales Analysis Dynamic Period Filtering & KPI Cards Fix:**
   - **Root Cause:** In `SalesDAO.java`, `period.start(today)` generated the multi-bucket history window start (`today.minusDays(29)` for Daily, `minusWeeks(11)` for Weekly, etc.). Passing this `from` timestamp into `totals()`, `products()`, `cashiers()`, and `hours()` caused all periods to query transactions from up to 30 days or years ago. Because the database contains 14 days of data, every period button captured the exact same cumulative transactions (₱65,118.03, 301 completed orders), freezing the KPI cards, top selling products, and cashier breakdown tables while only the bar chart updated its groupings.
   - **Architectural Separation of Windows:** Separated `trendStart(today)` (for historical chart series: 30 daily bars, 12 weekly bars, 12 monthly bars, etc.) from `currentPeriodStart(today)` and `currentPeriodEnd(today)` (for active period metrics: Daily = Today, Weekly = This Week, Monthly = This Month, Quarterly = This Quarter, Yearly = This Year).
   - **Dynamic Range Note:** Provided descriptive range labels in `d.rangeLabel()` (`"Today (Oct 25, 2026)"`, `"This Week (Oct 19 – Oct 25, 2026)"`, etc.) displayed in `lblRangeNote`.
   - **Chart Category Accumulation & Color Cycling Fix:** Cleared `CategoryAxis` categories before setting new series in `SalesController.java`, and eliminated specific `.default-color0` constraints in `sales.css` across `.chart-bar` and `.chart-series-line` to prevent JavaFX color shifts when toggling periods.
13. **[COMPLETED] Former Staff Name Preservation in Transaction History:**
   - **Requirement:** Former staff names must still be seen in transaction history even after account archiving or permanent deletion.
   - **Database Schema & Snapshot:** Added `cashier_name` and `cashier_role` columns to `transactions` table (with auto-migration on boot in `DatabaseManager.ensureSchema()`).
   - **Hard-Delete Snapshot:** In `UserDAO.hardDeleteAccount()`, before setting `cashier_id = NULL`, the staff member's full name is snapshotted into `t.cashier_name = u.full_name` and `t.cashier_role = 'Former Staff'` in the same database transaction.
   - **Order Creation Snapshot:** In `TransactionDAO.saveTransaction()`, `cashier_name` and `cashier_role` are stored directly on new orders.
   - **SQL Queries & Receipt Parsing Fallback:** Updated `TransactionDAO` (`loadAllTransactions`, `loadTodayTransactions`, `loadTransactionsByCashier`), `HomeDAO` (`recentOrders`), and `SalesDAO` (`cashiers`) to query `COALESCE(u.full_name, t.cashier_name, 'Former Staff')`. If legacy rows lack a snapshot, `extractCashierFromReceipt(receiptText)` dynamically parses the original cashier name from the receipt text.
   - **UI Rendering:** In `TransactionController.java` and `OrderManagementController.java`, the staff column displays the staff member's actual full name with a muted italic `Former Staff` indicator below it for former staff.
14. **[COMPLETED] Admin Cannot Create Another Admin Rule:**
   - **Requirement:** Admin accounts must not be able to create another Admin account.
   - **UI Role Selection:** In `AccountController.java` (`showAccountDialog`), when an Admin adds an account, `allowedRoles` is set strictly to `List.of("Manager", "Cashier")`, removing `"Admin"` from the role ComboBox options.
   - **UI Form Validation:** Added validation in `AccountController.validate()` returning `"Admins cannot create another Admin account. Only Manager or Cashier accounts can be created."` if an admin creation attempt occurs.
   - **DAO Security Enforcement:** In `UserDAO.registerUser()`, added guard `if ("ADMIN".equalsIgnoreCase(user.getRole())) return false;`, ensuring backend security defense-in-depth against secondary admin creation.
