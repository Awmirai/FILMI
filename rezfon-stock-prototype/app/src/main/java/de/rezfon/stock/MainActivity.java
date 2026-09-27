package de.rezfon.stock;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int NAV_HOME = 0;
    private static final int NAV_SALE = 1;
    private static final int NAV_INVENTORY = 2;
    private static final int NAV_SHOPPING = 3;

    private final int NAVY = Color.rgb(20, 31, 52);
    private final int BLUE = Color.rgb(37, 99, 235);
    private final int RED = Color.rgb(220, 38, 38);
    private final int ORANGE = Color.rgb(217, 119, 6);
    private final int GREEN = Color.rgb(22, 163, 74);
    private final int TEXT = Color.rgb(29, 41, 57);
    private final int MUTED = Color.rgb(102, 112, 133);
    private final int BG = Color.rgb(246, 248, 252);

    private Db db;
    private LinearLayout content;
    private TextView headerSubtitle;
    private int currentTab = NAV_HOME;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NAVY);
        getWindow().setNavigationBarColor(Color.WHITE);
        db = new Db(this);
        db.getReadableDatabase();
        StockReceiver.ensureChannels(this);
        StockReceiver.scheduleWeekly(this);
        requestNotificationPermission();
        buildShell();
        render(NAV_HOME);
    }

    @Override
    protected void onDestroy() {
        if (db != null) db.close();
        super.onDestroy();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 33);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(18));
        GradientDrawable hbg = new GradientDrawable(
                GradientDrawable.Orientation.RIGHT_LEFT,
                new int[]{Color.rgb(29, 78, 216), NAVY});
        header.setBackground(hbg);

        TextView title = text("REZFON STOCK", 25, Color.WHITE, true);
        header.addView(title);
        headerSubtitle = text("مدیریت هوشمند موجودی • آفلاین", 13, Color.rgb(219, 234, 254), false);
        headerSubtitle.setPadding(0, dp(5), 0, 0);
        header.addView(headerSubtitle);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(4), dp(6), dp(4), dp(6));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(12));
        nav.addView(navButton("خانه", NAV_HOME), weight());
        nav.addView(navButton("فروش سریع", NAV_SALE), weight());
        nav.addView(navButton("موجودی", NAV_INVENTORY), weight());
        nav.addView(navButton("لیست خرید", NAV_SHOPPING), weight());
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(62)));

        setContentView(root);
    }

    private View navButton(String label, int tab) {
        TextView v = text(label, 13, TEXT, tab == currentTab);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(2), dp(8), dp(2), dp(8));
        v.setOnClickListener(view -> render(tab));
        return v;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -1, 1);
    }

    private void render(int tab) {
        currentTab = tab;
        content.removeAllViews();
        if (tab == NAV_HOME) showDashboard();
        else if (tab == NAV_SALE) showQuickSale();
        else if (tab == NAV_INVENTORY) showInventory();
        else showShopping();
    }

    private void showDashboard() {
        headerSubtitle.setText("همه‌چیزهایی که امروز نیاز به توجه دارند");
        TextView hello = text("وضعیت امروز مغازه", 22, TEXT, true);
        hello.setPadding(dp(4), dp(4), dp(4), dp(12));
        content.addView(hello);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(statCard("کل موجودی", String.valueOf(db.totalUnits()), BLUE), weightWrap());
        row1.addView(statCard("ناموجود", String.valueOf(db.outCount()), RED), weightWrap());
        content.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("کم‌موجودی", String.valueOf(db.lowCount()), ORANGE), weightWrap());
        row2.addView(statCard("در لیست خرید", String.valueOf(db.shoppingCount()), GREEN), weightWrap());
        content.addView(row2);

        Button sale = primaryButton("ثبت فروش سریع  −1");
        sale.setOnClickListener(v -> render(NAV_SALE));
        content.addView(sale, blockParams(10));

        Button shopping = outlineButton("مشاهده لیست خرید آماده");
        shopping.setOnClickListener(v -> render(NAV_SHOPPING));
        content.addView(shopping, blockParams(8));

        sectionTitle("اقدام فوری");
        List<Product> shortages = db.shortages(5);
        if (shortages.isEmpty()) {
            content.addView(infoCard("فعلاً هیچ کالایی زیر حداقل موجودی نیست ✓"));
        } else {
            for (Product p : shortages) content.addView(shortageMini(p));
        }

        sectionTitle("آخرین تغییرات موجودی");
        List<String> history = db.recentTransactions(7);
        if (history.isEmpty()) {
            content.addView(infoCard("هنوز فروشی در نسخه آزمایشی ثبت نشده است."));
        } else {
            LinearLayout card = card();
            for (String h : history) {
                TextView t = text(h, 12, TEXT, false);
                t.setPadding(dp(2), dp(6), dp(2), dp(6));
                card.addView(t);
            }
            content.addView(card, blockParams(6));
        }

        TextView demo = text("نسخه آزمایشی: " + db.productCount() + " آرتیکل نمونه داخل دیتابیس است.", 12, MUTED, false);
        demo.setGravity(Gravity.CENTER);
        demo.setPadding(0, dp(18), 0, dp(6));
        content.addView(demo);

        Button reset = outlineButton("بازنشانی دیتای آزمایشی");
        reset.setTextColor(RED);
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("بازنشانی نسخه آزمایشی")
                .setMessage("تمام فروش‌ها و تغییرات تست پاک شود و موجودی نمونه از نو ساخته شود؟")
                .setNegativeButton("لغو", null)
                .setPositiveButton("بازنشانی", (d, w) -> {
                    db.resetDemo();
                    render(NAV_HOME);
                }).show());
        content.addView(reset, blockParams(4));
    }

    private View statCard(String label, String value, int accent) {
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER);
        TextView n = text(value, 27, accent, true);
        n.setGravity(Gravity.CENTER);
        TextView l = text(label, 12, MUTED, false);
        l.setGravity(Gravity.CENTER);
        c.addView(n);
        c.addView(l);
        return c;
    }

    private LinearLayout.LayoutParams weightWrap() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(96), 1);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private View shortageMini(Product p) {
        LinearLayout c = card();
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView info = text(p.displayName(), 13, TEXT, true);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(badge(p));
        c.addView(top);
        TextView detail = text("موجودی " + p.stock + "  •  حداقل " + p.minStock + "  •  خرید پیشنهادی " + p.orderQty(),
                12, MUTED, false);
        detail.setPadding(0, dp(6), 0, 0);
        c.addView(detail);
        contentMargin(c, 5);
        return c;
    }

    private void showQuickSale() {
        headerSubtitle.setText("فقط جنس را پیدا کن و فروش را بزن؛ بقیه کار با اپ است");
        LinearLayout top = card();
        top.addView(text("فروش سریع", 21, TEXT, true));
        EditText search = searchBox("مثلاً: 15 pro privacy یا A55 black");
        top.addView(search, blockParams(8));
        Spinner category = spinner(new String[]{"همه دسته‌ها", "Glass", "Case", "Repair"});
        top.addView(category, blockParams(4));
        content.addView(top, blockParams(4));

        TextView hint = text("روی «فروش −1» بزن؛ موجودی، هشدار و لیست خرید همان لحظه آپدیت می‌شود.", 12, MUTED, false);
        hint.setPadding(dp(5), dp(8), dp(5), dp(8));
        content.addView(hint);

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);

        Runnable refresh = () -> {
            String selected = String.valueOf(category.getSelectedItem());
            String cat = "همه دسته‌ها".equals(selected) ? "All" : selected;
            renderSaleResults(results, search.getText().toString(), cat);
        };
        search.addTextChangedListener(simpleWatcher(refresh));
        category.setOnItemSelectedListener(simpleSpinner(refresh));
        refresh.run();
    }

    private void renderSaleResults(LinearLayout results, String query, String category) {
        results.removeAllViews();
        List<Product> list = db.search(query, category, 60);
        if (list.isEmpty()) {
            results.addView(infoCard("کالایی پیدا نشد. از بخش «موجودی» می‌توانی آرتیکل جدید اضافه کنی."));
            return;
        }
        for (Product p : list) {
            LinearLayout c = card();
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(p.displayName(), 13, TEXT, true);
            head.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(badge(p));
            c.addView(head);
            TextView detail = text("موجودی: " + p.stock + "  •  حداقل: " + p.minStock + "  •  هدف: " + p.targetStock,
                    12, MUTED, false);
            detail.setPadding(0, dp(5), 0, dp(8));
            c.addView(detail);
            Button sale = smallButton("فروش  −1", p.stock > 0 ? BLUE : Color.GRAY);
            sale.setEnabled(p.stock > 0);
            sale.setOnClickListener(v -> {
                Product updated = db.adjustStock(p.id, -1, "SALE");
                StockReceiver.notifyProduct(this, updated);
                Toast.makeText(this, "فروش ثبت شد • موجودی: " + (updated == null ? "-" : updated.stock), Toast.LENGTH_SHORT).show();
                renderSaleResults(results, query, category);
            });
            c.addView(sale, new LinearLayout.LayoutParams(-1, dp(44)));
            contentMargin(c, 5);
            results.addView(c);
        }
    }

    private void showInventory() {
        headerSubtitle.setText("افزودن، اصلاح و ورود جنس بدون دفتر و لیست دستی");
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("موجودی کالا", 21, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        Button add = smallButton("+ کالا", BLUE);
        add.setOnClickListener(v -> showAddProductDialog());
        header.addView(add, new LinearLayout.LayoutParams(dp(94), dp(44)));
        content.addView(header, blockParams(2));

        EditText search = searchBox("جستجو مدل، نوع، رنگ...");
        content.addView(search, blockParams(7));
        Spinner category = spinner(new String[]{"همه دسته‌ها", "Glass", "Case", "Repair"});
        content.addView(category, blockParams(3));

        TextView count = text("", 12, MUTED, false);
        count.setPadding(dp(3), dp(7), dp(3), dp(7));
        content.addView(count);

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);

        Runnable refresh = () -> {
            String selected = String.valueOf(category.getSelectedItem());
            String cat = "همه دسته‌ها".equals(selected) ? "All" : selected;
            renderInventoryResults(results, count, search.getText().toString(), cat);
        };
        search.addTextChangedListener(simpleWatcher(refresh));
        category.setOnItemSelectedListener(simpleSpinner(refresh));
        refresh.run();
    }

    private void renderInventoryResults(LinearLayout results, TextView count, String query, String category) {
        results.removeAllViews();
        List<Product> list = db.search(query, category, 90);
        count.setText(list.size() + " نتیجه نمایش داده می‌شود");
        for (Product p : list) {
            LinearLayout c = card();
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(text(p.displayName(), 13, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(badge(p));
            c.addView(head);
            c.addView(text("موجودی " + p.stock + "  •  حداقل " + p.minStock + "  •  هدف " + p.targetStock,
                    12, MUTED, false));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(8), 0, 0);
            Button minus = smallButton("فروش −1", p.stock > 0 ? BLUE : Color.GRAY);
            minus.setEnabled(p.stock > 0);
            minus.setOnClickListener(v -> {
                Product updated = db.adjustStock(p.id, -1, "SALE");
                StockReceiver.notifyProduct(this, updated);
                renderInventoryResults(results, count, query, category);
            });
            Button plus = smallButton("+5 ورود", GREEN);
            plus.setOnClickListener(v -> {
                db.adjustStock(p.id, 5, "RESTOCK");
                Toast.makeText(this, "۵ عدد به موجودی اضافه شد", Toast.LENGTH_SHORT).show();
                renderInventoryResults(results, count, query, category);
            });
            LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(43), 1);
            a.setMargins(dp(3), 0, dp(3), 0);
            actions.addView(minus, a);
            LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, dp(43), 1);
            b.setMargins(dp(3), 0, dp(3), 0);
            actions.addView(plus, b);
            c.addView(actions);
            contentMargin(c, 5);
            results.addView(c);
        }
    }

    private void showShopping() {
        headerSubtitle.setText("این همان لیستی است که باید برداری و برای خرید بروی");
        List<Product> list = db.shortages(500);
        content.addView(text("لیست خرید هوشمند", 21, TEXT, true));
        TextView summary = text(list.size() + " آرتیکل نیاز به خرید دارد", 13, list.isEmpty() ? GREEN : RED, true);
        summary.setPadding(0, dp(4), 0, dp(10));
        content.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = smallButton("کپی", NAVY);
        copy.setOnClickListener(v -> copyShoppingList());
        Button share = smallButton("ارسال", BLUE);
        share.setOnClickListener(v -> shareShoppingList());
        Button csv = smallButton("CSV", GREEN);
        csv.setOnClickListener(v -> exportCsv());
        LinearLayout.LayoutParams ap1 = new LinearLayout.LayoutParams(0, dp(44), 1);
        ap1.setMargins(dp(3), 0, dp(3), 0);
        LinearLayout.LayoutParams ap2 = new LinearLayout.LayoutParams(0, dp(44), 1);
        ap2.setMargins(dp(3), 0, dp(3), 0);
        LinearLayout.LayoutParams ap3 = new LinearLayout.LayoutParams(0, dp(44), 1);
        ap3.setMargins(dp(3), 0, dp(3), 0);
        actions.addView(copy, ap1);
        actions.addView(share, ap2);
        actions.addView(csv, ap3);
        content.addView(actions, blockParams(5));

        if (list.isEmpty()) {
            content.addView(infoCard("عالی است؛ هیچ کالایی زیر حداقل تعریف‌شده نیست."));
            return;
        }

        String lastCategory = "";
        for (Product p : list) {
            if (!p.category.equals(lastCategory)) {
                TextView cat = text(categoryFa(p.category), 16, TEXT, true);
                cat.setPadding(dp(3), dp(14), dp(3), dp(6));
                content.addView(cat);
                lastCategory = p.category;
            }
            LinearLayout c = card();
            LinearLayout head = new LinearLayout(this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(text(p.displayName(), 13, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
            head.addView(badge(p));
            c.addView(head);
            TextView detail = text("الان: " + p.stock + "  →  خرید پیشنهادی: " + p.orderQty() + "  →  هدف: " + p.targetStock,
                    12, MUTED, false);
            detail.setPadding(0, dp(6), 0, dp(8));
            c.addView(detail);
            Button receive = smallButton("جنس رسید؛ موجودی را تا هدف پر کن", GREEN);
            receive.setOnClickListener(v -> {
                db.receiveToTarget(p.id);
                Toast.makeText(this, "ورود جنس ثبت شد", Toast.LENGTH_SHORT).show();
                render(NAV_SHOPPING);
            });
            c.addView(receive, new LinearLayout.LayoutParams(-1, dp(43)));
            contentMargin(c, 5);
            content.addView(c);
        }
    }

    private void showAddProductDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        Spinner category = spinner(new String[]{"Glass", "Case", "Repair", "Accessory"});
        Spinner brand = spinner(new String[]{"Apple", "Samsung", "Other"});
        EditText model = field("مدل گوشی، مثلاً iPhone 15 Pro");
        EditText type = field("نوع کالا، مثلاً Silicone Case");
        EditText variant = field("Variant، مثلاً Privacy / OLED / 5D");
        EditText color = field("رنگ، در صورت نیاز");
        EditText stock = numberField("موجودی فعلی", "5");
        EditText min = numberField("حداقل موجودی", "2");
        EditText target = numberField("موجودی هدف", "10");

        form.addView(label("دسته")); form.addView(category);
        form.addView(label("برند")); form.addView(brand);
        form.addView(model); form.addView(type); form.addView(variant); form.addView(color);
        form.addView(stock); form.addView(min); form.addView(target);
        ScrollView sv = new ScrollView(this);
        sv.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("افزودن آرتیکل جدید")
                .setView(sv)
                .setNegativeButton("لغو", null)
                .setPositiveButton("ذخیره", (d, which) -> {
                    if (model.getText().toString().trim().isEmpty() || type.getText().toString().trim().isEmpty()) {
                        Toast.makeText(this, "مدل و نوع کالا الزامی است", Toast.LENGTH_LONG).show();
                        return;
                    }
                    db.addProduct(String.valueOf(category.getSelectedItem()), String.valueOf(brand.getSelectedItem()),
                            model.getText().toString().trim(), type.getText().toString().trim(),
                            variant.getText().toString().trim(), color.getText().toString().trim(),
                            parseInt(stock, 0), parseInt(min, 2), parseInt(target, 10));
                    Toast.makeText(this, "کالا اضافه شد", Toast.LENGTH_SHORT).show();
                    render(NAV_INVENTORY);
                }).show();
    }

    private int parseInt(EditText e, int fallback) {
        try { return Integer.parseInt(e.getText().toString().trim()); }
        catch (Exception ex) { return fallback; }
    }

    private String shoppingText() {
        List<Product> list = db.shortages(500);
        StringBuilder b = new StringBuilder();
        b.append("REZFON – Einkaufsliste\n");
        b.append(new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(new Date())).append("\n\n");
        String cat = "";
        for (Product p : list) {
            if (!p.category.equals(cat)) {
                cat = p.category;
                b.append("\n").append(categoryFa(cat)).append("\n");
            }
            b.append("• ").append(p.displayName())
                    .append("  × ").append(p.orderQty())
                    .append("  (موجودی: ").append(p.stock).append(")\n");
        }
        if (list.isEmpty()) b.append("فعلاً چیزی برای خرید نیست.\n");
        return b.toString();
    }

    private void copyShoppingList() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Rezfon Einkaufsliste", shoppingText()));
        Toast.makeText(this, "لیست خرید کپی شد", Toast.LENGTH_SHORT).show();
    }

    private void shareShoppingList() {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Rezfon Einkaufsliste");
        i.putExtra(Intent.EXTRA_TEXT, shoppingText());
        startActivity(Intent.createChooser(i, "ارسال لیست خرید"));
    }

    private void exportCsv() {
        List<Product> list = db.shortages(500);
        StringBuilder csv = new StringBuilder("\ufeffcategory,brand,model,type,variant,color,current_stock,min_stock,target_stock,order_qty\n");
        for (Product p : list) {
            csv.append(csvCell(p.category)).append(',').append(csvCell(p.brand)).append(',')
                    .append(csvCell(p.model)).append(',').append(csvCell(p.type)).append(',')
                    .append(csvCell(p.variant)).append(',').append(csvCell(p.color)).append(',')
                    .append(p.stock).append(',').append(p.minStock).append(',').append(p.targetStock).append(',')
                    .append(p.orderQty()).append('\n');
        }
        String name = "Rezfon-Shopping-" + new SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(new Date()) + ".csv";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new IllegalStateException("Cannot create download");
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new IllegalStateException("Cannot open download");
                    os.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "CSV در Downloads ذخیره شد\n" + name, Toast.LENGTH_LONG).show();
            } else {
                java.io.File file = new java.io.File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), name);
                try (java.io.FileOutputStream os = new java.io.FileOutputStream(file)) {
                    os.write(csv.toString().getBytes(StandardCharsets.UTF_8));
                }
                Toast.makeText(this, "CSV ذخیره شد: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "خطا در ذخیره CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String csvCell(String s) {
        if (s == null) s = "";
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private String categoryFa(String c) {
        if ("Glass".equals(c)) return "گلس";
        if ("Case".equals(c)) return "کیس";
        if ("Repair".equals(c)) return "قطعات تعمیر";
        return c;
    }

    private TextWatcher simpleWatcher(Runnable r) {
        return new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int before, int count) { r.run(); }
            public void afterTextChanged(Editable s) {}
        };
    }

    private AdapterView.OnItemSelectedListener simpleSpinner(Runnable r) {
        return new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { r.run(); }
            public void onNothingSelected(AdapterView<?> parent) {}
        };
    }

    private Spinner spinner(String[] values) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(rounded(Color.WHITE, Color.rgb(208, 213, 221), 12));
        s.setPadding(dp(10), 0, dp(10), 0);
        return s;
    }

    private EditText searchBox(String hint) {
        EditText e = field(hint);
        e.setSingleLine(true);
        e.setTextSize(14);
        return e;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setTextSize(14);
        e.setTextColor(TEXT);
        e.setHintTextColor(Color.rgb(152, 162, 179));
        e.setPadding(dp(13), dp(10), dp(13), dp(10));
        e.setBackground(rounded(Color.WHITE, Color.rgb(208, 213, 221), 12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.setMargins(0, dp(5), 0, dp(5));
        e.setLayoutParams(p);
        return e;
    }

    private EditText numberField(String hint, String value) {
        EditText e = field(hint);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setText(value);
        return e;
    }

    private TextView label(String s) {
        TextView t = text(s, 12, MUTED, true);
        t.setPadding(0, dp(7), 0, dp(3));
        return t;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(13), dp(14), dp(13));
        c.setBackground(rounded(Color.WHITE, Color.rgb(229, 234, 242), 16));
        c.setElevation(dp(1));
        return c;
    }

    private View infoCard(String message) {
        LinearLayout c = card();
        TextView t = text(message, 13, MUTED, false);
        t.setGravity(Gravity.CENTER);
        c.addView(t);
        contentMargin(c, 5);
        return c;
    }

    private void sectionTitle(String s) {
        TextView t = text(s, 16, TEXT, true);
        t.setPadding(dp(3), dp(19), dp(3), dp(7));
        content.addView(t);
    }

    private TextView badge(Product p) {
        int bg;
        int fg;
        String label;
        if (p.stock == 0) {
            bg = Color.rgb(254, 226, 226); fg = RED; label = "ناموجود";
        } else if (p.stock <= p.minStock) {
            bg = Color.rgb(254, 243, 199); fg = ORANGE; label = "کم: " + p.stock;
        } else {
            bg = Color.rgb(220, 252, 231); fg = GREEN; label = "" + p.stock;
        }
        TextView b = text(label, 11, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(9), dp(5), dp(9), dp(5));
        b.setBackground(rounded(bg, bg, 20));
        return b;
    }

    private Button primaryButton(String label) {
        return styledButton(label, BLUE, Color.WHITE, BLUE);
    }

    private Button outlineButton(String label) {
        return styledButton(label, Color.WHITE, TEXT, Color.rgb(208, 213, 221));
    }

    private Button smallButton(String label, int bg) {
        return styledButton(label, bg, Color.WHITE, bg);
    }

    private Button styledButton(String label, int bg, int fg, int stroke) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(fg);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(rounded(bg, stroke, 12));
        b.setStateListAnimator(null);
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams blockParams(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(topDp), 0, 0);
        return p;
    }

    private void contentMargin(View v, int topDp) {
        v.setLayoutParams(blockParams(topDp));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
