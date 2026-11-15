package com.gmail.heagoo.apkeditor;

import common.types.StringItem;
import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.Assert.assertEquals;

public class StringItemXmlTest {
    @Test public void formattedFalseIsAValidAttribute() throws Exception {
        String item = StringItem.toString("example", "%s / %d", null);
        String xml = "<resources>" + item + "</resources>";
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        assertEquals("<string name=\"example\" formatted=\"false\">%s / %d</string>", item);
    }
}
