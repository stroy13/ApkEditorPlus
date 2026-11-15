package com.gmail.heagoo.apkeditor.plus;

import static org.junit.Assert.assertTrue;

import com.gmail.heagoo.neweditor.DefaultTokenHandler;
import com.gmail.heagoo.neweditor.Segment;
import com.gmail.heagoo.neweditor.Token;
import com.gmail.heagoo.neweditor.XmlSyntaxParser;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.SAXParserFactory;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/** Guards against XML highlighting silently falling back to plain text when syntax assets fail to load. */
public class XmlSyntaxHighlightAssetsTest {
    @Test
    public void xmlSyntaxModeLoadsOfflineAndTokenizesTagsAttributesAndValues() throws Exception {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("settings.gradle"))) root = root.getParent();
        if (root == null) throw new AssertionError("Repository root not found");
        Path syntax = root.resolve("apkEditorPlus/src/main/assets/syntax/xml.xml");
        String source = new String(Files.readAllBytes(syntax), StandardCharsets.UTF_8);
        assertTrue("syntax definition must not depend on a missing external DTD", !source.contains("DOCTYPE"));

        XmlSyntaxParser parser = new XmlSyntaxParser();
        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        reader.setContentHandler(parser);
        reader.setDTDHandler(parser);
        reader.setEntityResolver(parser);
        reader.setErrorHandler(parser);
        reader.parse(new InputSource(new StringReader(source)));

        String sample = "<string name=\"app_name\">APK Editor Plus</string>";
        Segment line = new Segment(sample.toCharArray(), 0, sample.length());
        DefaultTokenHandler tokens = new DefaultTokenHandler();
        parser.getTokenMarker().markTokens(null, tokens, line);
        boolean markup = false;
        boolean label = false;
        boolean literal = false;
        for (Token token = tokens.getTokens(); token != null; token = token.next) {
            markup |= token.id == Token.MARKUP;
            label |= token.id == Token.LABEL;
            literal |= token.id == Token.LITERAL1;
        }
        assertTrue("XML tags should be highlighted", markup);
        assertTrue("XML attribute names should be highlighted", label);
        assertTrue("XML attribute values should be highlighted", literal);
    }
}
