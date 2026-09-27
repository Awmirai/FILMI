package de.rezfon.stock;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;

public class StockReceiver extends BroadcastReceiver {
    public static final String CHANNEL_STOCK = "stock_alerts";
    public static final String CHANNEL_WEEKLY = "weekly_report";

    @Override
    public void onReceive(Context context, Intent intent) {
        Db db = new Db(context);
        int count = db.shoppingCount();
        if (count > 0) {
            notify(context, CHANNEL_WEEKLY, 7001,
                    "لیست خرید هفتگی آماده است",
                    count + " کالا نیاز به تهیه یا شارژ موجودی دارد.");
        }
        db.close();
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel stock = new NotificationChannel(
                    CHANNEL_STOCK, "هشدار موجودی", NotificationManager.IMPORTANCE_HIGH);
            stock.setDescription("هشدار اتمام یا کم شدن موجودی کالا");
            NotificationChannel weekly = new NotificationChannel(
                    CHANNEL_WEEKLY, "لیست خرید هفتگی", NotificationManager.IMPORTANCE_DEFAULT);
            weekly.setDescription("یادآوری خودکار لیست خرید هفتگی");
            nm.createNotificationChannel(stock);
            nm.createNotificationChannel(weekly);
        }
    }

    public static void notifyProduct(Context context, Product p) {
        if (p == null || !p.isLow()) return;
        String title = p.stock == 0 ? "کالا ناموجود شد" : "موجودی رو به اتمام است";
        String body = p.displayName() + " — موجودی: " + p.stock + " — پیشنهاد خرید: " + p.orderQty();
        notify(context, CHANNEL_STOCK, (int) (1000 + (p.id % 5000)), title, body);
    }

    private static void notify(Context context, String channel, int id, String title, String body) {
        ensureChannels(context);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, channel)
                : new Notification.Builder(context);
        b.setSmallIcon(android.R.drawable.stat_notify_more)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(id, b.build());
        } catch (SecurityException ignored) {
        }
    }

    public static void scheduleWeekly(Context context) {
        Calendar next = Calendar.getInstance();
        next.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.WEEK_OF_YEAR, 1);
        }
        Intent i = new Intent(context, StockReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 9001, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY * 7L, pi);
    }
}
