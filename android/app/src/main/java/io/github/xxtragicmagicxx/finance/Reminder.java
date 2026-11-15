package io.github.xxtragicmagicxx.finance;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The monthly reminder: one alarm, always set for the 1st of the next month at the chosen hour.
 * When it fires it shows the notification and sets the next one. The alarm is put back after a
 * reboot, an app update or a clock change (the system forgets alarms then), and every time the
 * app opens.
 */
public class Reminder extends BroadcastReceiver {
    static final String FIRE = "io.github.xxtragicmagicxx.finance.REMINDER";
    private static final String CHANNEL = "monthly";
    private static final String PREFS = "reminder";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context c) {
        return prefs(c).getBoolean("on", true);
    }

    static int hour(Context c) {
        return prefs(c).getInt("hour", 9);
    }

    static void set(Context c, boolean on, int hour) {
        prefs(c).edit().putBoolean("on", on).putInt("hour", Math.max(0, Math.min(23, hour))).apply();
        schedule(c);
    }

    /** When the next reminder is due, in milliseconds, or 0 when reminders are off. */
    static long next(Context c) {
        if (!enabled(c)) return 0;
        Calendar t = Calendar.getInstance();
        t.set(Calendar.DAY_OF_MONTH, 1);
        t.set(Calendar.HOUR_OF_DAY, hour(c));
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.MONTH, 1);
        return t.getTimeInMillis();
    }

    private static PendingIntent alarmIntent(Context c) {
        Intent i = new Intent(c, Reminder.class).setAction(FIRE);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = alarmIntent(c);
        am.cancel(pi);
        long at = next(c);
        // Not an exact alarm (that needs a special permission); Android may deliver it a few minutes late.
        if (at > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    static void channel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Monthly reminder", NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("Reminds you on the 1st of each month to enter your new totals.");
        nm.createNotificationChannel(ch);
    }

    static boolean allowed(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    @SuppressWarnings("deprecation")
    static void show(Context c) {
        channel(c);
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PAGE, "update")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setColor(c.getResources().getColor(R.color.accent))
                .setContentTitle("Time to update your finances")
                .setContentText("Enter your " + month + " totals to see how you're doing.")
                .setContentIntent(pi)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.notify(1, b.build());
        } catch (SecurityException ignored) {
            // Notifications not allowed (Android 13+ without permission): nothing to show.
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (FIRE.equals(i.getAction()) && enabled(c)) show(c);
        schedule(c);
    }
}
