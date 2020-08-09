/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.domain.propval.PropertyUniqueContextEntity;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link PropertyUniqueContextEntity} instances in order to optimise their serial
 * form.
 *
 * @author Axel Faust
 */
public class PropertyUniqueContextEntityBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String ID = "id";

    private static final String VERSION = "version";

    private static final String VALUE_1_PROP_ID = "value1PropId";

    private static final String VALUE_2_PROP_ID = "value2PropId";

    private static final String VALUE_3_PROP_ID = "value3PropId";

    private static final String PROPERTY_ID = "propertyId";

    private static final byte FLAG_NO_VALUE_1_PROP_ID = 1;

    private static final byte FLAG_NO_VALUE_2_PROP_ID = 2;

    private static final byte FLAG_NO_VALUE_3_PROP_ID = 4;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(PropertyUniqueContextEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final PropertyUniqueContextEntity puc = (PropertyUniqueContextEntity) obj;

        final Long value1 = puc.getValue1PropId();
        final Long value2 = puc.getValue2PropId();
        final Long value3 = puc.getValue3PropId();

        byte flags = 0;
        if (value1 == null)
        {
            flags |= FLAG_NO_VALUE_1_PROP_ID;
        }
        if (value2 == null)
        {
            flags |= FLAG_NO_VALUE_2_PROP_ID;
        }
        if (value3 == null)
        {
            flags |= FLAG_NO_VALUE_3_PROP_ID;
        }

        if (this.useRawSerialForm)
        {
            final BinaryRawWriter rawWriter = writer.rawWriter();

            rawWriter.writeByte(flags);
            this.writeDbId(puc.getId(), rawWriter);
            // short is too small for any variable length integer optimisation
            rawWriter.writeShort(puc.getVersion());
            if (value1 != null)
            {
                this.writeDbId(value1, rawWriter);
            }
            if (value2 != null)
            {
                this.writeDbId(value2, rawWriter);
            }
            if (value3 != null)
            {
                this.writeDbId(value3, rawWriter);
            }
            this.writeDbId(puc.getPropertyId(), rawWriter);
        }
        else
        {
            writer.writeByte(FLAGS, flags);
            writer.writeLong(ID, puc.getId());
            writer.writeShort(VERSION, puc.getVersion());
            if (value1 != null)
            {
                writer.writeLong(VALUE_1_PROP_ID, value1);
            }
            if (value2 != null)
            {
                writer.writeLong(VALUE_2_PROP_ID, value2);
            }
            if (value3 != null)
            {
                writer.writeLong(VALUE_3_PROP_ID, value3);
            }
            writer.writeLong(PROPERTY_ID, puc.getPropertyId());
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(PropertyUniqueContextEntity.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final PropertyUniqueContextEntity puc = (PropertyUniqueContextEntity) obj;

        Long id;
        short version;
        Long value1 = null;
        Long value2 = null;
        Long value3 = null;
        Long propertyId;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            final byte flags = rawReader.readByte();
            id = this.readDbId(rawReader);
            version = rawReader.readShort();
            if ((flags & FLAG_NO_VALUE_1_PROP_ID) == 0)
            {
                value1 = this.readDbId(rawReader);
            }
            if ((flags & FLAG_NO_VALUE_2_PROP_ID) == 0)
            {
                value2 = this.readDbId(rawReader);
            }
            if ((flags & FLAG_NO_VALUE_3_PROP_ID) == 0)
            {
                value3 = this.readDbId(rawReader);
            }
            propertyId = this.readDbId(rawReader);
        }
        else
        {
            final byte flags = reader.readByte(FLAGS);
            id = reader.readLong(ID);
            version = reader.readShort(VERSION);
            if ((flags & FLAG_NO_VALUE_1_PROP_ID) == 0)
            {
                value1 = reader.readLong(VALUE_1_PROP_ID);
            }
            if ((flags & FLAG_NO_VALUE_2_PROP_ID) == 0)
            {
                value2 = reader.readLong(VALUE_2_PROP_ID);
            }
            if ((flags & FLAG_NO_VALUE_3_PROP_ID) == 0)
            {
                value3 = reader.readLong(VALUE_3_PROP_ID);
            }
            propertyId = reader.readLong(PROPERTY_ID);
        }

        puc.setId(id);
        puc.setVersion(version);
        puc.setValue1PropId(value1);
        puc.setValue2PropId(value2);
        puc.setValue3PropId(value3);
        puc.setPropertyId(propertyId);
    }

}
