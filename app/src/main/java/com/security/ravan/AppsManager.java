package com.security.ravan;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.util.Date;
import java.util.List;

/**
 * Apps Manager - List all installed applications with details
 * Ported from BTMOB Apps_Manage.java, adapted for Ravan HTTP server
 */
public class AppsManager {

    /**
     * Get HTML for all installed apps
     */
    public static String getInstalledAppsHtml(Context context, String searchQuery) {
        StringBuilder html = new StringBuilder();

        try {
            PackageManager pm = context.getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            int userApps = 0;
            int systemApps = 0;
            int totalShown = 0;

            // Count totals
            for (ApplicationInfo app : apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) != null) {
                    if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        systemApps++;
                    } else {
                        userApps++;
                    }
                }
            }

            html.append("<div style=\"display: flex; gap: 15px; margin-bottom: 20px; flex-wrap: wrap;\">");
            html.append(
                    "<div style=\"padding: 12px 20px; background: rgba(46, 204, 113, 0.1); border-radius: 10px; border-left: 3px solid #2ecc71;\">");
            html.append("<span style=\"color: #888; font-size: 0.8rem;\">User Apps</span>");
            html.append("<div style=\"font-size: 1.3rem; font-weight: bold; color: #2ecc71;\">").append(userApps)
                    .append("</div>");
            html.append("</div>");
            html.append(
                    "<div style=\"padding: 12px 20px; background: rgba(52, 152, 219, 0.1); border-radius: 10px; border-left: 3px solid #3498db;\">");
            html.append("<span style=\"color: #888; font-size: 0.8rem;\">System Apps</span>");
            html.append("<div style=\"font-size: 1.3rem; font-weight: bold; color: #3498db;\">").append(systemApps)
                    .append("</div>");
            html.append("</div>");
            html.append(
                    "<div style=\"padding: 12px 20px; background: rgba(155, 89, 182, 0.1); border-radius: 10px; border-left: 3px solid #9b59b6;\">");
            html.append("<span style=\"color: #888; font-size: 0.8rem;\">Total</span>");
            html.append("<div style=\"font-size: 1.3rem; font-weight: bold; color: #9b59b6;\">")
                    .append(userApps + systemApps).append("</div>");
            html.append("</div>");
            html.append("</div>");

            // Search form
            html.append("<form method=\"get\" action=\"/apps\" style=\"margin-bottom: 20px;\">");
            html.append("<div style=\"display: flex; gap: 10px;\">");
            html.append("<input type=\"text\" name=\"search\" placeholder=\"Search apps...\" value=\"")
                    .append(searchQuery != null ? escapeHtml(searchQuery) : "").append("\" ");
            html.append(
                    "style=\"flex: 1; padding: 12px 16px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.2); border-radius: 10px; color: #fff; font-size: 0.9rem; outline: none;\">");
            html.append(
                    "<button type=\"submit\" style=\"padding: 12px 24px; background: linear-gradient(135deg, #e94560, #ff6b6b); border: none; border-radius: 10px; color: #fff; cursor: pointer; font-size: 0.9rem;\">Search</button>");
            html.append("</div></form>");

            // Apps grid
            html.append(
                    "<div style=\"display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px;\">");

            for (ApplicationInfo app : apps) {
                if (pm.getLaunchIntentForPackage(app.packageName) == null)
                    continue;

                String appName = pm.getApplicationLabel(app).toString();
                String packageName = app.packageName;

                // Search filter
                if (searchQuery != null && !searchQuery.isEmpty()) {
                    if (!appName.toLowerCase().contains(searchQuery.toLowerCase()) &&
                            !packageName.toLowerCase().contains(searchQuery.toLowerCase())) {
                        continue;
                    }
                }

                String flag;
                String flagColor;
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    flag = "System";
                    flagColor = "#3498db";
                } else {
                    flag = "User";
                    flagColor = "#2ecc71";
                }

                String installDate = "Unknown";
                try {
                    PackageInfo pInfo = pm.getPackageInfo(packageName, 0);
                    installDate = new java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            .format(new Date(pInfo.firstInstallTime));
                } catch (Exception e) {
                }

                // Get app icon as base64
                String iconBase64 = getAppIconBase64(pm, packageName);

                html.append(
                        "<div style=\"padding: 15px; background: rgba(255,255,255,0.03); border-radius: 12px; border: 1px solid rgba(255,255,255,0.08); display: flex; align-items: center; gap: 12px; transition: all 0.3s ease;\" onmouseover=\"this.style.borderColor='rgba(233,69,96,0.3)'; this.style.background='rgba(255,255,255,0.06)'\" onmouseout=\"this.style.borderColor='rgba(255,255,255,0.08)'; this.style.background='rgba(255,255,255,0.03)'\">");

                // App icon
                if (iconBase64 != null) {
                    html.append("<img src=\"data:image/png;base64,").append(iconBase64)
                            .append("\" style=\"width: 40px; height: 40px; border-radius: 10px;\" />");
                } else {
                    html.append(
                            "<div style=\"width: 40px; height: 40px; border-radius: 10px; background: linear-gradient(135deg, #667eea, #764ba2); display: flex; align-items: center; justify-content: center; font-size: 1.2rem;\">&#128187;</div>");
                }

                html.append("<div style=\"flex: 1; min-width: 0;\">");
                html.append(
                        "<div style=\"font-weight: 600; color: #fff; font-size: 0.9rem; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;\">")
                        .append(escapeHtml(appName)).append("</div>");
                html.append(
                        "<div style=\"font-size: 0.75rem; color: #888; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;\">")
                        .append(escapeHtml(packageName)).append("</div>");
                html.append("<div style=\"font-size: 0.7rem; color: #666; margin-top: 2px;\">").append(installDate)
                        .append("</div>");
                html.append("</div>");

                html.append("<span style=\"padding: 4px 10px; background: rgba(")
                        .append(flagColor.equals("#2ecc71") ? "46,204,113" : "52,152,219").append(",0.15); color: ")
                        .append(flagColor).append("; border-radius: 6px; font-size: 0.7rem; font-weight: 600;\">")
                        .append(flag).append("</span>");

                html.append("</div>");
                totalShown++;
            }

            html.append("</div>");

            if (totalShown == 0) {
                html.append(
                        "<div class=\"empty-state\"><div class=\"icon\">&#128270;</div><p>No apps found matching your search</p></div>");
            }

        } catch (Exception e) {
            html.append("<div class=\"empty-state\"><div class=\"icon\">&#9888;</div><p>Error loading apps: ")
                    .append(escapeHtml(e.getMessage())).append("</p></div>");
        }

        return html.toString();
    }

    /**
     * Get app icon as Base64 encoded string
     */
    private static String getAppIconBase64(PackageManager pm, String packageName) {
        try {
            Drawable icon = pm.getApplicationIcon(packageName);
            Bitmap bitmap = drawableToBitmap(icon);
            Bitmap resized = Bitmap.createScaledBitmap(bitmap, 40, 40, true);
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            resized.compress(Bitmap.CompressFormat.PNG, 70, stream);
            byte[] byteArray = stream.toByteArray();
            return Base64.encodeToString(byteArray, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convert Drawable to Bitmap
     */
    private static Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) {
            Bitmap bmp = ((BitmapDrawable) drawable).getBitmap();
            if (bmp != null)
                return bmp;
        }

        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0)
            width = 40;
        if (height <= 0)
            height = 40;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
