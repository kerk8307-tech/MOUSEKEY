package com.mousemapper.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.mousemapper.app.model.Codes;
import com.mousemapper.app.model.Element;
import com.mousemapper.app.model.Profile;

import java.util.Locale;

/** Full-screen overlay drawn on top of the game for placing controls and assigning keys. */
public class EditorView extends FrameLayout {
    public interface Listener {
        void onSave(Profile profile);
        void onCancel();
    }

    private static final int ASSIGN_TAP = -1;
    private static final int ASSIGN_TOGGLE = -2;
    private static final String[] DIR_NAMES = {"أعلى", "يسار", "أسفل", "يمين"};

    private final Profile profile;
    private final Listener listener;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] loc = new int[2];
    private final int touchSlop;
    private final float tapRadius;

    private final LinearLayout panel;
    private Button toggleButton;

    private Element selected;
    private Element dragging;
    private boolean dragged;
    private float downX;
    private float downY;
    private float grabDx;
    private float grabDy;

    private Element assignTarget;
    private int assignSlot;
    private boolean assigning;

    public EditorView(Context c, Profile profile, Listener listener) {
        super(c);
        this.profile = profile;
        this.listener = listener;
        setWillNotDraw(false);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setBackgroundColor(Color.argb(90, 0, 0, 0));
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        tapRadius = Ui.dp(c, 24);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Ui.dp(c, 2));
        dashed.setStyle(Paint.Style.STROKE);
        dashed.setStrokeWidth(Ui.dp(c, 2));
        dashed.setColor(Color.parseColor("#5CC8FF"));
        dashed.setPathEffect(new DashPathEffect(new float[] {Ui.dp(c, 8), Ui.dp(c, 6)}, 0));
        label.setColor(Ui.TEXT);
        label.setTextAlign(Paint.Align.CENTER);
        label.setTextSize(Ui.dp(c, 13));
        label.setFakeBoldText(true);

        addView(buildToolbar(c), new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(Color.argb(240, 27, 32, 39), Ui.dp(c, 16)));
        int p = Ui.dp(c, 14);
        panel.setPadding(p, p, p, p);
        panel.setVisibility(GONE);
        // Swallow touches so they don't reach the canvas underneath.
        panel.setOnTouchListener((v, e) -> true);
        addView(panel, new LayoutParams(Ui.dp(c, 330), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    // ---------- toolbar ----------

    private View buildToolbar(Context c) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int p = Ui.dp(c, 6);
        row.setPadding(p, p, p, p);
        row.setBackground(Ui.rounded(Color.argb(230, 27, 32, 39), Ui.dp(c, 14)));

        addToolbarButton(row, "+ زر", Ui.NEUTRAL, v -> {
            Element e = Element.tap(0.5f, 0.5f, 0, "؟");
            profile.elements.add(e);
            selected = e;
            invalidate();
            startAssign(e, ASSIGN_TAP);
        });
        addToolbarButton(row, "+ عصا الحركة", Ui.NEUTRAL, v -> {
            if (profile.find(Element.JOYSTICK) != null) {
                toast("يوجد عصا حركة بالفعل");
                return;
            }
            Element e = Element.joystick(0.18f, 0.68f, 0.11f);
            profile.elements.add(e);
            select(e);
        });
        addToolbarButton(row, "+ كاميرا", Ui.NEUTRAL, v -> {
            if (profile.find(Element.LOOK) != null) {
                toast("توجد منطقة كاميرا بالفعل");
                return;
            }
            Element e = Element.look(0.68f, 0.42f, 0.22f, 1f);
            profile.elements.add(e);
            select(e);
        });
        toggleButton = addToolbarButton(row, toggleText(), Ui.WARN, v -> startAssign(null, ASSIGN_TOGGLE));
        addToolbarButton(row, "حفظ", Ui.ACCENT, v -> listener.onSave(profile));
        addToolbarButton(row, "إلغاء", Ui.DANGER, v -> listener.onCancel());

        HorizontalScrollView scroll = new HorizontalScrollView(c);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(c, 8);
        scroll.setLayoutParams(lp);
        return scroll;
    }

    private Button addToolbarButton(LinearLayout row, String text, int color, OnClickListener l) {
        Button b = Ui.button(getContext(), text, color);
        b.setTextSize(13);
        b.setPadding(Ui.dp(getContext(), 12), Ui.dp(getContext(), 6), Ui.dp(getContext(), 12), Ui.dp(getContext(), 6));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(getContext(), 40));
        lp.setMarginStart(Ui.dp(getContext(), 4));
        lp.setMarginEnd(Ui.dp(getContext(), 4));
        row.addView(b, lp);
        return b;
    }

    private String toggleText() {
        return "زر التبديل: " + profile.toggleLabel;
    }

    private void toast(String s) {
        Toast.makeText(getContext(), s, Toast.LENGTH_SHORT).show();
    }

    // ---------- geometry ----------

    private Point screen() {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Point p = new Point();
        //noinspection deprecation
        wm.getDefaultDisplay().getRealSize(p);
        return p;
    }

    private float sx(Element e, Point s) {
        return e.x * s.x - loc[0];
    }

    private float sy(Element e, Point s) {
        return e.y * s.y - loc[1];
    }

    private float radiusPx(Element e, Point s) {
        return e.radius * Math.min(s.x, s.y);
    }

    // ---------- drawing ----------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        getLocationOnScreen(loc);
        Point s = screen();
        for (Element e : profile.elements) {
            float x = sx(e, s);
            float y = sy(e, s);
            boolean sel = e == selected;
            stroke.setColor(sel ? Ui.WARN : Ui.ACCENT);
            stroke.setStrokeWidth(Ui.dp(getContext(), sel ? 4 : 2));
            if (e.type == Element.TAP) {
                fill.setColor(Color.argb(210, 27, 32, 39));
                canvas.drawCircle(x, y, tapRadius, fill);
                if (e.code == 0) stroke.setColor(Ui.DANGER);
                canvas.drawCircle(x, y, tapRadius, stroke);
                drawLabel(canvas, e.label, x, y);
            } else if (e.type == Element.JOYSTICK) {
                float r = radiusPx(e, s);
                fill.setColor(Color.argb(70, 47, 210, 127));
                canvas.drawCircle(x, y, r, fill);
                canvas.drawCircle(x, y, r, stroke);
                fill.setColor(Color.argb(200, 27, 32, 39));
                canvas.drawCircle(x, y, Ui.dp(getContext(), 14), fill);
                float d = r * 0.65f;
                drawLabel(canvas, e.dirLabels[0], x, y - d);
                drawLabel(canvas, e.dirLabels[1], x - d, y);
                drawLabel(canvas, e.dirLabels[2], x, y + d);
                drawLabel(canvas, e.dirLabels[3], x + d, y);
            } else if (e.type == Element.LOOK) {
                float r = radiusPx(e, s);
                fill.setColor(Color.argb(45, 92, 200, 255));
                canvas.drawCircle(x, y, r, fill);
                canvas.drawCircle(x, y, r, sel ? stroke : dashed);
                drawLabel(canvas, "كاميرا (الماوس)", x, y);
                drawLabel(canvas, String.format(Locale.US, "×%.1f", e.sens), x, y + Ui.dp(getContext(), 18));
            }
        }
    }

    private void drawLabel(Canvas canvas, String text, float x, float y) {
        if (text == null) return;
        canvas.drawText(text, x, y - (label.descent() + label.ascent()) / 2, label);
    }

    // ---------- touch ----------

    private Element hit(float x, float y) {
        Point s = screen();
        for (int i = profile.elements.size() - 1; i >= 0; i--) {
            Element e = profile.elements.get(i);
            float r = e.type == Element.TAP ? tapRadius * 1.2f : Math.max(radiusPx(e, s), tapRadius);
            float dx = x - sx(e, s);
            float dy = y - sy(e, s);
            if (dx * dx + dy * dy <= r * r) return e;
        }
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                downX = ev.getX();
                downY = ev.getY();
                dragged = false;
                dragging = hit(downX, downY);
                if (dragging != null) {
                    Point s = screen();
                    grabDx = sx(dragging, s) - downX;
                    grabDy = sy(dragging, s) - downY;
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragging == null) return true;
                if (!dragged && Math.hypot(ev.getX() - downX, ev.getY() - downY) < touchSlop) return true;
                dragged = true;
                Point s = screen();
                float nx = (ev.getX() + grabDx + loc[0]) / s.x;
                float ny = (ev.getY() + grabDy + loc[1]) / s.y;
                dragging.x = Math.max(0f, Math.min(1f, nx));
                dragging.y = Math.max(0f, Math.min(1f, ny));
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                if (!dragged) {
                    if (dragging != null) select(dragging);
                    else hidePanel();
                }
                dragging = null;
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                dragging = null;
                return true;
            default:
                return true;
        }
    }

    // ---------- property panel ----------

    private void select(Element e) {
        selected = e;
        assigning = false;
        invalidate();
        showPanelFor(e);
    }

    private void hidePanel() {
        selected = null;
        assigning = false;
        panel.setVisibility(GONE);
        invalidate();
    }

    private TextView panelTitle(String s) {
        TextView t = Ui.text(getContext(), s, 16, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        panel.addView(t, Ui.matchWrap(getContext(), 0));
        return t;
    }

    private LinearLayout panelRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        panel.addView(row, Ui.matchWrap(getContext(), 8));
        return row;
    }

    private Button rowButton(LinearLayout row, String text, int color, OnClickListener l) {
        Button b = Ui.button(getContext(), text, color);
        b.setTextSize(13);
        b.setPadding(Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), Ui.dp(getContext(), 6), Ui.dp(getContext(), 6));
        b.setOnClickListener(l);
        b.setMinHeight(Ui.dp(getContext(), 42));
        b.setMinimumHeight(Ui.dp(getContext(), 42));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(Ui.dp(getContext(), 3));
        lp.setMarginEnd(Ui.dp(getContext(), 3));
        row.addView(b, lp);
        return b;
    }

    private void showPanelFor(final Element e) {
        panel.removeAllViews();
        panel.setVisibility(VISIBLE);
        if (e.type == Element.TAP) {
            panelTitle("زر على الشاشة: " + e.label);
            LinearLayout r = panelRow();
            rowButton(r, "تغيير المفتاح", Ui.WARN, v -> startAssign(e, ASSIGN_TAP));
        } else if (e.type == Element.JOYSTICK) {
            panelTitle("عصا الحركة");
            LinearLayout r1 = panelRow();
            for (int i = 0; i < 4; i++) {
                final int dir = i;
                rowButton(r1, DIR_NAMES[i] + "\n" + e.dirLabels[i], Ui.NEUTRAL, v -> startAssign(e, dir));
            }
            addRadiusRow(e);
        } else {
            panelTitle("منطقة الكاميرا");
            TextView hint = Ui.text(getContext(), "حرّك الماوس للنظر حولك. ضعها في مكان فارغ من الشاشة.", 13, Ui.MUTED, false);
            hint.setGravity(Gravity.CENTER);
            panel.addView(hint, Ui.matchWrap(getContext(), 4));
            LinearLayout r = panelRow();
            final TextView[] value = new TextView[1];
            rowButton(r, "− حساسية", Ui.NEUTRAL, v -> {
                e.sens = Math.max(0.1f, Math.round((e.sens - 0.1f) * 10f) / 10f);
                value[0].setText(String.format(Locale.US, "%.1f", e.sens));
                invalidate();
            });
            value[0] = Ui.text(getContext(), String.format(Locale.US, "%.1f", e.sens), 16, Ui.TEXT, true);
            value[0].setGravity(Gravity.CENTER);
            r.addView(value[0], new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 42), 0.6f));
            rowButton(r, "+ حساسية", Ui.NEUTRAL, v -> {
                e.sens = Math.min(5f, Math.round((e.sens + 0.1f) * 10f) / 10f);
                value[0].setText(String.format(Locale.US, "%.1f", e.sens));
                invalidate();
            });
            addRadiusRow(e);
        }
        LinearLayout last = panelRow();
        rowButton(last, "حذف", Ui.DANGER, v -> {
            profile.elements.remove(e);
            hidePanel();
        });
        rowButton(last, "تم", Ui.ACCENT, v -> hidePanel());
    }

    private void addRadiusRow(final Element e) {
        LinearLayout r = panelRow();
        rowButton(r, "− الحجم", Ui.NEUTRAL, v -> {
            e.radius = Math.max(0.04f, e.radius - 0.01f);
            invalidate();
        });
        rowButton(r, "+ الحجم", Ui.NEUTRAL, v -> {
            e.radius = Math.min(0.45f, e.radius + 0.01f);
            invalidate();
        });
    }

    // ---------- key assignment ----------

    private void startAssign(Element e, int slot) {
        assignTarget = e;
        assignSlot = slot;
        assigning = true;
        panel.removeAllViews();
        panel.setVisibility(VISIBLE);
        panelTitle("اضغط الآن زراً على الكيبورد");
        TextView hint = Ui.text(getContext(), "أو اختر زراً من الماوس:", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, Ui.matchWrap(getContext(), 6));
        int[][] rows = {
                {Codes.BTN_LEFT, Codes.BTN_RIGHT, Codes.BTN_MIDDLE},
                {Codes.BTN_SIDE, Codes.BTN_EXTRA},
                {Codes.WHEEL_UP, Codes.WHEEL_DOWN},
        };
        for (int[] codes : rows) {
            LinearLayout r = panelRow();
            for (final int code : codes) {
                if (slot == ASSIGN_TOGGLE && (code == Codes.WHEEL_UP || code == Codes.WHEEL_DOWN)) continue;
                rowButton(r, Codes.mouseLabel(code), Ui.NEUTRAL, v -> assign(code, Codes.mouseLabel(code)));
            }
        }
        LinearLayout r = panelRow();
        rowButton(r, "إلغاء", Ui.DANGER, v -> finishAssign());
        requestFocus();
    }

    private void assign(int code, String text) {
        if (!assigning) return;
        if (assignSlot == ASSIGN_TOGGLE) {
            profile.toggleCode = code;
            profile.toggleLabel = text;
            toggleButton.setText(toggleText());
        } else if (assignTarget != null) {
            if (assignSlot == ASSIGN_TAP) {
                assignTarget.code = code;
                assignTarget.label = text;
            } else {
                assignTarget.dirCodes[assignSlot] = code;
                assignTarget.dirLabels[assignSlot] = text;
            }
        }
        finishAssign();
    }

    private void finishAssign() {
        assigning = false;
        Element target = assignTarget;
        assignTarget = null;
        if (target != null && profile.elements.contains(target)) select(target);
        else hidePanel();
    }

    private static String keyLabel(KeyEvent ev) {
        String s = KeyEvent.keyCodeToString(ev.getKeyCode());
        if (s.startsWith("KEYCODE_")) s = s.substring(8);
        switch (s) {
            case "SPACE": return "Space";
            case "SHIFT_LEFT": return "Shift";
            case "SHIFT_RIGHT": return "RShift";
            case "CTRL_LEFT": return "Ctrl";
            case "CTRL_RIGHT": return "RCtrl";
            case "ALT_LEFT": return "Alt";
            case "ALT_RIGHT": return "RAlt";
            case "TAB": return "Tab";
            case "ESCAPE": return "Esc";
            case "ENTER": return "Enter";
            case "GRAVE": return "`";
            case "CAPS_LOCK": return "Caps";
            case "DEL": return "Back";
            case "DPAD_UP": return "↑";
            case "DPAD_DOWN": return "↓";
            case "DPAD_LEFT": return "←";
            case "DPAD_RIGHT": return "→";
            default:
                if (s.length() > 6) s = s.substring(0, 6);
                return s;
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent ev) {
        int kc = ev.getKeyCode();
        if (assigning) {
            if (ev.getAction() == KeyEvent.ACTION_DOWN && ev.getRepeatCount() == 0) {
                int scan = ev.getScanCode();
                if (kc == KeyEvent.KEYCODE_BACK && scan == 0) {
                    finishAssign();
                } else if (scan > 0) {
                    assign(scan, keyLabel(ev));
                } else {
                    toast("هذا الزر غير مدعوم، جرّب زراً آخر");
                }
            }
            return true;
        }
        if (kc == KeyEvent.KEYCODE_BACK) {
            if (ev.getAction() == KeyEvent.ACTION_UP) hidePanel();
            return true;
        }
        return super.dispatchKeyEvent(ev);
    }
}
