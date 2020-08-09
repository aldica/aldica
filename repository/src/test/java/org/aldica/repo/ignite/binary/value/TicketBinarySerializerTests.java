/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.value;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.InMemoryTicketComponentImpl.ExpiryMode;
import org.alfresco.repo.security.authentication.InMemoryTicketComponentImpl.Ticket;
import org.alfresco.service.cmr.repository.datatype.Duration;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.Ignition;
import org.apache.ignite.binary.BinaryTypeConfiguration;
import org.apache.ignite.configuration.BinaryConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.marshaller.Marshaller;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author Axel Faust
 */
public class TicketBinarySerializerTests extends GridTestsBase
{

    private static final Constructor<Ticket> TICKET_CTOR;

    static
    {
        try
        {
            TICKET_CTOR = Ticket.class.getDeclaredConstructor(ExpiryMode.class, Date.class, String.class, Duration.class);
            TICKET_CTOR.setAccessible(true);
        }
        catch (final NoSuchMethodException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup constructor");
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForTicket = new BinaryTypeConfiguration();
        binaryTypeConfigurationForTicket.setTypeName(Ticket.class.getName());
        final TicketBinarySerializer serializer = new TicketBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForTicket.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForTicket));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // negligible benefits - 2%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.02);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void rawSerialFormCorrectness()
    {
        final IgniteConfiguration conf = createConfiguration(true);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency()
    {
        final IgniteConfiguration referenceConf = createConfiguration(false);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // small differences due to variable length integers - 6.5%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.065);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            Duration validDuration;

            Ticket controlValue;
            Ticket serialisedValue;

            validDuration = new Duration("P1H");
            controlValue = TICKET_CTOR.newInstance(ExpiryMode.AFTER_INACTIVITY, Duration.add(new Date(), validDuration), "admin",
                    validDuration);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue, serialisedValue);

            validDuration = new Duration("P10H");
            controlValue = TICKET_CTOR.newInstance(ExpiryMode.AFTER_FIXED_TIME, Duration.add(new Date(), validDuration), "guest",
                    validDuration);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertEquals(controlValue, serialisedValue);
            Assert.assertNotSame(controlValue, serialisedValue);

            // ExpiryMode.DO_NOT_EXPIRE is bugged in Alfresco => NPE in Ticket.equals
        }
        catch (InstantiationException | InvocationTargetException | IllegalAccessException e)
        {
            throw new IllegalStateException("Failed to instantiated test value", e);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final Duration validDuration = new Duration("P1H");

        final Supplier<Ticket> comparisonValueSupplier = () -> {
            try
            {
                final Date expiryDate = Duration.add(new Date(), validDuration);
                final Ticket ticket = TICKET_CTOR.newInstance(ExpiryMode.AFTER_INACTIVITY, expiryDate, UUID.randomUUID().toString(),
                        validDuration);
                return ticket;
            }
            catch (InstantiationException | InvocationTargetException | IllegalAccessException e)
            {
                throw new IllegalStateException("Failed to instantiated benchmark value", e);
            }
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "Ticket", referenceSerialisationType, serialisationType,
                comparisonValueSupplier, marginFraction);
    }
}
