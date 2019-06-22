/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.binary;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.util.PropertyCheck;
import org.apache.ibatis.javassist.Modifier;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinarySerializer;
import org.apache.ignite.binary.BinaryWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * Instances of this class provide selective, reflective binary serialisation logic for Java objects. This is most relevant for classes of
 * objects which define superfluous instance members which do not form part of an object's identity, and those classes of objects may be
 * used as keys in Ignite caches.
 *
 * @author Axel Faust
 */
public class SelectivelyReflectiveBinarySerializer implements BinarySerializer, InitializingBean
{

    private static final Logger LOGGER = LoggerFactory.getLogger(SelectivelyReflectiveBinarySerializer.class);

    protected Function<Class<?>, Collection<String>> relevantFieldsProvider;

    protected Map<Class<?>, Collection<Field>> relevantFieldsByClass = new HashMap<>();

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "relevantFieldsProvider", this.relevantFieldsProvider);
    }

    /**
     * @param relevantFieldsProvider
     *            the relevantFieldsProvider to set
     */
    public void setRelevantFieldsProvider(final Function<Class<?>, Collection<String>> relevantFieldsProvider)
    {
        this.relevantFieldsProvider = relevantFieldsProvider;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        final Collection<Field> fields = this.relevantFieldsByClass.computeIfAbsent(cls, this::calculateFieldsForClass);

        LOGGER.debug("Serializing {}", obj);

        try
        {
            for (final Field field : fields)
            {
                final Object fieldValue = field.get(obj);
                if (fieldValue != null)
                {
                    final Class<?> type = field.getType();
                    if (type.isArray())
                    {
                        final Class<?> componentType = type.getComponentType();
                        if (componentType.isPrimitive())
                        {
                            if (Byte.TYPE.equals(componentType))
                            {
                                writer.writeByteArray(field.getName(), (byte[]) fieldValue);
                            }
                            else if (Short.TYPE.equals(componentType))
                            {
                                writer.writeShortArray(field.getName(), (short[]) fieldValue);
                            }
                            else if (Integer.TYPE.equals(componentType))
                            {
                                writer.writeIntArray(field.getName(), (int[]) fieldValue);
                            }
                            else if (Long.TYPE.equals(componentType))
                            {
                                writer.writeLongArray(field.getName(), (long[]) fieldValue);
                            }
                            else if (Float.TYPE.equals(componentType))
                            {
                                writer.writeFloatArray(field.getName(), (float[]) fieldValue);
                            }
                            else if (Double.TYPE.equals(componentType))
                            {
                                writer.writeDoubleArray(field.getName(), (double[]) fieldValue);
                            }
                            else if (Character.TYPE.equals(componentType))
                            {
                                writer.writeCharArray(field.getName(), (char[]) fieldValue);
                            }
                            else if (Boolean.TYPE.equals(componentType))
                            {
                                writer.writeBooleanArray(field.getName(), (boolean[]) fieldValue);
                            }
                            else
                            {
                                throw new IllegalStateException("Unsupported primitive array component type " + componentType);
                            }
                        }
                        // don't treat arrays of primitive wrapper types like primitive arrays - problem: potential presence of null value
                        // arrays of primitive wrapper types will be dealt with as regular Object[]
                        else if (BigDecimal.class.equals(componentType))
                        {
                            writer.writeDecimalArray(field.getName(), (BigDecimal[]) fieldValue);
                        }
                        else if (String.class.equals(componentType))
                        {
                            writer.writeStringArray(field.getName(), (String[]) fieldValue);
                        }
                        else if (UUID.class.equals(componentType))
                        {
                            writer.writeUuidArray(field.getName(), (UUID[]) fieldValue);
                        }
                        else if (Date.class.equals(componentType))
                        {
                            writer.writeDateArray(field.getName(), (Date[]) fieldValue);
                        }
                        else if (Timestamp.class.equals(componentType))
                        {
                            writer.writeTimestampArray(field.getName(), (Timestamp[]) fieldValue);
                        }
                        else if (Time.class.equals(componentType))
                        {
                            writer.writeTimeArray(field.getName(), (Time[]) fieldValue);
                        }
                        else if (componentType.isEnum())
                        {
                            writer.writeEnumArray(field.getName(), (Enum[]) fieldValue);
                        }
                        else
                        {
                            writer.writeObjectArray(field.getName(), (Object[]) fieldValue);
                        }
                    }
                    else if (Map.class.equals(type))
                    {
                        writer.writeMap(field.getName(), (Map<?, ?>) fieldValue);
                    }
                    else if (Collection.class.equals(type))
                    {
                        writer.writeCollection(field.getName(), (Collection<?>) fieldValue);
                    }
                    else if (Byte.class.equals(type) || Byte.TYPE.equals(type))
                    {
                        writer.writeByte(field.getName(), (Byte) fieldValue);
                    }
                    else if (Short.class.equals(type) || Short.TYPE.equals(type))
                    {
                        writer.writeShort(field.getName(), (Short) fieldValue);
                    }
                    else if (Integer.class.equals(type) || Integer.TYPE.equals(type))
                    {
                        writer.writeInt(field.getName(), (Integer) fieldValue);
                    }
                    else if (Long.class.equals(type) || Long.TYPE.equals(type))
                    {
                        writer.writeLong(field.getName(), (Long) fieldValue);
                    }
                    else if (Float.class.equals(type) || Float.TYPE.equals(type))
                    {
                        writer.writeFloat(field.getName(), (Float) fieldValue);
                    }
                    else if (Double.class.equals(type) || Double.TYPE.equals(type))
                    {
                        writer.writeDouble(field.getName(), (Double) fieldValue);
                    }
                    else if (Character.class.equals(type) || Character.TYPE.equals(type))
                    {
                        writer.writeChar(field.getName(), (Character) fieldValue);
                    }
                    else if (Boolean.class.equals(type) || Boolean.TYPE.equals(type))
                    {
                        writer.writeBoolean(field.getName(), (Boolean) fieldValue);
                    }
                    else if (BigDecimal.class.equals(type))
                    {
                        writer.writeDecimal(field.getName(), (BigDecimal) fieldValue);
                    }
                    else if (String.class.equals(type))
                    {
                        writer.writeString(field.getName(), (String) fieldValue);
                    }
                    else if (UUID.class.equals(type))
                    {
                        writer.writeUuid(field.getName(), (UUID) fieldValue);
                    }
                    else if (Date.class.equals(type))
                    {
                        writer.writeDate(field.getName(), (Date) fieldValue);
                    }
                    else if (Timestamp.class.equals(type))
                    {
                        writer.writeTimestamp(field.getName(), (Timestamp) fieldValue);
                    }
                    else if (Time.class.equals(type))
                    {
                        writer.writeTime(field.getName(), (Time) fieldValue);
                    }
                    else if (type.isEnum())
                    {
                        writer.writeEnum(field.getName(), (Enum<?>) fieldValue);
                    }
                }
            }
        }
        catch (final IllegalAccessException iae)
        {
            LOGGER.error("Failed to serialize {}", obj, iae);
            throw new AlfrescoRuntimeException("Failed to serialize " + obj, iae);
        }

        LOGGER.debug("Serialized {}", obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void readBinary(final Object obj, final BinaryReader reader) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        final Collection<Field> fields = this.relevantFieldsByClass.computeIfAbsent(cls, this::calculateFieldsForClass);

        LOGGER.debug("Desrializing instance of {}", cls);

        try
        {
            for (final Field field : fields)
            {
                Object fieldValue = null;
                final Class<?> type = field.getType();
                if (type.isArray())
                {
                    final Class<?> componentType = type.getComponentType();
                    if (componentType.isPrimitive())
                    {
                        if (Byte.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readByteArray(field.getName());
                        }
                        else if (Short.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readShortArray(field.getName());
                        }
                        else if (Integer.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readIntArray(field.getName());
                        }
                        else if (Long.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readLongArray(field.getName());
                        }
                        else if (Float.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readFloatArray(field.getName());
                        }
                        else if (Double.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readDoubleArray(field.getName());
                        }
                        else if (Character.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readCharArray(field.getName());
                        }
                        else if (Boolean.TYPE.equals(componentType))
                        {
                            fieldValue = reader.readBooleanArray(field.getName());
                        }
                        else
                        {
                            throw new IllegalStateException("Unsupported primitive array component type " + componentType);
                        }
                    }
                    // don't treat arrays of primitive wrapper types like primitive arrays - problem: potential presence of null value
                    // arrays of primitive wrapper types will be dealt with as regular Object[]
                    else if (BigDecimal.class.equals(componentType))
                    {
                        fieldValue = reader.readDecimalArray(field.getName());
                    }
                    else if (String.class.equals(componentType))
                    {
                        fieldValue = reader.readStringArray(field.getName());
                    }
                    else if (UUID.class.equals(componentType))
                    {
                        fieldValue = reader.readUuidArray(field.getName());
                    }
                    else if (Date.class.equals(componentType))
                    {
                        fieldValue = reader.readDateArray(field.getName());
                    }
                    else if (Timestamp.class.equals(componentType))
                    {
                        fieldValue = reader.readTimestampArray(field.getName());
                    }
                    else if (Time.class.equals(componentType))
                    {
                        fieldValue = reader.readTimeArray(field.getName());
                    }
                    else if (componentType.isEnum())
                    {
                        fieldValue = reader.readEnumArray(field.getName());
                    }
                    else
                    {
                        fieldValue = reader.readObjectArray(field.getName());
                    }
                }
                else if (Map.class.equals(type))
                {
                    fieldValue = reader.readMap(field.getName());
                }
                else if (Collection.class.equals(type))
                {
                    fieldValue = reader.readCollection(field.getName());
                }
                else if (Byte.class.equals(type) || Byte.TYPE.equals(type))
                {
                    fieldValue = reader.readByte(field.getName());
                }
                else if (Short.class.equals(type) || Short.TYPE.equals(type))
                {
                    fieldValue = reader.readShort(field.getName());
                }
                else if (Integer.class.equals(type) || Integer.TYPE.equals(type))
                {
                    fieldValue = reader.readInt(field.getName());
                }
                else if (Long.class.equals(type) || Long.TYPE.equals(type))
                {
                    fieldValue = reader.readLong(field.getName());
                }
                else if (Float.class.equals(type) || Float.TYPE.equals(type))
                {
                    fieldValue = reader.readFloat(field.getName());
                }
                else if (Double.class.equals(type) || Double.TYPE.equals(type))
                {
                    fieldValue = reader.readDouble(field.getName());
                }
                else if (Character.class.equals(type) || Character.TYPE.equals(type))
                {
                    fieldValue = reader.readChar(field.getName());
                }
                else if (Boolean.class.equals(type) || Boolean.TYPE.equals(type))
                {
                    fieldValue = reader.readBoolean(field.getName());
                }
                else if (BigDecimal.class.equals(type))
                {
                    fieldValue = reader.readDecimal(field.getName());
                }
                else if (String.class.equals(type))
                {
                    fieldValue = reader.readString(field.getName());
                }
                else if (UUID.class.equals(type))
                {
                    fieldValue = reader.readUuid(field.getName());
                }
                else if (Date.class.equals(type))
                {
                    fieldValue = reader.readDate(field.getName());
                }
                else if (Timestamp.class.equals(type))
                {
                    fieldValue = reader.readTimestamp(field.getName());
                }
                else if (Time.class.equals(type))
                {
                    fieldValue = reader.readTime(field.getName());
                }
                else if (type.isEnum())
                {
                    fieldValue = reader.readEnum(field.getName());
                }

                if (fieldValue != null)
                {
                    field.set(obj, fieldValue);
                }
            }
        }
        catch (final IllegalAccessException iae)
        {
            LOGGER.error("Failed to deserialize instance of {}", cls, iae);
            throw new AlfrescoRuntimeException("Failed to serialize instanceof of " + cls, iae);
        }

        LOGGER.debug("Deserialized {}", obj);
    }

    protected Collection<Field> calculateFieldsForClass(final Class<?> cls)
    {
        LOGGER.debug("Calculating fields to serialize for class {}", cls);
        final Collection<String> fieldNames = this.relevantFieldsProvider.apply(cls);

        if (fieldNames == null || fieldNames.isEmpty())
        {
            LOGGER.error("Provider of relevant field names did not provide any fields for {}", cls);
            throw new AlfrescoRuntimeException("Unable to handle objects of class " + cls + " due to lack of relevant fields");
        }

        final List<String> sortedFieldNames = new ArrayList<>(fieldNames);
        Collections.sort(sortedFieldNames);

        final List<Class<?>> clsHierarchy = new ArrayList<>(8);
        Class<?> currentCls = cls;
        while (!currentCls.equals(Object.class))
        {
            clsHierarchy.add(currentCls);
            currentCls = currentCls.getSuperclass();
        }

        final Collection<Field> fieldsForClass = sortedFieldNames.stream().map(name -> {
            Field field = null;

            for (final Class<?> clsInHierarchy : clsHierarchy)
            {
                try
                {
                    final Field fieldCandidate = clsInHierarchy.getDeclaredField(name);
                    if (!Modifier.isTransient(fieldCandidate.getModifiers()) && !Modifier.isStatic(fieldCandidate.getModifiers()))
                    {
                        LOGGER.debug("Non-transient instance field {} is defined by class {}", name, clsInHierarchy);
                        fieldCandidate.setAccessible(true);
                        field = fieldCandidate;
                        break;
                    }
                    LOGGER.debug(
                            "Field {} is defined in class {} but not relevant for serialization (either no an instance field or marked as transient)",
                            name, clsInHierarchy);
                }
                catch (final NoSuchFieldException nsfe)
                {
                    LOGGER.debug("Field {} is not defined in class {}", name, clsInHierarchy);
                }
            }

            if (field == null)
            {
                LOGGER.error("Field {} is not defined in the class hierarchy of {}", cls);
                throw new IllegalStateException("Field " + name + " not defined in class hierarchy of " + cls);
            }
            return field;
        }).collect(Collectors.toList());

        LOGGER.debug("Calculated {} fields to serialize for class {}: {}", fieldsForClass.size(), cls, fieldsForClass);

        return fieldsForClass;
    }
}
