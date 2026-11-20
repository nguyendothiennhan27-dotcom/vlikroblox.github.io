package com.norbench.app;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Vỏ mỏng: WebView tải giao diện nor-Bench trong assets, có màn hình chào và cầu nối NorNative. */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 7;
    private static final int BG = Color.parseColor("#060814");
    private static final int ACCENT = Color.parseColor("#38A8FF");   // màu vòng xoay (xanh của logo)
    private static final int TAG_COLOR = Color.parseColor("#8FA3C0");
    private static final long MIN_SPLASH_MS = 1200;                    // thời gian chào tối thiểu
    private static final long MAX_SPLASH_MS = 6000;                    // quá lâu thì vẫn vào app

    private final Handler ui = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView web;
    private View splash;
    private ValueCallback<Uri[]> fileCb;
    private boolean pageReady = false;
    private boolean splashGone = false;
    private long splashStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        splash = buildSplash();
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        splashStart = SystemClock.uptimeMillis();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        web.addJavascriptInterface(new NorBridge(this), "NorNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                hideSplash();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });

        // Chốt chặn: dù trang chậm thế nào cũng không kẹt ở màn hình chào.
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                pageReady = true;
                hideSplash();
            }
        }, MAX_SPLASH_MS);

        web.loadUrl("file:///android_asset/index.html");
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    /** Màn hình chào: logo + dòng mô tả ở giữa, vòng xoay nhỏ ở dưới. */
    private View buildSplash() {
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(BG);
        f.setClickable(true);

        int sw = getResources().getDisplayMetrics().widthPixels;

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        final ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.splash_logo);
        logo.setAdjustViewBounds(true);
        col.addView(logo, new LinearLayout.LayoutParams(
                Math.round(sw * 0.62f), ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView tag = new TextView(this);
        tag.setText(R.string.splash_tagline);
        tag.setTextColor(TAG_COLOR);
        tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tag.setLetterSpacing(0.06f);
        tag.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.topMargin = dp(12);
        col.addView(tag, tp);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.CENTER;
        cp.bottomMargin = dp(28);
        f.addView(col, cp);

        final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(30), dp(30));
        pp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pp.bottomMargin = dp(72);
        f.addView(pb, pp);

        // Hoạt ảnh vào: logo hiện mờ dần và phóng nhẹ, mô tả và vòng xoay hiện sau.
        logo.setAlpha(0f);
        logo.setScaleX(0.92f);
        logo.setScaleY(0.92f);
        tag.setAlpha(0f);
        pb.setAlpha(0f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start();
        tag.animate().alpha(1f).setStartDelay(250).setDuration(400).start();
        pb.animate().alpha(1f).setStartDelay(400).setDuration(400).start();
        return f;
    }

    /** Ẩn màn hình chào: chờ đủ thời gian tối thiểu rồi mờ dần để lộ app. */
    private void hideSplash() {
        if (splashGone || !pageReady || splash == null) return;
        splashGone = true;
        long wait = Math.max(0L, MIN_SPLASH_MS - (SystemClock.uptimeMillis() - splashStart));
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                splash.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        root.removeView(splash);
                        splash = null;
                    }
                }).start();
            }
        }, wait);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}
