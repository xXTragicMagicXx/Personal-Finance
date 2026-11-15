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
 * The monthly reminders: one alarm for the 1st of the month and, optionally, a follow-up on a later
 * day (the 5th by default) that only notifies if this month's totals still aren't saved. Each alarm
 * shows its notification and sets the next one. Alarms are put back after a reboot, an app update
 * or a clock change (the system forgets alarms then), and every time the app opens.
 */
public class Reminder extends BroadcastReceiver {
    static final String FIRE = "io.github.xxtragicmagicxx.finance.REMINDER";
    static final String FOLLOW = "io.github.xxtragicmagicxx.finance.FOLLOW_UP";
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

    static boolean follow(Context c) {
        return prefs(c).getBoolean("follow", true);
    }

    static int followDay(Context c) {
        return prefs(c).getInt("followDay", 5);
    }

    static void set(Context c, boolean on, int hour) {
        prefs(c).edit().putBoolean("on", on).putInt("hour", Math.max(0, Math.min(23, hour))).apply();
        schedule(c);
    }

    static void setFollow(Context c, boolean on, int day) {
        prefs(c).edit().putBoolean("follow", on).putInt("followDay", Math.max(2, Math.min(28, day))).apply();
        schedule(c);
    }

    /** "2026-11" for the current month, the same form the page uses for its months. */
    static String monthKey() {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(Calendar.getInstance().getTime());
    }

    /** Whether this month's totals are saved, from what the page last reported (see MainActivity.Bridge.sync). */
    static boolean entered(Context c) {
        String latest = Widget.data(c).optString("latest", "");
        return latest.compareTo(monthKey()) >= 0;
    }

    /** When the next reminder is due, in milliseconds, or 0 when reminders are off. */
    static long next(Context c) {
        return enabled(c) ? nextOn(c, 1) : 0;
    }

    static long nextFollow(Context c) {
        return enabled(c) && follow(c) ? nextOn(c, followDay(c)) : 0;
    }

    private static long nextOn(Context c, int day) {
        Calendar t = Calendar.getInstance();
        t.set(Calendar.DAY_OF_MONTH, day);
        t.set(Calendar.HOUR_OF_DAY, hour(c));
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        if (t.getTimeInMillis() <= System.currentTimeMillis()) t.add(Calendar.MONTH, 1);
        return t.getTimeInMillis();
    }

    private static PendingIntent alarmIntent(Context c, String action, int code) {
        Intent i = new Intent(c, Reminder.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        set(am, alarmIntent(c, FIRE, 0), next(c));
        set(am, alarmIntent(c, FOLLOW, 2), nextFollow(c));
    }

    private static void set(AlarmManager am, PendingIntent pi, long at) {
        am.cancel(pi);
        // Not an exact alarm (that needs a special permission); Android may deliver it a few minutes late.
        if (at > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** Clears the reminder from the notification shade, e.g. once the month has been saved. */
    static void dismiss(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1);
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
        show(c, false);
    }

    static void show(Context c, boolean followUp) {
        channel(c);
        Intent open = new Intent(c, MainActivity.class)
                .putExtra(MainActivity.EXTRA_PAGE, "update")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime());
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setColor(c.getResources().getColor(R.color.accent))
                .setContentTitle(followUp ? "Your " + month + " totals are still missing" : "Time to update your finances")
                .setContentText(followUp ? "Take a minute to enter them and see how the month went."
                        : "Enter your " + month + " totals to see how you're doing.")
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
        String a = i.getAction();
        if (FIRE.equals(a) && enabled(c) && !entered(c)) show(c, false);
        if (FOLLOW.equals(a) && enabled(c) && follow(c) && !entered(c)) show(c, true);
        schedule(c);
        Widget.refresh(c);
    }
}
