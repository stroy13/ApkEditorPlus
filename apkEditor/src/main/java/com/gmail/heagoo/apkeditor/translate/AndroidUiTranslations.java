package com.gmail.heagoo.apkeditor.translate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exact-match AndroidX AppCompat English-to-Russian translations.
 *
 * Values adapted from AndroidX AppCompat strings.xml and values-ru/strings.xml,
 * Copyright The Android Open Source Project, Apache License 2.0:
 * https://github.com/androidx/androidx/tree/androidx-main/appcompat/appcompat/src/main/res
 * Only match both the resource name and exact unmodified source; an application
 * can override either. All other strings go to the user's chosen engine.
 */
public final class AndroidUiTranslations {
    private static final class Entry {
        final String source, translated;
        Entry(String source, String translated) { this.source = source; this.translated = translated; }
    }
    private static final Map<String, Entry> EN_RU = entries();
    private AndroidUiTranslations() {}

    private static Map<String, Entry> entries() {
        Map<String, Entry> result = new HashMap<>();
        add(result, "abc_action_mode_done", "Done", "Готово");
        add(result, "abc_action_bar_home_description", "Navigate home", "Перейти на главный экран");
        add(result, "abc_action_bar_up_description", "Navigate up", "Перейти вверх");
        add(result, "abc_action_menu_overflow_description", "More options", "Ещё");
        add(result, "abc_toolbar_collapse_description", "Collapse", "Свернуть");
        add(result, "abc_searchview_description_search", "Search", "Поиск");
        add(result, "abc_search_hint", "Search…", "Введите запрос");
        add(result, "abc_searchview_description_query", "Search query", "Поисковый запрос");
        add(result, "abc_searchview_description_clear", "Clear query", "Удалить запрос");
        add(result, "abc_searchview_description_submit", "Submit query", "Отправить запрос");
        add(result, "abc_searchview_description_voice", "Voice search", "Голосовой поиск");
        add(result, "abc_activitychooserview_choose_application", "Choose an app", "Выберите приложение");
        add(result, "abc_activity_chooser_view_see_all", "See all", "Показать все");
        add(result, "abc_shareactionprovider_share_with", "Share with", "Поделиться с помощью");
        add(result, "abc_capital_on", "ON", "ВКЛ");
        add(result, "abc_capital_off", "OFF", "ВЫКЛ");
        add(result, "search_menu_title", "Search", "Поиск");
        add(result, "abc_prepend_shortcut_label", "Menu+", "Меню +");
        add(result, "abc_menu_meta_shortcut_label", "Meta+", "Meta +");
        add(result, "abc_menu_ctrl_shortcut_label", "Ctrl+", "Ctrl +");
        add(result, "abc_menu_alt_shortcut_label", "Alt+", "Alt +");
        add(result, "abc_menu_shift_shortcut_label", "Shift+", "Shift +");
        add(result, "abc_menu_sym_shortcut_label", "Sym+", "Sym +");
        add(result, "abc_menu_function_shortcut_label", "Function+", "Fn +");
        add(result, "abc_menu_space_shortcut_label", "space", "Пробел");
        add(result, "abc_menu_enter_shortcut_label", "enter", "Ввод");
        add(result, "abc_menu_delete_shortcut_label", "delete", "Удалить");
        add(result, "copy", "Copy", "Копировать");
        add(result, "a11y_action_label_collapse", "Collapse", "Свернуть");
        add(result, "a11y_action_label_expand", "Expand", "Развернуть");
        add(result, "call_notification_hang_up_action", "Hang Up", "Завершить вызов");
        add(result, "close_sheet", "Close sheet", "Закрыть панель");
        add(result, "confirm_device_credential_password", "Use password", "Использовать пароль");
        add(result, "default_error_message", "Invalid input", "Недопустимые данные");
        add(result, "fingerprint_error_no_fingerprint", "No fingerprints are enrolled", "Отпечатки пальцев не добавлены");
        add(result, "fingerprint_error_hw_not_present", "This device does not have a fingerprint sensor", "На устройстве нет датчика отпечатков пальцев");
        add(result, "fingerprint_error_hw_not_available", "Fingerprint hardware not available", "Сканер отпечатков пальцев недоступен");
        add(result, "fingerprint_error_lockout", "Too many attempts. Please try again later.", "Слишком много попыток. Повторите попытку позже.");
        return Collections.unmodifiableMap(result);
    }
    private static void add(Map<String, Entry> map, String name, String source, String translated) {
        map.put(name, new Entry(source, translated));
    }
    /** Returns null for other languages, modified strings and unknown resources. */
    public static String find(String sourceLanguage, String targetLanguage, String name, String source) {
        if (!"en".equals(sourceLanguage) || !"ru".equals(targetLanguage) || name == null || source == null) return null;
        Entry entry = EN_RU.get(name);
        return entry != null && entry.source.equals(source) && TranslationTokens.preserved(source, entry.translated)
                ? entry.translated : null;
    }
}
