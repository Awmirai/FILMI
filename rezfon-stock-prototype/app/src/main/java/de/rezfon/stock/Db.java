package de.rezfon.stock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Db extends SQLiteOpenHelper {
    private static final String DB_NAME = "rezfon_stock.db";
    private static final int DB_VERSION = 1;

    public Db(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE products (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "category TEXT NOT NULL," +
                "brand TEXT NOT NULL," +
                "model TEXT NOT NULL," +
                "type TEXT NOT NULL," +
                "variant TEXT DEFAULT ''," +
                "color TEXT DEFAULT ''," +
                "stock INTEGER NOT NULL DEFAULT 0," +
                "min_stock INTEGER NOT NULL DEFAULT 2," +
                "target_stock INTEGER NOT NULL DEFAULT 10," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE transactions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "product_id INTEGER NOT NULL," +
                "change_qty INTEGER NOT NULL," +
                "kind TEXT NOT NULL," +
                "created_at INTEGER NOT NULL)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS transactions");
        db.execSQL("DROP TABLE IF EXISTS products");
        onCreate(db);
    }

    private void seed(SQLiteDatabase db) {
        String[] iphones = {
                "iPhone X", "iPhone XR", "iPhone XS", "iPhone XS Max",
                "iPhone 11", "iPhone 11 Pro", "iPhone 11 Pro Max",
                "iPhone 12", "iPhone 12 Pro", "iPhone 12 Pro Max",
                "iPhone 13", "iPhone 13 Pro", "iPhone 13 Pro Max",
                "iPhone 14", "iPhone 14 Plus", "iPhone 14 Pro", "iPhone 14 Pro Max",
                "iPhone 15", "iPhone 15 Plus", "iPhone 15 Pro", "iPhone 15 Pro Max",
                "iPhone 16", "iPhone 16 Plus", "iPhone 16 Pro", "iPhone 16 Pro Max",
                "iPhone 17", "iPhone 17 Pro", "iPhone 17 Pro Max",
                "iPhone SE 2020", "iPhone SE 2022"
        };
        String[] samsung = {
                "Galaxy A12", "Galaxy A13", "Galaxy A14", "Galaxy A15", "Galaxy A16",
                "Galaxy A32", "Galaxy A33", "Galaxy A34", "Galaxy A35", "Galaxy A36",
                "Galaxy A52", "Galaxy A53", "Galaxy A54", "Galaxy A55", "Galaxy A56",
                "Galaxy S20", "Galaxy S20 FE", "Galaxy S21", "Galaxy S21 FE",
                "Galaxy S22", "Galaxy S22 Ultra", "Galaxy S23", "Galaxy S23 Ultra",
                "Galaxy S24", "Galaxy S24 Ultra", "Galaxy S25", "Galaxy S25 Ultra",
                "Galaxy S26", "Galaxy S26 Ultra"
        };
        for (String model : iphones) seedModel(db, "Apple", model);
        for (String model : samsung) seedModel(db, "Samsung", model);
    }

    private void seedModel(SQLiteDatabase db, String brand, String model) {
        insertSeed(db, "Glass", brand, model, "Glass", "9H", "", 2, 10);
        insertSeed(db, "Glass", brand, model, "Glass", "5D", "", 2, 10);
        insertSeed(db, "Glass", brand, model, "Glass", "Privacy", "", 2, 10);
        insertSeed(db, "Case", brand, model, "Silicone Case", "", "Black", 2, 8);
        insertSeed(db, "Case", brand, model, "Transparent Case", "", "Transparent", 2, 8);
        insertSeed(db, "Case", brand, model, "360 Case", "", "Black", 2, 8);
        insertSeed(db, "Case", brand, model, "Book Case", "", "Black", 2, 6);
        insertSeed(db, "Case", brand, model, "Hard Case", "", "Black", 2, 6);
        insertRepair(db, brand, model, "Display", "LCD");
        insertRepair(db, brand, model, "Display", "OLED");
        insertRepair(db, brand, model, "Battery", "");
    }

    private void insertSeed(SQLiteDatabase db, String category, String brand, String model,
                            String type, String variant, String color, int min, int target) {
        int seed = Math.abs((brand + model + type + variant + color).hashCode());
        int stock = seed % 9;
        insert(db, category, brand, model, type, variant, color, stock, min, target);
    }

    private void insertRepair(SQLiteDatabase db, String brand, String model, String type, String variant) {
        int seed = Math.abs(("repair" + brand + model + type + variant).hashCode());
        int stock = seed % 4;
        insert(db, "Repair", brand, model, type, variant, "", stock, 1, 3);
    }

    private long insert(SQLiteDatabase db, String category, String brand, String model,
                        String type, String variant, String color, int stock, int min, int target) {
        ContentValues v = new ContentValues();
        v.put("category", category);
        v.put("brand", brand);
        v.put("model", model);
        v.put("type", type);
        v.put("variant", variant == null ? "" : variant);
        v.put("color", color == null ? "" : color);
        v.put("stock", Math.max(0, stock));
        v.put("min_stock", Math.max(0, min));
        v.put("target_stock", Math.max(Math.max(0, min), target));
        v.put("updated_at", System.currentTimeMillis());
        return db.insert("products", null, v);
    }

    public long addProduct(String category, String brand, String model, String type,
                           String variant, String color, int stock, int min, int target) {
        return insert(getWritableDatabase(), category, brand, model, type, variant, color, stock, min, target);
    }

    public List<Product> search(String query, String category, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        String q = query == null ? "" : query.trim();
        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder("1=1");
        if (!q.isEmpty()) {
            where.append(" AND (brand LIKE ? OR model LIKE ? OR type LIKE ? OR variant LIKE ? OR color LIKE ?)");
            String like = "%" + q + "%";
            for (int i = 0; i < 5; i++) args.add(like);
        }
        if (category != null && !category.isEmpty() && !"All".equals(category)) {
            where.append(" AND category=?");
            args.add(category);
        }
        Cursor c = db.query("products", null, where.toString(), args.toArray(new String[0]),
                null, null, "model COLLATE NOCASE, category, type, variant", String.valueOf(limit));
        return readProducts(c);
    }

    public List<Product> shortages(int limit) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM products WHERE stock<=min_stock " +
                        "ORDER BY CASE WHEN stock=0 THEN 0 ELSE 1 END, stock ASC, model COLLATE NOCASE LIMIT ?",
                new String[]{String.valueOf(limit)});
        return readProducts(c);
    }

    public Product getProduct(long id) {
        Cursor c = getReadableDatabase().query("products", null, "id=?",
                new String[]{String.valueOf(id)}, null, null, null, "1");
        try {
            if (c.moveToFirst()) return map(c);
            return null;
        } finally {
            c.close();
        }
    }

    public Product adjustStock(long id, int delta, String kind) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            Product p = getProduct(id);
            if (p == null) return null;
            int next = Math.max(0, p.stock + delta);
            int actual = next - p.stock;
            ContentValues v = new ContentValues();
            v.put("stock", next);
            v.put("updated_at", System.currentTimeMillis());
            db.update("products", v, "id=?", new String[]{String.valueOf(id)});
            if (actual != 0) {
                ContentValues t = new ContentValues();
                t.put("product_id", id);
                t.put("change_qty", actual);
                t.put("kind", kind);
                t.put("created_at", System.currentTimeMillis());
                db.insert("transactions", null, t);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return getProduct(id);
    }

    public Product receiveToTarget(long id) {
        Product p = getProduct(id);
        if (p == null) return null;
        return adjustStock(id, Math.max(0, p.targetStock - p.stock), "RESTOCK");
    }

    public int totalUnits() {
        return scalarInt("SELECT COALESCE(SUM(stock),0) FROM products");
    }

    public int outCount() {
        return scalarInt("SELECT COUNT(*) FROM products WHERE stock=0");
    }

    public int lowCount() {
        return scalarInt("SELECT COUNT(*) FROM products WHERE stock>0 AND stock<=min_stock");
    }

    public int shoppingCount() {
        return scalarInt("SELECT COUNT(*) FROM products WHERE stock<=min_stock");
    }

    public int productCount() {
        return scalarInt("SELECT COUNT(*) FROM products");
    }

    private int scalarInt(String sql) {
        Cursor c = getReadableDatabase().rawQuery(sql, null);
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public List<String> recentTransactions(int limit) {
        List<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT t.change_qty,t.kind,t.created_at,p.model,p.type,p.variant,p.color " +
                        "FROM transactions t JOIN products p ON p.id=t.product_id " +
                        "ORDER BY t.id DESC LIMIT ?", new String[]{String.valueOf(limit)});
        SimpleDateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        try {
            while (c.moveToNext()) {
                int q = c.getInt(0);
                String sign = q > 0 ? "+" : "";
                String name = c.getString(3) + " • " + c.getString(4);
                String variant = c.getString(5);
                String color = c.getString(6);
                if (variant != null && !variant.isEmpty()) name += " • " + variant;
                if (color != null && !color.isEmpty()) name += " • " + color;
                out.add(df.format(new Date(c.getLong(2))) + "  " + sign + q + "  " + name + "  [" + c.getString(1) + "]");
            }
        } finally {
            c.close();
        }
        return out;
    }

    public void resetDemo() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("transactions", null, null);
            db.delete("products", null, null);
            seed(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<Product> readProducts(Cursor c) {
        List<Product> list = new ArrayList<>();
        try {
            while (c.moveToNext()) list.add(map(c));
        } finally {
            c.close();
        }
        return list;
    }

    private Product map(Cursor c) {
        Product p = new Product();
        p.id = c.getLong(c.getColumnIndexOrThrow("id"));
        p.category = c.getString(c.getColumnIndexOrThrow("category"));
        p.brand = c.getString(c.getColumnIndexOrThrow("brand"));
        p.model = c.getString(c.getColumnIndexOrThrow("model"));
        p.type = c.getString(c.getColumnIndexOrThrow("type"));
        p.variant = c.getString(c.getColumnIndexOrThrow("variant"));
        p.color = c.getString(c.getColumnIndexOrThrow("color"));
        p.stock = c.getInt(c.getColumnIndexOrThrow("stock"));
        p.minStock = c.getInt(c.getColumnIndexOrThrow("min_stock"));
        p.targetStock = c.getInt(c.getColumnIndexOrThrow("target_stock"));
        return p;
    }
}
