package com.mousemapper.app;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mousemapper.app.model.Profile;
import com.mousemapper.app.model.ProfileJson;
import com.mousemapper.app.service.InputService;
import com.mousemapper.app.ui.EditorView;
import com.mousemapper.app.ui.Ui;

import rikka.shizuku.Shizuku;

/** Foreground service: floating bubble, layout editor, and the link to the Shizuku-side InputService. */
public class OverlayService extends Service {
    public static final String ACTION_STOP = "com.mousemapper.app.STOP";
    public static final String ACTION_EDIT = "com.mousemapper.app.EDIT";
    public static final String ACTION_RELOAD = "com.mousemapper.app.RELOAD";
    private static final String CHANNEL = "mapper";
    private static final int NOTIFICATION_ID = 7;

    public static volatile boolean running;
    public static volatile boolean connected;
    public static volatile boolean gameMode;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleParams;
    private View menu;
    private EditorView editor;
    private volatile IInputService remote;
    private boolean bound;

    private Shizuku.UserServiceArgs serviceArgs;

    private final IStateCallback callback = new IStateCallback.Stub() {
        @Override
        public void onModeChanged(boolean on) {
            gameMode = on;
            main.post(OverlayService.this::refreshBubble);
        }

        @Override
        public void onMessage(String message) {
            main.post(() -> toast(message));
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) {
                toast("تعذّر تشغيل خدمة التحويل");
                return;
            }
            remote = IInputService.Stub.asInterface(binder);
            try {
                String err = remote.start(buildConfig(), callback);
                if (err != null && !err.isEmpty()) {
                    toast(err);
                    return;
                }
                connected = true;
                toast("جاهز ✓ اضغط " + ProfileStore.active(OverlayService.this).toggleLabel + " لبدء وضع اللعب");
            } catch (Exception e) {
                toast("خطأ: " + e);
            }
            refreshBubble();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            remote = null;
            connected = false;
            gameMode = false;
            main.post(OverlayService.this::refreshBubble);
        }
    };

    private final Shizuku.OnBinderDeadListener binderDead = () -> {
        toast("توقف Shizuku. أعد تشغيله ثم شغّل التطبيق من جديد.");
        stopSelf();
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), InputService.class.getName()))
                .daemon(false)
                .processNameSuffix("mapper")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
        startInForeground();
        showBubble();
        Shizuku.addBinderDeadListener(binderDead);
        bindInputService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopSelf();
        } else if (ACTION_EDIT.equals(action)) {
            openEditor();
        } else if (ACTION_RELOAD.equals(action)) {
            pushConfig();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        connected = false;
        gameMode = false;
        Shizuku.removeBinderDeadListener(binderDead);
        IInputService r = remote;
        if (r != null) {
            try {
                r.stop();
            } catch (Exception ignored) {
                // Service already gone.
            }
        }
        if (bound) {
            try {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            } catch (Exception ignored) {
                // Shizuku already gone.
            }
        }
        removeView(menu);
        removeView(editor);
        removeView(bubble);
        super.onDestroy();
    }

    // ---------- Shizuku ----------

    private void bindInputService() {
        try {
            if (!Shizuku.pingBinder()) {
                toast("Shizuku غير مشغّل");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                toast("امنح التطبيق صلاحية Shizuku أولاً");
                return;
            }
            Shizuku.bindUserService(serviceArgs, connection);
            bound = true;
        } catch (Throwable t) {
            toast("تعذّر الاتصال بـ Shizuku: " + t.getMessage());
        }
    }

    private String buildConfig() throws Exception {
        Point p = new Point();
        //noinspection deprecation
        wm.getDefaultDisplay().getRealSize(p);
        int w = Math.max(p.x, p.y);
        int h = Math.min(p.x, p.y);
        Profile profile = ProfileStore.active(this);
        return ProfileJson.serviceConfig(profile, w, h, getApplicationInfo().nativeLibraryDir);
    }

    private void pushConfig() {
        IInputService r = remote;
        if (r == null) return;
        try {
            r.updateConfig(buildConfig());
        } catch (Exception e) {
            toast("خطأ: " + e);
        }
    }

    private void setGameMode(boolean on) {
        IInputService r = remote;
        if (r == null) {
            toast("الخدمة غير متصلة بعد");
            return;
        }
        try {
            r.setGameMode(on);
        } catch (Exception e) {
            toast("خطأ: " + e);
        }
    }

    // ---------- notification ----------

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "محاكي ماوس", NotificationManager.IMPORTANCE_LOW));
        Intent stop = new Intent(this, OverlayService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("محاكي ماوس يعمل")
                .setContentText("اضغط زر التبديل للتنقل بين وضع اللعب ووضع المؤشر")
                .setContentIntent(openPi)
                .addAction(new Notification.Action.Builder(null, "إيقاف", stopPi).build())
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    // ---------- bubble ----------

    private WindowManager.LayoutParams overlayParams(int w, int h, boolean focusable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!focusable) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBubble() {
        int size = Ui.dp(this, 50);
        bubble = new TextView(this);
        bubble.setText("🎮");
        bubble.setTextSize(22);
        bubble.setGravity(Gravity.CENTER);
        bubbleParams = overlayParams(size, size, false);
        bubbleParams.x = Ui.dp(this, 8);
        bubbleParams.y = Ui.dp(this, 120);
        refreshBubble();
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            float startY;
            int origX;
            int origY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX();
                        startY = e.getRawY();
                        origX = bubbleParams.x;
                        origY = bubbleParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startX;
                        float dy = e.getRawY() - startY;
                        if (Math.abs(dx) + Math.abs(dy) > Ui.dp(OverlayService.this, 6)) moved = true;
                        if (moved) {
                            bubbleParams.x = origX + (int) dx;
                            bubbleParams.y = origY + (int) dy;
                            wm.updateViewLayout(bubble, bubbleParams);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) toggleMenu();
                        return true;
                    default:
                        return true;
                }
            }
        });
        wm.addView(bubble, bubbleParams);
    }

    private void refreshBubble() {
        if (bubble == null) return;
        int color = !connected ? Ui.DANGER : gameMode ? Ui.ACCENT : Ui.NEUTRAL;
        bubble.setBackground(Ui.rounded(color, Ui.dp(this, 25)));
        bubble.setAlpha(gameMode ? 0.55f : 0.9f);
    }

    private void toggleMenu() {
        if (menu != null) {
            removeView(menu);
            menu = null;
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.rounded(Ui.CARD, Ui.dp(this, 14)));
        int p = Ui.dp(this, 10);
        box.setPadding(p, p, p, p);
        addMenuButton(box, gameMode ? "إيقاف وضع اللعب" : "تشغيل وضع اللعب", Ui.ACCENT, () -> setGameMode(!gameMode));
        addMenuButton(box, "تعديل أماكن الأزرار", Ui.WARN, this::openEditor);
        addMenuButton(box, "الأجهزة المتصلة", Ui.NEUTRAL, this::showDevices);
        addMenuButton(box, "إغلاق التطبيق", Ui.DANGER, this::stopSelf);
        WindowManager.LayoutParams lp = overlayParams(Ui.dp(this, 210),
                WindowManager.LayoutParams.WRAP_CONTENT, false);
        lp.x = bubbleParams.x + Ui.dp(this, 56);
        lp.y = bubbleParams.y;
        menu = box;
        wm.addView(menu, lp);
    }

    private void addMenuButton(LinearLayout box, String text, int color, Runnable action) {
        Button b = Ui.button(this, text, color);
        b.setTextSize(14);
        b.setOnClickListener(v -> {
            removeView(menu);
            menu = null;
            action.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 44));
        lp.topMargin = Ui.dp(this, 4);
        box.addView(b, lp);
    }

    private void showDevices() {
        IInputService r = remote;
        if (r == null) {
            toast("الخدمة غير متصلة بعد");
            return;
        }
        try {
            toast(r.listDevices());
        } catch (Exception e) {
            toast("خطأ: " + e);
        }
    }

    // ---------- editor ----------

    private void openEditor() {
        if (editor != null) return;
        removeView(menu);
        menu = null;
        setPaused(true);
        Profile profile = ProfileStore.active(this);
        editor = new EditorView(this, profile, new EditorView.Listener() {
            @Override
            public void onSave(Profile p) {
                if (ProfileStore.save(OverlayService.this, p)) toast("تم الحفظ ✓");
                else toast("تعذّر الحفظ");
                closeEditor();
                pushConfig();
            }

            @Override
            public void onCancel() {
                closeEditor();
            }
        });
        WindowManager.LayoutParams lp = overlayParams(WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT, true);
        if (bubble != null) bubble.setVisibility(View.GONE);
        wm.addView(editor, lp);
        editor.requestFocus();
        toast("اسحب الدوائر إلى أماكن الأزرار في اللعبة، واضغط على أي دائرة لتغيير مفتاحها");
    }

    private void closeEditor() {
        removeView(editor);
        editor = null;
        if (bubble != null) bubble.setVisibility(View.VISIBLE);
        setPaused(false);
    }

    private void setPaused(boolean paused) {
        IInputService r = remote;
        if (r == null) return;
        try {
            r.setPaused(paused);
        } catch (Exception ignored) {
            // Service gone; nothing to pause.
        }
    }

    // ---------- helpers ----------

    private void removeView(View v) {
        if (v == null) return;
        try {
            wm.removeView(v);
        } catch (Exception ignored) {
            // Already removed.
        }
    }

    private void toast(String s) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> toast(s));
            return;
        }
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    public static void send(Context c, String action) {
        Intent i = new Intent(c, OverlayService.class).setAction(action);
        if (running) c.startService(i);
    }
}
