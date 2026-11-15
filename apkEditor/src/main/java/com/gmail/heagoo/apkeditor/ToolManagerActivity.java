package com.gmail.heagoo.apkeditor;

import com.gmail.heagoo.common.ThemedActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.gmail.heagoo.apkeditor.util.ToolFramework;
import com.gmail.heagoo.apkeditor.util.ToolUpdateChecker;
import java.io.File;
import java.text.DateFormat;
import java.util.Date;

public final class ToolManagerActivity extends ThemedActivity {
    private int pad;
    private SharedPreferences prefs;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Инструменты и версии");
        this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
        this.pad = (int) (getResources().getDisplayMetrics().density * 16.0f);
        ScrollView scroll = new ScrollView(this);
        this.root = new LinearLayout(this);
        this.root.setOrientation(1);
        this.root.setPadding(this.pad, this.pad, this.pad, this.pad);
        scroll.addView(this.root);
        setContentView(scroll);
        render();
        ToolUpdateChecker.check(this, false, new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m364lambda$onCreate$0$comgmailheagooapkeditorToolManagerActivity();
            }
        });
    }

    void m364lambda$onCreate$0$comgmailheagooapkeditorToolManagerActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        render();
    }

    private void text(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14.0f);
        t.setPadding(0, this.pad / 3, 0, this.pad / 3);
        this.root.addView(t);
    }

    private void button(String s, final Runnable r) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        this.root.addView(b);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public final void onClick(View view) {
                r.run();
            }
        });
    }

    private String value(String key, String def) {
        return this.prefs.getString(key, def);
    }

    private void put(String k, String v) {
        this.prefs.edit().putString(k, v).apply();
        render();
    }

    private boolean nativeFile(String name) {
        return new File(getApplicationInfo().nativeLibraryDir, name).isFile();
    }

    private void render() {
        this.root.removeAllViews();
        text("Версии используются при сборке и обработке. Выбор вручную; проверка обновлений не меняет инструменты и ключи.");
        button("Framework Android • " + ("33-thirdparty".equals(value("ToolFramework", "37")) ? "Android 13 / API 33" : "Android 17 / API 37"), new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.framework();
            }
        });
        String ac = value("AaptVersion", "legacy");
        button("AAPT / AAPT2 / AAPTz • " + ("aapt2".equals(ac) ? "AAPT2 Android 9+" : "aapt2-37".equals(ac) ? "AAPT2 37" : "aaptz-2022".equals(ac) ? "AAPTz из архива 2022, ARM32" : "прежний AAPT"), new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.aapt();
            }
        });
        String cp = value("MyCpVersion", "legacy");
        button("mycp / cp • " + ("system".equals(cp) ? "системный cp" : "2022".equals(cp) ? "mycp из архива 2022, ARM32" : "прежний mycp"), new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.cp();
            }
        });
        text("zipalign • Встроенный FastZip: выравнивание до подписи, 4 байта и 16 КБ для несжатых библиотек. Отдельный zipalign из архива недоступен: ARM32 и зависимость от системной библиотеки; он не заменяет FastZip.");
        button("ZIP для слияния • " + value("LabZipEngine", "legacy"), new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m365lambda$render$2$comgmailheagooapkeditorToolManagerActivity();
            }
        });
        button("JADX • " + value("JadxVersion", "legacy"), new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m366lambda$render$3$comgmailheagooapkeditorToolManagerActivity();
            }
        });
        text("testkey.pk8 / testkey.x509.pem • встроенные ключ и сертификат. Версии ключей не обновляются автоматически: замена сертификата может помешать обновить ранее подписанные APK. Выбор ключа и схем подписи — в основных настройках.");
        SharedPreferences status = getSharedPreferences("tool_update_status", 0);
        long date = status.getLong("checked", 0L);
        String source = status.getString("community", "unknown");
        String sourceText = "same".equals(source) ? "без изменений" : "changed".equals(source) ? "изменился; загрузка заблокирована до проверки" : "offline".equals(source) ? "нет связи" : "нет данных";
        text("ПРОВЕРКА ОБНОВЛЕНИЙ\n" + (date == 0 ? "Ещё не выполнена" : "Проверено: " + DateFormat.getDateTimeInstance().format(new Date(date))) + "\nGoogle SDK platform: " + status.getString("sdk", "—") + " (встроен API 37)\nGoogle SDK Build Tools: " + status.getString("buildTools", "—") + " (настольные, в приложении не устанавливаются)\nGoogle AAPT2 Maven: " + status.getString("aapt", "—") + " (настольные пакеты, не Android ARM)\nAAPT2 Android (сторонний проект): " + status.getString("aaptArm", "—") + "; встроен aapt2-android17-37.0.0\nАрхив Modder Hub: " + sourceText + "\nПроверка запускается при открытии приложения не чаще раза в сутки. Найденные версии не устанавливаются автоматически. Для mycp, AAPTz и testkey универсального официального каталога версий нет.");
        button("Проверить сейчас", new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m368lambda$render$5$comgmailheagooapkeditorToolManagerActivity();
            }
        });
        button("Источники и ограничения", new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m369lambda$render$6$comgmailheagooapkeditorToolManagerActivity();
            }
        });
    }

    void m365lambda$render$2$comgmailheagooapkeditorToolManagerActivity() {
        choose("ZIP для слияния", new String[]{"Прежний ZIP", "libzip 1.12.1 (экспериментальный)"}, new String[]{"legacy", "libzip-1.12.1"}, "LabZipEngine");
    }

    void m366lambda$render$3$comgmailheagooapkeditorToolManagerActivity() {
        choose("Декомпилятор", new String[]{"Прежний JADX", "JADX 1.5.6"}, new String[]{"legacy", "modern"}, "JadxVersion");
    }

    void m367lambda$render$4$comgmailheagooapkeditorToolManagerActivity() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        render();
        Toast.makeText(this, "Проверка завершена", 0).show();
    }

    void m368lambda$render$5$comgmailheagooapkeditorToolManagerActivity() {
        Toast.makeText(this, "Проверка…", 0).show();
        ToolUpdateChecker.check(this, true, new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m367lambda$render$4$comgmailheagooapkeditorToolManagerActivity();
            }
        });
    }

    void m369lambda$render$6$comgmailheagooapkeditorToolManagerActivity() {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Источники и ограничения")
                .setMessage("Что уже входит в APK Editor Plus\nFramework Android 17 (API 37), основные инструменты и сборки приложены к приложению. AAPT2 37 собран из открытого проекта vhynlain/aapt2.\n\nЧто является сторонним\nFramework Android 13 (API 33), AAPTz и mycp 2022 взяты из архива Modder Hub. Их происхождение, лицензии и совместимость полностью не подтверждены. Используйте их только если понимаете риск. Архив framework и сам framework проверяются по контрольным суммам SHA-256.\n\nПочему некоторые инструменты недоступны\nAndroid 10 и новее ограничивает запуск новых исполняемых файлов из папки данных приложения. Поэтому приложение не скачивает туда новые ELF-файлы для запуска. Некоторые старые инструменты собраны только для ARM32 и не работают в установленной ARM64-версии приложения.\n\nПроверка версий\nПроверка сообщает сведения о доступных версиях, но ничего не устанавливает и не меняет автоматически.\n\nИсточники:\nModder Hub: github.com/modder-hub/ApkEditor_Patches_V2\nAAPT2 Android: github.com/vhynlain/aapt2\nДокументация Android: developer.android.com/tools/aapt2")
                .setPositiveButton("Понятно", (DialogInterface.OnClickListener) null).create();
        dialog.setOnShowListener(ignored -> {
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextSize(14);
                message.setLineSpacing(dp(3), 1.0f);
            }
        });
        dialog.show();
    }

    /** Palette-aware selectable cards instead of the platform's flat legacy choice rows. */
    private void showChoiceDialog(String title, String[] labels, DialogInterface.OnClickListener listener) {
        final int cardAttr = R.attr.labCardBackground;
        final android.util.TypedValue card = new android.util.TypedValue();
        getTheme().resolveAttribute(cardAttr, card, true);
        final int textColor = com.gmail.heagoo.common.ThemeColors.color(this, R.attr.labText);
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return labels.length; }
            @Override public Object getItem(int position) { return labels[position]; }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView row = convertView instanceof TextView ? (TextView) convertView : new TextView(ToolManagerActivity.this);
                int inset = dp(14);
                row.setText(labels[position]);
                row.setTextColor(textColor);
                row.setTextSize(16);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(inset, dp(12), inset, dp(12));
                row.setMinHeight(dp(56));
                row.setFocusable(false);
                if (card.resourceId != 0) row.setBackgroundResource(card.resourceId);
                else row.setBackgroundColor(com.gmail.heagoo.common.ThemeColors.color(ToolManagerActivity.this, R.attr.labSurfaceAlt));
                return row;
            }
        };
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setAdapter(adapter, listener)
                .setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).create();
        dialog.setOnShowListener(ignored -> {
            android.widget.ListView list = dialog.getListView();
            list.setDivider(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            list.setDividerHeight(dp(8));
            list.setPadding(dp(8), dp(4), dp(8), dp(4));
            list.setClipToPadding(false);
        });
        dialog.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void choose(String title, String[] labels, final String[] values, final String key) {
        showChoiceDialog(title, labels, new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                ToolManagerActivity.this.m356lambda$choose$7$comgmailheagooapkeditorToolManagerActivity(key, values, dialogInterface, i);
            }
        });
    }

    void m356lambda$choose$7$comgmailheagooapkeditorToolManagerActivity(String key, String[] values, DialogInterface d, int i) {
        put(key, values[i]);
    }

    public void aapt() {
        String[] labels = {"Прежний AAPT", "AAPT2 (Android 9+)", "AAPT2 37 (Android 11+)", "AAPTz 2022 • сторонний, только ARM32"};
        final String[] vals = {"legacy", "aapt2", "aapt2-37", "aaptz-2022"};
        showChoiceDialog("Сборщик ресурсов", labels, new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                ToolManagerActivity.this.m355lambda$aapt$9$comgmailheagooapkeditorToolManagerActivity(vals, dialogInterface, i);
            }
        });
    }

    void m355lambda$aapt$9$comgmailheagooapkeditorToolManagerActivity(final String[] vals, DialogInterface d, final int i) {
        if ((i == 1 && Build.VERSION.SDK_INT < 28) || (i == 2 && Build.VERSION.SDK_INT < 30)) {
            notice("Версия Android слишком старая для этого AAPT2.");
            return;
        }
        if (i == 3 && !nativeFile("libaaptz_exec.so")) {
            notice("AAPTz недоступен в установленной ARM64-версии приложения. Нужна ARM32-версия и поддержка ARM32 устройством.");
        } else if (i == 3) {
            confirm("AAPTz — сторонний ARM32-бинарник из архива 2022 года. Происхождение и совместимость не подтверждены. Использовать его при сборке ресурсов?", new Runnable() {
                @Override
                public final void run() {
                    ToolManagerActivity.this.m354lambda$aapt$8$comgmailheagooapkeditorToolManagerActivity(vals, i);
                }
            });
        } else {
            put("AaptVersion", vals[i]);
        }
    }

    void m354lambda$aapt$8$comgmailheagooapkeditorToolManagerActivity(String[] vals, int i) {
        put("AaptVersion", vals[i]);
    }

    public void cp() {
        String[] labels = {"Прежний встроенный mycp (ARM32, может не запускаться)", "Системный /system/bin/cp", "mycp 2022 • сторонний, только ARM32"};
        final String[] vals = {"legacy", "system", "2022"};
        showChoiceDialog("Копирование данных", labels, new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                ToolManagerActivity.this.m358lambda$cp$11$comgmailheagooapkeditorToolManagerActivity(vals, dialogInterface, i);
            }
        });
    }

    void m358lambda$cp$11$comgmailheagooapkeditorToolManagerActivity(final String[] vals, DialogInterface d, final int i) {
        if (i == 2 && !nativeFile("libmycp2022_exec.so")) {
            notice("Этот mycp доступен только в установленной ARM32-версии приложения.");
        } else if (i == 2) {
            confirm("Сторонний mycp из архива 2022 года. Происхождение и совместимость не подтверждены. Использовать?", new Runnable() {
                @Override
                public final void run() {
                    ToolManagerActivity.this.m357lambda$cp$10$comgmailheagooapkeditorToolManagerActivity(vals, i);
                }
            });
        } else {
            put("MyCpVersion", vals[i]);
        }
    }

    void m357lambda$cp$10$comgmailheagooapkeditorToolManagerActivity(String[] vals, int i) {
        put("MyCpVersion", vals[i]);
    }

    public void framework() {
        final boolean have = ToolFramework.installed(this).isFile();
        String[] options = {"Android 17 / API 37 • встроен", "Android 13 / API 33 • сторонний" + (have ? " • установлен" : " • скачать и установить")};
        showChoiceDialog("Ресурсный framework", options, new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                ToolManagerActivity.this.m360x9c40e250(have, dialogInterface, i);
            }
        });
    }

    void m360x9c40e250(boolean have, DialogInterface d, int i) {
        if (i == 0) {
            put("ToolFramework", "37");
        } else if (have) {
            confirm("Использовать сторонний framework Android 13 при сборке ресурсов?", new Runnable() {
                @Override
                public final void run() {
                    ToolManagerActivity.this.m359x62764071();
                }
            });
        } else {
            confirm("Скачать сторонний архив Modder Hub (около 3 МБ) и установить framework Android 13? Его происхождение и лицензия не подтверждены. Будут проверены закреплённые SHA-256 архива и framework. Выбор версии произойдёт отдельно.", new Runnable() {
                @Override
                public final void run() {
                    ToolManagerActivity.this.installFramework();
                }
            });
        }
    }

    void m359x62764071() {
        put("ToolFramework", "33-thirdparty");
    }

    public void installFramework() {
        Toast.makeText(this, "Загрузка framework…", 0).show();
        final ContextHolder holder = new ContextHolder(getApplicationContext());
        new Thread(new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m363x3c653eac(holder);
            }
        }, "framework-download").start();
    }

    void m363x3c653eac(ContextHolder holder) {
        String error = null;
        try {
            ToolFramework.install(holder.context);
        } catch (Exception e) {
            error = e.getMessage();
        }
        final String result = error;
        runOnUiThread(new Runnable() {
            @Override
            public final void run() {
                ToolManagerActivity.this.m362x29a9ccd(result);
            }
        });
    }

    void m362x29a9ccd(String result) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        if (result != null) {
            notice("Не удалось установить framework: " + result);
        } else {
            render();
            confirm("Framework установлен. Использовать Android 13 при сборке ресурсов?", new Runnable() {
                @Override
                public final void run() {
                    ToolManagerActivity.this.m361xc8cffaee();
                }
            });
        }
    }

    void m361xc8cffaee() {
        put("ToolFramework", "33-thirdparty");
    }

    static final class ContextHolder {
        final Context context;

        ContextHolder(Context c) {
            this.context = c;
        }
    }

    private void notice(String s) {
        new AlertDialog.Builder(this).setMessage(s).setPositiveButton("Понятно", (DialogInterface.OnClickListener) null).show();
    }

    private void confirm(String s, final Runnable r) {
        new AlertDialog.Builder(this).setMessage(s).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).setPositiveButton("Подтвердить", new DialogInterface.OnClickListener() {
            @Override
            public final void onClick(DialogInterface dialogInterface, int i) {
                r.run();
            }
        }).show();
    }
}
