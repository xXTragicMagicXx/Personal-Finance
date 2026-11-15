package io.github.xxtragicmagicxx.finance;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home-screen widget: net worth, the change since last month, the closest goal, and a nudge when
 * this month hasn't been entered. The page sends its numbers (already formatted) through
 * AndroidApp.sync each time it saves, so the widget never has to read the page's storage.
 */
public class Widget extends AppWidgetProvider {
    private static final String PREFS = "widget";

    static JSONObject data(Context c) {
        try {
            return new JSONObject(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("json", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    static void save(Context c, String json) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("json", json).apply();
        refresh(c);
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        if (m == null) return;
        int[] ids = m.getAppWidgetIds(new ComponentName(c, Widget.class));
        if (ids.length > 0) m.updateAppWidget(ids, views(c));
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        m.updateAppWidget(ids, views(c));
    }

    private static RemoteViews views(Context c) {
        JSONObject d = data(c);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        boolean any = !d.optString("latest").isEmpty();
        boolean due = any && !Reminder.entered(c);

        v.setTextViewText(R.id.w_title, any ? "Net worth · " + d.optString("month") : "Finance");
        v.setTextViewText(R.id.w_total, any ? d.optString("total") : "—");

        String chg = d.optString("chg");
        if (!any) {
            v.setTextViewText(R.id.w_change, "Open the app to enter your first month");
            v.setTextColor(R.id.w_change, c.getColor(R.color.w_mute));
        } else if (chg.isEmpty()) {
            v.setTextViewText(R.id.w_change, "Save another month to see your change");
            v.setTextColor(R.id.w_change, c.getColor(R.color.w_mute));
        } else {
            String dir = d.optString("dir");
            String arrow = "up".equals(dir) ? "▲ " : "down".equals(dir) ? "▼ " : "";
            String pct = d.optString("pct");
            v.setTextViewText(R.id.w_change, arrow + chg + (pct.isEmpty() ? "" : " · " + pct) + "  vs " + d.optString("vs"));
            v.setTextColor(R.id.w_change, c.getColor("up".equals(dir) ? R.color.w_up : "down".equals(dir) ? R.color.w_down : R.color.w_mute));
        }

        if (due) {
            String month = new SimpleDateFormat("MMMM", Locale.getDefault()).format(Calendar.getInstance().getTime());
            v.setTextViewText(R.id.w_due, "Time to enter " + month + " ›");
        }
        v.setViewVisibility(R.id.w_due, due ? View.VISIBLE : View.GONE);

        int gp = d.optInt("goalPct", -1);
        if (gp >= 0) {
            v.setTextViewText(R.id.w_goal, "🎯 " + d.optString("goal") + " · " + gp + "%");
            v.setProgressBar(R.id.w_bar, 100, gp, false);
        }
        v.setViewVisibility(R.id.w_goalrow, gp >= 0 ? View.VISIBLE : View.GONE);

        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (due) open.putExtra(MainActivity.EXTRA_PAGE, "update");
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 3, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return v;
    }
}
