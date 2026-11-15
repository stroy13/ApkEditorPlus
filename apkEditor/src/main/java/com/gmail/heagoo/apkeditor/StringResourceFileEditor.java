package com.gmail.heagoo.apkeditor;

import com.gmail.heagoo.common.FileUtil;
import com.gmail.heagoo.common.TextFileReader;
import common.types.StringItem;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies edited and newly added string resources without discarding untouched XML lines. */
final class StringResourceFileEditor {
    private StringResourceFileEditor() { }

    static void save(File file, Map<String, String> modifiedValues) throws IOException {
        List<String> lines = new TextFileReader(file.getPath()).getLines();
        List<String> newLines = new ArrayList<>(lines.size() + modifiedValues.size());
        Set<String> savedStrings = new HashSet<>();
        int lastStringLine = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int pos = line.indexOf("<string name=\"");
            if (pos < 0) {
                newLines.add(line);
                continue;
            }
            int start = pos + "<string name=\"".length();
            int end = line.indexOf('\"', start);
            if (end < 0) {
                newLines.add(line);
                continue;
            }
            String name = line.substring(start, end);
            String newValue = modifiedValues.get(name);
            if (newValue == null) {
                newLines.add(line);
            } else {
                newLines.add(StringItem.toString(name, newValue, null));
                savedStrings.add(name);
                while (!line.contains("</string>") && i + 1 < lines.size()) {
                    line = lines.get(++i);
                    if (!line.contains("</string>")) continue;
                }
            }
            lastStringLine = newLines.size() - 1;
        }

        int insertAt = lastStringLine >= 0 ? lastStringLine + 1 : newLines.size();
        for (Map.Entry<String, String> entry : modifiedValues.entrySet()) {
            if (savedStrings.add(entry.getKey())) {
                newLines.add(insertAt++, StringItem.toString(entry.getKey(), entry.getValue(), null));
            }
        }
        FileUtil.writeToFile(file.getPath(), newLines);
    }
}
