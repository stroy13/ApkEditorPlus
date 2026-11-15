package com.gmail.heagoo.apkeditor;

import com.gmail.heagoo.common.ThemedPreferenceActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.gmail.heagoo.apkeditor.ProcessingDialog;
import com.gmail.heagoo.apkeditor.base.R;
import com.gmail.heagoo.common.AppListOrder;
import com.gmail.heagoo.common.CommandRunner;
import com.gmail.heagoo.common.RandomUtil;
import com.gmail.heagoo.common.SDCard;
import com.gmail.heagoo.common.ThemeRegistry;
import com.gmail.heagoo.neweditor.ParserRule;
import java.io.File;

public class SettingActivity extends ThemedPreferenceActivity implements Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {
    public static final int EXTRACT_AUTORENAME = 0;
    public static final int EXTRACT_OVERWRITE = 1;
    public static final String STR_PRIVATEKEYPATH = "PrivateKeyPath";
    public static final String STR_PUBLICKEYPATH = "PublicKeyPath";
    private static final String apkNameKey = "OutputApkName";
    private static final String appIconKey = "MyIcon";
    private static final String appOrderKey = "AppListOrder";
    private static final String cleanKey = "CleanGarbage";
    private static final String compressLevelKey = "CompressionLevel";
    private static final String confirmEnabledKey = "RebuildConfirmation";
    private static final String decodeDirKey = "DecodeDirectory";
    private static final String decodeModeKey = "DecodeMode";
    private static final String dex2smaliEnabledKey = "SmaliEditingEnabled";
    private static final String fileRenameKey = "FileRenameOption";
    private static final String signWithKey = "SignApkWith";

    private static boolean isFreeVersion() {
        return false;
    }

    public static String getListOrder(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String order = sp.getString(appOrderKey, "");
        String[] labels = ctx.getResources().getStringArray(R.array.order_key);
        return AppListOrder.fromPreference(order, labels).getPreferenceValue();
    }

    public static void setListOrder(Context ctx, String value) {
        AppListOrder order = AppListOrder.fromPreference(
                value, ctx.getResources().getStringArray(R.array.order_key));
        PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                .putString(appOrderKey, order.getPreferenceValue()).apply();
    }

    public static int getCompressionLevel(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String strLevel = sp.getString(compressLevelKey, "6");
        int level = 6;
        try {
            level = Integer.valueOf(strLevel).intValue();
        } catch (Exception e) {
        }
        if (level < 0) {
            return 0;
        }
        if (level > 9) {
            return 9;
        }
        return level;
    }

    public static String getSignKeyName(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sp.getString(signWithKey, "testkey");
    }

    public static int getOutputApkRule(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String strNameRule = sp.getString(apkNameKey, "1");
        try {
            int ret = Integer.valueOf(strNameRule).intValue();
            return ret;
        } catch (Exception e) {
            return 1;
        }
    }

    public static boolean isDex2smaliEnabled(Context ctx) {
        if (!isFreeVersion()) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
            return sp.getBoolean(dex2smaliEnabledKey, true);
        }
        return false;
    }

    public static boolean isRebuildConfirmEnabled(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        return sp.getBoolean(confirmEnabledKey, false);
    }

    public static int getFileRenameOption(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String str = sp.getString(fileRenameKey, "1");
        try {
            return Integer.valueOf(str).intValue();
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean dirCanWrite(String dir) {
        File f = new File(dir);
        if (f.exists() && f.isDirectory()) {
            String rand = RandomUtil.getRandomString(8);
            File tryF = new File(f, rand);
            boolean ret = tryF.mkdir();
            if (ret) {
                tryF.delete();
            }
            return ret;
        }
        return false;
    }

    public static String getDecodeDirectory(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String str = sp.getString(decodeDirKey, null);
        if (str != null) {
            if (str.endsWith("/")) {
                str = str.substring(0, str.length() - 1);
            }
            if (dirCanWrite(str)) {
                return str;
            }
        }
        return null;
    }

    public static String getDecodeMode(Context ctx) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        String value = sp.getString(decodeModeKey, "2");
        if (!value.equals("0") && !value.equals("1") && !value.equals("2")) {
            return "2";
        }
        return value;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(8);
        super.onCreate(savedInstanceState);
        if (GlobalConfig.instance(this).isFullScreen()) {
            getWindow().setFlags(ParserRule.NO_WORD_BREAK, ParserRule.NO_WORD_BREAK);
        }
        addPreferencesFromResource(R.xml.settings);
        initData();
        Preference notificationSettings = findPreference("NotificationSettings");
        if (notificationSettings != null) {
            notificationSettings.setOnPreferenceClickListener(pref -> {
                NotificationAccess.openSettings(this);
                return true;
            });
        } else {
            android.util.Log.w("SettingActivity", "NotificationSettings preference is missing");
        }
        Preference localAiModel = findPreference("LocalAiModel");
        if (localAiModel != null) localAiModel.setOnPreferenceClickListener(pref -> {
            showLocalAiModelMenu();
            return true;
        });
        findPreference("ToolManager").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public final boolean onPreferenceClick(Preference preference) {
                return SettingActivity.this.m291lambda$onCreate$0$comgmailheagooapkeditorSettingActivity(preference);
            }
        });
    }

    private void showLocalAiModelMenu() {
        new android.app.AlertDialog.Builder(this).setTitle(R.string.ai_models)
                .setItems(new String[]{getString(R.string.ai_download), getString(R.string.ai_import), getString(R.string.ai_select_model)}, (dialog, which) -> {
                    if (which == 0) confirmRecommendedModelDownload();
                    else if (which == 1) {
                        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        pick.addCategory(Intent.CATEGORY_OPENABLE);
                        pick.setType("*/*");
                        startActivityForResult(pick, 9274);
                    } else chooseInstalledLocalModel();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseInstalledLocalModel() {
        java.io.File[] models = com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.available(this);
        if (models.length == 0) {
            android.widget.Toast.makeText(this, R.string.ai_no_installed_models, android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[models.length];
        String selected = com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.selected(this) == null ? null
                : com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.selected(this).getName();
        int checked = -1;
        for (int i = 0; i < models.length; i++) {
            names[i] = models[i].getName() + " (" + android.text.format.Formatter.formatFileSize(this, models[i].length()) + ")";
            if (models[i].getName().equals(selected)) checked = i;
        }
        new android.app.AlertDialog.Builder(this).setTitle(R.string.ai_select_model)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    boolean ok = com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.select(this, models[which].getName());
                    if (ok) d.dismiss();
                    else android.widget.Toast.makeText(this, R.string.ai_model_import_failed, android.widget.Toast.LENGTH_LONG).show();
                }).setNegativeButton(android.R.string.cancel, null).show();
    }

    private void confirmRecommendedModelDownload() {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle(R.string.ai_download_confirm_title)
                .setMessage(R.string.ai_download_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.ai_license, (d, w) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://ai.google.dev/gemma/terms"))); }
                    catch (Exception e) { android.widget.Toast.makeText(this, R.string.ai_model_import_failed, android.widget.Toast.LENGTH_LONG).show(); }
                })
                .setPositiveButton(R.string.ai_download, (d, w) -> downloadRecommendedModel())
                .create();
        dialog.show();
    }

    private void downloadRecommendedModel() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        android.app.ProgressDialog progress = new android.app.ProgressDialog(this);
        progress.setTitle(R.string.ai_download);
        progress.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        progress.setMax(1000);
        progress.setIndeterminate(false);
        progress.setCancelable(false);
        progress.setButton(android.content.DialogInterface.BUTTON_NEGATIVE, getString(android.R.string.cancel), (d, w) -> {
            cancelled.set(true);
            progress.setMessage(getString(R.string.ai_cancelling));
        });
        progress.show();
        new Thread(() -> {
            boolean ok = false;
            try {
                com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.downloadRecommended(getApplicationContext(),
                        new com.gmail.heagoo.apkeditor.translate.LocalModelStore.Progress() {
                            private long total;
                            @Override public boolean isCancelled() { return cancelled.get(); }
                            @Override public void onTotal(long bytes) {
                                total = bytes;
                                runOnUiThread(() -> progress.setMessage(getString(R.string.ai_progress_bytes_total, 0, (int) (bytes / (1024 * 1024)))));
                            }
                            @Override public void onBytes(long bytes) {
                                long max = total;
                                runOnUiThread(() -> {
                                    if (max > 0) progress.setProgress((int) Math.min(1000, bytes * 1000 / max));
                                    progress.setMessage(getString(R.string.ai_progress_bytes_total,
                                            (int) (bytes / (1024 * 1024)), (int) (max / (1024 * 1024))));
                                });
                            }
                        });
                ok = true;
            } catch (Exception e) { android.util.Log.e("SettingActivity", "Local AI model download failed", e); }
            final boolean downloaded = ok;
            runOnUiThread(() -> {
                progress.dismiss();
                android.widget.Toast.makeText(this, downloaded ? R.string.ai_model_downloaded : R.string.ai_transfer_error,
                        android.widget.Toast.LENGTH_LONG).show();
            });
        }, "local-model-download").start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 9274 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        new Thread(() -> {
            boolean ok = false;
            try {
                android.database.Cursor cursor = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null);
                String name = null;
                if (cursor != null) {
                    try { if (cursor.moveToFirst()) name = cursor.getString(0); }
                    finally { cursor.close(); }
                }
                if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".litertlm"))
                    throw new java.io.IOException("Expected a .litertlm model file");
                com.gmail.heagoo.apkeditor.translate.LocalLlmModelStore.importModel(getApplicationContext(), uri, name);
                ok = true;
            } catch (Exception e) { android.util.Log.e("SettingActivity", "Local AI model import failed", e); }
            final boolean imported = ok;
            runOnUiThread(() -> android.widget.Toast.makeText(this,
                    imported ? R.string.ai_model_imported : R.string.ai_model_import_failed, android.widget.Toast.LENGTH_LONG).show());
        }, "local-model-import").start();
    }

    boolean m291lambda$onCreate$0$comgmailheagooapkeditorSettingActivity(Preference pref) {
        startActivity(new Intent(this, (Class<?>) ToolManagerActivity.class));
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void initData() {
        PreferenceManager manager = getPreferenceManager();
        ListPreference modePref = (ListPreference) manager.findPreference(decodeModeKey);
        String value = getDecodeMode(this);
        modePref.setSummary(getDecodeModeSummary(value));
        modePref.setOnPreferenceChangeListener(this);
        ListPreference orderPref = (ListPreference) manager.findPreference(appOrderKey);
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        AppListOrder order = AppListOrder.fromPreference(
                sp.getString(appOrderKey, ""), getResources().getStringArray(R.array.order_key));
        orderPref.setValue(order.getPreferenceValue());
        orderPref.setSummary(orderPref.getEntries()[order.ordinal()]);
        orderPref.setOnPreferenceChangeListener(this);
        ListPreference compressLevelPref = (ListPreference) manager.findPreference(compressLevelKey);
        SharedPreferences sp2 = PreferenceManager.getDefaultSharedPreferences(this);
        String strLevel = sp2.getString(compressLevelKey, "6");
        compressLevelPref.setValue(strLevel);
        compressLevelPref.setOnPreferenceChangeListener(this);
        ListPreference signPref = (ListPreference) manager.findPreference(signWithKey);
        SharedPreferences sp3 = PreferenceManager.getDefaultSharedPreferences(this);
        String strKeyname = sp3.getString(signWithKey, "testkey");
        signPref.setValue(strKeyname);
        signPref.setOnPreferenceChangeListener(this);
        ListPreference signPref2 = (ListPreference) manager.findPreference(apkNameKey);
        SharedPreferences sp4 = PreferenceManager.getDefaultSharedPreferences(this);
        String strNameIdx = sp4.getString(apkNameKey, "1");
        signPref2.setValue(strNameIdx);
        signPref2.setOnPreferenceChangeListener(this);
        CheckBoxPreference checkBox = (CheckBoxPreference) findPreference("FullScreen");
        checkBox.setChecked(GlobalConfig.instance(this).isFullScreen());
        checkBox.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference arg0, Object newValue) {
                GlobalConfig.instance(SettingActivity.this).setFullScreen(SettingActivity.this, ((Boolean) newValue).booleanValue());
                return true;
            }
        });
        ListPreference appThemeList = (ListPreference) findPreference("AppTheme");
        int id = GlobalConfig.instance(this).getThemeId();
        String[] values = getResources().getStringArray(R.array.theme_value);
        appThemeList.setValue(values[id]);
        appThemeList.setSummary(appThemeList.getEntries()[id]);
        appThemeList.setOnPreferenceChangeListener(this);
        ListPreference languageList = (ListPreference) findPreference("Language");
        String language = PreferenceManager.getDefaultSharedPreferences(this).getString("Language", "");
        languageList.setValue(language);
        languageList.setOnPreferenceChangeListener(this);
        CheckBoxPreference smaliPref = (CheckBoxPreference) findPreference(dex2smaliEnabledKey);
        if (isFreeVersion()) {
            PreferenceCategory category = (PreferenceCategory) findPreference("ApkBuilding");
            category.removePreference(smaliPref);
        } else {
            boolean b = isDex2smaliEnabled(this);
            smaliPref.setChecked(b);
            smaliPref.setSummary(b ? R.string.smali_edit_summary : R.string.smali_edit_disabled_summary);
            smaliPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    CheckBoxPreference cb = (CheckBoxPreference) arg0;
                    boolean val = ((Boolean) newValue).booleanValue();
                    cb.setChecked(val);
                    cb.setSummary(val ? R.string.smali_edit_summary : R.string.smali_edit_disabled_summary);
                    return true;
                }
            });
        }
        CheckBoxPreference confirmPref = (CheckBoxPreference) findPreference(confirmEnabledKey);
        boolean enabled = isRebuildConfirmEnabled(this);
        confirmPref.setChecked(enabled);
        confirmPref.setSummary(enabled ? R.string.rebuild_confirm_e_summary : R.string.rebuild_confirm_d_summary);
        confirmPref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference arg0, Object newValue) {
                int i;
                CheckBoxPreference cb = (CheckBoxPreference) arg0;
                boolean val = ((Boolean) newValue).booleanValue();
                cb.setChecked(val);
                if (val) {
                    i = R.string.rebuild_confirm_e_summary;
                } else {
                    i = R.string.rebuild_confirm_d_summary;
                }
                cb.setSummary(i);
                return true;
            }
        });
        Preference p = findPreference(cleanKey);
        p.setOnPreferenceClickListener(this);
        ListPreference renamePref = (ListPreference) manager.findPreference(fileRenameKey);
        int option = getFileRenameOption(this);
        renamePref.setValue("" + option);
        EditTextPreference dirPref = (EditTextPreference) manager.findPreference(decodeDirKey);
        dirPref.setOnPreferenceChangeListener(this);
        String decodeDir = getDecodeDirectory(this);
        if (decodeDir != null) {
            dirPref.setSummary(decodeDir);
        }
    }

    private int getDecodeModeSummary(String value) {
        if ("0".equals(value)) {
            return R.string.summary_decode_all_files;
        }
        if ("1".equals(value)) {
            return R.string.summary_decode_partial_files;
        }
        return R.string.summary_decide_at_decoding;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        if (appOrderKey.equals(key)) {
            setListOrder(this, (String) newValue);
            AppListOrder order = AppListOrder.fromPreference(
                    (String) newValue, getResources().getStringArray(R.array.order_key));
            ((ListPreference) preference).setSummary(
                    ((ListPreference) preference).getEntries()[order.ordinal()]);
        } else if ("AppTheme".equals(key)) {
            try {
                String value = (String) newValue;
                int themeId = Integer.parseInt(value);
                if (!ThemeRegistry.valid(themeId)) {
                    return false;
                }
                PreferenceManager.getDefaultSharedPreferences(this).edit().putString("AppTheme", value).apply();
                GlobalConfig.instance(this).updateThemeId(themeId);
                ((ListPreference) preference).setSummary(((ListPreference) preference).getEntries()[themeId]);
                getWindow().getDecorView().post(new Runnable() {
                    @Override
                    public void run() {
                        if (SettingActivity.this.isFinishing()) {
                            return;
                        }
                        SettingActivity.this.recreate();
                    }
                });
            } catch (Exception e) {
            }
        } else if (decodeDirKey.equals(key)) {
            String dir = (String) newValue;
            if (dir == null || dir.trim().isEmpty()) {
                preference.setSummary(R.string.decode_dir_summary_may_change);
            } else if (dirCanWrite(dir)) {
                preference.setSummary(dir);
            } else {
                String msg = String.format(getString(R.string.decode_folder_unwritable), dir);
                Toast.makeText(this, msg, 1).show();
                return false;
            }
        } else if (decodeModeKey.equals(key)) {
            preference.setSummary(getDecodeModeSummary((String) newValue));
        }
        return true;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String clickedKey = preference.getKey();
        final String path = getFilesDir().getAbsolutePath();
        if (cleanKey.equals(clickedKey)) {
            ProcessingDialog dlg = new ProcessingDialog(this, new ProcessingDialog.ProcessingInterface() {
                @Override
                public void process() throws Exception {
                    SettingActivity.this.cleanSdcard();
                    CommandRunner cr = new CommandRunner();
                    cr.runCommand("rm -rf " + path + "/decoded\nrm -rf " + path + "/temp", (String[]) null, 8000);
                }

                @Override
                public void afterProcess() {
                }
            }, R.string.temp_file_cleaned);
            dlg.show();
            return false;
        }
        return false;
    }

    protected void cleanSdcard() {
        File f = new File(SDCard.getEditorDirectory());
        if (!f.exists() || !f.isDirectory()) {
            return;
        }
        String[] keepingFolders = {"backups", ".projects"};
        File[] subfiles = f.listFiles();
        if (subfiles != null) {
            for (File subfile : subfiles) {
                if (subfile.isDirectory()) {
                    boolean bKeep = false;
                    String folder = subfile.getName();
                    int length = keepingFolders.length;
                    int i = 0;
                    while (true) {
                        if (i >= length) {
                            break;
                        }
                        String keepDir = keepingFolders[i];
                        if (!folder.equals(keepDir)) {
                            i++;
                        } else {
                            bKeep = true;
                            break;
                        }
                    }
                    if (!bKeep) {
                        cleanDirectory(subfile);
                        subfile.delete();
                    }
                } else {
                    subfile.delete();
                }
            }
        }
    }

    protected void cleanDirectory(File dir) {
        File[] subfiles = dir.listFiles();
        if (subfiles != null) {
            for (File f : subfiles) {
                if (f.isDirectory()) {
                    cleanDirectory(f);
                    f.delete();
                } else {
                    f.delete();
                }
            }
        }
    }
}
