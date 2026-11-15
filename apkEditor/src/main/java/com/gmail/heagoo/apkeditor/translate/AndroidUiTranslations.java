package com.gmail.heagoo.apkeditor.translate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Exact-match AndroidX and Android system UI English-to-Russian translations.
 *
 * Values adapted from AndroidX AppCompat/Material strings.xml and Android system resource translations,
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
        add(result, "call_notification_answer_action", "Answer", "Ответить");
        add(result, "call_notification_decline_action", "Decline", "Отклонить");
        add(result, "call_notification_screening_text", "Screening an incoming call", "Обрабатывается входящий вызов");
        add(result, "action_call_notification_ongoing_text", "Ongoing call", "Текущий вызов");
        add(result, "default_error_msg", "Unknown error", "Неизвестная ошибка");
        add(result, "default_popup_window_title", "Pop-Up Window", "Всплывающее окно");
        add(result, "fingerprint_dialog_touch_sensor", "Touch the fingerprint sensor", "Коснитесь датчика отпечатков пальцев");
        add(result, "fingerprint_error_no_fingerprints", "No fingerprints enrolled", "Отпечатки пальцев не добавлены");
        add(result, "fingerprint_error_user_canceled", "Fingerprint operation canceled by user.", "Операция со сканером отпечатков отменена пользователем.");
        add(result, "fingerprint_not_recognized", "Not recognized", "Отпечаток не распознан");
        add(result, "generic_error_no_device_credential", "This device does not have a secure lock screen.", "На устройстве не настроена безопасная блокировка экрана.");
        add(result, "generic_error_no_keyguard", "This device does not have a secure lock screen. To use device credentials, set up a screen lock.", "На устройстве не настроена безопасная блокировка экрана. Чтобы использовать учётные данные, настройте блокировку экрана.");
        add(result, "generic_error_user_canceled", "Authentication canceled by user.", "Аутентификация отменена пользователем.");
        add(result, "in_progress", "In progress", "Выполняется");
        add(result, "indeterminate", "Partially checked", "Частично отмечено");
        add(result, "m3_bottom_sheet_cancel_description", "Cancel", "Отмена");
        add(result, "m3_bottom_sheet_collapse_description", "Collapse", "Свернуть");
        add(result, "m3_bottom_sheet_dismiss_description", "Dismiss", "Закрыть");
        add(result, "m3_bottom_sheet_drag_handle_description", "Drag handle", "Маркер перемещения панели");
        add(result, "m3_bottom_sheet_expand_description", "Expand", "Развернуть");
        add(result, "m3_dialog", "Dialog", "Диалоговое окно");
        add(result, "m3_search_bar_search", "Search", "Поиск");
        add(result, "m3_snackbar_dismiss", "Dismiss", "Закрыть");
        add(result, "m3_alert", "Alert", "Предупреждение");
        add(result, "m3_suggestions_available", "Suggestions below", "Ниже доступны подсказки");
        add(result, "m3_not_selected", "Not selected", "Не выбрано");
        add(result, "not_selected", "Not selected", "Не выбрано");
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
        if (!"en".equals(languageCode(sourceLanguage)) || !"ru".equals(languageCode(targetLanguage))
                || name == null || source == null) return null;
        Entry entry = EN_RU.get(name);
        if (entry == null) return null;
        boolean exact = entry.source.equals(source) || entry.source.equalsIgnoreCase(source);
        // AndroidX bundles appear with both the Unicode ellipsis and three ASCII dots.
        if (!exact && "abc_search_hint".equals(name)) exact = "Search...".equals(source);
        if (!exact && "fingerprint_error_hw_not_present".equals(name)) exact = "No fingerprint sensor".equalsIgnoreCase(source);
        String fullCredentialError = "This device does not have a secure lock screen. To use device credentials, set up a screen lock.";
        if (!exact && ("generic_error_no_device_credential".equals(name) || "generic_error_no_keyguard".equals(name)))
            exact = fullCredentialError.equals(source);
        String translated = entry.translated;
        if (exact && fullCredentialError.equals(source))
            translated = "На устройстве не настроена безопасная блокировка экрана. Чтобы использовать учётные данные, настройте блокировку экрана.";
        return exact && TranslationTokens.preserved(source, translated) ? translated : null;
    }

    private static String languageCode(String value) {
        if (value == null) return null;
        String tag = value.trim();
        if (tag.startsWith("-") && tag.matches("-[A-Za-z]{2,3}(?:-r[A-Za-z]{2,3})?"))
            return ResourceLanguageTag.languageOnlyFromQualifier(tag);
        String lower = tag.toLowerCase(java.util.Locale.ROOT);
        if (lower.equals("english") || lower.equals("английский") || lower.equals("английский язык")) return "en";
        if (lower.equals("russian") || lower.equals("русский") || lower.equals("русский язык")) return "ru";
        return java.util.Locale.forLanguageTag(tag).getLanguage();
    }
}
