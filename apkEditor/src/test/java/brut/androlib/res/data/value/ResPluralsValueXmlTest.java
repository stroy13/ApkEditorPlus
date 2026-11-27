package brut.androlib.res.data.value;

import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilderFactory;

import brut.androlib.res.data.ResConfigFlags;
import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResType;
import brut.androlib.res.data.ResTypeSpec;
import brut.util.Duo;
import brut.util.KXmlSerializer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Regression coverage for values XML emitted from plural bags. */
public class ResPluralsValueXmlTest {

    @Test
    public void nullStringPoolItemStillProducesWellFormedPluralsXml() throws Exception {
        ResTable table = new ResTable();
        ResPackage pkg = new ResPackage(table, 0x7f, "com.example");
        table.addPackage(pkg, true);
        ResTypeSpec type = new ResTypeSpec("plurals", table, pkg, 1, 1);
        pkg.addType(type);
        ResResSpec spec = new ResResSpec(new ResID(0x7f010000), "broken_plural", pkg,
                type, false);
        pkg.addResSpec(spec);
        type.addResSpec(spec);
        ResType config = new ResType(new ResConfigFlags());
        ResPluralsValue plurals = new ResPluralsValue(new ResReferenceValue(pkg, 0, null),
                items(new ResStringValue(null, 0)));
        ResResource resource = new ResResource(config, spec, plurals);

        StringWriter output = new StringWriter();
        KXmlSerializer serializer = new KXmlSerializer();
        serializer.setOutput(output);
        serializer.startTag(null, "resources");
        plurals.serializeToResValuesXml(serializer, resource);
        serializer.endTag(null, "resources");
        serializer.flush();

        String xml = output.toString();
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
                new InputSource(new StringReader(xml)));
        assertTrue(xml.contains("<plurals name=\"broken_plural\">")
                && xml.contains("<item quantity=\"other\"></item>"));
        assertEquals(0, serializer.getDepth());
    }

    @SuppressWarnings("unchecked")
    private static Duo<Integer, ResScalarValue>[] items(ResScalarValue value) {
        return new Duo[]{new Duo<Integer, ResScalarValue>(
                ResPluralsValue.BAG_KEY_PLURALS_START, value)};
    }
}
