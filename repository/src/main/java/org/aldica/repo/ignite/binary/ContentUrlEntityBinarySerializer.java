/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary;

import java.util.Locale;

import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.apache.ignite.internal.binary.BinaryWriterEx;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Instances of this class handle (de-)serialisations of {@link ContentUrlEntity} instances in order to optimise their serial form.
 *
 * @author Axel Faust
 */
public class ContentUrlEntityBinarySerializer extends AbstractExtendedBinarySerializer<ContentUrlEntity>
{

    private static final String ID = "id";

    private static final String CONTENT_URL = "contentUrl";

    private static final String SIZE = "size";

    private static final String ORPHAN_TIME = "orphanTime";

    private static final String IS_ENCRYPTED = "isEncrypted";

    private static final String KEY_ID = "keyId";

    private static final String ENCRYPTED_KEY_BYTES = "encryptedKeyBytes";

    private static final String KEY_SIZE = "keySize";

    private static final String ALGORITHM = "algorithm";

    private static final String MASTER_KEYSTORE_ID = "masterKeystoreId";

    private static final String MASTER_KEY_ALIAS = "masterKeyAlias";

    private static final String UNENCRYPTED_FILE_SIZE = "unencryptedFileSize";

    private static final byte FLAG_ID_UNSIGNED = 0x01;

    private static final byte FLAG_ORPHAN_TIME_NULL = 0x02;

    private static final byte FLAG_ORPHAN_TIME_UNSIGNED = 0x04;

    private static final byte FLAG_KEY_NULL = 0x08;

    private static final byte FLAG_KEY_ID_UNSIGNED = 0x10;

    public ContentUrlEntityBinarySerializer()
    {
        super(ContentUrlEntity.class);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void writeRawSerialForm(final ContentUrlEntity contentUrlEntity, final BinaryWriterEx rawWriter)
    {
        final BinaryOutputStream out = rawWriter.out();

        byte flags = 0;

        final int startPos = out.position();
        out.unsafeEnsure(1);
        out.position(startPos + 1);

        flags |= this.writeWithFlagIfUnsigned(contentUrlEntity.getId(), FLAG_ID_UNSIGNED, out);
        // we only serialise contentUrl
        // it costs us computationally during deserialisation to derive contentUrlShort / contentUrlCrc
        // but ContentUrlEntity should not be accessed often (ContentData/NodeProperties caches shield it)
        this.writeString(contentUrlEntity.getContentUrl(), out);
        // size is always unsigned
        this.writeUnsigned(contentUrlEntity.getSize(), out);

        final Long orphanTime = contentUrlEntity.getOrphanTime();
        if (orphanTime != null)
        {
            flags |= this.writeWithFlagIfUnsigned(orphanTime, FLAG_ORPHAN_TIME_UNSIGNED, out);
        }
        else
        {
            flags |= FLAG_ORPHAN_TIME_NULL;
        }

        final ContentUrlKeyEntity contentUrlKeyEntity = contentUrlEntity.getContentUrlKey();
        if (contentUrlKeyEntity != null)
        {
            flags |= this.writeWithFlagIfUnsigned(contentUrlKeyEntity.getId(), FLAG_KEY_ID_UNSIGNED, out);

            final byte[] encryptedKeyAsBytes = contentUrlKeyEntity.getEncryptedKeyAsBytes();
            this.writeUnsigned(encryptedKeyAsBytes.length, out);
            out.unsafeEnsure(encryptedKeyAsBytes.length);
            for (final byte b : encryptedKeyAsBytes)
            {
                out.unsafeWriteByte(b);
            }
            // key size is always unsigned
            this.writeUnsigned(contentUrlKeyEntity.getKeySize(), out);

            this.writeString(contentUrlKeyEntity.getAlgorithm(), out);
            this.writeString(contentUrlKeyEntity.getMasterKeystoreId(), out);
            this.writeString(contentUrlKeyEntity.getMasterKeyAlias(), out);

            // unencrypted file size is always unsigned
            this.writeUnsigned(contentUrlKeyEntity.getUnencryptedFileSize(), out);
        }
        else
        {
            flags |= FLAG_KEY_NULL;
        }

        final int endPos = out.position();
        out.position(startPos);
        out.unsafeWriteByte(flags);
        out.position(endPos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void writeRegularSerialForm(final ContentUrlEntity contentUrlEntity, final BinaryWriter writer)
    {
        writer.writeLong(ID, contentUrlEntity.getId());
        writer.writeString(CONTENT_URL, contentUrlEntity.getContentUrl());
        writer.writeLong(SIZE, contentUrlEntity.getSize());

        // write as object to support null - extra flag field for just handling nullability of a single field is not really efficient
        final Long orphanTime = contentUrlEntity.getOrphanTime();
        writer.writeObject(ORPHAN_TIME, orphanTime);

        ContentUrlKeyEntity contentUrlKeyEntity = contentUrlEntity.getContentUrlKey();
        boolean isEncrypted = contentUrlKeyEntity != null;
        writer.writeBoolean(IS_ENCRYPTED, isEncrypted);
        if (isEncrypted)
        {
            writer.writeLong(KEY_ID, contentUrlKeyEntity.getId());
            writer.writeByteArray(ENCRYPTED_KEY_BYTES, contentUrlKeyEntity.getEncryptedKeyAsBytes());
            writer.writeInt(KEY_SIZE, contentUrlKeyEntity.getKeySize());
            writer.writeString(ALGORITHM, contentUrlKeyEntity.getAlgorithm());
            writer.writeString(MASTER_KEYSTORE_ID, contentUrlKeyEntity.getMasterKeystoreId());
            writer.writeString(MASTER_KEY_ALIAS, contentUrlKeyEntity.getMasterKeyAlias());
            writer.writeLong(UNENCRYPTED_FILE_SIZE, contentUrlKeyEntity.getUnencryptedFileSize());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRawSerialForm(final ContentUrlEntity contentUrlEntity, final BinaryRawReader rawReader)
    {
        final byte flags = rawReader.readByte();

        final long id = this.readLong(rawReader, (flags & FLAG_ID_UNSIGNED) == FLAG_ID_UNSIGNED);
        final String contentUrl = this.readString(rawReader);
        final long size = this.readLong(rawReader, true);
        final Long orphanTime;
        if ((flags & FLAG_ORPHAN_TIME_NULL) != FLAG_ORPHAN_TIME_NULL)
        {
            orphanTime = this.readLong(rawReader, (flags & FLAG_ORPHAN_TIME_UNSIGNED) == FLAG_ORPHAN_TIME_UNSIGNED);
        }
        else
        {
            orphanTime = null;
        }

        contentUrlEntity.setId(id);
        contentUrlEntity.setContentUrl(contentUrl);
        contentUrlEntity.setSize(size);
        contentUrlEntity.setOrphanTime(orphanTime);

        // since we did not serialise contentUrlShort we have to duplicate the toLowerCase() that is applied to it
        final String contentUrlShort = contentUrlEntity.getContentUrlShort();
        if (contentUrlShort != null)
        {
            contentUrlEntity.setContentUrlShort(contentUrlShort.toLowerCase(Locale.ENGLISH));
        }

        if ((flags & FLAG_KEY_NULL) != FLAG_KEY_NULL)
        {
            final long keyId = readLong(rawReader, (flags & FLAG_KEY_ID_UNSIGNED) == FLAG_KEY_ID_UNSIGNED);
            final int keyBytes = readUnsignedInt(rawReader);
            final byte[] encryptedKeyAsBytes = new byte[keyBytes];
            for (int i = 0; i < keyBytes; i++)
            {
                encryptedKeyAsBytes[i] = rawReader.readByte();
            }
            final int keySize = readUnsignedInt(rawReader);
            final String algorithm = readString(rawReader);
            final String masterKeystoreId = readString(rawReader);
            final String masterKeyAlias = readString(rawReader);
            final long unencryptedFileSize = readUnsignedLong(rawReader);

            final ContentUrlKeyEntity contentUrlKeyEntity = new ContentUrlKeyEntity();
            contentUrlKeyEntity.setId(keyId);
            contentUrlKeyEntity.setContentUrlId(id);
            contentUrlKeyEntity.setEncryptedKeyAsBytes(encryptedKeyAsBytes);
            contentUrlKeyEntity.setKeySize(keySize);
            contentUrlKeyEntity.setAlgorithm(algorithm);
            contentUrlKeyEntity.setMasterKeystoreId(masterKeystoreId);
            contentUrlKeyEntity.setMasterKeyAlias(masterKeyAlias);
            contentUrlKeyEntity.setUnencryptedFileSize(unencryptedFileSize);
            contentUrlEntity.setContentUrlKey(contentUrlKeyEntity);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readRegularSerialForm(final ContentUrlEntity contentUrlEntity, final BinaryReader reader)
    {
        final long id = reader.readLong(ID);
        final String contentUrl = reader.readString(CONTENT_URL);
        final long size = reader.readLong(SIZE);
        final Long orphanTime = reader.readObject(ORPHAN_TIME);

        contentUrlEntity.setId(id);
        contentUrlEntity.setContentUrl(contentUrl);
        contentUrlEntity.setSize(size);
        contentUrlEntity.setOrphanTime(orphanTime);

        // since we did not serialise contentUrlShort we have to duplicate the toLowerCase() that is applied to it
        final String contentUrlShort = contentUrlEntity.getContentUrlShort();
        if (contentUrlShort != null)
        {
            contentUrlEntity.setContentUrlShort(contentUrlShort.toLowerCase(Locale.ENGLISH));
        }

        final boolean isEncrypted = reader.readBoolean(IS_ENCRYPTED);
        if (isEncrypted)
        {
            final long keyId = reader.readLong(KEY_ID);
            final byte[] encryptedKeyAsBytes = reader.readByteArray(ENCRYPTED_KEY_BYTES);
            final int keySize = reader.readInt(KEY_SIZE);
            final String algorithm = reader.readString(ALGORITHM);
            final String masterKeystoreId = reader.readString(MASTER_KEYSTORE_ID);
            final String masterKeyAlias = reader.readString(MASTER_KEY_ALIAS);
            final long unencryptedFileSize = reader.readLong(UNENCRYPTED_FILE_SIZE);

            final ContentUrlKeyEntity contentUrlKeyEntity = new ContentUrlKeyEntity();
            contentUrlKeyEntity.setId(keyId);
            contentUrlKeyEntity.setContentUrlId(id);
            contentUrlKeyEntity.setEncryptedKeyAsBytes(encryptedKeyAsBytes);
            contentUrlKeyEntity.setKeySize(keySize);
            contentUrlKeyEntity.setAlgorithm(algorithm);
            contentUrlKeyEntity.setMasterKeystoreId(masterKeystoreId);
            contentUrlKeyEntity.setMasterKeyAlias(masterKeyAlias);
            contentUrlKeyEntity.setUnencryptedFileSize(unencryptedFileSize);
            contentUrlEntity.setContentUrlKey(contentUrlKeyEntity);
        }
    }
}
