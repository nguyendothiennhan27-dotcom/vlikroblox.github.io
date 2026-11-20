package com.norbench.app;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.OutputStream;
import java.util.Arrays;

/** Cầu nối JS -> Android. Mọi hàm trả chuỗi JSON để giao diện HTML đọc bằng JSON.parse. */
public class NorBridge {

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long[] maxFreq;          // kHz mỗi nhân, đọc một lần
    private File[] zones;            // /sys/class/thermal/thermal_zone*

    public NorBridge(Context c) {
        ctx = c.getApplicationContext();
    }

    /** Thông tin máy, gọi một lần khi mở app. */
    @JavascriptInterface
    public String info() {
        try {
            JSONObject o = new JSONObject();
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("brand", Build.BRAND);
            o.put("model", Build.MODEL);
            o.put("device", Build.DEVICE);
            o.put("hardware", Build.HARDWARE);
            o.put("board", Build.BOARD);
            o.put("android", Build.VERSION.RELEASE);
            o.put("sdk", Build.VERSION.SDK_INT);
            if (Build.VERSION.SDK_INT >= 31) {
                o.put("soc", Build.SOC_MODEL);
                o.put("socMaker", Build.SOC_MANUFACTURER);
            }
            o.put("abis", new JSONArray(Arrays.asList(Build.SUPPORTED_ABIS)));
            o.put("cores", Runtime.getRuntime().availableProcessors());
            ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            o.put("ramMB", mi.totalMem / (1024 * 1024));
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Mẫu theo giây: nhiệt pin (t), nhiệt CPU nếu đọc được (c), mức nóng của hệ điều hành (th),
     * dư địa nhiệt (hr), xung hiện tại (f) và xung tối đa (fm) mỗi nhân, đơn vị kHz. -1 là không đọc được.
     */
    @JavascriptInterface
    public String sample() {
        try {
            JSONObject o = new JSONObject();

            Intent b = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b != null) {
                int t = b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (t > 0) o.put("t", t / 10.0);
                o.put("lv", b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1));
                o.put("plug", b.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
            }

            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            o.put("th", pm.getCurrentThermalStatus());
            if (Build.VERSION.SDK_INT >= 30) {
                float h = pm.getThermalHeadroom(10);
                if (!Float.isNaN(h)) o.put("hr", (double) h);
            }

            int n = Runtime.getRuntime().availableProcessors();
            if (maxFreq == null) {
                maxFreq = new long[n];
                for (int i = 0; i < n; i++) {
                    maxFreq[i] = readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
                }
            }
            JSONArray f = new JSONArray();
            JSONArray m = new JSONArray();
            for (int i = 0; i < n; i++) {
                f.put(readLong("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq"));
                m.put(maxFreq[i]);
            }
            o.put("f", f);
            o.put("fm", m);

            double c = cpuZoneTemp();
            if (c > 0) o.put("c", c);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    /** Lớn nhất trong các vùng nhiệt có tên chứa cpu, soc hoặc cluster. Phần lớn máy mới chặn đọc, khi đó trả 0. */
    private double cpuZoneTemp() {
        if (zones == null) {
            File[] all = new File("/sys/class/thermal").listFiles();
            if (all == null) {
                zones = new File[0];
            } else {
                java.util.ArrayList<File> keep = new java.util.ArrayList<>();
                for (File z : all) {
                    if (!z.getName().startsWith("thermal_zone")) continue;
                    String type = readLine(new File(z, "type")).toLowerCase();
                    if (type.contains("cpu") || type.contains("soc") || type.contains("cluster")) keep.add(new File(z, "temp"));
                }
                zones = keep.toArray(new File[0]);
            }
        }
        double best = 0;
        for (File z : zones) {
            String s = readLine(z).trim();
            if (s.isEmpty()) continue;
            try {
                double v = Double.parseDouble(s);
                if (v > 1000) v /= 1000.0;
                if (v > 0 && v < 150 && v > best) best = v;
            } catch (NumberFormatException ignored) { }
        }
        return best;
    }

    private static long readLong(String path) {
        String s = readLine(new File(path)).trim();
        if (s.isEmpty()) return -1;
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String readLine(File f) {
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String s = r.readLine();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    @JavascriptInterface
    public void copy(final String text) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("nor-Bench", text));
                Toast.makeText(ctx, "Đã sao chép", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Lưu tệp vào Downloads/nor-Bench (MediaStore, không cần quyền lưu trữ). Dữ liệu truyền sang dạng base64. */
    @JavascriptInterface
    public void saveFile(final String name, final String mime, final String b64) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    byte[] data = Base64.decode(b64, Base64.DEFAULT);
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/nor-Bench");
                    Uri u = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (u == null) throw new Exception("insert");
                    try (OutputStream os = ctx.getContentResolver().openOutputStream(u)) {
                        os.write(data);
                    }
                    msg = "Đã lưu: Downloads/nor-Bench/" + name;
                } catch (Exception e) {
                    msg = "Không lưu được: " + e.getMessage();
                }
                final String m = msg;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ctx, m, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
