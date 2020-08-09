/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.lang.reflect.Field;
import java.util.Date;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.repo.security.authentication.InMemoryTicketComponentImpl.ExpiryMode;
import org.alfresco.repo.security.authentication.InMemoryTicketComponentImpl.Ticket;
import org.alfresco.service.cmr.repository.datatype.Duration;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

/**
 * Instances of this class handle (de-)serialisations of {@link Ticket} instances in order to optimise their serial form. This
 * implementation primarily aims to optimise by inlining the immutable constituent, non-complex value components. Though the ticket itself
 * could either be left out of the serial form as it can be recalculated on-the-fly, or be handled more efficiently by using its byte-
 * instead of hex-representation, the cost of doing so on every read would be too prohibitive.
 *
 * @author Axel Faust
 */
public class TicketBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String EXPIRES = "expires";

    private static final String USER_NAME = "userName";

    private static final String VALID_DURATION = "validDuration";

    private static final String TEST_DURATION = "testDuration";

    private static final String TICKET_ID = "ticketId";

    private static final String EXPIRY_DATE = "expiryDate";

    private static final Field EXPIRES_FIELD;

    private static final Field EXPIRY_DATE_FIELD;

    private static final Field USER_NAME_FIELD;

    private static final Field TICKET_ID_FIELD;

    private static final Field VALID_DURATION_FIELD;

    private static final Field TEST_DURATION_FIELD;

    static
    {
        try
        {
            EXPIRES_FIELD = Ticket.class.getDeclaredField(EXPIRES);
            EXPIRY_DATE_FIELD = Ticket.class.getDeclaredField(EXPIRY_DATE);
            USER_NAME_FIELD = Ticket.class.getDeclaredField(USER_NAME);
            TICKET_ID_FIELD = Ticket.class.getDeclaredField(TICKET_ID);
            VALID_DURATION_FIELD = Ticket.class.getDeclaredField(VALID_DURATION);
            TEST_DURATION_FIELD = Ticket.class.getDeclaredField(TEST_DURATION);

            EXPIRES_FIELD.setAccessible(true);
            EXPIRY_DATE_FIELD.setAccessible(true);
            USER_NAME_FIELD.setAccessible(true);
            TICKET_ID_FIELD.setAccessible(true);
            VALID_DURATION_FIELD.setAccessible(true);
            TEST_DURATION_FIELD.setAccessible(true);
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
        if (!cls.equals(Ticket.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final Ticket ticket = (Ticket) obj;

        try
        {
            // ticket class does not provide public getters
            final ExpiryMode expires = (ExpiryMode) EXPIRES_FIELD.get(ticket);
            final Date expiryDate = (Date) EXPIRY_DATE_FIELD.get(ticket);
            final String userName = (String) USER_NAME_FIELD.get(ticket);
            final String ticketId = (String) TICKET_ID_FIELD.get(ticket);
            final Duration validDuration = (Duration) VALID_DURATION_FIELD.get(ticket);
            final Duration testDuration = (Duration) TEST_DURATION_FIELD.get(ticket);

            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();

                // non-null fields (ensured via checkValidTIcketParameters)
                rawWriter.writeByte((byte) expires.ordinal());
                this.write(userName, rawWriter);
                rawWriter.writeObject(validDuration);
                if (expires != ExpiryMode.DO_NOT_EXPIRE)
                {
                    // expiry timestamp will never be negative (before 1970)
                    this.write(expiryDate.getTime(), true, rawWriter);
                }

                // test duration is computed from valid duration, so also always non-null
                rawWriter.writeObject(testDuration);
                this.write(ticketId, rawWriter);
            }
            else
            {
                // non-null fields (ensured via checkValidTIcketParameters)
                writer.writeByte(EXPIRES, (byte) expires.ordinal());
                writer.writeString(USER_NAME, userName);
                writer.writeObject(VALID_DURATION, validDuration);
                if (expires != ExpiryMode.DO_NOT_EXPIRE)
                {
                    writer.writeLong(EXPIRY_DATE, expiryDate.getTime());
                }

                writer.writeObject(TEST_DURATION, testDuration);
                writer.writeString(TICKET_ID, ticketId);
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
        if (!cls.equals(Ticket.class))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        final ExpiryMode expires;
        final Date expiryDate;
        final String userName;
        final String ticketId;
        final Duration validDuration;
        final Duration testDuration;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();

            expires = ExpiryMode.values()[rawReader.readByte()];
            userName = this.readString(rawReader);
            validDuration = rawReader.readObject();
            if (expires != ExpiryMode.DO_NOT_EXPIRE)
            {
                expiryDate = new Date(this.readLong(true, rawReader));
            }
            else
            {
                expiryDate = null;
            }
            testDuration = rawReader.readObject();
            ticketId = this.readString(rawReader);
        }
        else
        {
            expires = ExpiryMode.values()[reader.readByte(EXPIRES)];
            userName = reader.readString(USER_NAME);
            validDuration = reader.readObject(VALID_DURATION);
            if (expires != ExpiryMode.DO_NOT_EXPIRE)
            {
                expiryDate = new Date(reader.readLong(EXPIRY_DATE));
            }
            else
            {
                expiryDate = null;
            }
            testDuration = reader.readObject(TEST_DURATION);
            ticketId = reader.readString(TICKET_ID);
        }

        try
        {
            EXPIRES_FIELD.set(obj, expires);
            EXPIRY_DATE_FIELD.set(obj, expiryDate);
            USER_NAME_FIELD.set(obj, userName);
            TICKET_ID_FIELD.set(obj, ticketId);
            VALID_DURATION_FIELD.set(obj, validDuration);
            TEST_DURATION_FIELD.set(obj, testDuration);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

}
