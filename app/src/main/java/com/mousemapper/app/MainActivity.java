package com.mousemapper.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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

import com.mousemapper.app.model.Profile;
import com.mousemapper.app.ui.Ui;

import java.util.List;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private static final int SHIZUKU_REQUEST = 11;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView shizukuStatus;
    private Button shizukuButton;
    private TextView overlayStatus;
    private Button overlayButton;
    private Spinner profileSpinner;
    private Button startButton;
    private TextView runStatus;
    private boolean updatingSpinner;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, 1000);
        }
    };

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> refresh();
    private final Shizuku.OnBinderReceivedListener binderReceived = this::refresh;
    private final Shizuku.OnBinderDeadListener binderDead = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        setContentView(buildUi());
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderReceived);
        Shizuku.addBinderDeadListener(binderDead);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 3);
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderReceived);
        Shizuku.removeBinderDeadListener(binderDead);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    // ---------- UI ----------

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 18);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = Ui.text(this, "🎮 محاكي ماوس", 26, Ui.TEXT, true);
        root.addView(title);
        root.addView(Ui.text(this, "العب ألعاب الهاتف بالماوس والكيبورد", 15, Ui.MUTED, false), Ui.matchWrap(this, 2));

        // Step 1: Shizuku
        LinearLayout c1 = Ui.card(this);
        c1.addView(Ui.text(this, "1. تطبيق Shizuku", 17, Ui.TEXT, true));
        shizukuStatus = Ui.text(this, "", 14, Ui.MUTED, false);
        c1.addView(shizukuStatus, Ui.matchWrap(this, 6));
        shizukuButton = Ui.button(this, "", Ui.ACCENT);
        shizukuButton.setOnClickListener(v -> onShizukuButton());
        c1.addView(shizukuButton, Ui.matchWrap(this, 10));
        root.addView(c1, Ui.matchWrap(this, 18));

        // Step 2: overlay permission
        LinearLayout c2 = Ui.card(this);
        c2.addView(Ui.text(this, "2. الظهور فوق التطبيقات", 17, Ui.TEXT, true));
        overlayStatus = Ui.text(this, "", 14, Ui.MUTED, false);
        c2.addView(overlayStatus, Ui.matchWrap(this, 6));
        overlayButton = Ui.button(this, "منح الإذن", Ui.ACCENT);
        overlayButton.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        c2.addView(overlayButton, Ui.matchWrap(this, 10));
        root.addView(c2, Ui.matchWrap(this, 12));

        // Step 3: profile
        LinearLayout c3 = Ui.card(this);
        c3.addView(Ui.text(this, "3. ملف الأزرار", 17, Ui.TEXT, true));
        c3.addView(Ui.text(this, "اصنع ملفاً لكل لعبة. أماكن الأزرار تُعدَّل من فوق اللعبة نفسها.", 14, Ui.MUTED, false),
                Ui.matchWrap(this, 6));
        profileSpinner = new Spinner(this);
        profileSpinner.setBackground(Ui.rounded(Ui.NEUTRAL, Ui.dp(this, 12)));
        profileSpinner.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        profileSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (updatingSpinner) return;
                ProfileStore.setActive(MainActivity.this, (String) parent.getItemAtPosition(position));
                OverlayService.send(MainActivity.this, OverlayService.ACTION_RELOAD);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        c3.addView(profileSpinner, Ui.matchWrap(this, 10));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button add = Ui.button(this, "ملف جديد", Ui.NEUTRAL);
        add.setOnClickListener(v -> newProfileDialog());
        Button del = Ui.button(this, "حذف", Ui.DANGER);
        del.setOnClickListener(v -> deleteProfile());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.setMarginEnd(Ui.dp(this, 8));
        row.addView(add, half);
        row.addView(del, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        c3.addView(row, Ui.matchWrap(this, 10));
        root.addView(c3, Ui.matchWrap(this, 12));

        // Step 4: start
        startButton = Ui.button(this, "", Ui.ACCENT);
        startButton.setTextSize(18);
        startButton.setPadding(0, Ui.dp(this, 16), 0, Ui.dp(this, 16));
        startButton.setOnClickListener(v -> onStartButton());
        root.addView(startButton, Ui.matchWrap(this, 18));
        runStatus = Ui.text(this, "", 14, Ui.MUTED, false);
        runStatus.setGravity(Gravity.CENTER);
        root.addView(runStatus, Ui.matchWrap(this, 8));

        // Help
        LinearLayout help = Ui.card(this);
        help.addView(Ui.text(this, "طريقة الاستخدام", 17, Ui.TEXT, true));
        help.addView(Ui.text(this,
                "• صِل الماوس والكيبورد (بلوتوث أو OTG).\n"
                        + "• اضغط «تشغيل» ثم افتح اللعبة. ستظهر فقاعة 🎮.\n"
                        + "• أول مرة: اضغط الفقاعة ← «تعديل أماكن الأزرار»، واسحب كل دائرة فوق زر اللعبة المناسب، ثم «حفظ».\n"
                        + "• اضغط زر التبديل (افتراضياً ` أو ذ) للدخول في وضع اللعب: الماوس يحرّك الكاميرا، وWASD للحركة.\n"
                        + "• اضغطه مرة أخرى لاستعادة مؤشر الماوس العادي (للقوائم والدردشة).\n"
                        + "• لون الفقاعة: أخضر = وضع اللعب، رمادي = وضع المؤشر، أحمر = غير متصل.\n\n"
                        + "⚠ بعض الألعاب قد تكشف استخدام الماوس وتعاقب الحساب. جرّب على حساب ثانوي أولاً.",
                14, Ui.MUTED, false), Ui.matchWrap(this, 8));
        root.addView(help, Ui.matchWrap(this, 18));
        return scroll;
    }

    // ---------- state ----------

    private boolean shizukuInstalled() {
        try {
            getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean shizukuRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean shizukuGranted() {
        try {
            return shizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    private void refresh() {
        if (shizukuStatus == null) return;
        if (!shizukuInstalled()) {
            shizukuStatus.setText("غير مثبت. Shizuku تطبيق مجاني يعطي هذا التطبيق صلاحية محاكاة اللمس بدون روت.");
            shizukuButton.setText("تحميل Shizuku");
            shizukuButton.setVisibility(View.VISIBLE);
        } else if (!shizukuRunning()) {
            shizukuStatus.setText("مثبت لكنه متوقف. افتحه واضغط «Start» عبر تصحيح الأخطاء اللاسلكي (Wireless debugging). يجب تشغيله من جديد بعد كل إعادة تشغيل للجهاز.");
            shizukuButton.setText("فتح Shizuku");
            shizukuButton.setVisibility(View.VISIBLE);
        } else if (!shizukuGranted()) {
            shizukuStatus.setText("Shizuku يعمل ✓ — بقي منح الإذن لهذا التطبيق.");
            shizukuButton.setText("منح إذن Shizuku");
            shizukuButton.setVisibility(View.VISIBLE);
        } else {
            shizukuStatus.setText("جاهز ✓");
            shizukuStatus.setTextColor(Ui.ACCENT);
            shizukuButton.setVisibility(View.GONE);
        }
        if (!shizukuGranted()) shizukuStatus.setTextColor(Ui.WARN);

        boolean overlay = Settings.canDrawOverlays(this);
        overlayStatus.setText(overlay ? "مسموح ✓" : "مطلوب لإظهار الفقاعة ومحرر الأزرار فوق اللعبة.");
        overlayStatus.setTextColor(overlay ? Ui.ACCENT : Ui.WARN);
        overlayButton.setVisibility(overlay ? View.GONE : View.VISIBLE);

        boolean running = OverlayService.running;
        startButton.setText(running ? "إيقاف" : "تشغيل");
        startButton.setBackground(Ui.rounded(running ? Ui.DANGER : Ui.ACCENT, Ui.dp(this, 14)));
        startButton.setTextColor(running ? Ui.TEXT : 0xFF0B0E11);
        if (!running) runStatus.setText("متوقف");
        else if (!OverlayService.connected) runStatus.setText("جارٍ الاتصال بـ Shizuku…");
        else if (OverlayService.gameMode) runStatus.setText("وضع اللعب مفعّل 🎯");
        else runStatus.setText("يعمل — افتح اللعبة واضغط زر التبديل");
    }

    private void onShizukuButton() {
        if (!shizukuInstalled()) {
            openUrl("https://play.google.com/store/apps/details?id=" + SHIZUKU_PACKAGE);
        } else if (!shizukuRunning()) {
            Intent i = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (i != null) startActivity(i);
        } else if (!shizukuGranted()) {
            try {
                Shizuku.requestPermission(SHIZUKU_REQUEST);
            } catch (Throwable t) {
                toast("تعذّر طلب الإذن: " + t.getMessage());
            }
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast(url);
        }
    }

    private void onStartButton() {
        if (OverlayService.running) {
            startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP));
            handler.postDelayed(this::refresh, 300);
            return;
        }
        if (!shizukuGranted()) {
            toast("أكمل الخطوة 1 (Shizuku) أولاً");
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            toast("أكمل الخطوة 2 (الظهور فوق التطبيقات) أولاً");
            return;
        }
        startForegroundService(new Intent(this, OverlayService.class));
        handler.postDelayed(this::refresh, 300);
    }

    // ---------- profiles ----------

    private void loadProfiles() {
        List<String> names = ProfileStore.names(this);
        String active = ProfileStore.activeName(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        updatingSpinner = true;
        profileSpinner.setAdapter(adapter);
        int idx = names.indexOf(active);
        if (idx >= 0) profileSpinner.setSelection(idx, false);
        profileSpinner.post(() -> updatingSpinner = false);
    }

    private void newProfileDialog() {
        final EditText input = new EditText(this);
        input.setHint("مثال: PUBG أو Free Fire");
        int p = Ui.dp(this, 20);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(p, p / 2, p, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle("اسم الملف الجديد")
                .setView(box)
                .setPositiveButton("إنشاء", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (ProfileStore.exists(this, name)) {
                        toast("يوجد ملف بهذا الاسم");
                        return;
                    }
                    ProfileStore.save(this, Profile.defaultShooter(name));
                    ProfileStore.setActive(this, name);
                    loadProfiles();
                    OverlayService.send(this, OverlayService.ACTION_RELOAD);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void deleteProfile() {
        List<String> names = ProfileStore.names(this);
        if (names.size() <= 1) {
            toast("لا يمكن حذف الملف الوحيد");
            return;
        }
        final String name = ProfileStore.activeName(this);
        new AlertDialog.Builder(this)
                .setTitle("حذف «" + name + "»؟")
                .setPositiveButton("حذف", (d, w) -> {
                    ProfileStore.delete(this, name);
                    List<String> left = ProfileStore.names(this);
                    ProfileStore.setActive(this, left.get(0));
                    loadProfiles();
                    OverlayService.send(this, OverlayService.ACTION_RELOAD);
                })
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
