/**
 * Copyright 2014 Ryszard Wiśniewski <brut.alll@gmail.com>
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

import com.google.common.io.LittleEndianDataInputStream;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import brut.androlib.AndrolibException;
import brut.androlib.res.data.CountingInputStream;
import brut.androlib.res.data.ResConfigFlags;
import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResType;
import brut.androlib.res.data.ResTypeSpec;
import brut.androlib.res.data.value.ResBagValue;
import brut.androlib.res.data.value.ResBoolValue;
import brut.androlib.res.data.value.DummyResourceTypes;
import brut.androlib.res.data.value.ResReferenceValue;
import brut.androlib.res.data.value.ResFileValue;
import brut.androlib.res.data.value.ResIntBasedValue;
import brut.androlib.res.data.value.ResScalarValue;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.data.value.ResValue;
import brut.androlib.res.data.value.ResValueFactory;
import brut.util.Duo;
import brut.util.ExtDataInput;

/**
 * @author Ryszard Wiśniewski <brut.alll@gmail.com>
 */
// Modified a lot by Pujiang
public class ARSCDecoder {
    private final static short ENTRY_FLAG_COMPLEX = 0x0001;
    private final static short ENTRY_FLAG_PUBLIC = 0x0002;
    private final static short ENTRY_FLAG_WEAK = 0x0004;
    private final static short ENTRY_FLAG_COMPACT = 0x0008;
    // ResTable_type flags. Bundletool enables sparse encoding for APK variants
    // whose effective minSdk is 32 (Android 12L/S_V2).
    private static final int TYPE_FLAG_SPARSE = 0x01;
    private static final int TYPE_FLAG_OFFSET16 = 0x02;
    private static final int NO_ENTRY = -1;
    // A map entry must fit in its containing type chunk. This additional cap
    // prevents a corrupt count from forcing a large object-array allocation even
    // when a malformed chunk advertises an implausibly large size.
    private static final int MAX_COMPLEX_ENTRY_ITEMS = 65536;
    // A resource table should contain only a small number of package chunks. Keep
    // malformed packageCount values from turning into an unbounded array
    // allocation before the first package can be validated.
    private static final int MAX_PACKAGE_COUNT = 0xffff;
    private static final Logger LOGGER = Logger.getLogger(ARSCDecoder.class.getName());
    private static final int KNOWN_CONFIG_BYTES = 56;
    private final ExtDataInput mIn;
    private final ResTable mResTable;
    private final CountingInputStream mCountIn;
    private final List<FlagsOffset> mFlagsOffsets;
    private final boolean mKeepBroken;
    // Resource id provider, most likely from R.smali
    private ResourceIdProvider idProvider;
    private boolean[] mIsFileValues;
    private boolean mIsFileValue;
    private boolean mApkProtected;
    // readTableTypeSpec call readTableType
    private byte[] perfBuf = new byte[12];
    private Header mHeader;
    private StringBlock mTableStrings;
    private StringBlock mTypeNames;
    private StringBlock mSpecNames;
    private ResPackage mPkg;
    private ResTypeSpec mTypeSpec;
    private ResType mType;
    private int mResId;
    private int mTypeIdOffset = 0;
    private boolean[] mMissingResSpecs;
    // Set once addMissingResSpecs() has handled the current type spec.
    private boolean mMissingResSpecsDone;
    private HashMap<Integer, ResTypeSpec> mResTypeSpecs = new HashMap<>();

    private ARSCDecoder(InputStream arscStream, ResTable resTable, ResourceIdProvider idProvider, boolean storeFlagsOffsets, boolean keepBroken, boolean apkProtected) {
        this.idProvider = idProvider;
        this.mApkProtected = apkProtected;
        arscStream = mCountIn = new CountingInputStream(arscStream);
        if (storeFlagsOffsets) {
            mFlagsOffsets = new ArrayList<FlagsOffset>();
        } else {
            mFlagsOffsets = null;
        }
        // We need to explicitly cast to DataInput as otherwise the constructor is ambiguous.
        // We choose DataInput instead of InputStream as ExtDataInput wraps an InputStream in
        // a DataInputStream which is big-endian and ignores the little-endian behavior.
        mIn = new ExtDataInput((DataInput) new LittleEndianDataInputStream(arscStream));
        mResTable = resTable;
        mKeepBroken = keepBroken;
    }

    public static ARSCData decode(InputStream arscStream, boolean findFlagsOffsets, boolean keepBroken)
            throws AndrolibException {
        return decode(arscStream, findFlagsOffsets, keepBroken, new ResTable(), null, false);
    }

    public static ARSCData decode(InputStream arscStream, boolean findFlagsOffsets, boolean keepBroken,
                                  ResTable resTable, ResourceIdProvider idProvider, boolean apkProtected)
            throws AndrolibException {
        try {
            ARSCDecoder decoder = new ARSCDecoder(
                    arscStream, resTable, idProvider, findFlagsOffsets, keepBroken, apkProtected);
            //TimeDumper timer = new TimeDumper(true);
            ResPackage[] pkgs = decoder.readTableHeader();
            logPackageSummary(pkgs, apkProtected, keepBroken);
            //timer.lastTime("Decode Time");
            return new ARSCData(pkgs, decoder.mFlagsOffsets == null
                    ? null
                    : decoder.mFlagsOffsets.toArray(new FlagsOffset[0]), resTable);
        } catch (IOException ex) {
            throw new AndrolibException("Could not decode arsc file", ex);
        }
    }

    private ResPackage[] readTableHeader() throws IOException, AndrolibException {
        nextChunkCheckType(Header.TYPE_TABLE);
        int packageCount = mIn.readInt();
        if (packageCount < 0 || packageCount > MAX_PACKAGE_COUNT) {
            throw new AndrolibException("Invalid resource package count: " + packageCount);
        }

        mTableStrings = StringBlock.read(mIn);
        //dumpStrings(mTableStrings);
        ResPackage[] packages = new ResPackage[packageCount];

        nextChunk();
        // Nearly all time consumed in following code
        int found = 0;
        while (found < packageCount && mHeader.type != Header.TYPE_NONE) {
            if (mHeader.type == Header.TYPE_PACKAGE) {
                packages[found++] = readTablePackage();
            } else {
                // Not a package: never abort the table, skip whatever we do not know.
                skipChunkAndReadNext("table");
            }
        }
        if (found < packageCount) {
            LOGGER.warning("Resource table declares " + packageCount + " package(s), found " + found);
            ResPackage[] trimmed = new ResPackage[found];
            System.arraycopy(packages, 0, trimmed, 0, found);
            return trimmed;
        }
        return packages;
    }

    private static boolean isStringResourceSpec(ResResSpec spec) {
        if (spec == null || spec.getType() == null) {
            return false;
        }
        if ("string".equalsIgnoreCase(spec.getType().getName())) {
            return true;
        }
        for (ResResource resource : spec.listResources()) {
            if (resource != null && resource.getValue() instanceof ResStringValue) {
                return true;
            }
        }
        return false;
    }

    private static void logPackageSummary(ResPackage[] packages, boolean protectedApk,
                                          boolean keepBroken) {
        if (packages == null) {
            LOGGER.info("decoded packages=null; protected=" + protectedApk
                    + "; keepBroken=" + keepBroken);
            return;
        }
        StringBuilder result = new StringBuilder("decoded packages=")
                .append(packages.length).append("; protected=")
                .append(protectedApk).append("; keepBroken=").append(keepBroken);
        for (ResPackage pkg : packages) {
            int stringSpecs = 0;
            int stringResources = 0;
            if (pkg != null) {
                for (ResResSpec spec : pkg.listResSpecs()) {
                    if (isStringResourceSpec(spec)) {
                        stringSpecs++;
                        if (spec.getAllResources() != null) {
                            stringResources += spec.getAllResources().size();
                        }
                    }
                }
                result.append(" [id=0x").append(Integer.toHexString(pkg.getId()))
                        .append(", name=").append(pkg.getName())
                        .append(", specs=").append(pkg.getResSpecCount())
                        .append(", stringSpecs=").append(stringSpecs)
                        .append(", stringResources=").append(stringResources)
                        .append(", configs=").append(pkg.getConfigs().size()).append(']');
            } else {
                result.append(" [null]");
            }
        }
        LOGGER.info(result.toString());
    }

    private void dumpStrings(StringBlock strBlock) {
//        int count = strBlock.getCount();
//        Log.d("DEBUG", "count=" + count);
//        for (int i = 0; i < count; i++) {
//            String v = strBlock.getString(i);
//            if (v.equals("WhatsApp")) {
//                Log.d("DEBUG", "string[" + i + "]=" + v);
//            }
//        }
    }

    private ResPackage readTablePackage() throws IOException, AndrolibException {
        checkChunkType(Header.TYPE_PACKAGE);
        int id = mIn.readInt();

        if (id == 0) {
            // This means we are dealing with a Library Package, we should just temporarily
            // set the packageId to the next available id . This will be set at runtime regardless, but
            // for Apktool's use we need a non-zero packageId.
            // AOSP indicates 0x02 is next, as 0x01 is system and 0x7F is private.
            id = 2;
            if (mResTable.getPackageOriginal() == null && mResTable.getPackageRenamed() == null) {
                mResTable.setSharedLibrary(true);
            }
        }

        String name = mIn.readNullEndedString(128, true);
        /* typeStrings */
        mIn.skipInt();
        /* lastPublicType */
        mIn.skipInt();
        /* keyStrings */
        mIn.skipInt();
        /* lastPublicKey */
        mIn.skipInt();

        mTypeNames = StringBlock.read(mIn);
        mSpecNames = StringBlock.read(mIn);

        // Added by Pujiang to revise the invalid name to _rr
        if (mApkProtected) {
            int invalidIdx = 0;
            StringBuilder sb = new StringBuilder();
            sb.append('_');
            sb.append('r');
            sb.append('r');
            String header = sb.toString();
            for (int i = 0; i < mSpecNames.getCount(); ++i) {
                String specName = mSpecNames.getString(i);
                // Not empty but invalid
                if (specName != null && !specName.equals("") &&
                        !specName.matches("[a-zA-Z0-9_\\.]+")) {
                    //Log.d("DEBUG", "rename " + specName + " to " + header + invalidIdx);
                    mSpecNames.setString(i, header + invalidIdx);
                    invalidIdx += 1;
                } else if ("do".equals(specName)) {
                    mSpecNames.setString(i, "do" + header + invalidIdx);
                    invalidIdx += 1;
                } else if ("if".equals(specName)) {
                    mSpecNames.setString(i, "if" + header + invalidIdx);
                    invalidIdx += 1;
                } else if ("for".equals(specName)) {
                    mSpecNames.setString(i, "for" + header + invalidIdx);
                    invalidIdx += 1;
                }
            }
        }

        // Added by Pujiang
        this.mIsFileValues = new boolean[mTypeNames.getCount() + 32];
        Arrays.fill(mIsFileValues, false);
        for (int i = 0; i < mTypeNames.getCount(); i++) {
            String curName = mTypeNames.getString(i);
            if (curName.equals("ani") || curName.equals("animator")
                    || curName.equals("drawable") || curName.equals("interpolator")
                    || curName.equals("layout") || curName.equals("menu")
                    || curName.equals("mipmap") || curName.equals("raw")
                    || curName.equals("transition") || curName.equals("xml")) {
                mIsFileValues[i] = true;
            }
        }

        mResId = id << 24;
        mPkg = new ResPackage(mResTable, id, name);

        nextChunk();
        // Library / type-spec chunks belong to this package; unknown chunks (overlayable,
        // overlayable policy, staged aliases on new Android releases) are skipped but do
        // not end the package, otherwise the following type specs would be lost.
        while (mHeader.type == Header.TYPE_LIBRARY
                || mHeader.type == Header.TYPE_SPEC_TYPE
                || isSkippableChunk(mHeader.type)) {
            if (mHeader.type == Header.TYPE_LIBRARY) {
                readLibraryType();
            } else if (mHeader.type == Header.TYPE_SPEC_TYPE) {
                // Nearly all the time consumed here
                readTableTypeSpec();
            } else {
                skipChunkAndReadNext("package");
            }
        }

        return mPkg;
    }

    private void readLibraryType() throws AndrolibException, IOException {
        checkChunkType(Header.TYPE_LIBRARY);
        int libraryCount = mIn.readInt();

        int packageId;
        String packageName;

        for (int i = 0; i < libraryCount; i++) {
            packageId = mIn.readInt();
            packageName = mIn.readNullEndedString(128, true);
            LOGGER.info(String.format("Decoding (%s), pkgId: %d", packageName, packageId));
        }

        while (nextChunk().type == Header.TYPE_TYPE) {
            readTableTypeSpec();
        }
    }

    private ResTypeSpec readTableTypeSpec() throws AndrolibException, IOException {
        mTypeSpec = readSingleTableTypeSpec();
        addTypeSpec(mTypeSpec);

        int type = nextChunk().type;
        ResTypeSpec resTypeSpec;

        while (type == Header.TYPE_SPEC_TYPE) {
            resTypeSpec = readSingleTableTypeSpec();
            addTypeSpec(resTypeSpec);
            type = nextChunk().type;
        }

        // Nearly all time consumed here
        while (type == Header.TYPE_TYPE || isSkippableChunk((short) type)) {
            if (type == Header.TYPE_TYPE) {
                readTableType();

                // skip "TYPE 8 chunks" and/or padding data at the end of this chunk
                skipToChunkEnd();
            } else {
                // Unknown chunk in the middle of a type: skip it and keep the type.
                LOGGER.warning(String.format("Skipping unsupported chunk type 0x%04x in type spec", type));
                skipToChunkEnd();
            }

            type = nextChunk().type;

            addMissingResSpecs();
        }

        return mTypeSpec;
    }

    private ResTypeSpec readSingleTableTypeSpec() throws AndrolibException, IOException {
        checkChunkType(Header.TYPE_SPEC_TYPE);
        int id = mIn.readUnsignedByte();
        if (id == 0 || id > mTypeNames.getCount()) {
            throw new AndrolibException("Invalid resource type-spec ID: " + id);
        }
        if (mIn.skipBytes(3) != 3) {
            throw new IOException("Truncated resource type-spec header");
        }
        int entryCount = mIn.readInt();
        if (entryCount < 0 || entryCount > 0xffff) {
            throw new AndrolibException("Invalid resource type-spec entry count: " + entryCount);
        }
        long flagsBytes = (long) entryCount * 4;
        requireChunkBytes(flagsBytes, "type-spec flags");

        if (mFlagsOffsets != null) {
            mFlagsOffsets.add(new FlagsOffset(mCountIn.getCount(), entryCount));
        }

        /* flags */
        if (mIn.skipBytes(entryCount * 4) != entryCount * 4) {
            throw new IOException("Truncated resource type-spec flags");
        }
        mTypeSpec = new ResTypeSpec(mTypeNames.getString(id - 1), mResTable, mPkg, id, entryCount);
        mPkg.addType(mTypeSpec);
        this.mMissingResSpecsDone = false;
        return mTypeSpec;
    }

    private ResType readTableType() throws IOException, AndrolibException {
        checkChunkType(Header.TYPE_TYPE);
        // ResTable_type fields after the common chunk header:
        // id (u8), flags (u8), reserved (u16), entryCount (u32),
        // entriesStart (u32). The configuration follows these fields.
        mIn.readFully(perfBuf);
        int typeId = perfBuf[0] & 0xff;
        int typeFlags = perfBuf[1] & 0xff;
        if (typeId == 0 || typeId > mTypeNames.getCount()) {
            throw new AndrolibException("Invalid resource type ID: " + typeId);
        }
        ResTypeSpec typeSpec = mResTypeSpecs.get(typeId);
        if (typeSpec == null) {
            throw new AndrolibException("Resource type has no matching type-spec: " + typeId);
        }
        mResId = (0xff000000 & mResId) | typeSpec.getId() << 16;
        mTypeSpec = typeSpec;
        if ((typeFlags & (TYPE_FLAG_SPARSE | TYPE_FLAG_OFFSET16))
                == (TYPE_FLAG_SPARSE | TYPE_FLAG_OFFSET16)) {
            throw new AndrolibException("Resource type cannot use sparse and offset16 indexes together");
        }
        if ((typeFlags & TYPE_FLAG_SPARSE) != 0) {
            // Keep the fact that the input used a sparse type-entry index. This
            // is diagnostic state today, and also prevents later writers from
            // accidentally treating the table as an ordinary dense table.
            mResTable.setSparseResources(true);
        }
        if (typeId < mIsFileValues.length) {
            mIsFileValue = this.mIsFileValues[typeId - 1];
        } else {
            mIsFileValue = false;
        }

        int encodedEntryCount = readLittleEndianInt(perfBuf, 4);
        int entriesStart = readLittleEndianInt(perfBuf, 8);
        if (encodedEntryCount < 0 || encodedEntryCount > 0xffff) {
            throw new AndrolibException("Invalid resource entry count: " + encodedEntryCount);
        }
        if (entriesStart < mHeader.headerSize || entriesStart > mHeader.chunkSize) {
            throw new AndrolibException("Invalid resource entriesStart: " + entriesStart);
        }

        // A sparse type chunk stores the number of present entries here, while
        // the preceding type-spec stores the full entry-ID range. Dense chunks
        // use the same value for both. Keeping these counts separate is the key
        // difference between an API-32 bundle variant and a legacy resources.arsc.
        int totalEntryCount = typeSpec == null ? encodedEntryCount : typeSpec.getEntryCount();
        if (totalEntryCount < 0 || totalEntryCount > 0xffff) {
            throw new AndrolibException("Invalid resource type-spec entry count: " + totalEntryCount);
        }

        ResConfigFlags flags = readConfigFlags();
        int offsetTableWidth = (typeFlags & TYPE_FLAG_SPARSE) != 0
                || (typeFlags & TYPE_FLAG_OFFSET16) == 0 ? 4 : 2;
        int offsetTableStart = mHeader.startPosition + entriesStart - encodedEntryCount * offsetTableWidth;

        // For some APKs there is a disconnect between the reported size of the
        // config and the current stream position. Preserve the existing tolerant
        // behavior, but never silently attempt to rewind a forward-only stream.
        skipToPosition(offsetTableStart, "resource entry offset table");

        int[] entryOffsets;
        if ((typeFlags & TYPE_FLAG_SPARSE) != 0) {
            entryOffsets = decodeSparseEntryOffsets(
                    mIn.readIntArray(encodedEntryCount), totalEntryCount);
        } else if ((typeFlags & TYPE_FLAG_OFFSET16) != 0) {
            entryOffsets = new int[encodedEntryCount];
            for (int i = 0; i < encodedEntryCount; i++) {
                int offset = mIn.readUnsignedShort();
                entryOffsets[i] = offset == 0xffff ? NO_ENTRY : offset * 4;
            }
            totalEntryCount = encodedEntryCount;
        } else {
            entryOffsets = mIn.readIntArray(encodedEntryCount);
            totalEntryCount = encodedEntryCount;
        }

        mMissingResSpecs = new boolean[totalEntryCount];
        Arrays.fill(mMissingResSpecs, true);

        if (flags.isInvalid) {
            String resName = mTypeSpec.getName() + flags.getQualifiers();
            if (mKeepBroken) {
                LOGGER.warning("Invalid config flags detected: " + resName);
            } else {
                LOGGER.warning("Invalid config flags detected. Dropping resources: " + resName);
            }
        }

        mType = flags.isInvalid && !mKeepBroken ? null : mPkg.getOrCreateConfig(flags);
        Map<Integer, EntryData> offsetsToEntryData = new TreeMap<Integer, EntryData>();
        int entriesStartAbsolute = mHeader.startPosition + entriesStart;

        // Offsets are byte offsets from entriesStart. Sparse entries are sorted
        // by resource ID, not necessarily by their physical offset, so collect
        // offsets first and read them in physical order rather than assuming a
        // dense index order. This also handles padding and shared entry offsets.
        for (int offset : entryOffsets) {
            if (offset == NO_ENTRY) {
                continue;
            }
            if (offset < 0 || offset > mHeader.chunkSize - entriesStart) {
                throw new AndrolibException("Resource entry offset out of bounds: " + offset);
            }
            if (!offsetsToEntryData.containsKey(offset)) {
                offsetsToEntryData.put(offset, null);
            }
        }
        for (Integer offset : offsetsToEntryData.keySet()) {
            skipToPosition(entriesStartAbsolute + offset, "resource entry");
            try {
                offsetsToEntryData.put(offset, readEntryData());
            } catch (AndrolibException ex) {
                if (!mKeepBroken) {
                    throw ex;
                }
                // Framework tables can contain newer entry forms that this
                // resource model cannot represent. Do not let one malformed or
                // unsupported entry abort the whole framework load; the caller
                // can still use the other configurations and the parser gate
                // will report the skipped type in diagnostics.
                LOGGER.warning("Skipping malformed resource type 0x"
                        + Integer.toHexString(typeId) + " at offset " + offset
                        + ": " + ex.getMessage());
                skipToChunkEnd();
                return mType;
            }
        }

        for (int i = 0; i < entryOffsets.length; i++) {
            if (entryOffsets[i] != NO_ENTRY) {
                if (i >= mMissingResSpecs.length) {
                    throw new AndrolibException("Resource entry index out of bounds: " + i);
                }
                mMissingResSpecs[i] = false;
                mResId = (mResId & 0xffff0000) | i;
                EntryData entryData = offsetsToEntryData.get(entryOffsets[i]);
                if (entryData == null) {
                    throw new AndrolibException("Missing resource entry at offset " + entryOffsets[i]);
                }
                readEntry(entryData);
            }
        }

        return mType;
    }

    private static int readLittleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xff)
                | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16)
                | ((data[offset + 3] & 0xff) << 24);
    }

    /**
     * Converts the API-32 sparse index ({@code entryId, offset / 4}) into the
     * dense ID-indexed representation used by the existing resource model.
     * Package-private for focused JVM tests; the method intentionally performs
     * strict ordering/range checks so a corrupt table cannot shift subsequent
     * entries and produce a misleading manifest.
     */
    static int[] decodeSparseEntryOffsets(int[] encoded, int totalEntryCount)
            throws AndrolibException {
        if (totalEntryCount < 0 || totalEntryCount > 0xffff) {
            throw new AndrolibException("Invalid sparse resource entry range: " + totalEntryCount);
        }
        int[] offsets = new int[totalEntryCount];
        Arrays.fill(offsets, NO_ENTRY);
        int previousIndex = -1;
        for (int raw : encoded) {
            int index = raw & 0xffff;
            int offset = (raw >>> 16) & 0xffff;
            if (index <= previousIndex) {
                throw new AndrolibException("Sparse resource entries are not strictly sorted");
            }
            if (index >= totalEntryCount) {
                throw new AndrolibException("Sparse resource entry ID out of bounds: " + index
                        + " (entryCount=" + totalEntryCount + ")");
            }
            offsets[index] = offset * 4;
            previousIndex = index;
        }
        return offsets;
    }

    private void skipToPosition(int target, String what) throws IOException, AndrolibException {
        long distance = (long) target - mCountIn.getCount();
        if (distance < 0 || distance > Integer.MAX_VALUE) {
            throw new AndrolibException("Cannot seek backwards to " + what + ": target=" + target
                    + ", current=" + mCountIn.getCount());
        }
        if (distance != 0) {
            int skipped = mIn.skipBytes((int) distance);
            if (skipped != distance) {
                throw new IOException("Truncated " + what + ": expected " + distance
                        + " bytes, skipped " + skipped);
            }
        }
    }

    private long remainingChunkBytes() {
        return (long) mHeader.endPosition - mCountIn.getCount();
    }

    private void requireChunkBytes(long bytes, String what) throws AndrolibException {
        if (bytes < 0 || bytes > remainingChunkBytes()) {
            throw new AndrolibException("Resource " + what + " exceeds type chunk: need "
                    + bytes + " bytes, remaining " + remainingChunkBytes());
        }
    }

    private EntryData readEntryData() throws IOException, AndrolibException {
        requireChunkBytes(8, "entry header");
        int sizeOrKey = mIn.readUnsignedShort();
        int rawFlags = mIn.readUnsignedShort();
        int keyOrData = mIn.readInt();
        EntryData entryData = new EntryData();

        // Android 14 introduced compact entries. They are relevant here because
        // a current AAPT2 may emit them in an APK whose variant still has an
        // effective minSdk of 32. The compact eight-byte form stores the key in
        // the first uint16, the value type in the high byte of flags, and the
        // value data in the final uint32.
        if ((rawFlags & ENTRY_FLAG_COMPACT) != 0) {
            int valueType = (rawFlags >>> 8) & 0xff;
            entryData.mFlags = (short) rawFlags;
            entryData.mSpecNamesId = sizeOrKey;
            entryData.mValue = readValue((byte) valueType, keyOrData);
            return entryData;
        }

        if (sizeOrKey < 8) {
            throw new AndrolibException("Entry size is under 8 bytes: " + sizeOrKey);
        }
        if (sizeOrKey > 8) {
            int extra = sizeOrKey - 8;
            requireChunkBytes(extra, "extended entry header");
            if (mIn.skipBytes(extra) != extra) {
                throw new IOException("Truncated extended resource entry");
            }
        }

        short flags = (short) rawFlags;
        ResValue value = (flags & ENTRY_FLAG_COMPLEX) == 0 ? readValue() : readComplexEntry();
        entryData.mFlags = flags;
        entryData.mSpecNamesId = keyOrData;
        entryData.mValue = value;
        return entryData;
    }

    // Pujiang: Revise the name if we can get it from other providers (like R.smali)
    private String getResName(int resId, int specNamesId) {
        String name = null;
        if (idProvider == null) {
            name = mSpecNames.getString(specNamesId);
        } else {
            name = idProvider.getNameById(resId);
            if (name == null) {
                name = mSpecNames.getString(specNamesId);
            }
        }
        return name;
    }

    private void readEntry(EntryData entryData) throws AndrolibException {
        int specNamesId = entryData.mSpecNamesId;
        ResValue value = entryData.mValue;

        if (mTypeSpec.isString() && value instanceof ResFileValue) {
            value = new ResStringValue(value.toString(), ((ResFileValue) value).getRawIntValue());
        }
        if (mType == null) {
            return;
        }

        ResID resId = new ResID(mResId);
        ResResSpec spec;
        if (mPkg.hasResSpec(resId)) {
            spec = mPkg.getResSpec(resId);

            if (spec.isDummyResSpec()) {
                removeResSpec(spec);

                //spec = new ResResSpec(resId, mSpecNames.getString(specNamesId), mPkg, mTypeSpec);
                spec = new ResResSpec(resId, getResName(resId.id, specNamesId), mPkg, mTypeSpec, mApkProtected);
                mPkg.addResSpec(spec);
                addResSpecToType(spec);
            }
        } else {
            //spec = new ResResSpec(resId, mSpecNames.getString(specNamesId), mPkg, mTypeSpec);
            spec = new ResResSpec(resId, getResName(resId.id, specNamesId), mPkg, mTypeSpec, mApkProtected);
            mPkg.addResSpec(spec);
            addResSpecToType(spec);
        }
        ResResource res = new ResResource(mType, spec, value);

        try {
            mType.addResource(res);
            spec.addResource(res);
        } catch (AndrolibException ex) {
            if (mKeepBroken) {
                mType.addResource(res, true);
                spec.addResource(res, true);
                LOGGER.warning(String.format("Duplicate res igonred: %s", res.toString()));
            } else {
                throw ex;
            }
        }
        mPkg.addResource(res);
    }

    private ResBagValue readComplexEntry() throws IOException, AndrolibException {
//        int parent = mIn.readInt();
//        int count = mIn.readInt();
        requireChunkBytes(8, "complex entry header");
        int head[] = mIn.readIntArray(2);
        int parent = head[0];
        int count = head[1];
        if (count < 0 || count > MAX_COMPLEX_ENTRY_ITEMS) {
            throw new AndrolibException("Invalid complex resource entry count: " + count
                    + " (parent=0x" + Integer.toHexString(parent)
                    + ", streamOffset=" + mCountIn.getCount()
                    + ", typeChunkStart=" + mHeader.startPosition
                    + ", typeChunkSize=" + mHeader.chunkSize + ")");
        }
        long rawSize = (long) (4 + 8) * count;
        requireChunkBytes(rawSize, "complex entry values");

        ResValueFactory factory = mPkg.getValueFactory();
        Duo<Integer, ResScalarValue>[] items = new Duo[count];
        ResIntBasedValue resValue;
        int resId;

        byte[] raw = new byte[(int) rawSize];
        mIn.readFully(raw);
        for (int i = 0; i < count; i++) {
//            resId = mIn.readInt();
//            resValue = readValue();
            int offset = i * 12;
            resId = (raw[offset + 3] & 0xff) << 24 | (raw[offset + 2] & 0xff) << 16
                    | (raw[offset + 1] & 0xff) << 8 | (raw[offset] & 0xff);
            byte type = raw[offset + 7];
            int data = (raw[offset + 11] & 0xff) << 24 | (raw[offset + 10] & 0xff) << 16
                    | (raw[offset + 9] & 0xff) << 8 | (raw[offset + 8] & 0xff);
            if (((raw[offset + 5] & 0xff) << 8 | (raw[offset + 4] & 0xff)) != 8) {
                LOGGER.fine("Resource bag value size is not 8 bytes");
            }
            resValue = readValue(type, data);

            if (resValue instanceof ResScalarValue) {
                items[i] = new Duo<Integer, ResScalarValue>(resId, (ResScalarValue) resValue);
            } else {
                resValue = new ResStringValue(resValue.toString(), resValue.getRawIntValue());
                items[i] = new Duo<Integer, ResScalarValue>(resId, (ResScalarValue) resValue);
            }
        }

        return factory.bagFactory(parent, items, mTypeSpec);
    }

    private ResIntBasedValue readValue(byte type, int data) throws IOException, AndrolibException {
//		/* size */mIn.skipCheckShort((short) 8);
//		/* zero */mIn.skipCheckByte((byte) 0);
//        byte type = mIn.readByte();
//        int data = mIn.readInt();

        // Modified by pujiang to get better perf
        boolean isStyled = false;
        String rawStr = "";
        String value = "";
        if ((mResId & 0xff000000) != 0x1000000) {
            rawStr = mTableStrings.getString(data);
            value = mTableStrings.getHTML(data, rawStr);
            isStyled = mTableStrings.isStyledString();
        }

        return type == TypedValue.TYPE_STRING
                ? mPkg.getValueFactory().factory(value, data, mIsFileValue, rawStr, isStyled)
                : mPkg.getValueFactory().factory(type, data, null);
    }

    private ResIntBasedValue readValue() throws IOException, AndrolibException {
        requireChunkBytes(8, "value");
//      /* size */mIn.skipCheckShort((short) 8);
//      /* zero */mIn.skipCheckByte((byte) 0);
//        byte type = mIn.readByte();
//        int data = mIn.readInt();

        byte[] raw = new byte[8];
        mIn.readFully(raw);
        byte type = raw[3];
        int data = (raw[7] & 0xff) << 24 | (raw[6] & 0xff) << 16
                | (raw[5] & 0xff) << 8 | (raw[4] & 0xff);

        return readValue(type, data);
        // Modified by pujiang to get better perf
//        return type == TypedValue.TYPE_STRING
//                ? mPkg.getValueFactory().factory((mResId & 0xff000000) == 0x1000000 ? "" : mTableStrings.getHTML(data), data, mIsFileValue)
//                : mPkg.getValueFactory().factory(type, data, null);
    }

    private ResConfigFlags readConfigFlags() throws IOException, AndrolibException {
        int size = mIn.readInt();
        int read = 28;

        if (size < 28) {
            throw new AndrolibException("Config size < 28");
        }
        if ((long) size - 4 > remainingChunkBytes()) {
            throw new AndrolibException("Config size exceeds type chunk: " + size
                    + " bytes, remaining " + (remainingChunkBytes() + 4));
        }

        boolean isInvalid = false;

        int index = 0;
        byte[] raw = new byte[24];
        mIn.readFully(raw);

//        short mcc = mIn.readShort();
//        short mnc = mIn.readShort();
        short mcc = (short) ((raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff));
        index += 2;
        short mnc = (short) ((raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff));
        index += 2;

//        char[] language = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), 'a');
//        char[] country = this.unpackLanguageOrRegion(mIn.readByte(), mIn.readByte(), '0');
        char[] language = this.unpackLanguageOrRegion(raw[index++], raw[index++], 'a');
        char[] country = this.unpackLanguageOrRegion(raw[index++], raw[index++], '0');

//        byte orientation = mIn.readByte();
//        byte touchscreen = mIn.readByte();
        byte orientation = raw[index++];
        byte touchscreen = raw[index++];

//        int density = mIn.readUnsignedShort();
        int density = (raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff);
        index += 2;

//        byte keyboard = mIn.readByte();
//        byte navigation = mIn.readByte();
//        byte inputFlags = mIn.readByte();
//		/* inputPad0 */mIn.skipBytes(1);
        byte keyboard = raw[index++];
        byte navigation = raw[index++];
        byte inputFlags = raw[index++];
        index++;

//        short screenWidth = mIn.readShort();
//        short screenHeight = mIn.readShort();
        short screenWidth = (short) ((raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff));
        index += 2;
        short screenHeight = (short) ((raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff));
        index += 2;

//        short sdkVersion = mIn.readShort();
//		/* minorVersion, now must always be 0 */mIn.skipBytes(2);
        short sdkVersion = (short) ((raw[index + 1] & 0xff) << 8 | (raw[index] & 0xff));
        index += 2;
        /* minorVersion, now must always be 0 */
        index += 2;

        byte screenLayout = 0;
        byte uiMode = 0;
        short smallestScreenWidthDp = 0;
        if (size >= 32) {
//            screenLayout = mIn.readByte();
//            uiMode = mIn.readByte();
//            smallestScreenWidthDp = mIn.readShort();
            byte[] buf = new byte[4];
            mIn.readFully(buf);
            screenLayout = buf[0];
            uiMode = buf[1];
            smallestScreenWidthDp = (short) ((buf[3] & 0xff) << 8 | (buf[2] & 0xff));
            read = 32;
        }

        short screenWidthDp = 0;
        short screenHeightDp = 0;
        if (size >= 36) {
//            screenWidthDp = mIn.readShort();
//            screenHeightDp = mIn.readShort();
            byte[] buf = new byte[4];
            mIn.readFully(buf);
            screenWidthDp = (short) ((buf[1] & 0xff) << 8 | (buf[0] & 0xff));
            screenHeightDp = (short) ((buf[3] & 0xff) << 8 | (buf[2] & 0xff));
            read = 36;
        }

        char[] localeScript = null;
        char[] localeVariant = null;
        if (size >= 48) {
            localeScript = readScriptOrVariantChar(4).toCharArray();
            localeVariant = readScriptOrVariantChar(8).toCharArray();
            read = 48;
        }

        byte screenLayout2 = 0;
        byte colorMode = 0;
        if (size >= 52) {
            screenLayout2 = mIn.readByte();
            colorMode = mIn.readByte();
            mIn.skipBytes(2); // reserved padding
            read = 52;
        }

        if (size >= 56) {
            mIn.skipBytes(4);
            read = 56;
        }

        int exceedingSize = size - KNOWN_CONFIG_BYTES;
        if (exceedingSize > 0) {
            byte[] buf = new byte[exceedingSize];
            read += exceedingSize;
            mIn.readFully(buf);
            BigInteger exceedingBI = new BigInteger(1, buf);

            if (exceedingBI.equals(BigInteger.ZERO)) {
                //LOGGER.fine(String
                //        .format("Config flags size > %d, but exceeding bytes are all zero, so it should be ok.",
                //                KNOWN_CONFIG_BYTES));
            } else {
                //LOGGER.warning(String.format("Config flags size > %d. Exceeding bytes: 0x%X.",
                //        KNOWN_CONFIG_BYTES, exceedingBI));
                isInvalid = true;
            }
        }

        int remainingSize = size - read;
        if (remainingSize > 0) {
            mIn.skipBytes(remainingSize);
        }

        return new ResConfigFlags(mcc, mnc, language, country,
                orientation, touchscreen, density, keyboard, navigation,
                inputFlags, screenWidth, screenHeight, sdkVersion,
                screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp,
                screenHeightDp, localeScript, localeVariant, screenLayout2,
                colorMode, isInvalid, size);
    }

    private char[] unpackLanguageOrRegion(byte in0, byte in1, char base) throws AndrolibException {
        // check high bit, if so we have a packed 3 letter code
        if (((in0 >> 7) & 1) == 1) {
            int first = in1 & 0x1F;
            int second = ((in1 & 0xE0) >> 5) + ((in0 & 0x03) << 3);
            int third = (in0 & 0x7C) >> 2;

            // since this function handles languages & regions, we add the value(s) to the base char
            // which is usually 'a' or '0' depending on language or region.
            return new char[]{(char) (first + base), (char) (second + base), (char) (third + base)};
        }
        return new char[]{(char) in0, (char) in1};
    }

    private String readScriptOrVariantChar(int length) throws AndrolibException, IOException {
        StringBuilder string = new StringBuilder(16);

        while (length-- != 0) {
            short ch = mIn.readByte();
            if (ch == 0) {
                break;
            }
            string.append((char) ch);
        }
        mIn.skipBytes(length);

        return string.toString();
    }

    private void addTypeSpec(ResTypeSpec resTypeSpec) {
        mResTypeSpecs.put(resTypeSpec.getId(), resTypeSpec);
    }

    private void addMissingResSpecs() throws AndrolibException {
        // mMissingResSpecs is re-created for every TYPE chunk, i.e. for every
        // configuration of the same type. The first chunk of a type already adds a
        // dummy spec for every id that is missing anywhere in that type, so later
        // chunks only repeated the scan (with a String concatenation and a fresh
        // ResResSpec object per entry) and threw the result away. On Android 16 the
        // framework resource table has tens of types with dozens of configurations
        // each, which made loading the framework take minutes ("Decoding manifest
        // and resources..." hang).
        if (this.mMissingResSpecs == null || this.mMissingResSpecsDone) {
            return;
        }

        int resId = mResId & 0xffff0000;

        for (int i = 0; i < mMissingResSpecs.length; i++) {
            if (!mMissingResSpecs[i]) {
                continue;
            }

            ResID id = new ResID(resId | i);

            // If we already have this resID dont add it again. Checked before the
            // dummy spec is built, the string name was the hot spot of this loop.
            if (mPkg.hasResSpec(id)) {
                continue;
            }

            ResResSpec spec = new ResResSpec(id, "dummy_ae_" + Integer.toHexString(i), mPkg, mTypeSpec, false);
            mPkg.addResSpec(spec);
            mTypeSpec.addResSpec(spec);

            if (mType == null) {
                mType = mPkg.getOrCreateConfig(new ResConfigFlags());
            }

            // Keep synthetic IDs, but avoid a boolean for types AAPT2 rejects.
            ResValue value = DummyResourceTypes.needsNullReference(mTypeSpec.getName())
                    ? new ResReferenceValue(mPkg, 0, null) : new ResBoolValue(false, 0, null);
            ResResource res = new ResResource(mType, spec, value);

            mPkg.addResource(res);
            mType.addResource(res);
            spec.addResource(res);
        }

        this.mMissingResSpecsDone = true;
    }

    private void addResSpecToType(ResResSpec spec) throws AndrolibException {
        try {
            mTypeSpec.addResSpec(spec);
        } catch (AndrolibException ex) {
            if (!mKeepBroken) {
                throw ex;
            }
            // A modern framework table can retain two entry IDs with the same
            // source name (for example a generated alias). The package's ID map
            // still preserves both resources, while the legacy type map can only
            // expose one name. Keep parsing the table and report the lossy name
            // collision instead of aborting the whole framework load.
            LOGGER.warning("Keeping resource with duplicate type name " + spec.getName()
                    + " at " + spec.getId());
        }
    }

    private void removeResSpec(ResResSpec spec) throws AndrolibException {
        if (mPkg.hasResSpec(spec.getId())) {
            mPkg.removeResSpec(spec);
            mTypeSpec.removeResSpec(spec);
        }
    }

    private Header nextChunk() throws IOException {
        return mHeader = Header.read(mIn, mCountIn);
    }

    private void checkChunkType(int expectedType) throws AndrolibException {
        if (mHeader.type != expectedType) {
            throw new AndrolibException(String.format("Invalid chunk type: expected=0x%08x, got=0x%08x",
                    expectedType, mHeader.type));
        }
    }

    private void nextChunkCheckType(int expectedType) throws IOException, AndrolibException {
        nextChunk();
        checkChunkType(expectedType);
    }

    private static boolean isSkippableChunk(short type) {
        return type == Header.TYPE_OVERLAYABLE
                || type == Header.TYPE_OVERLAYABLE_POLICY
                || type == Header.TYPE_STAGED_ALIAS;
    }

    /**
     * Moves the counting stream exactly to the end of the current chunk, so the next
     * {@link #nextChunk()} starts at the next chunk header.
     */
    private void skipToChunkEnd() throws IOException {
        while (mCountIn.getCount() < mHeader.endPosition) {
            long skipped = mCountIn.skip(mHeader.endPosition - mCountIn.getCount());
            if (skipped > 0) {
                continue;
            }
            if (mCountIn.read() < 0) {
                throw new EOFException("Truncated resource chunk type 0x"
                        + Integer.toHexString(mHeader.type & 0xffff));
            }
        }
    }

    /**
     * Skips a chunk we do not decode (newer Android formats) and advances to the
     * following chunk. Aborting on such a chunk is what made the Android 16 system
     * resource table unreadable ("Invalid chunk type: expected=0x00000200,
     * got=0x00000206").
     */
    private void skipChunkAndReadNext(String where) throws IOException, AndrolibException {
        if (mHeader.chunkSize <= 0) {
            throw new AndrolibException(String.format(
                    "Invalid chunk size 0x%08x for chunk type 0x%08x (%s)",
                    mHeader.chunkSize, mHeader.type, where));
        }
        LOGGER.warning(String.format("Skipping unsupported chunk type 0x%04x (size=%d) in %s",
                mHeader.type, mHeader.chunkSize, where));
        skipToChunkEnd();
        nextChunk();
    }

    public static class Header {
        public final static short TYPE_NONE = -1, TYPE_TABLE = 0x0002,
                TYPE_PACKAGE = 0x0200, TYPE_TYPE = 0x0201, TYPE_SPEC_TYPE = 0x0202, TYPE_LIBRARY = 0x0203,
                // Chunks added in newer Android releases (RES_TABLE_OVERLAYABLE_TYPE,
                // RES_TABLE_OVERLAYABLE_POLICY_TYPE, RES_TABLE_STAGED_ALIAS_TYPE). They
                // are not needed to decode resources, but they must be skipped instead
                // of aborting the whole table - Android 16 system tables contain them.
                TYPE_OVERLAYABLE = 0x0204, TYPE_OVERLAYABLE_POLICY = 0x0205, TYPE_STAGED_ALIAS = 0x0206;
        public final short type;
        public final int headerSize;
        public final int chunkSize;
        public final int startPosition;
        public final int endPosition;

        public Header(short type, int headerSize, int chunkSize, int headerStart) {
            this.type = type;
            this.headerSize = headerSize;
            this.chunkSize = chunkSize;
            this.startPosition = headerStart;
            this.endPosition = headerStart + chunkSize;
        }

        public static Header read(ExtDataInput in, CountingInputStream countIn) throws IOException {
            short type;
            int start = countIn.getCount();
            try {
                type = in.readShort();
            } catch (EOFException ex) {
                return new Header(TYPE_NONE, 0, 0, countIn.getCount());
            }
            int headerSize = in.readUnsignedShort();
            int chunkSize = in.readInt();
            if (headerSize < 8 || chunkSize < headerSize
                    || chunkSize > Integer.MAX_VALUE - start) {
                throw new IOException(String.format(
                        "Invalid resource chunk header: type=0x%04x, header=%d, size=%d, start=%d",
                        type & 0xffff, headerSize, chunkSize, start));
            }
            return new Header(type, headerSize, chunkSize, start);
        }
    }

    public static class FlagsOffset {
        public final int offset;
        public final int count;

        public FlagsOffset(int offset, int count) {
            this.offset = offset;
            this.count = count;
        }
    }

    public static class ARSCData {

        private final ResPackage[] mPackages;
        private final FlagsOffset[] mFlagsOffsets;
        private final ResTable mResTable;

        public ARSCData(ResPackage[] packages, FlagsOffset[] flagsOffsets, ResTable resTable) {
            mPackages = packages;
            mFlagsOffsets = flagsOffsets;
            mResTable = resTable;
        }

        public FlagsOffset[] getFlagsOffsets() {
            return mFlagsOffsets;
        }

        public ResPackage[] getPackages() {
            return mPackages;
        }

        public ResPackage getOnePackage() throws AndrolibException {
            if (mPackages.length <= 0) {
                throw new AndrolibException("Arsc file contains zero packages");
            } else if (mPackages.length != 1) {
                int id = findPackageWithMostResSpecs();
                LOGGER.info("Arsc file contains multiple packages. Using package "
                        + mPackages[id].getName() + " as default.");

                return mPackages[id];
            }
            return mPackages[0];
        }

        public int findPackageWithMostResSpecs() {
            int count = mPackages[0].getResSpecCount();
            int id = 0;

            for (int i = 0; i < mPackages.length; i++) {
                if (mPackages[i].getResSpecCount() >= count) {
                    count = mPackages[i].getResSpecCount();
                    id = i;
                }
            }
            return id;
        }

        public ResTable getResTable() {
            return mResTable;
        }
    }

    private class EntryData {
        public short mFlags;
        public int mSpecNamesId;
        public ResValue mValue;
    }
}
