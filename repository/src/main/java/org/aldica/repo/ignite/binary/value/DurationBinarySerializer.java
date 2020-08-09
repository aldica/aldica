/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.lang.reflect.Field;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.service.cmr.repository.datatype.Duration;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link Duration} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise by merging most component integer values into a bit-masked composite value, as those component
 * (i.e. seconds, minutes, hours) will be value bound and not require the full integer value space.
 *
 * @author Axel Faust
 */
public class DurationBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String POSITIVE = "positive";

    private static final String AGGREGATED_DURATION_FRAGMENTS = "aggregatedDrationFragments";

    private static final String NANOS = "nanos";

    private static final Field POSITIVE_FIELD;

    private static final Field YEARS_FIELD;

    private static final Field MONTHS_FIELD;

    private static final Field DAYS_FIELD;

    private static final Field HOURS_FIELD;

    private static final Field MINUTES_FIELD;

    private static final Field SECONDS_FIELD;

    private static final Field NANOS_FIELD;

    static
    {
        try
        {
            POSITIVE_FIELD = Duration.class.getDeclaredField("m_positive");
            YEARS_FIELD = Duration.class.getDeclaredField("m_years");
            MONTHS_FIELD = Duration.class.getDeclaredField("m_months");
            DAYS_FIELD = Duration.class.getDeclaredField("m_days");
            HOURS_FIELD = Duration.class.getDeclaredField("m_hours");
            MINUTES_FIELD = Duration.class.getDeclaredField("m_mins");
            SECONDS_FIELD = Duration.class.getDeclaredField("m_seconds");
            NANOS_FIELD = Duration.class.getDeclaredField("m_nanos");

            POSITIVE_FIELD.setAccessible(true);
            YEARS_FIELD.setAccessible(true);
            MONTHS_FIELD.setAccessible(true);
            DAYS_FIELD.setAccessible(true);
            HOURS_FIELD.setAccessible(true);
            MINUTES_FIELD.setAccessible(true);
            SECONDS_FIELD.setAccessible(true);
            NANOS_FIELD.setAccessible(true);
        }
        catch (final NoSuchFieldException nsfe)
        {
            throw new RuntimeException("Failed to initialise reflective field accessors", nsfe);
        }
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(Duration.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final Duration duration = (Duration) obj;

        try
        {
            // duration class does not provide public getters
            final Boolean positive = (Boolean) POSITIVE_FIELD.get(duration);
            final Integer years = (Integer) YEARS_FIELD.get(duration);
            final Integer months = (Integer) MONTHS_FIELD.get(duration);
            final Integer days = (Integer) DAYS_FIELD.get(duration);
            final Integer hours = (Integer) HOURS_FIELD.get(duration);
            final Integer minutes = (Integer) MINUTES_FIELD.get(duration);
            final Integer seconds = (Integer) SECONDS_FIELD.get(duration);
            final Integer nanos = (Integer) NANOS_FIELD.get(duration);

            // all high level fields do not fit in a single int unless years could safely be limited to 0 - 63
            // very likely though, any duration in Alfresco will be shorter than this
            final long aggregate = years << 26 | months << 22 | days << 17 | hours << 12 | minutes << 6 | seconds;

            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();

                rawWriter.writeBoolean(positive);
                this.write(aggregate, true, rawWriter);
                this.write(nanos, true, rawWriter);
            }
            else
            {
                writer.writeBoolean(POSITIVE, positive);
                writer.writeLong(AGGREGATED_DURATION_FRAGMENTS, aggregate);
                writer.writeInt(NANOS, nanos);
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to read field values to serialise", iae);
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
        if (!cls.equals(Duration.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final boolean positive;
        final long aggregate;
        final int nanos;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            positive = rawReader.readBoolean();
            aggregate = this.readLong(true, rawReader);
            nanos = this.readInt(true, rawReader);
        }
        else
        {
            positive = reader.readBoolean(POSITIVE);
            aggregate = reader.readLong(AGGREGATED_DURATION_FRAGMENTS);
            nanos = reader.readInt(NANOS);
        }

        final int years = (int) (aggregate >> 26);
        final int months = (int) (aggregate >> 22) & 0x0f;
        final int days = (int) (aggregate >> 17) & 0x1f;
        final int hours = (int) (aggregate >> 12) & 0x1f;
        final int minutes = (int) (aggregate >> 6) & 0x3f;
        final int seconds = (int) (aggregate) & 0x3f;

        try
        {
            POSITIVE_FIELD.set(obj, positive);
            YEARS_FIELD.set(obj, years);
            MONTHS_FIELD.set(obj, months);
            DAYS_FIELD.set(obj, days);
            HOURS_FIELD.set(obj, hours);
            MINUTES_FIELD.set(obj, minutes);
            SECONDS_FIELD.set(obj, seconds);
            NANOS_FIELD.set(obj, nanos);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
