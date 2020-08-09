/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.base;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.alfresco.repo.content.ContentStore;
import org.alfresco.repo.content.filestore.FileContentStore;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinarySerializer;
import org.jetbrains.annotations.NotNull;

/**
 * This class provides a base class for custom {@link BinarySerializer serializer} implementations for Alfresco key and value types,
 * providing the following reusable features:
 * <ul>
 * <li>writing/reading long values as variable length values with a reduced value space of 61/62 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading integer values as variable length values with a reduced value space of 29/30 bits, depending on the used of the
 * signed/unsigned values</li>
 * <li>writing/reading database IDs as variable length values with a reduced value space of 62 bits, or 61 bits if
 * {@link #setHandleNegativeIds(boolean) negative IDs must be supported}</li>
 * <li>writing/reading file / content size values as variable length values with a reduced value space of 62 bits unless
 * {@link #setHandle4EiBFileSizes(boolean) 4 EiB} is set to support larger file sizes</li>
 * </ul>
 *
 * @author Axel Faust
 */
public abstract class AbstractCustomBinarySerializer implements BinarySerializer
{

    public static final long LONG_AS_SHORT_UNSIGNED_MAX = 0x3fffffffffffffffL;

    public static final long LONG_AS_SHORT_SIGNED_POSITIVE_MAX = 0x1fffffffffffffffL;

    public static final long LONG_AS_SHORT_SIGNED_NEGATIVE_MAX = 0x9fffffffffffffffL;

    public static final int INT_AS_BYTE_UNSIGNED_MAX = 0x3fffffff;

    public static final int INT_AS_BYTE_SIGNED_POSITIVE_MAX = 0x1fffffff;

    public static final int INT_AS_BYTE_SIGNED_NEGATIVE_MAX = 0x9fffffff;

    private static final String CONTENT_URL_BIN_SUFFIX = ".bin";

    // match for all variations of TimeBasedFileContentUrlProvider / VolumeAwareContentUrlProvider URLs
    // support optional volumes and buckets in value range of 0-255 (fits well with optional byte)
    // 256 buckets would mean one bucket is created every ~235ms
    private static final Pattern OPTIMISABLE_CONTENT_URL_PATH_PATTERN = Pattern.compile(
            "^(?:([^/]+)/)?([0-9]|[1-3][0-9]{1,3}|40[0-8][0-9]|409[0-5])/([1-9]|1[0-2])/([1-9]|[12][0-9]|3[01])/(1?[0-9]|2[0-3])/([1-5]?[0-9])(?:/([1-9]?[0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5]))?$");

    // match a GUID-based value which can be encoded in raw bytes rather than expensive characters
    protected static final Pattern OPTIMISABLE_GUID_PATTERN = Pattern
            .compile("^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$");

    private static final byte FLAG_CONTENT_URL_DEFAULT_PROTOCOL = (byte) 0x80;

    private static final byte FLAG_CONTENT_URL_SUPPORTED_PATH = (byte) 0x40;

    private static final byte FLAG_CONTENT_URL_PATH_WITH_VOLUMES = (byte) 0x20;

    private static final byte FLAG_CONTENT_URL_PATH_WITH_BUCKETS = (byte) 0x10;

    private static final byte FLAG_CONTENT_URL_GUID_NAME = (byte) 0x08;

    private static final byte FLAG_CONTENT_URL_BIN_SUFFIX = (byte) 0x04;

    private static final byte FLAG_CONTENT_URL_NO_PATH = (byte) 0x02;

    private static final byte FLAG_CONTENT_URL_NO_SUFFIX = (byte) 0x01;

    private static final byte FLAG_CONTENT_URL_ANY_OPTIMISATION = FLAG_CONTENT_URL_DEFAULT_PROTOCOL | FLAG_CONTENT_URL_SUPPORTED_PATH
            | FLAG_CONTENT_URL_GUID_NAME | FLAG_CONTENT_URL_BIN_SUFFIX;

    protected boolean useRawSerialForm;

    protected boolean useVariableLengthIntegers;

    protected boolean useOptimisedContentURL;

    protected boolean handleNegativeIds;

    protected boolean handle4EiBFileSizes;

    /**
     * Specifies whether this instance should use/handle a raw serialised form for objects, meaning the elimination of any field metadata
     * and use of byte-level optimisations possible when writing/reading directly to/from the object byte stream via raw
     * {@link BinaryRawWriter writer}/{@link BinaryRawReader reader}.
     *
     * @param useRawSerialForm
     *            {@code true} if objects should be written in a raw serialised
     */
    public void setUseRawSerialForm(final boolean useRawSerialForm)
    {
        this.useRawSerialForm = useRawSerialForm;
    }

    /**
     * Specifies whether this instance should use/handle variable length integers when dealing with objects in a raw serialised form.
     *
     * @param useVariableLengthIntegers
     *            {@code true} if variable length integers should be be used
     */
    public void setUseVariableLengthIntegers(final boolean useVariableLengthIntegers)
    {
        this.useVariableLengthIntegers = useVariableLengthIntegers;
    }

    /**
     * Specifies whether this instance should use an optimised content URL serialisation when dealing with objects in a raw serialised form.
     *
     * @param useOptimisedContentURL
     *            {@code true} if optimised content URL serialisation should be be used
     */
    public void setUseOptimisedContentURL(final boolean useOptimisedContentURL)
    {
        this.useOptimisedContentURL = useOptimisedContentURL;
    }

    /**
     * Specifies whether this instance must support negative database IDs when dealing with objects in a raw serialised form. Alfresco by
     * default uses auto-incrementing database IDs starting from {@code 0}, so unless manual manipulation is performed at the database
     * level, negative IDs should not need to be supported and the sign bit could be used to optimise
     * {@link #setUseVariableLengthIntegers(boolean) variable length integers}.
     *
     * @param handleNegativeIds
     *            {@code true} if negative database IDs must be supported
     */
    public void setHandleNegativeIds(final boolean handleNegativeIds)
    {
        this.handleNegativeIds = handleNegativeIds;
    }

    /**
     * Specifies whether this instance must support files sizes of 4 EiB or higher when dealing with objects in a raw serialised form.
     *
     * @param handle4EiBFileSizes
     *            {@code true} if files sizes of at least 4 EiB must be supported, {@code false otherwise}
     */
    public void setHandle4EiBFileSizes(final boolean handle4EiBFileSizes)
    {
        this.handle4EiBFileSizes = handle4EiBFileSizes;
    }

    /**
     * Writes the value of a database ID to a raw serialised form of an object.
     *
     * @param id
     *            the ID to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeDbId(@NotNull final Long id, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("id", id);
        this.write(id.longValue(), !handleNegativeIds, rawWriter);
    }

    /**
     * Writes the value of a database ID to a raw serialised form of an object.
     *
     * @param id
     *            the ID to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeDbId(final long id, @NotNull final BinaryRawWriter rawWriter)
    {
        this.write(id, !handleNegativeIds, rawWriter);
    }

    /**
     * Reads the value of a database ID from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the database ID
     */
    protected final long readDbId(@NotNull final BinaryRawReader rawReader)
    {
        final long dbId = readLong(!handleNegativeIds, rawReader);
        return dbId;
    }

    /**
     * Writes the value of a file / content size to a raw serialised form of an object.
     *
     * @param size
     *            the file / content size to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeFileSize(@NotNull final Long size, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("size", size);
        this.writeFileSize(size.longValue(), rawWriter);
    }

    /**
     * Writes the value of a file / content size to a raw serialised form of an object.
     *
     * @param size
     *            the file / content size to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeFileSize(final long size, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (handle4EiBFileSizes || !useVariableLengthIntegers)
        {
            rawWriter.writeLong(size);
        }
        else
        {
            writeUnsignedLong(size, rawWriter);
        }
    }

    /**
     * Reads the value of a file / content size from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the file / content size
     */
    protected final long readFileSize(@NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long fileSize;
        if (handle4EiBFileSizes || !useVariableLengthIntegers)
        {
            fileSize = rawReader.readLong();
        }
        else
        {
            fileSize = readUnsignedLong(rawReader);
        }
        return fileSize;
    }

    /**
     * Checks whether the provided String value is a supported content URL that can be {@link #writeContentURL(String, BinaryRawWriter)
     * written in an optimised form}.
     *
     * @param contentURL
     *            the potential content URL to check
     * @return {@code true} if the value is a supported content URL, {@code false} otherwise
     */
    protected final boolean isSupportedContentUrl(@NotNull final String contentURL)
    {
        ParameterCheck.mandatory("contentUrl", contentURL);

        final String trimmedContentURL = contentURL.trim();
        boolean supported = !trimmedContentURL.isEmpty();

        if (supported)
        {
            final int protDelIdx = trimmedContentURL.indexOf(ContentStore.PROTOCOL_DELIMITER);
            final int lastSlashIdx = trimmedContentURL.lastIndexOf('/');
            final int lastDotIdx = trimmedContentURL.lastIndexOf('.');

            supported = protDelIdx > 0;

            if (supported)
            {
                // at this point we can only be sure we have some kind of URL
                if (!(trimmedContentURL.substring(0, protDelIdx).equals(FileContentStore.STORE_PROTOCOL)
                        || trimmedContentURL.endsWith(CONTENT_URL_BIN_SUFFIX)
                        || (lastSlashIdx > protDelIdx + 3 && (OPTIMISABLE_CONTENT_URL_PATH_PATTERN
                                .matcher(trimmedContentURL.substring(protDelIdx + 3, lastSlashIdx)).matches()))
                        || (lastSlashIdx > protDelIdx + 3 && OPTIMISABLE_GUID_PATTERN
                                .matcher(lastDotIdx > lastSlashIdx ? trimmedContentURL.substring(lastSlashIdx + 1, lastDotIdx)
                                        : trimmedContentURL.substring(lastSlashIdx + 1))
                                .matches())))
                {
                    // at this point we are sure we cannot reasonably optimise the string value
                    supported = false;
                }
                // else if only one element of the well known content URL pattern matches, we can optimise away at least a few bytes
            }
        }

        return supported;
    }

    /**
     * Writes a non-null content URL value to a raw serialised form of an object. Callers are expected to have
     * {@link #isSupportedContentUrl(String) checked whether the content URL is supported for optimisation} before calling this operation.
     *
     * @param contentURL
     *            the content URL to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void writeContentURL(@NotNull final String contentURL, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("contentUrl", contentURL);

        if (!useOptimisedContentURL)
        {
            this.write(contentURL, rawWriter);
        }
        else
        {
            final String trimmedContentURL = contentURL.trim();
            final int protDelIdx = trimmedContentURL.indexOf(ContentStore.PROTOCOL_DELIMITER);
            final int lastSlashIdx = trimmedContentURL.lastIndexOf('/');
            final int lastDotIdx = trimmedContentURL.lastIndexOf('.');

            byte flags = 0;

            String protocol = null;
            String volume = null;
            String path = null;
            int pathVal = 0;
            byte bucketVal = 0;
            String name = null;
            long guidMostSignificantBits = 0;
            long guidLeastSignificantBits = 0;
            String suffix = null;

            if (protDelIdx > 0)
            {
                protocol = trimmedContentURL.substring(0, protDelIdx);
                if (FileContentStore.STORE_PROTOCOL.equals(protocol))
                {
                    flags |= FLAG_CONTENT_URL_DEFAULT_PROTOCOL;
                    protocol = null;
                }

                if (trimmedContentURL.endsWith(CONTENT_URL_BIN_SUFFIX))
                {
                    flags |= FLAG_CONTENT_URL_BIN_SUFFIX;
                }
                else if (lastDotIdx > lastSlashIdx)
                {
                    suffix = trimmedContentURL.substring(lastDotIdx + 1);
                }
                else
                {
                    flags |= FLAG_CONTENT_URL_NO_SUFFIX;
                }

                if (lastSlashIdx > protDelIdx + 3)
                {
                    path = trimmedContentURL.substring(protDelIdx + 3, lastSlashIdx);

                    final Matcher pathMatcher = OPTIMISABLE_CONTENT_URL_PATH_PATTERN.matcher(path);
                    if (pathMatcher.matches())
                    {
                        flags |= FLAG_CONTENT_URL_SUPPORTED_PATH;

                        path = null;
                        volume = pathMatcher.group(1);
                        if (volume != null)
                        {
                            flags |= FLAG_CONTENT_URL_PATH_WITH_VOLUMES;
                        }

                        final String year = pathMatcher.group(2);
                        final String month = pathMatcher.group(3);
                        final String day = pathMatcher.group(4);
                        final String hour = pathMatcher.group(5);
                        final String minute = pathMatcher.group(6);

                        final int yearI = Integer.parseInt(year);
                        final int monthI = Integer.parseInt(month);
                        final int dayI = Integer.parseInt(day);
                        final int hourI = Integer.parseInt(hour);
                        final int minuteI = Integer.parseInt(minute);

                        // all date time related path elements fit nicely in 32 bit since we restrict years to 0-4095 range
                        pathVal |= yearI << 20;
                        // left shift cannot shift into sign bit
                        if (yearI >= 2048)
                        {
                            pathVal |= 0x80000000;
                        }
                        pathVal |= monthI << 16;
                        pathVal |= dayI << 11;
                        pathVal |= hourI << 6;
                        pathVal |= minuteI;

                        final String bucket = pathMatcher.group(7);
                        if (bucket != null)
                        {
                            flags |= FLAG_CONTENT_URL_PATH_WITH_BUCKETS;

                            // some manual handling to fill bucketVal as if it were an unsigned byte (0-255)
                            final int bucketValI = Integer.parseInt(bucket);
                            bucketVal = (byte) (bucketValI & 0x7f);
                            if ((bucketValI & 0x80) != 0)
                            {
                                bucketVal |= 0x80;
                            }
                        }
                    }
                }
                else
                {
                    flags |= FLAG_CONTENT_URL_NO_PATH;
                }

                name = lastDotIdx > lastSlashIdx ? trimmedContentURL.substring(lastSlashIdx + 1, lastDotIdx)
                        : trimmedContentURL.substring(lastSlashIdx + 1);
                final Matcher guidMatcher = OPTIMISABLE_GUID_PATTERN.matcher(name);
                if (guidMatcher.matches())
                {
                    flags |= FLAG_CONTENT_URL_GUID_NAME;

                    final String hex = name.replace("-", "");
                    guidMostSignificantBits = Long.parseUnsignedLong(hex.substring(0, 16), 16);
                    guidLeastSignificantBits = Long.parseUnsignedLong(hex.substring(16), 16);
                    name = null;
                }
            }

            rawWriter.writeByte(flags);
            if ((flags & FLAG_CONTENT_URL_ANY_OPTIMISATION) == 0)
            {
                this.write(contentURL, rawWriter);
            }
            else
            {
                if (protocol != null)
                {
                    this.write(protocol, rawWriter);
                }
                if (path != null)
                {
                    this.write(path, rawWriter);
                }
                else
                {
                    if (volume != null)
                    {
                        this.write(volume, rawWriter);
                    }
                    if ((flags & FLAG_CONTENT_URL_NO_PATH) == 0)
                    {
                        rawWriter.writeInt(pathVal);
                    }
                    if ((flags & FLAG_CONTENT_URL_PATH_WITH_BUCKETS) != 0)
                    {
                        rawWriter.writeByte(bucketVal);
                    }
                }

                if (name != null)
                {
                    this.write(name, rawWriter);
                }
                else
                {
                    rawWriter.writeLong(guidMostSignificantBits);
                    rawWriter.writeLong(guidLeastSignificantBits);
                }

                if (suffix != null)
                {
                    this.write(suffix, rawWriter);
                }
            }
        }
    }

    /**
     * Reads a non-null content URL value from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the content URL value
     */
    @NotNull
    protected final String readContentURL(@NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        String contentURL;

        if (!useOptimisedContentURL)
        {
            contentURL = readString(rawReader);
        }
        else
        {
            final byte flags = rawReader.readByte();
            if ((flags & FLAG_CONTENT_URL_ANY_OPTIMISATION) == 0)
            {
                contentURL = readString(rawReader);
            }
            else
            {
                final StringBuilder contentURLBuilder = new StringBuilder(128);

                String protocol;
                if ((flags & FLAG_CONTENT_URL_DEFAULT_PROTOCOL) != 0)
                {
                    protocol = FileContentStore.STORE_PROTOCOL;
                }
                else
                {
                    protocol = readString(rawReader);
                }
                contentURLBuilder.append(protocol).append(ContentStore.PROTOCOL_DELIMITER);

                if ((flags & FLAG_CONTENT_URL_SUPPORTED_PATH) != 0)
                {
                    if ((flags & FLAG_CONTENT_URL_PATH_WITH_VOLUMES) != 0)
                    {
                        final String volume = readString(rawReader);
                        contentURLBuilder.append(volume).append('/');
                    }

                    final int pathVal = rawReader.readInt();
                    int year = (pathVal & 0x7ff00000) >> 20;
                    if ((pathVal & 0x80000000) == 0x80000000)
                    {
                        year |= 0x0800;
                    }
                    final int month = (pathVal & 0x000f0000) >> 16;
                    final int day = (pathVal & 0xf800) >> 11;
                    final int hour = (pathVal & 0x07c0) >> 6;
                    final int minute = (pathVal & 0x3f);

                    contentURLBuilder.append(year).append('/');
                    contentURLBuilder.append(month).append('/');
                    contentURLBuilder.append(day).append('/');
                    contentURLBuilder.append(hour).append('/');
                    contentURLBuilder.append(minute).append('/');

                    if ((flags & FLAG_CONTENT_URL_PATH_WITH_BUCKETS) != 0)
                    {
                        final byte bucketVal = rawReader.readByte();
                        int bucketValI = bucketVal & 0x7f;
                        if ((bucketVal & 0x80) == 0x80)
                        {
                            bucketValI |= 0x80;
                        }
                        contentURLBuilder.append(bucketValI).append('/');
                    }
                }
                else if ((flags & FLAG_CONTENT_URL_NO_PATH) == 0)
                {
                    final String path = readString(rawReader);
                    contentURLBuilder.append(path);
                }

                if (contentURLBuilder.charAt(contentURLBuilder.length() - 1) != '/')
                {
                    contentURLBuilder.append('/');
                }
                if ((flags & FLAG_CONTENT_URL_GUID_NAME) != 0)
                {
                    final long guidMostSignificantBits = rawReader.readLong();
                    final long guidLeastSignificantBits = rawReader.readLong();

                    contentURLBuilder.append(new UUID(guidMostSignificantBits, guidLeastSignificantBits));
                }
                else
                {
                    final String name = readString(rawReader);
                    contentURLBuilder.append(name);
                }

                if ((flags & FLAG_CONTENT_URL_BIN_SUFFIX) != 0)
                {
                    contentURLBuilder.append(CONTENT_URL_BIN_SUFFIX);
                }
                else if ((flags & FLAG_CONTENT_URL_NO_SUFFIX) == 0)
                {
                    final String suffix = readString(rawReader);
                    contentURLBuilder.append('.').append(suffix);
                }

                contentURL = contentURLBuilder.toString();
            }
        }

        return contentURL;
    }

    /**
     * Writes a non-null String value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void write(@NotNull final String value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("value", value);
        ParameterCheck.mandatory("rawWriter", rawWriter);

        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        this.write(bytes.length, true, rawWriter);
        if (bytes.length != 0)
        {
            for (final byte b : bytes)
            {
                rawWriter.writeByte(b);
            }
        }
    }

    /**
     * Reads a non-null String value from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the String value
     */
    @NotNull
    protected final String readString(@NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        final int size = readInt(true, rawReader);
        final byte[] bytes = new byte[size];
        for (int idx = 0; idx < size; idx++)
        {
            bytes[idx] = rawReader.readByte();
        }

        final String value = new String(bytes, StandardCharsets.UTF_8);
        return value;
    }

    /**
     * Writes a non-null {@link Locale} value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    protected final void write(@NotNull final Locale value, @NotNull final BinaryRawWriter rawWriter)
    {
        final String valueStr = value.toString();
        this.write(valueStr, rawWriter);
    }

    /**
     * Reads a non-null {@link Locale} value from a raw serialised form of an object.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the {@link Locale} value
     */
    @NotNull
    protected final Locale readLocale(@NotNull final BinaryRawReader rawReader)
    {
        final String valueStr = readString(rawReader);
        // we know there is at least a default converter for Locale, maybe even an optimised (caching) one
        final Locale value = DefaultTypeConverter.INSTANCE.convert(Locale.class, valueStr);
        return value;
    }

    /**
     * Writes a long value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the long value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(@NotNull final Long value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("value", value);
        this.write(value.longValue(), nonNegativeOnly, rawWriter);
    }

    /**
     * Writes a long value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the long value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(final long value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (!useVariableLengthIntegers)
        {
            rawWriter.writeLong(value);
        }
        else
        {
            if (nonNegativeOnly)
            {
                writeUnsignedLong(value, rawWriter);
            }
            else
            {
                writeSignedLong(value, rawWriter);
            }
        }
    }

    /**
     * Reads a long value from a raw serialised form of an object.
     *
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    protected final long readLong(final boolean nonNegativeOnly, @NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;
        if (!useVariableLengthIntegers)
        {
            value = rawReader.readLong();
        }
        else
        {
            if (nonNegativeOnly)
            {
                value = readUnsignedLong(rawReader);
            }
            else
            {
                value = readSignedLong(rawReader);
            }
        }

        return value;
    }

    /**
     * Writes an integer value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the integer value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(@NotNull final Integer value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("value", value);
        this.write(value.intValue(), nonNegativeOnly, rawWriter);
    }

    /**
     * Writes an integer value to a raw serialised form of an object.
     *
     * @param value
     *            the value to write
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     * @throws BinaryObjectException
     *             if the integer value cannot be written, e.g. if {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     *             are enabled and the value exceeds the supported (reduced) value space for long values
     */
    protected final void write(final int value, final boolean nonNegativeOnly, @NotNull final BinaryRawWriter rawWriter)
            throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (!useVariableLengthIntegers)
        {
            rawWriter.writeInt(value);
        }
        else
        {
            if (nonNegativeOnly)
            {
                writeUnsignedInteger(value, rawWriter);
            }
            else
            {
                writeSignedInteger(value, rawWriter);
            }
        }
    }

    /**
     * Reads an integer value from a raw serialised form of an object.
     *
     * @param nonNegativeOnly
     *            {@code true} if the value can always be expected to hold non-negative values and will be read the same - this is an
     *            important input into handling {@link #setUseVariableLengthIntegers(boolean) variable length integers}
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return
     *         the value read from the raw serialised form
     */
    protected final int readInt(final boolean nonNegativeOnly, @NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;
        if (!useVariableLengthIntegers)
        {
            value = rawReader.readInt();
        }
        else
        {
            if (nonNegativeOnly)
            {
                value = readUnsignedInteger(rawReader);
            }
            else
            {
                value = readSignedInteger(rawReader);
            }
        }

        return value;
    }

    /**
     * Writes an unsigned long value as a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeUnsignedLong(final long value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final short s = 0;
            rawWriter.writeShort(s);
        }
        else if (value < 0)
        {
            throw new BinaryObjectException("Long value exceeds value range for unsigned values in variable length integer mode");
        }
        else
        {
            final int leadingZeros = Long.numberOfLeadingZeros(value);

            if (leadingZeros < 2)
            {
                throw new BinaryObjectException("Long value exceeds value range for unsigned values in variable length integer mode");
            }

            // 64 possible bits per long
            // 2 additional bits for length prefix => 66 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 65 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x3fffl => 50 leading zeroes => 65 - 50 = 15 => 15 / 16 = 0
            // ex. 0x7fffl => 49 leading zeroes => 65 - 49 = 16 => 16 / 16 = 1
            final int additionalShorts = (65 - leadingZeros) / 16;
            rawWriter.writeShort((short) ((additionalShorts << 14) | (value & 0x3fff)));

            switch (additionalShorts)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    break;
                case 2:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 30) & 0xffff));
                    break;
                case 3:
                    rawWriter.writeShort((short) ((value >> 14) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 30) & 0xffff));
                    rawWriter.writeShort((short) ((value >> 46) & 0xffff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of shorts to write for an unsigned long");
            }
        }
    }

    /**
     * Writes an signed long value as a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeSignedLong(final long value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final short s = 0;
            rawWriter.writeShort(s);
        }
        else
        {
            final boolean negative = value < 0;
            final long effV = negative ? (-value - 1) : value;
            final int leadingZeros = Long.numberOfLeadingZeros(effV);

            if (leadingZeros < 3)
            {
                throw new BinaryObjectException("Long value exceeds value range for signed values in variable length integer mode");
            }

            // 64 possible bits per long
            // 3 additional bits for length prefix and sign => 67 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 66 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x1fffl => 51 leading zeroes => 66 - 51 = 15 => 15 / 16 = 0
            // ex. 0x3fffl => 50 leading zeroes => 66 - 50 = 16 => 16 / 16 = 1
            final int additionalShorts = (66 - leadingZeros) / 16;
            rawWriter.writeShort((short) ((additionalShorts << 14) | (effV & 0x1fff) | (negative ? 0x2000 : 0)));

            switch (additionalShorts)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    break;
                case 2:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 29) & 0xffff));
                    break;
                case 3:
                    rawWriter.writeShort((short) ((effV >> 13) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 29) & 0xffff));
                    rawWriter.writeShort((short) ((effV >> 45) & 0xffff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of shorts to write for a signed long");
            }
        }
    }

    /**
     * Reads an unsigned long value from a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the unsigned long value
     */
    private final long readUnsignedLong(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;

        final short s = rawReader.readShort();
        value = (s & 0x3fffl);

        final int additionalShorts = (s & 0xc000) >> 14;
        switch (additionalShorts)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                break;
            case 2:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                value = value | ((rawReader.readShort() & 0xffffl) << 30);
                break;
            case 3:
                value = value | ((rawReader.readShort() & 0xffffl) << 14);
                value = value | ((rawReader.readShort() & 0xffffl) << 30);
                value = value | ((rawReader.readShort() & 0xffffl) << 46);
                break;
            default:
                throw new BinaryObjectException("Invalid number of shorts to read for an unsigned long");
        }

        return value;
    }

    /**
     * Reads a signed long value from a series of short fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the signed long value
     */
    private final long readSignedLong(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        long value;

        final short s = rawReader.readShort();
        value = (s & 0x1fffl);

        final int additionalShorts = (s & 0xc000) >> 14;
        switch (additionalShorts)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                break;
            case 2:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                value = value | ((rawReader.readShort() & 0xffffl) << 29);
                break;
            case 3:
                value = value | ((rawReader.readShort() & 0xffffl) << 13);
                value = value | ((rawReader.readShort() & 0xffffl) << 29);
                value = value | ((rawReader.readShort() & 0xffffl) << 45);
                break;
            default:
                throw new BinaryObjectException("Invalid number of shorts to read for a signed long");
        }

        if ((s & 0x2000) == 0x2000)
        {
            value = -(value + 1);
        }

        return value;
    }

    /**
     * Writes an unsigned integer value as a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeUnsignedInteger(final int value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
        }
        else if (value < 0)
        {
            throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
        }
        else
        {
            final int leadingZeros = Integer.numberOfLeadingZeros(value);

            if (leadingZeros < 2)
            {
                throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
            }

            // 32 possible bits per integer
            // 2 additional bits for length prefix => 34 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 33 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x3f => 26 leading zeroes => 33 - 26 = 7 => 7 / 8 = 0
            // ex. 0x7f => 25 leading zeroes => 33 - 25 = 8 => 8 / 8 = 1
            final int additionalBytes = (33 - leadingZeros) / 8;
            rawWriter.writeByte((byte) ((additionalBytes << 6) | (value & 0x3f)));

            switch (additionalBytes)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    break;
                case 2:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 14) & 0xff));
                    break;
                case 3:
                    rawWriter.writeByte((byte) ((value >> 6) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 14) & 0xff));
                    rawWriter.writeByte((byte) ((value >> 22) & 0xff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of bytes to write for an unsigned integer");
            }
        }
    }

    /**
     * Writes a signed integer value as a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param value
     *            the value to write
     * @param rawWriter
     *            the writer to use to write to the raw serialised form
     */
    private final void writeSignedInteger(final int value, @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("rawWriter", rawWriter);

        if (value == 0)
        {
            final byte b = 0;
            rawWriter.writeByte(b);
        }
        else
        {
            final boolean negative = value < 0;
            final int effV = negative ? (-value - 1) : value;
            final int leadingZeros = Integer.numberOfLeadingZeros(effV);

            if (leadingZeros < 3)
            {
                throw new BinaryObjectException("Integer value exceeds value range for unsigned values in variable length integer mode");
            }

            // 32 possible bits per integer
            // 3 additional bits for length prefix and sign => 35 bits
            // 1 bit negative offset to shift exact matches of "bits necessary vs bits per byte" => 34 bits
            // subtraction of leading zero count as number non-significant bits
            // ex. 0x1f => 27 leading zeroes => 34 - 27 = 7 => 7 / 8 = 0
            // ex. 0x3f => 26 leading zeroes => 34 - 26 = 8 => 8 / 8 = 1
            final int additionalBytes = (34 - leadingZeros) / 8;
            rawWriter.writeByte((byte) ((additionalBytes << 6) | (effV & 0x1f) | (negative ? 0x20 : 0)));

            switch (additionalBytes)
            {
                case 0: // NO-OP
                    break;
                case 1:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    break;
                case 2:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 13) & 0xff));
                    break;
                case 3:
                    rawWriter.writeByte((byte) ((effV >> 5) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 13) & 0xff));
                    rawWriter.writeByte((byte) ((effV >> 21) & 0xff));
                    break;
                default:
                    throw new BinaryObjectException("Invalid number of bytes to write for a signed integer");
            }
        }
    }

    /**
     * Reads an unsigned integer value from a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the unsigned integer value
     */
    private final int readUnsignedInteger(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;

        final byte b = rawReader.readByte();
        value = (b & 0x3f);

        final int additionalBytes = (b & 0xc0) >> 6;
        switch (additionalBytes)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                break;
            case 2:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                value = value | ((rawReader.readByte() & 0xff) << 14);
                break;
            case 3:
                value = value | ((rawReader.readByte() & 0xff) << 6);
                value = value | ((rawReader.readByte() & 0xff) << 14);
                value = value | ((rawReader.readByte() & 0xff) << 22);
                break;
            default:
                throw new BinaryObjectException("Invalid number of bytes to read for an unsigned integer");
        }

        return value;
    }

    /**
     * Reads a signed integer value from a series of byte fragments, with the top two bits of the first fragment encoding the number of
     * additional fragments. This allows for a least amount of overhead write/read handling using a switch instead of iterative loops or
     * if-else-if constructs.
     *
     * @param rawReader
     *            the reader to use to read from the raw serialised form
     * @return the signed integer value
     */
    private final int readSignedInteger(@NotNull final BinaryRawReader rawReader) throws BinaryObjectException
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        int value;

        final byte b = rawReader.readByte();
        value = (b & 0x1f);

        final int additionalBytes = (b & 0xc0) >> 6;
        switch (additionalBytes)
        {
            case 0: // NO-OP
                break;
            case 1:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                break;
            case 2:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                value = value | ((rawReader.readByte() & 0xff) << 13);
                break;
            case 3:
                value = value | ((rawReader.readByte() & 0xff) << 5);
                value = value | ((rawReader.readByte() & 0xff) << 13);
                value = value | ((rawReader.readByte() & 0xff) << 21);
                break;
            default:
                throw new BinaryObjectException("Invalid number of bytes to read for a signed integer");
        }

        if ((b & 0x20) == 0x20)
        {
            value = -(value + 1);
        }

        return value;
    }
}
