/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.contentdata.ContentUrlEntity;
import org.alfresco.repo.domain.contentdata.ContentUrlKeyEntity;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link ContentUrlEntity} instances. This implementation is capable of inlining
 * {@link ContentUrlKeyEntity the content encryption key} details in case a particular URL has been stored in encrypted format. This
 * implementation also exclusively uses the public getters and setters of the involved entity classes to only handle those fields in the
 * serial form which are part of the core identity - the {@link ContentUrlEntity#getContentUrlCrc() content URL CRC} and the
 * {@link ContentUrlEntity#getContentUrlShort() content short URL} are excluded from this form for instances as their values are not
 * essential for the identity as defined by {@code equals}/{@code hashCode} and are automatically reconstructed in the
 * {@link ContentUrlEntity#setContentUrl(String) content URL setter}.
 *
 *
 * @author Axel Faust
 */
public class ContentUrlEntityBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String CONTENT_URL = "contentUrl";

    private static final String SIZE = "size";

    private static final String ORPHAN_TIME = "orphanTime";

    private static final String KEY_ID = "keyId";

    private static final String KEY_BYTES = "keyBytes";

    private static final String KEY_SIZE = "keySize";

    private static final String KEY_ALGORITHM = "keyAlgorithm";

    private static final String KEY_MASTER_KEYSTORE_ID = "keyMasterKeystoreId";

    private static final String KEY_MASTER_KEY_ALIAS = "keyMasterKeyAlias";

    private static final String KEY_UNENCRYPTED_SIZE = "keyUnencryptedSize";

    // apparently some use cases in Alfresco exist where ID is null despite being persisted in the DB
    private static final byte FLAG_ID_NULL = 1;

    // content URL - like ID - should never be null, but since we can't trust ID, we handle its absence as well
    private static final byte FLAG_CONTENT_URL_NULL = 2;
    // size cannot be null due to use of primitves in ContentUrlEntity

    private static final byte FLAG_ORPHAN_TIME_NULL = 4;

    private static final byte FLAG_KEY_NULL = 8;

    // while all other fields are not null as per schema and code paths, the unencrypted file size can actually not be set
    private static final byte FLAG_UNENCRYPTED_FILE_SIZE_NULL = 16;

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ContentUrlEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ContentUrlEntity contentUrlEntity = (ContentUrlEntity) obj;

        final Long id = contentUrlEntity.getId();
        final String contentUrl = contentUrlEntity.getContentUrl();
        final long size = contentUrlEntity.getSize();
        final Long orphanTime = contentUrlEntity.getOrphanTime();

        final ContentUrlKeyEntity contentUrlKeyEntity = contentUrlEntity.getContentUrlKey();
        Long unencryptedFileSize = contentUrlKeyEntity != null ? contentUrlKeyEntity.getUnencryptedFileSize() : null;

        byte flags = 0;
        if (id == null)
        {
            flags |= FLAG_ID_NULL;
        }
        if (contentUrl == null)
        {
            flags |= FLAG_CONTENT_URL_NULL;
        }
        if (orphanTime == null)
        {
            flags |= FLAG_ORPHAN_TIME_NULL;
        }
        if (contentUrlKeyEntity == null)
        {
            flags |= FLAG_KEY_NULL;
        }
        if (unencryptedFileSize == null)
        {
            flags |= FLAG_UNENCRYPTED_FILE_SIZE_NULL;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            if (id != null)
            {
                this.writeDbId(id, rawWriter);
            }
            if (contentUrl != null)
            {
                this.writeContentURL(contentUrl, rawWriter);
            }
            this.writeFileSize(size, rawWriter);
            if (orphanTime != null)
            {
                // as a time stamp after 1970, value is always non-negative
                this.write(orphanTime, true, rawWriter);
            }

            if (contentUrlKeyEntity != null)
            {
                this.writeDbId(contentUrlKeyEntity.getId(), rawWriter);
                rawWriter.writeByteArray(contentUrlKeyEntity.getEncryptedKeyAsBytes());
                this.write(contentUrlKeyEntity.getKeySize(), true, rawWriter);
                this.write(contentUrlKeyEntity.getAlgorithm(), rawWriter);
                this.write(contentUrlKeyEntity.getMasterKeystoreId(), rawWriter);
                this.write(contentUrlKeyEntity.getMasterKeyAlias(), rawWriter);
                if (unencryptedFileSize != null)
                {
                    this.writeFileSize(unencryptedFileSize, rawWriter);
                }
            }
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            if (id != null)
            {
                writer.writeLong(ID, id);
            }
            if (contentUrl != null)
            {
                writer.writeString(CONTENT_URL, contentUrl);
            }
            writer.writeLong(SIZE, size);
            if (orphanTime != null)
            {
                writer.writeLong(ORPHAN_TIME, orphanTime);
            }

            if (contentUrlKeyEntity != null)
            {
                writer.writeLong(KEY_ID, contentUrlKeyEntity.getId());
                writer.writeByteArray(KEY_BYTES, contentUrlKeyEntity.getEncryptedKeyAsBytes());
                writer.writeInt(KEY_SIZE, contentUrlKeyEntity.getKeySize());
                writer.writeString(KEY_ALGORITHM, contentUrlKeyEntity.getAlgorithm());
                writer.writeString(KEY_MASTER_KEYSTORE_ID, contentUrlKeyEntity.getMasterKeystoreId());
                writer.writeString(KEY_MASTER_KEY_ALIAS, contentUrlKeyEntity.getMasterKeyAlias());
                if (unencryptedFileSize != null)
                {
                    writer.writeLong(KEY_UNENCRYPTED_SIZE, unencryptedFileSize);
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(ContentUrlEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ContentUrlEntity contentUrlEntity = (ContentUrlEntity) obj;

        Long id = null;
        String contentUrl = null;
        Long size;
        Long orphanTime = null;
        ContentUrlKeyEntity contentUrlKeyEntity = null;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte flags = rawReader.readByte();

            if ((flags & FLAG_ID_NULL) == 0)
            {
                id = this.readDbId(rawReader);
            }
            if ((flags & FLAG_CONTENT_URL_NULL) == 0)
            {
                contentUrl = this.readContentURL(rawReader);
            }
            size = this.readFileSize(rawReader);
            if ((flags & FLAG_ORPHAN_TIME_NULL) == 0)
            {
                orphanTime = this.readLong(true, rawReader);
            }

            if ((flags & FLAG_KEY_NULL) == 0)
            {
                contentUrlKeyEntity = new ContentUrlKeyEntity();

                final long keyId = this.readDbId(rawReader);
                final byte[] keyBytes = rawReader.readByteArray();
                final int keySize = this.readInt(true, rawReader);
                final String keyAlgorithm = this.readString(rawReader);
                final String masterKeystoreId = this.readString(rawReader);
                final String masterKeyAlias = this.readString(rawReader);

                Long unencryptedSize = null;
                if ((flags & FLAG_UNENCRYPTED_FILE_SIZE_NULL) == 0)
                {
                    unencryptedSize = this.readFileSize(rawReader);
                }

                contentUrlKeyEntity.setId(keyId);
                contentUrlKeyEntity.setContentUrlId(id);
                contentUrlKeyEntity.setEncryptedKeyAsBytes(keyBytes);
                contentUrlKeyEntity.setKeySize(keySize);
                contentUrlKeyEntity.setAlgorithm(keyAlgorithm);
                contentUrlKeyEntity.setMasterKeystoreId(masterKeystoreId);
                contentUrlKeyEntity.setMasterKeyAlias(masterKeyAlias);
                contentUrlKeyEntity.setUnencryptedFileSize(unencryptedSize);
            }
        }
        else
        {
            final byte flags = reader.readByte(FLAGS);

            if ((flags & FLAG_ID_NULL) == 0)
            {
                id = reader.readLong(ID);
            }
            if ((flags & FLAG_CONTENT_URL_NULL) == 0)
            {
                contentUrl = reader.readString(CONTENT_URL);
            }
            size = reader.readLong(SIZE);
            if ((flags & FLAG_ORPHAN_TIME_NULL) == 0)
            {
                orphanTime = reader.readLong(ORPHAN_TIME);
            }
            if ((flags & FLAG_KEY_NULL) == 0)
            {
                contentUrlKeyEntity = new ContentUrlKeyEntity();

                final long keyId = reader.readLong(KEY_ID);
                final byte[] keyBytes = reader.readByteArray(KEY_BYTES);
                final int keySize = reader.readInt(KEY_SIZE);
                final String keyAlgorithm = reader.readString(KEY_ALGORITHM);
                final String masterKeystoreId = reader.readString(KEY_MASTER_KEYSTORE_ID);
                final String masterKeyAlias = reader.readString(KEY_MASTER_KEY_ALIAS);

                Long unencryptedSize = null;
                if ((flags & FLAG_UNENCRYPTED_FILE_SIZE_NULL) == 0)
                {
                    unencryptedSize = reader.readLong(KEY_UNENCRYPTED_SIZE);
                }

                contentUrlKeyEntity.setId(keyId);
                contentUrlKeyEntity.setContentUrlId(id);
                contentUrlKeyEntity.setEncryptedKeyAsBytes(keyBytes);
                contentUrlKeyEntity.setKeySize(keySize);
                contentUrlKeyEntity.setAlgorithm(keyAlgorithm);
                contentUrlKeyEntity.setMasterKeystoreId(masterKeystoreId);
                contentUrlKeyEntity.setMasterKeyAlias(masterKeyAlias);
                contentUrlKeyEntity.setUnencryptedFileSize(unencryptedSize);
            }
        }

        contentUrlEntity.setId(id);
        if (contentUrl != null)
        {
            // Alfresco setter is not null-safe
            contentUrlEntity.setContentUrl(contentUrl);
        }
        contentUrlEntity.setSize(size);
        contentUrlEntity.setOrphanTime(orphanTime);
        contentUrlEntity.setContentUrlKey(contentUrlKeyEntity);
    }
}
