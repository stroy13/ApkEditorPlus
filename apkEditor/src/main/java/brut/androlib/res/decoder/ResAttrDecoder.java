/**
 * Copyright 2011 Ryszard Wiśniewski <brut.alll@gmail.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package brut.androlib.res.decoder;

import android.util.TypedValue;

import brut.androlib.AndrolibException;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.value.ResAttr;
import brut.androlib.res.data.value.ResBoolValue;
import brut.androlib.res.data.value.ResIntValue;
import brut.androlib.res.data.value.ResReferenceValue;
import brut.androlib.res.data.value.ResScalarValue;
import brut.androlib.res.data.value.ResValue;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
public class ResAttrDecoder {
    private static final int ANDROID_ATTR_THEME = 0x01010000;
    private static final int ANDROID_ATTR_LABEL = 0x01010001;
    private static final int ANDROID_ATTR_ICON = 0x01010002;
    private static final int ANDROID_ATTR_NAME = 0x01010003;
    private static final int ANDROID_ATTR_PERMISSION = 0x01010006;
    private static final int ANDROID_ATTR_READ_PERMISSION = 0x01010007;
    private static final int ANDROID_ATTR_PROTECTION_LEVEL = 0x01010009;
    private static final int ANDROID_ATTR_SHARED_USER_ID = 0x0101000b;
    private static final int ANDROID_ATTR_ENABLED = 0x0101000e;
    private static final int ANDROID_ATTR_EXPORTED = 0x01010010;
    private static final int ANDROID_ATTR_STATE_NOT_NEEDED = 0x01010016;
    private static final int ANDROID_ATTR_EXCLUDE_FROM_RECENTS = 0x01010017;
    private static final int ANDROID_ATTR_GRANT_URI_PERMISSIONS = 0x0101001b;
    private static final int ANDROID_ATTR_LAUNCH_MODE = 0x0101001d;
    private static final int ANDROID_ATTR_CONFIG_CHANGES = 0x0101001f;
    private static final int ANDROID_ATTR_RESOURCE = 0x01010025;
    private static final int ANDROID_ATTR_MIN_SDK = 0x0101020c;
    private static final int ANDROID_ATTR_VERSION_CODE = 0x0101021b;
    private static final int ANDROID_ATTR_VERSION_NAME = 0x0101021c;
    private static final int ANDROID_ATTR_WINDOW_SOFT_INPUT_MODE = 0x0101022b;
    private static final int ANDROID_ATTR_NO_HISTORY = 0x0101022d;
    private static final int ANDROID_ATTR_TARGET_SDK = 0x01010270;
    private static final int ANDROID_ATTR_MAX_SDK = 0x01010271;
    private static final int ANDROID_ATTR_ALLOW_BACKUP = 0x01010280;
    private static final int ANDROID_ATTR_REQUIRED = 0x0101028e;
    private static final int ANDROID_ATTR_STOP_WITH_TASK = 0x0101036a;
    private static final int ANDROID_ATTR_ISOLATED_PROCESS = 0x010103a9;
    private static final int ANDROID_ATTR_SUPPORTS_RTL = 0x010103af;
    private static final int ANDROID_ATTR_RELINQUISH_TASK_IDENTITY = 0x01010476;
    private static final int ANDROID_ATTR_PERSISTABLE_MODE = 0x0101042d;
    private static final int ANDROID_ATTR_AUTO_REMOVE_FROM_RECENTS = 0x01010447;
    private static final int ANDROID_ATTR_EXTRACT_NATIVE_LIBS = 0x010104ea;
    private static final int ANDROID_ATTR_FULL_BACKUP_CONTENT = 0x010104eb;
    private static final int ANDROID_ATTR_USES_CLEARTEXT_TRAFFIC = 0x010104ec;
    private static final int ANDROID_ATTR_AUTO_VERIFY = 0x010104ee;
    private static final int ANDROID_ATTR_RESIZEABLE_ACTIVITY = 0x010104f6;
    private static final int ANDROID_ATTR_SUPPORTS_PIP = 0x010104f7;
    private static final int ANDROID_ATTR_DIRECT_BOOT_AWARE = 0x01010505;
    private static final int ANDROID_ATTR_NETWORK_SECURITY_CONFIG = 0x01010527;
    private static final int ANDROID_ATTR_ROUND_ICON = 0x0101052c;
    private static final int ANDROID_ATTR_COMPILE_SDK = 0x01010572;
    private static final int ANDROID_ATTR_COMPILE_SDK_CODENAME = 0x01010573;
    private static final int ANDROID_ATTR_APP_COMPONENT_FACTORY = 0x0101057a;
    private static final int ANDROID_ATTR_USE_APP_ZYGOTE = 0x01010597;
    private static final int ANDROID_ATTR_FOREGROUND_SERVICE_TYPE = 0x01010599;
    private static final int ANDROID_ATTR_ZYGOTE_PRELOAD_NAME = 0x0101059d;
    private static final int ANDROID_ATTR_DATA_EXTRACTION_RULES = 0x0101063e;
    private static final int ANDROID_ATTR_ENABLE_ON_BACK_INVOKED_CALLBACK = 0x0101066c;

    private ResPackage mCurrentPackage;

    public String decode(int type, int value, String rawValue, int attrResId)
            throws AndrolibException {
        return decode(type, value, rawValue, attrResId, null);
    }

    public String decode(int type, int value, String rawValue, int attrResId,
                        String attrName) throws AndrolibException {
        int namedAttrId = knownFrameworkAttrId(attrName);
        int effectiveAttrId = attrResId;
        if (namedAttrId != 0 && attrResId != namedAttrId) {
            // A protected manifest can carry an app-package or decoy resource-map
            // ID for an android:* attribute. The binary XML name is the safer
            // source for the framework contract in that case.
            effectiveAttrId = namedAttrId;
        }

        // Decode framework enums, flags and typed references before consulting the
        // APK resource table. Protected APKs can have a usable manifest resource
        // map while their ARSC package is unavailable; the old early return in
        // this method preserved values such as "0x2" and "0x2db3" verbatim, which
        // makes aapt2 reject the decoded XML.
        ResScalarValue resValue = null;
        if (mCurrentPackage != null) {
            resValue = mCurrentPackage.getValueFactory().factory(type, value, rawValue);
        }
        String known = decodeKnownValue(type, value, rawValue, effectiveAttrId, resValue);
        if (known != null) {
            return known;
        }
        if (mCurrentPackage == null) {
            // No table is available for symbolic lookup. Keep the original string
            // only after all framework-typed conversions above have had a chance
            // to normalize it.
            return rawValue != null ? rawValue : "";
        }

        String decoded = null;
        if (effectiveAttrId > 0) {
            try {
                ResResSpec spec = mCurrentPackage.getResTable().getResSpec(effectiveAttrId);
                if (spec != null && spec.getDefaultResource() != null
                        && spec.getDefaultResource().getValue() instanceof ResAttr) {
                    decoded = ((ResAttr) spec.getDefaultResource().getValue())
                            .convertToResXmlFormat(resValue);
                }
            } catch (Exception ex) {
                // A framework mismatch must not abort manifest decoding. The scalar
                // encoder below still preserves the typed data.
            }
        }

        return decoded != null ? decoded : resValue.encodeAsResXmlAttr();
    }

    private String decodeKnownValue(int type, int value, String rawValue, int attrResId,
                                    ResScalarValue resValue) throws AndrolibException {
        if (isReferenceType(type)) {
            if (isBooleanAttribute(attrResId)) {
                String bool = decodeReferencedBoolean(value);
                if (bool != null) {
                    return bool;
                }
            }
            return formatReference(value, type == TypedValue.TYPE_ATTRIBUTE
                    || type == 0x08);
        }

        // Some protected writers incorrectly keep a numeric @id in the raw string
        // slot while marking the value as a string. Recover it only for attributes
        // whose framework contract is a reference; ordinary manifest strings must
        // remain untouched.
        if (isReferenceAttribute(attrResId) && rawValue != null) {
            Integer rawReference = parseReferenceId(rawValue);
            if (rawReference != null) {
                return formatReference(rawReference, false);
            }
            if (rawValue.startsWith("@") || rawValue.startsWith("?")) {
                return rawValue;
            }
        }

        if (isBooleanAttribute(attrResId)) {
            String bool = decodeBoolean(type, value, rawValue, resValue);
            if (bool != null) {
                return bool;
            }
            // A boolean may legally be backed by a bool resource. If that
            // resource cannot be inspected because the protected ARSC package is
            // unavailable, still normalize the decimal form to an explicit
            // resource-ID reference; "@213..." is not valid aapt2 XML syntax.
            Integer referencedId = parseReferenceId(rawValue);
            if (referencedId != null && (rawValue.startsWith("@")
                    || rawValue.startsWith("?"))) {
                return formatReference(referencedId, false);
            }
        }

        Integer scalar = integerValue(type, value, rawValue, resValue);
        if (scalar == null) {
            return null;
        }
        switch (attrResId) {
            case ANDROID_ATTR_PROTECTION_LEVEL:
                return renderProtectionLevel(scalar);
            case ANDROID_ATTR_CONFIG_CHANGES:
                return renderFlags(scalar,
                        new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048,
                                4096, 8192, 16384, 32768, 0x08000000, 0x10000000,
                                0x40000000, 0x80000000},
                        new String[]{"mcc", "mnc", "locale", "touchscreen", "keyboard",
                                "keyboardHidden", "navigation", "orientation", "screenLayout",
                                "uiMode", "screenSize", "smallestScreenSize", "density",
                                "layoutDirection", "colorMode", "grammaticalGender",
                                "resourcesUnused", "fontWeightAdjustment", "fontScale",
                                "assetsPaths"});
            case ANDROID_ATTR_LAUNCH_MODE:
                return exact(scalar, new int[]{0, 1, 2, 3, 4},
                        new String[]{"standard", "singleTop", "singleTask", "singleInstance",
                                "singleInstancePerTask"});
            case ANDROID_ATTR_WINDOW_SOFT_INPUT_MODE:
                return exact(scalar, new int[]{0, 1, 2, 3, 4, 5, 16, 32, 48},
                        new String[]{"adjustUnspecified", "stateUnchanged", "stateHidden",
                                "stateAlwaysHidden", "stateVisible", "stateAlwaysVisible",
                                "adjustResize", "adjustPan", "adjustNothing"});
            case ANDROID_ATTR_PERSISTABLE_MODE:
                return exact(scalar, new int[]{0, 1, 2},
                        new String[]{"persistNever", "persistAcrossReboots", "persistRootOnly"});
            case ANDROID_ATTR_FOREGROUND_SERVICE_TYPE:
                return renderFlags(scalar,
                        new int[]{1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096,
                                8192, 16384, 32768, 65536},
                        new String[]{"dataSync", "mediaPlayback", "phoneCall", "location",
                                "connectedDevice", "mediaProjection", "camera", "microphone",
                                "remoteMessaging", "systemExempted", "shortService",
                                "specialUse", "health", "remoteMessaging", "systemExempted",
                                "shortService"});
            default:
                return null;
        }
    }

    private String decodeBoolean(int type, int value, String rawValue,
                                 ResScalarValue resValue) throws AndrolibException {
        if (resValue instanceof ResBoolValue) {
            return ((ResBoolValue) resValue).getValue() ? "true" : "false";
        }
        if (resValue instanceof ResReferenceValue) {
            String bool = decodeReferencedBoolean(value);
            if (bool != null) {
                return bool;
            }
            return null;
        }
        if (rawValue != null) {
            String text = rawValue.trim();
            if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                return text.toLowerCase();
            }
            if (text.startsWith("@") || text.startsWith("?")) {
                Integer referencedId = parseReferenceId(text);
                if (referencedId != null) {
                    String referencedBoolean = decodeReferencedBoolean(referencedId);
                    if (referencedBoolean != null) {
                        return referencedBoolean;
                    }
                }
            }
        }
        if (type == TypedValue.TYPE_INT_BOOLEAN
                || (type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT)) {
            return value == 0 ? "false" : "true";
        }
        return null;
    }

    private String decodeReferencedBoolean(int id) throws AndrolibException {
        if (id == 0) {
            return null;
        }
        try {
            ResResSpec spec = mCurrentPackage.getResTable().getResSpec(id);
            if (spec == null || !spec.hasDefaultResource()) {
                return null;
            }
            ResValue value = spec.getDefaultResource().getValue();
            if (value instanceof ResBoolValue) {
                return ((ResBoolValue) value).getValue() ? "true" : "false";
            }
            if (value instanceof ResIntValue && "bool".equalsIgnoreCase(
                    ((ResIntValue) value).getType())) {
                return ((ResIntValue) value).getValue() == 0 ? "false" : "true";
            }
        } catch (Exception ignored) {
            // Keep the original typed reference when the bool resource is absent.
        }
        return null;
    }

    private String formatReference(int id, boolean theme) throws AndrolibException {
        if (id == 0) {
            return "@null";
        }
        try {
            ResResSpec spec = mCurrentPackage.getResTable().getResSpec(id);
            if (spec != null && spec.getName() != null && !spec.getName().isEmpty()) {
                // Protected tables commonly synthesize valid names such as
                // noname_0x..., *_dup_0x..., or dummy_ae_.... They are the only
                // symbols available for the decoded res/ tree and are preferable
                // to a numeric @0x... literal, which aapt2 rejects for typed
                // manifest references.
                return (theme ? "?" : "@") + spec.getFullName(mCurrentPackage,
                        theme && "attr".equals(spec.getType().getName()));
            }
        } catch (Exception ignored) {
            // Use the typed literal below when the app/framework table cannot resolve it.
        }
        return (theme ? "?" : "@") + String.format("0x%08x", id);
    }

    private static Integer integerValue(int type, int value, String rawValue,
                                       ResScalarValue resValue) {
        if (resValue instanceof ResIntValue && !(resValue instanceof ResReferenceValue)) {
            return ((ResIntValue) resValue).getValue();
        }
        if (type != TypedValue.TYPE_STRING) {
            return value;
        }
        return parseInteger(rawValue);
    }

    private static Integer parseReferenceId(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.startsWith("@") || text.startsWith("?")) {
            text = text.substring(1);
        }
        return parseInteger(text);
    }

    private static Integer parseInteger(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 0) {
            return null;
        }
        try {
            if (text.startsWith("0x") || text.startsWith("0X")) {
                return (int) Long.parseLong(text.substring(2), 16);
            }
            if (text.matches("[-+]?\\d+")) {
                return Integer.parseInt(text);
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }

    private static boolean isReferenceType(int type) {
        return type == TypedValue.TYPE_REFERENCE || type == TypedValue.TYPE_ATTRIBUTE
                || type == 0x07 || type == 0x08;
    }

    private static boolean isReferenceAttribute(int attrResId) {
        switch (attrResId) {
            case ANDROID_ATTR_THEME:
            case ANDROID_ATTR_LABEL:
            case ANDROID_ATTR_ICON:
            case ANDROID_ATTR_PERMISSION:
            case ANDROID_ATTR_READ_PERMISSION:
            case ANDROID_ATTR_RESOURCE:
            case ANDROID_ATTR_FULL_BACKUP_CONTENT:
            case ANDROID_ATTR_NETWORK_SECURITY_CONFIG:
            case ANDROID_ATTR_ROUND_ICON:
            case ANDROID_ATTR_DATA_EXTRACTION_RULES:
                return true;
            default:
                return false;
        }
    }

    private static boolean isBooleanAttribute(int attrResId) {
        switch (attrResId) {
            case ANDROID_ATTR_ENABLED:
            case ANDROID_ATTR_EXPORTED:
            case ANDROID_ATTR_STATE_NOT_NEEDED:
            case ANDROID_ATTR_EXCLUDE_FROM_RECENTS:
            case ANDROID_ATTR_GRANT_URI_PERMISSIONS:
            case ANDROID_ATTR_NO_HISTORY:
            case ANDROID_ATTR_ALLOW_BACKUP:
            case ANDROID_ATTR_REQUIRED:
            case ANDROID_ATTR_STOP_WITH_TASK:
            case ANDROID_ATTR_ISOLATED_PROCESS:
            case ANDROID_ATTR_SUPPORTS_RTL:
            case ANDROID_ATTR_RELINQUISH_TASK_IDENTITY:
            case ANDROID_ATTR_AUTO_REMOVE_FROM_RECENTS:
            case ANDROID_ATTR_EXTRACT_NATIVE_LIBS:
            case ANDROID_ATTR_USES_CLEARTEXT_TRAFFIC:
            case ANDROID_ATTR_AUTO_VERIFY:
            case ANDROID_ATTR_RESIZEABLE_ACTIVITY:
            case ANDROID_ATTR_SUPPORTS_PIP:
            case ANDROID_ATTR_DIRECT_BOOT_AWARE:
            case ANDROID_ATTR_USE_APP_ZYGOTE:
            case ANDROID_ATTR_ENABLE_ON_BACK_INVOKED_CALLBACK:
                return true;
            default:
                return false;
        }
    }

    private static String renderProtectionLevel(int value) {
        switch (value) {
            case 0: return "normal";
            case 1: return "dangerous";
            case 2: return "signature";
            case 3: return "signatureOrSystem";
            case 4: return "internal";
            case 16: return "system";
            case 32: return "development";
            case 64: return "appop";
            case 128: return "pre23";
            case 256: return "installer";
            case 512: return "verifier";
            case 1024: return "preinstalled";
            case 2048: return "setup";
            case 4096: return "instant";
            case 8192: return "runtime";
            case 16384: return "oem";
            case 32768: return "vendorPrivileged";
            case 65536: return "textClassifier";
            case 131072: return "configurator";
            case 262144: return "incidentReportApprover";
            case 524288: return "configurator";
            case 1048576: return "incidentReportApprover";
            case 2097152: return "appPredictor";
            case 4194304: return "module";
            case 8388608: return "companion";
            case 16777216: return "retailDemo";
            case 33554432: return "recents";
            case 67108864: return "role";
            case 134217728: return "knownSigner";
            default:
                return renderFlags(value,
                        new int[]{1, 2, 4, 16, 32, 64, 128, 256, 512, 1024, 2048,
                                4096, 8192, 16384, 32768, 65536, 524288, 1048576,
                                2097152, 4194304, 8388608, 16777216, 33554432, 67108864,
                                134217728},
                        new String[]{"dangerous", "signature", "internal", "system",
                                "development", "appop", "pre23", "installer", "verifier",
                                "preinstalled", "setup", "instant", "runtime", "oem",
                                "vendorPrivileged", "textClassifier", "configurator",
                                "incidentReportApprover", "appPredictor", "module",
                                "companion", "retailDemo", "recents", "role", "knownSigner"});
        }
    }

    private static String exact(int value, int[] values, String[] names) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return names[i];
            }
        }
        return null;
    }

    private static String renderFlags(int value, int[] flags, String[] names) {
        if (value == 0) {
            return null;
        }
        StringBuilder result = new StringBuilder();
        int remaining = value;
        for (int i = 0; i < flags.length; i++) {
            int flag = flags[i];
            if ((remaining & flag) == flag) {
                if (result.length() != 0) {
                    result.append('|');
                }
                result.append(names[i]);
                remaining &= ~flag;
            }
        }
        return remaining == 0 && result.length() != 0 ? result.toString() : null;
    }

    public String decodeManifestAttr(int attrResId) throws AndrolibException {
        if (attrResId != 0 && mCurrentPackage != null) {
            try {
                ResResSpec resResSpec = mCurrentPackage.getResTable().getResSpec(attrResId);
                if (resResSpec != null && resResSpec.getName() != null
                        && !isGeneratedName(resResSpec.getName())) {
                    return resResSpec.getName();
                }
            } catch (Exception ignored) {
                // Use the stable framework name below when the table is unavailable.
            }
        }
        return knownFrameworkAttrName(attrResId);
    }

    private static boolean isGeneratedName(String name) {
        return name.startsWith("noname_") || name.indexOf("_dup_") >= 0
                || name.startsWith("dummy_ae_") || name.startsWith("attr_0x");
    }

    private static int knownFrameworkAttrId(String name) {
        if (name == null) {
            return 0;
        }
        if ("theme".equals(name)) return ANDROID_ATTR_THEME;
        if ("label".equals(name)) return ANDROID_ATTR_LABEL;
        if ("icon".equals(name)) return ANDROID_ATTR_ICON;
        if ("name".equals(name)) return ANDROID_ATTR_NAME;
        if ("permission".equals(name)) return ANDROID_ATTR_PERMISSION;
        if ("readPermission".equals(name)) return ANDROID_ATTR_READ_PERMISSION;
        if ("protectionLevel".equals(name)) return ANDROID_ATTR_PROTECTION_LEVEL;
        if ("sharedUserId".equals(name)) return ANDROID_ATTR_SHARED_USER_ID;
        if ("enabled".equals(name)) return ANDROID_ATTR_ENABLED;
        if ("exported".equals(name)) return ANDROID_ATTR_EXPORTED;
        if ("stateNotNeeded".equals(name)) return ANDROID_ATTR_STATE_NOT_NEEDED;
        if ("excludeFromRecents".equals(name)) return ANDROID_ATTR_EXCLUDE_FROM_RECENTS;
        if ("grantUriPermissions".equals(name)) return ANDROID_ATTR_GRANT_URI_PERMISSIONS;
        if ("launchMode".equals(name)) return ANDROID_ATTR_LAUNCH_MODE;
        if ("configChanges".equals(name)) return ANDROID_ATTR_CONFIG_CHANGES;
        if ("resource".equals(name)) return ANDROID_ATTR_RESOURCE;
        if ("minSdkVersion".equals(name)) return ANDROID_ATTR_MIN_SDK;
        if ("versionCode".equals(name)) return ANDROID_ATTR_VERSION_CODE;
        if ("versionName".equals(name)) return ANDROID_ATTR_VERSION_NAME;
        if ("windowSoftInputMode".equals(name)) return ANDROID_ATTR_WINDOW_SOFT_INPUT_MODE;
        if ("noHistory".equals(name)) return ANDROID_ATTR_NO_HISTORY;
        if ("targetSdkVersion".equals(name)) return ANDROID_ATTR_TARGET_SDK;
        if ("maxSdkVersion".equals(name)) return ANDROID_ATTR_MAX_SDK;
        if ("allowBackup".equals(name)) return ANDROID_ATTR_ALLOW_BACKUP;
        if ("required".equals(name)) return ANDROID_ATTR_REQUIRED;
        if ("stopWithTask".equals(name)) return ANDROID_ATTR_STOP_WITH_TASK;
        if ("isolatedProcess".equals(name)) return ANDROID_ATTR_ISOLATED_PROCESS;
        if ("supportsRtl".equals(name)) return ANDROID_ATTR_SUPPORTS_RTL;
        if ("relinquishTaskIdentity".equals(name)) return ANDROID_ATTR_RELINQUISH_TASK_IDENTITY;
        if ("persistableMode".equals(name)) return ANDROID_ATTR_PERSISTABLE_MODE;
        if ("autoRemoveFromRecents".equals(name)) return ANDROID_ATTR_AUTO_REMOVE_FROM_RECENTS;
        if ("extractNativeLibs".equals(name)) return ANDROID_ATTR_EXTRACT_NATIVE_LIBS;
        if ("fullBackupContent".equals(name)) return ANDROID_ATTR_FULL_BACKUP_CONTENT;
        if ("usesCleartextTraffic".equals(name)) return ANDROID_ATTR_USES_CLEARTEXT_TRAFFIC;
        if ("autoVerify".equals(name)) return ANDROID_ATTR_AUTO_VERIFY;
        if ("resizeableActivity".equals(name)) return ANDROID_ATTR_RESIZEABLE_ACTIVITY;
        if ("supportsPictureInPicture".equals(name)) return ANDROID_ATTR_SUPPORTS_PIP;
        if ("directBootAware".equals(name)) return ANDROID_ATTR_DIRECT_BOOT_AWARE;
        if ("networkSecurityConfig".equals(name)) return ANDROID_ATTR_NETWORK_SECURITY_CONFIG;
        if ("roundIcon".equals(name)) return ANDROID_ATTR_ROUND_ICON;
        if ("compileSdkVersion".equals(name)) return ANDROID_ATTR_COMPILE_SDK;
        if ("compileSdkVersionCodename".equals(name)) return ANDROID_ATTR_COMPILE_SDK_CODENAME;
        if ("appComponentFactory".equals(name)) return ANDROID_ATTR_APP_COMPONENT_FACTORY;
        if ("useAppZygote".equals(name)) return ANDROID_ATTR_USE_APP_ZYGOTE;
        if ("foregroundServiceType".equals(name)) return ANDROID_ATTR_FOREGROUND_SERVICE_TYPE;
        if ("zygotePreloadName".equals(name)) return ANDROID_ATTR_ZYGOTE_PRELOAD_NAME;
        if ("dataExtractionRules".equals(name)) return ANDROID_ATTR_DATA_EXTRACTION_RULES;
        if ("enableOnBackInvokedCallback".equals(name)) {
            return ANDROID_ATTR_ENABLE_ON_BACK_INVOKED_CALLBACK;
        }
        return 0;
    }

    private static String knownFrameworkAttrName(int id) {
        switch (id) {
            case ANDROID_ATTR_THEME: return "theme";
            case ANDROID_ATTR_LABEL: return "label";
            case ANDROID_ATTR_ICON: return "icon";
            case ANDROID_ATTR_NAME: return "name";
            case ANDROID_ATTR_PERMISSION: return "permission";
            case ANDROID_ATTR_READ_PERMISSION: return "readPermission";
            case ANDROID_ATTR_PROTECTION_LEVEL: return "protectionLevel";
            case ANDROID_ATTR_SHARED_USER_ID: return "sharedUserId";
            case ANDROID_ATTR_ENABLED: return "enabled";
            case ANDROID_ATTR_EXPORTED: return "exported";
            case ANDROID_ATTR_STATE_NOT_NEEDED: return "stateNotNeeded";
            case ANDROID_ATTR_EXCLUDE_FROM_RECENTS: return "excludeFromRecents";
            case ANDROID_ATTR_GRANT_URI_PERMISSIONS: return "grantUriPermissions";
            case ANDROID_ATTR_LAUNCH_MODE: return "launchMode";
            case ANDROID_ATTR_CONFIG_CHANGES: return "configChanges";
            case ANDROID_ATTR_RESOURCE: return "resource";
            case ANDROID_ATTR_MIN_SDK: return "minSdkVersion";
            case ANDROID_ATTR_VERSION_CODE: return "versionCode";
            case ANDROID_ATTR_VERSION_NAME: return "versionName";
            case ANDROID_ATTR_WINDOW_SOFT_INPUT_MODE: return "windowSoftInputMode";
            case ANDROID_ATTR_NO_HISTORY: return "noHistory";
            case ANDROID_ATTR_TARGET_SDK: return "targetSdkVersion";
            case ANDROID_ATTR_MAX_SDK: return "maxSdkVersion";
            case ANDROID_ATTR_ALLOW_BACKUP: return "allowBackup";
            case ANDROID_ATTR_REQUIRED: return "required";
            case ANDROID_ATTR_STOP_WITH_TASK: return "stopWithTask";
            case ANDROID_ATTR_ISOLATED_PROCESS: return "isolatedProcess";
            case ANDROID_ATTR_SUPPORTS_RTL: return "supportsRtl";
            case ANDROID_ATTR_RELINQUISH_TASK_IDENTITY: return "relinquishTaskIdentity";
            case ANDROID_ATTR_PERSISTABLE_MODE: return "persistableMode";
            case ANDROID_ATTR_AUTO_REMOVE_FROM_RECENTS: return "autoRemoveFromRecents";
            case ANDROID_ATTR_EXTRACT_NATIVE_LIBS: return "extractNativeLibs";
            case ANDROID_ATTR_FULL_BACKUP_CONTENT: return "fullBackupContent";
            case ANDROID_ATTR_USES_CLEARTEXT_TRAFFIC: return "usesCleartextTraffic";
            case ANDROID_ATTR_AUTO_VERIFY: return "autoVerify";
            case ANDROID_ATTR_RESIZEABLE_ACTIVITY: return "resizeableActivity";
            case ANDROID_ATTR_SUPPORTS_PIP: return "supportsPictureInPicture";
            case ANDROID_ATTR_DIRECT_BOOT_AWARE: return "directBootAware";
            case ANDROID_ATTR_NETWORK_SECURITY_CONFIG: return "networkSecurityConfig";
            case ANDROID_ATTR_ROUND_ICON: return "roundIcon";
            case ANDROID_ATTR_COMPILE_SDK: return "compileSdkVersion";
            case ANDROID_ATTR_COMPILE_SDK_CODENAME: return "compileSdkVersionCodename";
            case ANDROID_ATTR_APP_COMPONENT_FACTORY: return "appComponentFactory";
            case ANDROID_ATTR_USE_APP_ZYGOTE: return "useAppZygote";
            case ANDROID_ATTR_FOREGROUND_SERVICE_TYPE: return "foregroundServiceType";
            case ANDROID_ATTR_ZYGOTE_PRELOAD_NAME: return "zygotePreloadName";
            case ANDROID_ATTR_DATA_EXTRACTION_RULES: return "dataExtractionRules";
            case ANDROID_ATTR_ENABLE_ON_BACK_INVOKED_CALLBACK:
                return "enableOnBackInvokedCallback";
            default: return null;
        }
    }

    public ResPackage getCurrentPackage() throws AndrolibException {
        if (mCurrentPackage == null) {
            throw new AndrolibException("Current package is null");
        }
        return mCurrentPackage;
    }

    public void setCurrentPackage(ResPackage currentPackage) {
        mCurrentPackage = currentPackage;
    }
}
