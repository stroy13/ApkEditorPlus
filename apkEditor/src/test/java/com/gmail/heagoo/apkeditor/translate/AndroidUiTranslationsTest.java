package com.gmail.heagoo.apkeditor.translate;

import org.junit.Test;
import static org.junit.Assert.*;

public class AndroidUiTranslationsTest {
    @Test public void recognizesOnlyUnmodifiedKnownAndroidXStrings() {
        assertEquals("Готово", AndroidUiTranslations.find("en", "ru", "abc_action_mode_done", "Done"));
        assertEquals("Перейти вверх", AndroidUiTranslations.find("en", "ru", "abc_action_bar_up_description", "Navigate up"));
        assertEquals("Fn +", AndroidUiTranslations.find("en", "ru", "abc_menu_function_shortcut_label", "Function+"));
        assertEquals("Копировать", AndroidUiTranslations.find("en", "ru", "copy", "Copy"));
        assertEquals("ВЫКЛ", AndroidUiTranslations.find("en", "ru", "abc_capital_off", "OFF"));
        assertEquals("Недопустимые данные", AndroidUiTranslations.find("en", "ru", "default_error_message", "Invalid input"));
        assertEquals("Развернуть", AndroidUiTranslations.find("en", "ru", "a11y_action_label_expand", "Expand"));
        assertEquals("Завершить вызов", AndroidUiTranslations.find("en", "ru", "call_notification_hang_up_action", "Hang Up"));
        assertEquals("Удалить", AndroidUiTranslations.find("en", "ru", "abc_menu_delete_shortcut_label", "delete"));
        assertNull(AndroidUiTranslations.find("en", "ru", "unknown_copy", "Copy"));
        assertNull(AndroidUiTranslations.find("en", "ru", "abc_action_mode_done", "Done now"));
        assertNull(AndroidUiTranslations.find("fr", "ru", "abc_action_mode_done", "Done"));
        assertNull(AndroidUiTranslations.find("en", "de", "abc_action_mode_done", "Done"));
        assertNull(AndroidUiTranslations.find(null, "ru", null, "Done"));
    }
}
