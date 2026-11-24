package com.gmail.heagoo.apkeditor.util;

import java.util.Locale;

/** Shared rendering for the live log view and the optional localized log file. */
public final class BuildLogDisplay {
    private static final String[][] RU = {
        {"=== APK Editor Plus build log ===", "=== Журнал сборки APK Editor Plus ==="},
        {"Full compose mode: ", "Режим полной сборки: "},
        {"Compose stage start: ", "Начало этапа: "},
        {"Compose stage end: ", "Завершён этап: "},
        {"Compose result: ", "Результат сборки: "},
        {"Resource tool preparation: ", "Подготовка инструментов: "},
        {"Manifest reference normalization: ", "Нормализация ссылок манифеста: "},
        {"Resource compose total: ", "Компоновка ресурсов: "},
        {"AAPT2 values scan: ", "AAPT2: проверка values: "},
        {"AAPT2 synthetic name scan: ", "AAPT2: поиск имён ресурсов: "},
        {"AAPT2 temporary resource copy: ", "AAPT2: копирование ресурсов: "},
        {"AAPT2 synthetic name rewrite: ", "AAPT2: обработка имён: "},
        {"AAPT2 temporary synthetic drawable aliases: ", "AAPT2: временных имён drawable: "},
        {"AAPT2 resource scan and staging: ", "AAPT2: подготовка ресурсов: "},
        {"AAPT2 synthetic drawable names restored in resource table: ", "AAPT2: имена ресурсов восстановлены: "},
        {"AAPT2 compile: ", "AAPT2: компиляция: "},
        {"AAPT2 link: ", "AAPT2: компоновка: "},
        {"AAPT2 manifest: ", "AAPT2: манифест: "},
        {"DEX change check: ", "Проверка изменений DEX: "},
        {"Smali assembler: ", "Компилятор smali: "},
        {"Merge file collection and mapping: ", "Подготовка файлов для объединения: "},
        {"Merge ZIP engine: ", "Объединение ZIP: "},
        {"Merge Java ZIP pipeline: ", "Конвейер Java ZIP: "},
        {"Signature schemes preference: ", "Схемы подписи: "},
        {"Signing key preference: ", "Ключ подписи: "},
        {"Source manifest: ", "Исходный манифест: "},
        {"Effective replacement manifest: ", "Манифест для замены: "},
        {"Output manifest: ", "Итоговый манифест: "},
        {"Signing options: ", "Параметры подписи: "},
        {"Replacements: ", "Замены: "},
        {"Replacement files validated", "Файлы замен проверены"},
        {"Certificate subject: ", "Субъект сертификата: "},
        {"Temp unsigned: ", "Временный неподписанный APK: "},
        {"Temp signed: ", "Временный подписанный APK: "},
        {"Merged size: ", "Размер объединённого APK: "},
        {"Signed size: ", "Размер подписанного APK: "},
        {"Native library ZIP policy verified: ", "Проверка политики ZIP для native-библиотек: "},
        {"Android package parser returned no component-rich PackageInfo; retrying with identity-only flags", "Парсер Android не вернул PackageInfo с компонентами; повторная проверка только идентичности"},
        {"Android package parser identity-only retry succeeded", "Повторная проверка идентичности через парсер Android успешна"},
        {"Android package parser rejected generated APK; parsed output manifest: ", "Парсер Android отклонил собранный APK; разобранный итоговый манифест: "},
        {"Android package parser rejected the generated APK; preserving signed output for diagnosis: ", "Парсер Android отклонил собранный APK; подписанный файл сохранён для диагностики: "},
        {"Framework parser diagnostics: ", "Диагностика системного парсера: "},
        {"Android package parser cannot read generated APK; ", "Системный парсер Android не может прочитать собранный APK; "},
        {"SignHelper failed: ", "Ошибка SignHelper: "},
        {"apksig sign: ", "apksig: подпись: "},
        {"apksig sign finished: ", "apksig: подпись завершена: "},
        {"apksig verify: ", "apksig: проверка: "},
        {"AAPT2 resource archive validated: ", "AAPT2: архив ресурсов проверен: "},
        {"AAPT2 manifest: restored synthetic Android attribute names", "AAPT2: восстановлены синтетические имена атрибутов Android в манифесте"},
        {"Merge entries: ", "Элементы объединения: "},
        {"Unsigned APK assembled and validated: ", "Неподписанный APK собран и проверен: "},
        {"Signed APK structure verified", "Структура подписанного APK проверена"},
        {"ERROR: ", "ОШИБКА: "}, {"Caused by: ", "Причина: "},
        {"--- Environment ---", "--- Окружение ---"},
        {"--- Image replacements ---", "--- Замены изображений ---"},
        {"--- Prepare interfaces ---", "--- Подготовка интерфейсов ---"},
        {"--- DEX class renames ---", "--- Переименование классов DEX ---"},
        {"--- Output ---", "--- Результат ---"}, {"--- Result ---", "--- Итог ---"},
        {"--- Failure ---", "--- Ошибка ---"},
        {"App package: ", "Пакет приложения: "}, {"Android SDK: ", "Android SDK: "},
        {"Device: ", "Устройство: "}, {"Source APK: ", "Исходный APK: "},
        {"Source APK size: ", "Размер исходного APK: "}, {"Package name: ", "Имя пакета: "},
        {"Working dir: ", "Рабочая папка: "}, {"Old app name (ARSC): ", "Старое имя приложения (ARSC): "},
        {"New app name (ARSC): ", "Новое имя приложения (ARSC): "},
        {"New package name (ARSC): ", "Новое имя пакета (ARSC): "},
        {"Class renames: ", "Переименования классов: "}, {"Extra interfaces: ", "Дополнительные интерфейсы: "},
        {"Image replacement count: ", "Количество замен изображений: "},
        {"signed structure OK: ", "Структура подписанного APK проверена: "},
        {"structure OK: ", "Структура проверена: "},
        {"AAPT: undeclared app attributes ", "AAPT: найдены необъявленные атрибуты приложения "},
        {"AAPT: older manifest validator rejected modern elements; retrying with validation warnings. Original error: ", "AAPT: повторяем проверку с совместимыми предупреждениями манифеста. Исходная ошибка: "},
        {"SignHelper: source=", "SignHelper: источник="},
        {"build() started", "Сборка начата"},
        {"SignHelper finished successfully", "Подпись APK успешно завершена"},
        {"Android package parser OK: ", "Проверка APK пройдена: "},
        {"Output rejected by Android package parser; removing unusable APK: ", "APK отклонён системой Android и будет удалён: "},
        {"Temporary files cleaned", "Временные файлы удалены"},
        {"Published target: ", "Готовый APK: "},
        {"Source: ", "Источник: "}, {"Target: ", "Результат: "},
        {"Stage: merge", "Этап: объединение"}, {"Stage: sign", "Этап: подпись"},
        {"Stage: verify", "Этап: проверка"}, {"Stage: publish", "Этап: сохранение"},
        {"merge ", "Добавление в APK: "}, {"add ", "Добавление в APK: "}, {"skip ", "Пропуск: "},
        {"build() succeeded", "Сборка успешно завершена"},
        {"build() failed: ", "Ошибка сборки: "},
        {"Finished: ", "Завершено: "}, {"Started: ", "Начало: "},
        {"Total elapsed: ", "Общее время: "}, {"Log file: ", "Файл журнала: "}
    };
    private BuildLogDisplay() {}
    public static String render(String line, String language) {
        if (line == null) return null;
        line = formatTimes(line, language);
        if (!"ru".equals(language)) return line;
        int end = line.startsWith("[") ? line.indexOf("] ") : -1;
        String prefix = end >= 0 ? line.substring(0, end + 2) : "";
        String body = end >= 0 ? line.substring(end + 2) : line;
        for (String[] pair : RU) if (body.startsWith(pair[0])) {
            String rest = body.substring(pair[0].length());
            if (pair[0].startsWith("Compose stage")) rest = stage(rest);
            else if (pair[0].equals("Compose result: ")) {
                if (rest.startsWith("success")) rest = "успех" + rest.substring(7);
                else if (rest.startsWith("failed")) rest = "ошибка" + rest.substring(6);
                else if (rest.startsWith("cancelled")) rest = "отменено" + rest.substring(9);
            } else if (pair[0].equals("Published target: ")) {
                rest = localizePublishedTarget(rest);
            } else if (pair[0].equals("Android package parser OK: ")) {
                rest = localizePackageSummary(rest);
            } else if (pair[0].equals("Framework parser diagnostics: ")) {
                rest = rest.replace("legacy parser rejected:", "старый парсер отклонил:")
                        .replace("legacy parser unavailable:", "старый парсер недоступен:")
                        .replace("legacy parser accepted archive", "старый парсер принял архив")
                        .replace("legacy parser returned null", "старый парсер вернул null")
                        .replace("AssetManager failed:", "ошибка AssetManager:")
                        .replace("AssetManager opened AndroidManifest.xml", "AssetManager открыл AndroidManifest.xml")
                        .replace("addAssetPath returned 0", "addAssetPath вернул 0");
            } else if (pair[0].equals("AAPT: undeclared app attributes ")) {
                rest = rest.replace("; retrying with temporary declarations", "; повторяем сборку с временными объявлениями атрибутов");
            } else if (pair[0].equals("merge ") || pair[0].equals("add ")) {
                rest = rest.replace("sourceMethod=", "метод источника=")
                        .replace("outputMethod=", "метод результата=")
                        .replace("aligned=", "выравнивание=")
                        .replace("size=", "размер=")
                        .replace("replacement=", "замена=");
            }
            return prefix + pair[1] + rest;
        }
        return line;
    }
    private static final java.util.regex.Pattern TIMESTAMP = java.util.regex.Pattern.compile("^(\\[[^\\]]*? \\+)([0-9]+)ms(\\])");
    private static final java.util.regex.Pattern ELAPSED = java.util.regex.Pattern.compile("(?<![A-Za-z0-9_/.-])([0-9]+)\\s*ms\\b");

    static String formatDuration(long millis, String language) {
        boolean ru = "ru".equals(language);
        if (millis < 1000) return millis + (ru ? " мс" : " ms");
        long seconds = millis / 1000;
        long fraction = millis % 1000;
        String decimal = Long.toString(seconds % 60);
        if (fraction != 0) decimal += String.format(Locale.US, ".%03d", fraction).replaceFirst("0+$", "");
        if (millis < 60000) return decimal + (ru ? " с" : " s");
        return (seconds / 60) + (ru ? " мин " : " min ") + decimal + (ru ? " с" : " s");
    }

    private static String formatTimes(String line, String language) {
        java.util.regex.Matcher timestamp = TIMESTAMP.matcher(line);
        if (timestamp.find()) {
            try {
                String replacement = timestamp.group(1) + formatDuration(Long.parseLong(timestamp.group(2)), language) + timestamp.group(3);
                line = timestamp.replaceFirst(java.util.regex.Matcher.quoteReplacement(replacement));
            } catch (NumberFormatException ignored) { }
        }
        int separator = line.startsWith("[") ? line.indexOf("] ") : -1;
        String header = separator >= 0 ? line.substring(0, separator + 2) : "";
        String body = separator >= 0 ? line.substring(separator + 2) : line;
        // Only our own timing messages: arbitrary diagnostics and file names are
        // left untouched, even if they contain a number followed by "ms".
        if (!(body.startsWith("Compose stage end: ") || body.startsWith("Resource tool preparation: ")
                || body.startsWith("Resource compose total: ") || body.startsWith("AAPT2 values scan: ")
                || body.startsWith("AAPT2 synthetic name scan: ") || body.startsWith("AAPT2 temporary resource copy: ")
                || body.startsWith("AAPT2 synthetic name rewrite: ") || body.startsWith("AAPT2 resource scan and staging: ")
                || body.startsWith("AAPT2 synthetic drawable names restored in resource table: ")
                || body.startsWith("AAPT2 compile: ") || body.startsWith("AAPT2 link: ")
                || body.startsWith("Merge file collection and mapping: ") || body.startsWith("Merge ZIP engine: ")
                || body.startsWith("Total elapsed: "))) return line;
        java.util.regex.Matcher duration = ELAPSED.matcher(body);
        StringBuffer result = new StringBuffer();
        while (duration.find()) {
            try { duration.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(formatDuration(Long.parseLong(duration.group(1)), language))); }
            catch (NumberFormatException e) { duration.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(duration.group())); }
        }
        duration.appendTail(result);
        return header + result;
    }
    private static String localizePublishedTarget(String value) {
        java.util.regex.Matcher size = java.util.regex.Pattern.compile(" \\(([0-9]+) bytes\\)$").matcher(value);
        if (!size.find()) return value;
        try {
            long count = Long.parseLong(size.group(1));
            long lastTwo = count % 100;
            long last = count % 10;
            String unit = lastTwo >= 11 && lastTwo <= 14 ? "байт"
                    : last == 1 ? "байт" : last >= 2 && last <= 4 ? "байта" : "байт";
            return value.substring(0, size.start()) + " (" + count + " " + unit + ")";
        } catch (NumberFormatException ignored) { return value; }
    }

    private static String localizePackageSummary(String value) {
        String[][] fields = {
                {"package=", "пакет="}, {"versionCode=", "код версии="}, {"versionName=", "версия="},
                {"minSdk=", "мин. SDK="}, {"targetSdk=", "целевой SDK="}, {"application=", "приложение="},
                {"metadataMode=", "режим метаданных="}, {"components={", "компоненты={"},
                {"activities=", "действия="}, {"services=", "службы="},
                {"receivers=", "получатели="}, {"providers=", "провайдеры="}, {"permissions=", "разрешения="},
                {"splitName=", "имя split="}, {"configForSplit=", "конфигурация split="},
                {"isFeatureSplit=", "feature split="}, {"isSplitRequired=", "требуется split="},
                {"requiredSplitTypes=", "требуемые типы split="}, {"splitTypes=", "типы split="},
                {"extractNativeLibs=", "извлечение native-библиотек="}, {"useEmbeddedDex=", "встроенный DEX="}
        };
        for (String[] field : fields) value = value.replace(field[0], field[1]);
        return value;
    }

    private static String stage(String name) {
        String[][] names = {{"resource change scan", "проверка изменений ресурсов"}, {"smali change scan", "проверка изменений smali"}, {"plan build", "планирование сборки"}, {"prepare and compile resources", "компоновка ресурсов"}, {"assemble ", "компиляция "}, {"merge APK", "объединение APK"}, {"sign and verify", "подпись и проверка"}, {"merge unsigned APK", "объединение неподписанного APK"}, {"normalize unsigned APK", "упаковка неподписанного APK"}, {"prepare original entries", "подготовка исходных файлов"}, {"cleanup", "очистка"}};
        for (String[] pair : names) if (name.startsWith(pair[0])) return pair[1] + name.substring(pair[0].length());
        return name;
    }
    public static String resolveLanguage(String preference, String systemLanguage) {
        String language = "system".equals(preference) ? systemLanguage : preference;
        return "ru".equals(language == null ? "" : language.toLowerCase(Locale.ROOT)) ? "ru" : "en";
    }
}
