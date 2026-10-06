package com.mendhak.gpslogger;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 保活重启后推送 HTTP GET 通知（例如 pushplus / Server酱）
 */
public class KeepAliveNotifier {

    private static final String TAG = "KeepAliveNotifier";
    private static final String PREFS_NAME = "keepalive_stats";
    private static final String KEY_NOTIFY_URL = "notify_url";

    public static String getUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_NOTIFY_URL, "");
    }

    public static void setUrl(Context context, String url) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_NOTIFY_URL, url == null ? "" : url.trim()).apply();
    }

    /**
     * 后台线程发送 GET 请求，不阻塞调用方
     */
    public static void notifyRestartAsync(Context context, int restartCount) {
        String url = getUrl(context);
        if (url == null || url.isEmpty()) {
            return;
        }
        // 把重启次数拼到 URL 上，方便模板使用（如果 URL 已有 restart 参数则不重复加）
        final String baseUrl;
        if (url.contains("restartCount") || url.contains("restart_count")) {
            baseUrl = url;
        } else {
            baseUrl = url + (url.contains("?") ? "&" : "?") + "restartCount=" + restartCount;
        }
        final String finalUrl = encodeUrl(baseUrl);

        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                int code = conn.getResponseCode();
                // 读掉响应体，避免连接泄漏
                try (InputStream is = conn.getInputStream()) {
                    byte[] buf = new byte[1024];
                    while (is.read(buf) != -1) { /* drain */ }
                } catch (Exception ignored) { }
                Log.i(TAG, "Notify sent, response code: " + code);
            } catch (Exception e) {
                Log.w(TAG, "Notify failed: " + e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    /**
     * 对 URL 的 query 参数做百分号编码（中文标题内容常见）
     */
    private static String encodeUrl(String url) {
        try {
            int qIndex = url.indexOf('?');
            if (qIndex < 0) {
                return url;
            }
            String base = url.substring(0, qIndex);
            String query = url.substring(qIndex + 1);
            StringBuilder sb = new StringBuilder(base).append('?');
            String[] pairs = query.split("&");
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                if (eq < 0) {
                    sb.append(encodeComponent(pair));
                } else {
                    sb.append(encodeComponent(pair.substring(0, eq)));
                    sb.append('=');
                    sb.append(encodeComponent(pair.substring(eq + 1)));
                }
                if (i < pairs.length - 1) {
                    sb.append('&');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "URL encode failed, using raw: " + e.getMessage());
            return url;
        }
    }

    private static String encodeComponent(String s) {
        try {
            // URLEncoder 会把空格编成 +，对 query 参数是合法的；但为避免二次编码，先判断
            String encoded = java.net.URLEncoder.encode(s, "UTF-8");
            // 已经是 %XX 形式的不要重复编码
            return encoded.replace("%25", "%");
        } catch (Exception e) {
            return s;
        }
    }

    /**
     * 同步发送一次，用于测试按钮（调用方需在后台线程调用）
     * @return 成功返回 null，失败返回错误信息
     */
    public static String notifyTestSync(String url) {
        HttpURLConnection conn = null;
        try {
            String encodedUrl = encodeUrl(url);
            Log.i(TAG, "Test notify URL: " + encodedUrl);
            conn = (HttpURLConnection) new URL(encodedUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            int code = conn.getResponseCode();
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[1024];
                while (is.read(buf) != -1) { /* drain */ }
            } catch (Exception ignored) { }
            return code >= 200 && code < 300 ? null : "HTTP " + code;
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
