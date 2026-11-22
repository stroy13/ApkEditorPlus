package com.gmail.heagoo.apkeditor.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts explicit binary resource IDs left in an old decoded manifest to the
 * symbolic names recorded by the decoded public.xml. AAPT2 accepts symbolic
 * references in manifest XML, but rejects both decimal and {@code @0x...}
 * literals for typed attributes.
 */
public final class ManifestResourceReferenceNormalizer {
    private static final Pattern PUBLIC_TAG = Pattern.compile("<public\\b([^>]*)>", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\b([a-zA-Z][a-zA-Z0-9_]*)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern REFERENCE = Pattern.compile(
            "([@?])(?:0[xX]([0-9a-fA-F]{1,8})(?![0-9a-fA-F])|([0-9]{1,10})(?![0-9]))");

    private ManifestResourceReferenceNormalizer() {
    }

    /**
     * Normalize the decoded manifest in place. Returns true when its contents
     * changed. The original file is retained until the replacement is complete.
     */
    public static boolean normalize(File manifest, File resRoot,
                                    ApkBuildPipeline.Logger logger) throws IOException {
        if (manifest == null || !manifest.isFile() || resRoot == null || !resRoot.isDirectory()) {
            return false;
        }
        Map<Integer, ResourceRef> byId = new HashMap<>();
        readPublicFiles(resRoot, byId);
        if (byId.isEmpty()) {
            log(logger, "Manifest reference normalization: public.xml entries=0");
            return false;
        }
        String original = new String(Files.readAllBytes(manifest.toPath()), StandardCharsets.UTF_8);
        Matcher matcher = REFERENCE.matcher(original);
        StringBuffer normalized = new StringBuffer(original.length());
        int converted = 0;
        int unresolved = 0;
        while (matcher.find()) {
            int id;
            try {
                String hex = matcher.group(2);
                String decimal = matcher.group(3);
                long parsed = Long.parseLong(hex != null ? hex : decimal,
                        hex != null ? 16 : 10);
                if (parsed > 0xffffffffL) {
                    throw new NumberFormatException("resource ID is too large");
                }
                id = (int) parsed;
            } catch (NumberFormatException ignored) {
                matcher.appendReplacement(normalized, Matcher.quoteReplacement(matcher.group()));
                unresolved++;
                continue;
            }
            ResourceRef ref = byId.get(id);
            String replacement = null;
            if (ref != null && isUsableName(ref.type) && isUsableName(ref.name)) {
                // Keep even bool references symbolic. A @bool/name manifest value
                // is type-checked by AAPT2 and retains resource qualifiers; using
                // the default true/false value would silently change behavior.
                replacement = matcher.group(1) + ref.type + "/" + ref.name;
            }
            if (replacement == null) {
                replacement = matcher.group();
                unresolved++;
            } else {
                converted++;
            }
            matcher.appendReplacement(normalized, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(normalized);
        log(logger, "Manifest reference normalization: public.xml entries=" + byId.size()
                + "; converted=" + converted + "; unresolved=" + unresolved);
        String result = normalized.toString();
        if (result.equals(original)) {
            return false;
        }
        File temporary = new File(manifest.getParentFile(), manifest.getName() + ".normalized.tmp");
        Files.write(temporary.toPath(), result.getBytes(StandardCharsets.UTF_8));
        try {
            try {
                Files.move(temporary.toPath(), manifest.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary.toPath(), manifest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary.toPath());
        }
        return true;
    }

    private static void readPublicFiles(File resRoot, Map<Integer, ResourceRef> byId)
            throws IOException {
        File[] dirs = resRoot.listFiles();
        if (dirs == null) throw new IOException("Cannot list resources: " + resRoot);
        for (File dir : dirs) {
            if (!dir.isDirectory() || !(dir.getName().equals("values")
                    || dir.getName().startsWith("values-"))) continue;
            File publicFile = new File(dir, "public.xml");
            if (!publicFile.isFile() || publicFile.length() > 16L * 1024 * 1024) continue;
            String xml = new String(Files.readAllBytes(publicFile.toPath()), StandardCharsets.UTF_8);
            Matcher tags = PUBLIC_TAG.matcher(xml);
            while (tags.find()) {
                Map<String, String> attrs = attributes(tags.group(1));
                String type = attrs.get("type");
                String name = attrs.get("name");
                String idText = attrs.get("id");
                if (type == null || name == null || idText == null) continue;
                try {
                    int id = (int) Long.parseLong(stripHex(idText), 16);
                    ResourceRef ref = new ResourceRef(type, name);
                    byId.put(id, ref);
                } catch (NumberFormatException ignored) {
                    // Ignore one malformed public entry and retain the rest.
                }
            }
        }
    }

    private static Map<String, String> attributes(String text) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = ATTRIBUTE.matcher(text == null ? "" : text);
        while (matcher.find()) result.put(matcher.group(1), matcher.group(2));
        return result;
    }

    private static String stripHex(String text) {
        String value = text.trim();
        return value.startsWith("0x") || value.startsWith("0X") ? value.substring(2) : value;
    }

    private static boolean isUsableName(String value) {
        return value != null && value.matches("[a-zA-Z0-9_.]+")
                && !value.equals(".") && !value.equals("..");
    }

    private static void log(ApkBuildPipeline.Logger logger, String message) {
        if (logger != null) logger.log(message);
    }

    private static final class ResourceRef {
        final String type;
        final String name;

        ResourceRef(String type, String name) {
            this.type = type;
            this.name = name;
        }
    }
}
