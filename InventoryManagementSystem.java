// InventoryManagementSystem.java
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Simple Inventory Management System (console).
 * - Single-file program
 * - Persists inventory to inventory.dat (Java serialization)
 * - Writes audit records to audit.log
 *
 * Note: Replace the simple auth with real authentication for production.
 */
public class InventoryManagementSystem implements Serializable {
    private static final long serialVersionUID = 1L;

    // Storage
    private Map<String, InventoryItem> items = new HashMap<>(); // key = SKU

    // Files
    private static final String DATA_FILE = "inventory.dat";
    private static final String AUDIT_FILE = "audit.log";

    // Simple user placeholder (for audit). In production, get from real auth.
    private String currentUser = "admin";

    // ----------------- Inventory Item -----------------
    public static class InventoryItem implements Serializable {
        private static final long serialVersionUID = 1L;

        public String sku;
        public String name;
        public String category;
        public int quantity;
        public String supplier;
        public double price;
        public String location;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;

        public InventoryItem(String sku, String name, String category, int quantity, String supplier, double price, String location) {
            this.sku = sku;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.supplier = supplier;
            this.price = price;
            this.location = location;
            this.createdAt = LocalDateTime.now();
            this.updatedAt = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return String.format("SKU: %s | Name: %s | Category: %s | Qty: %d | Supplier: %s | Price: %.2f | Loc: %s | Updated: %s",
                    sku, name, category, quantity, supplier, price, location,
                    updatedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
    }

    // ----------------- Core operations -----------------
    public synchronized boolean addItem(InventoryItem item) {
        if (items.containsKey(item.sku)) return false; // duplicate SKU
        items.put(item.sku, item);
        audit("ADD", item.sku, "Added item: " + item.name);
        return saveData();
    }

    public synchronized boolean updateItem(String sku, Map<String, String> updates) {
        InventoryItem it = items.get(sku);
        if (it == null) return false;
        // Apply updates (only allowed fields)
        if (updates.containsKey("name")) it.name = updates.get("name");
        if (updates.containsKey("category")) it.category = updates.get("category");
        if (updates.containsKey("supplier")) it.supplier = updates.get("supplier");
        if (updates.containsKey("location")) it.location = updates.get("location");
        if (updates.containsKey("quantity")) {
            try {
                int q = Integer.parseInt(updates.get("quantity"));
                it.quantity = q;
            } catch (NumberFormatException e) { /* skip invalid */ }
        }
        if (updates.containsKey("price")) {
            try {
                double p = Double.parseDouble(updates.get("price"));
                it.price = p;
            } catch (NumberFormatException e) { /* skip invalid */ }
        }
        it.updatedAt = LocalDateTime.now();
        audit("UPDATE", sku, "Updated fields: " + updates.keySet().toString());
        return saveData();
    }

    public synchronized boolean deleteItem(String sku) {
        InventoryItem removed = items.remove(sku);
        if (removed == null) return false;
        audit("DELETE", sku, "Deleted item: " + removed.name);
        return saveData();
    }

    public InventoryItem getItem(String sku) {
        return items.get(sku);
    }

    public List<InventoryItem> listAll() {
        List<InventoryItem> list = new ArrayList<>(items.values());
        list.sort(Comparator.comparing(i -> i.sku));
        return list;
    }

    public List<InventoryItem> searchByName(String term) {
        String t = term.toLowerCase();
        List<InventoryItem> res = new ArrayList<>();
        for (InventoryItem it : items.values()) {
            if (it.name.toLowerCase().contains(t) || it.category.toLowerCase().contains(t) || it.sku.toLowerCase().contains(t)) {
                res.add(it);
            }
        }
        return res;
    }

    // ----------------- Persistence -----------------
    private boolean saveData() {
        // Save to temp and then rename to enable rollback if failure occurs
        File temp = new File(DATA_FILE + ".tmp");
        File target = new File(DATA_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(temp))) {
            oos.writeObject(this.items);
            oos.flush();
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
            return false;
        }
        // rename
        if (temp.renameTo(target)) {
            return true;
        } else {
            System.err.println("Error finalizing save.");
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean loadData() {
        File f = new File(DATA_FILE);
        if (!f.exists()) return true; // nothing to load, not an error
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            Object obj = ois.readObject();
            if (obj instanceof Map) {
                this.items = (Map<String, InventoryItem>) obj;
            } else {
                System.err.println("Data file invalid.");
                return false;
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Failed to load data: " + e.getMessage());
            return false;
        }
        return true;
    }

    // ----------------- Audit -----------------
    private void audit(String action, String sku, String message) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String user = (currentUser == null ? "unknown" : currentUser);
        String rec = String.format("%s | %s | user=%s | sku=%s | %s%n", ts, action, user, sku, message);
        try (FileWriter fw = new FileWriter(AUDIT_FILE, true)) {
            fw.write(rec);
        } catch (IOException e) {
            System.err.println("Failed to write audit: " + e.getMessage());
        }
    }

    // ----------------- Console UI -----------------
    private void runConsole() {
        Scanner sc = new Scanner(System.in);
        if (!loadData()) {
            System.out.println("Warning: Could not load previous data. Starting fresh.");
        }
        System.out.println("=== Inventory Management System ===");
        // Simple access check (replace with real auth)
        if (!simpleAuth(sc)) {
            System.out.println("Authentication failed. Exiting.");
            return;
        }

        while (true) {
            showMenu();
            System.out.print("Choice> ");
            String choice = sc.nextLine().trim();
            try {
                switch (choice) {
                    case "1": handleAdd(sc); break;
                    case "2": handleUpdate(sc); break;
                    case "3": handleDelete(sc); break;
                    case "4": handleList(); break;
                    case "5": handleSearch(sc); break;
                    case "6": handleReportLowStock(sc); break;
                    case "7": printAuditSample(); break;
                    case "0": System.out.println("Exiting. Bye!"); sc.close(); return;
                    default: System.out.println("Unknown option. Try again.");
                }
            } catch (Exception e) {
                System.out.println("Operation failed: " + e.getMessage());
            }
        }
    }

    private boolean simpleAuth(Scanner sc) {
        // Placeholder: simple username & password hardcoded for demo.
        System.out.print("Username: ");
        String user = sc.nextLine().trim();
        System.out.print("Password: ");
        String pass = sc.nextLine().trim();
        // In production replace this.
        if ("admin".equals(user) && "admin123".equals(pass)) {
            this.currentUser = user;
            return true;
        }
        return false;
    }

    private void showMenu() {
        System.out.println();
        System.out.println("1. Add Item");
        System.out.println("2. Update Item");
        System.out.println("3. Delete Item");
        System.out.println("4. List All Items");
        System.out.println("5. Search by name/category/sku");
        System.out.println("6. Report: Low stock items");
        System.out.println("7. Show last 10 audit entries");
        System.out.println("0. Exit");
    }

    private void handleAdd(Scanner sc) {
        System.out.println("=== Add Item ===");
        System.out.print("SKU (unique) *: ");
        String sku = sc.nextLine().trim();
        if (sku.isEmpty()) { System.out.println("SKU is required."); return; }
        if (items.containsKey(sku)) { System.out.println("SKU already exists. Use Update instead."); return; }

        System.out.print("Product Name *: ");
        String name = sc.nextLine().trim();
        if (name.isEmpty()) { System.out.println("Name required."); return; }

        System.out.print("Category: ");
        String category = sc.nextLine().trim();
        System.out.print("Quantity (integer) *: ");
        String qStr = sc.nextLine().trim();
        int qty = 0;
        try { qty = Integer.parseInt(qStr); if (qty < 0) throw new NumberFormatException(); } catch (NumberFormatException e) { System.out.println("Invalid quantity."); return; }

        System.out.print("Supplier: ");
        String supplier = sc.nextLine().trim();

        System.out.print("Price (decimal) *: ");
        String pStr = sc.nextLine().trim();
        double price = 0.0;
        try { price = Double.parseDouble(pStr); if (price < 0) throw new NumberFormatException(); } catch (NumberFormatException e) { System.out.println("Invalid price."); return; }

        System.out.print("Location: ");
        String location = sc.nextLine().trim();

        InventoryItem it = new InventoryItem(sku, name, category, qty, supplier, price, location);
        if (addItem(it)) {
            System.out.println("Item added successfully.");
        } else {
            System.out.println("Failed to add item (save error).");
        }
    }

    private void handleUpdate(Scanner sc) {
        System.out.println("=== Update Item ===");
        System.out.print("Enter SKU to update: ");
        String sku = sc.nextLine().trim();
        InventoryItem it = items.get(sku);
        if (it == null) { System.out.println("SKU not found."); return; }
        System.out.println("Current: " + it);

        Map<String, String> updates = new HashMap<>();
        System.out.print("New Name (leave blank to keep): ");
        String v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("name", v);
        System.out.print("New Category (leave blank to keep): ");
        v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("category", v);
        System.out.print("New Supplier (leave blank to keep): ");
        v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("supplier", v);
        System.out.print("New Location (leave blank to keep): ");
        v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("location", v);
        System.out.print("New Quantity (leave blank to keep): ");
        v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("quantity", v);
        System.out.print("New Price (leave blank to keep): ");
        v = sc.nextLine().trim(); if (!v.isEmpty()) updates.put("price", v);

        if (updates.isEmpty()) { System.out.println("No changes provided."); return; }
        if (updateItem(sku, updates)) {
            System.out.println("Item updated.");
        } else {
            System.out.println("Update failed.");
        }
    }

    private void handleDelete(Scanner sc) {
        System.out.println("=== Delete Item ===");
        System.out.print("Enter SKU to delete: ");
        String sku = sc.nextLine().trim();
        InventoryItem it = items.get(sku);
        if (it == null) { System.out.println("SKU not found."); return; }
        System.out.println("About to delete: " + it);
        System.out.print("Type 'YES' to confirm deletion: ");
        String confirm = sc.nextLine().trim();
        if ("YES".equals(confirm)) {
            if (deleteItem(sku)) System.out.println("Item deleted.");
            else System.out.println("Deletion failed.");
        } else {
            System.out.println("Deletion aborted.");
        }
    }

    private void handleList() {
        System.out.println("=== All Inventory Items ===");
        List<InventoryItem> list = listAll();
        if (list.isEmpty()) {
            System.out.println("[No items in inventory]");
            return;
        }
        for (InventoryItem it : list) {
            System.out.println(it);
        }
    }

    private void handleSearch(Scanner sc) {
        System.out.println("=== Search ===");
        System.out.print("Enter search term (name/category/sku): ");
        String term = sc.nextLine().trim();
        List<InventoryItem> res = searchByName(term);
        if (res.isEmpty()) System.out.println("[No results]");
        else {
            for (InventoryItem it : res) System.out.println(it);
        }
    }

    private void handleReportLowStock(Scanner sc) {
        System.out.println("=== Low Stock Report ===");
        System.out.print("Threshold (e.g., 5): ");
        String t = sc.nextLine().trim();
        int threshold = 5;
        try { threshold = Integer.parseInt(t); } catch (NumberFormatException e) { System.out.println("Invalid number, using 5."); }
        List<InventoryItem> low = new ArrayList<>();
        for (InventoryItem it : items.values()) {
            if (it.quantity <= threshold) low.add(it);
        }
        low.sort(Comparator.comparingInt(i -> i.quantity));
        if (low.isEmpty()) {
            System.out.println("No items at or below threshold " + threshold);
        } else {
            System.out.println("Items at or below " + threshold + ":");
            for (InventoryItem it : low) System.out.println(it);
        }
    }

    private void printAuditSample() {
        System.out.println("=== Last 10 Audit Entries ===");
        File f = new File(AUDIT_FILE);
        if (!f.exists()) {
            System.out.println("[No audit entries]");
            return;
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String l;
            while ((l = br.readLine()) != null) lines.add(l);
        } catch (IOException e) {
            System.out.println("Failed to read audit: " + e.getMessage());
            return;
        }
        int start = Math.max(0, lines.size() - 10);
        for (int i = start; i < lines.size(); i++) {
            System.out.println(lines.get(i));
        }
    }

    // ----------------- Main -----------------
    public static void main(String[] args) {
        InventoryManagementSystem app = new InventoryManagementSystem();
        app.runConsole();
    }
}
