package com.gmail.heagoo.apkeditor.translate;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidUiTranslationsTest {
    @Test public void recognizesOnlyUnmodifiedKnownAndroidXStrings() {
        assertEquals("Готово", AndroidUiTranslations.find("en", "ru", "abc_action_mode_done", "Done"));
        assertEquals("Готово", AndroidUiTranslations.find("-en-rXC", "-ru", "abc_action_mode_done", "Done"));
        assertEquals("Введите запрос", AndroidUiTranslations.find("English", "Russian", "abc_search_hint", "Search..."));
        assertEquals("Перейти вверх", AndroidUiTranslations.find("en", "ru", "abc_action_bar_up_description", "Navigate up"));
        assertEquals("Fn +", AndroidUiTranslations.find("en", "ru", "abc_menu_function_shortcut_label", "Function+"));
        assertEquals("Копировать", AndroidUiTranslations.find("en", "ru", "copy", "Copy"));
        assertEquals("ВЫКЛ", AndroidUiTranslations.find("en", "ru", "abc_capital_off", "OFF"));
        assertEquals("Недопустимые данные", AndroidUiTranslations.find("en", "ru", "default_error_message", "Invalid input"));
        assertEquals("Развернуть", AndroidUiTranslations.find("en", "ru", "a11y_action_label_expand", "Expand"));
        assertEquals("Завершить вызов", AndroidUiTranslations.find("en", "ru", "call_notification_hang_up_action", "Hang Up"));
        assertEquals("Удалить", AndroidUiTranslations.find("en", "ru", "abc_menu_delete_shortcut_label", "Delete"));
        assertEquals("Свернуть", AndroidUiTranslations.find("en", "ru", "m3_bottom_sheet_collapse_description", "Collapse"));
        assertEquals("Закрыть", AndroidUiTranslations.find("en", "ru", "m3_bottom_sheet_dismiss_description", "Dismiss"));
        assertEquals("На устройстве не настроена безопасная блокировка экрана. Чтобы использовать учётные данные, настройте блокировку экрана.",
                AndroidUiTranslations.find("en", "ru", "generic_error_no_keyguard", "This device does not have a secure lock screen. To use device credentials, set up a screen lock."));
        assertEquals("Удалить", AndroidUiTranslations.find("en", "ru", "abc_menu_delete_shortcut_label", "delete"));
        assertNull(AndroidUiTranslations.find("en", "ru", "unknown_copy", "Copy"));
        assertNull(AndroidUiTranslations.find("en", "ru", "abc_action_mode_done", "Done now"));
        assertNull(AndroidUiTranslations.find("fr", "ru", "abc_action_mode_done", "Done"));
        assertNull(AndroidUiTranslations.find("en", "de", "abc_action_mode_done", "Done"));
        assertNull(AndroidUiTranslations.find(null, "ru", null, "Done"));
    }
}
