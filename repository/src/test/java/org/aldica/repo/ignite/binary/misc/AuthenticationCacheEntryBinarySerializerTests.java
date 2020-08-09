/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.misc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.Supplier;

import org.aldica.common.ignite.GridTestsBase;
import org.aldica.repo.ignite.ExpensiveTestCategory;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.CompositePasswordEncoder;
import org.alfresco.repo.security.authentication.MD4PasswordEncoderImpl;
import org.alfresco.repo.security.authentication.RepositoryAuthenticatedUser;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.GUID;
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

import net.sf.acegisecurity.GrantedAuthority;
import net.sf.acegisecurity.GrantedAuthorityImpl;
import net.sf.acegisecurity.UserDetails;
import net.sf.acegisecurity.providers.encoding.PasswordEncoder;

/**
 * @author Axel Faust
 */
public class AuthenticationCacheEntryBinarySerializerTests extends GridTestsBase
{

    private static final Class<?> CACHE_ENTRY_CLASS;

    private static final Constructor<?> CACHE_ENTRY_CTOR;

    private static final Field CACHE_ENTRY_NODE_REF;

    private static final Field CACHE_ENTRY_USER_DETAILS;

    private static final Field CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE;

    static
    {
        try
        {
            CACHE_ENTRY_CLASS = Class.forName("org.alfresco.repo.security.authentication.RepositoryAuthenticationDao$CacheEntry");
            CACHE_ENTRY_CTOR = CACHE_ENTRY_CLASS.getDeclaredConstructor(NodeRef.class, UserDetails.class, Date.class);
            CACHE_ENTRY_NODE_REF = CACHE_ENTRY_CLASS.getDeclaredField("nodeRef");
            CACHE_ENTRY_USER_DETAILS = CACHE_ENTRY_CLASS.getDeclaredField("userDetails");
            CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE = CACHE_ENTRY_CLASS.getDeclaredField("credentialExpiryDate");

            CACHE_ENTRY_CTOR.setAccessible(true);
            CACHE_ENTRY_NODE_REF.setAccessible(true);
            CACHE_ENTRY_USER_DETAILS.setAccessible(true);
            CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.setAccessible(true);
        }
        catch (final ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup class / constructor / fields");
        }
    }

    protected static IgniteConfiguration createConfiguration(final boolean serialForm)
    {
        final IgniteConfiguration conf = createConfiguration(1, false, null);

        final BinaryConfiguration binaryConfiguration = new BinaryConfiguration();

        final BinaryTypeConfiguration binaryTypeConfigurationForCacheEntry = new BinaryTypeConfiguration();
        binaryTypeConfigurationForCacheEntry.setTypeName(CACHE_ENTRY_CLASS.getName());
        final AuthenticationCacheEntryBinarySerializer serializer = new AuthenticationCacheEntryBinarySerializer();
        serializer.setUseRawSerialForm(serialForm);
        serializer.setUseVariableLengthIntegers(serialForm);
        binaryTypeConfigurationForCacheEntry.setSerializer(serializer);

        binaryConfiguration.setTypeConfigurations(Arrays.asList(binaryTypeConfigurationForCacheEntry));
        conf.setBinaryConfiguration(binaryConfiguration);

        return conf;
    }

    @Test
    public void defaultFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(false);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void defaultFormEfficiency() throws Exception
    {
        final IgniteConfiguration referenceConf = createConfiguration(1, false, null);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(false);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // inlining and flag aggregation - 28%
            this.efficiencyImpl(referenceGrid, grid, "aldica optimised", "Ignite default", 0.18);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void rawSerialFormCorrectness() throws Exception
    {
        final IgniteConfiguration conf = createConfiguration(true);
        this.correctnessImpl(conf);
    }

    @Category(ExpensiveTestCategory.class)
    @Test
    public void rawSerialFormEfficiency() throws Exception
    {
        final IgniteConfiguration referenceConf = createConfiguration(false);
        referenceConf.setIgniteInstanceName(referenceConf.getIgniteInstanceName() + "-reference");
        final IgniteConfiguration conf = createConfiguration(true);

        try
        {
            final Ignite referenceGrid = Ignition.start(referenceConf);
            final Ignite grid = Ignition.start(conf);

            // optimised string / variable integers - 13%
            this.efficiencyImpl(referenceGrid, grid, "aldica raw serial", "aldica optimised", 0.13);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    protected void correctnessImpl(final IgniteConfiguration conf)
            throws InstantiationException, InvocationTargetException, IllegalAccessException
    {
        try (Ignite grid = Ignition.start(conf))
        {
            @SuppressWarnings("deprecation")
            final Marshaller marshaller = grid.configuration().getMarshaller();

            Object controlValue;
            NodeRef controlNode;
            RepositoryAuthenticatedUser controlUser;
            Date controlExpiryDate;

            Object serialisedValue;
            NodeRef serialisedNode;
            RepositoryAuthenticatedUser serialisedUser;
            Date serialisedExpiryDate;

            controlNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString());
            controlUser = new RepositoryAuthenticatedUser("jdoe", "someHash", true, true, true, true,
                    new GrantedAuthority[] { new GrantedAuthorityImpl("role1"), new GrantedAuthorityImpl("role2") },
                    CompositePasswordEncoder.MD4, UUID.randomUUID());
            controlExpiryDate = Date.from(Instant.now().plus(5, ChronoUnit.DAYS));

            controlValue = CACHE_ENTRY_CTOR.newInstance(controlNode, controlUser, controlExpiryDate);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            Assert.assertNotSame(controlValue, serialisedValue);

            serialisedNode = (NodeRef) CACHE_ENTRY_NODE_REF.get(serialisedValue);
            serialisedUser = (RepositoryAuthenticatedUser) CACHE_ENTRY_USER_DETAILS.get(serialisedValue);
            serialisedExpiryDate = (Date) CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.get(serialisedValue);

            Assert.assertNotSame(controlNode, serialisedNode);
            Assert.assertEquals(controlNode, serialisedNode);
            Assert.assertNotSame(controlUser, serialisedUser);
            // no equals in the class hierarchy of the user
            Assert.assertEquals(controlUser.getUsername(), serialisedUser.getUsername());
            Assert.assertEquals(controlUser.getPassword(), serialisedUser.getPassword());
            Assert.assertEquals(controlUser.isEnabled(), serialisedUser.isEnabled());
            Assert.assertEquals(controlUser.isAccountNonExpired(), serialisedUser.isAccountNonExpired());
            Assert.assertEquals(controlUser.isCredentialsNonExpired(), serialisedUser.isCredentialsNonExpired());
            Assert.assertEquals(controlUser.isAccountNonLocked(), serialisedUser.isAccountNonLocked());
            Assert.assertEquals(controlUser.getHashIndicator(), serialisedUser.getHashIndicator());
            Assert.assertEquals(controlUser.getSalt(), serialisedUser.getSalt());

            Assert.assertNotSame(controlExpiryDate, serialisedExpiryDate);
            Assert.assertEquals(controlExpiryDate, serialisedExpiryDate);

            controlNode = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString());
            controlUser = new RepositoryAuthenticatedUser("maxmuster", "differentHash", false, false, false, false,
                    new GrantedAuthority[] { new GrantedAuthorityImpl("role1"), new GrantedAuthorityImpl("role2"),
                            new GrantedAuthorityImpl("role3"), new GrantedAuthorityImpl("role4"), new GrantedAuthorityImpl("role5") },
                    Arrays.asList("123", "098", "019283", "564738"), UUID.randomUUID());
            controlExpiryDate = null;

            controlValue = CACHE_ENTRY_CTOR.newInstance(controlNode, controlUser, controlExpiryDate);
            serialisedValue = marshaller.unmarshal(marshaller.marshal(controlValue), this.getClass().getClassLoader());

            serialisedNode = (NodeRef) CACHE_ENTRY_NODE_REF.get(serialisedValue);
            serialisedUser = (RepositoryAuthenticatedUser) CACHE_ENTRY_USER_DETAILS.get(serialisedValue);
            serialisedExpiryDate = (Date) CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.get(serialisedValue);

            Assert.assertNotSame(controlNode, serialisedNode);
            Assert.assertEquals(controlNode, serialisedNode);
            Assert.assertNotSame(controlUser, serialisedUser);
            // no equals in the class hierarchy of the user
            Assert.assertEquals(controlUser.getUsername(), serialisedUser.getUsername());
            Assert.assertEquals(controlUser.getPassword(), serialisedUser.getPassword());
            Assert.assertEquals(controlUser.isEnabled(), serialisedUser.isEnabled());
            Assert.assertEquals(controlUser.isAccountNonExpired(), serialisedUser.isAccountNonExpired());
            Assert.assertEquals(controlUser.isCredentialsNonExpired(), serialisedUser.isCredentialsNonExpired());
            Assert.assertEquals(controlUser.isAccountNonLocked(), serialisedUser.isAccountNonLocked());
            Assert.assertEquals(controlUser.getHashIndicator(), serialisedUser.getHashIndicator());
            Assert.assertEquals(controlUser.getSalt(), serialisedUser.getSalt());

            Assert.assertEquals(controlExpiryDate, serialisedExpiryDate);
        }
        catch (final IgniteCheckedException ice)
        {
            throw new IllegalStateException("Serialisation failed in correctness test", ice);
        }
    }

    protected void efficiencyImpl(final Ignite referenceGrid, final Ignite grid, final String serialisationType,
            final String referenceSerialisationType, final double marginFraction)
    {
        final SecureRandom rnJesus = new SecureRandom();
        final PasswordEncoder encoder = new MD4PasswordEncoderImpl();

        final Supplier<Object> comparisonValueSupplier = () -> {
            final String salt = GUID.generate();
            final String password = encoder.encodePassword(UUID.randomUUID().toString(), salt);
            final NodeRef node = new NodeRef(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE, UUID.randomUUID().toString());
            final RepositoryAuthenticatedUser user = new RepositoryAuthenticatedUser(UUID.randomUUID().toString(), password, true, true,
                    true, true,
                    // default Alfresco only ever uses a single granted authority
                    new GrantedAuthority[] { new GrantedAuthorityImpl("ROLE_AUTHENTICATED") }, CompositePasswordEncoder.MD4, salt);
            final Date credentialsExpiryDate = Date.from(Instant.now().plus(rnJesus.nextInt(365), ChronoUnit.DAYS));

            try
            {
                final Object value = CACHE_ENTRY_CTOR.newInstance(node, user, credentialsExpiryDate);

                return value;
            }
            catch (final InstantiationException | InvocationTargetException | IllegalAccessException e)
            {
                throw new RuntimeException("Failed to instantiate benchmark value");
            }
        };

        super.serialisationEfficiencyComparison(referenceGrid, grid, "AuthenticationCacheEntry", referenceSerialisationType,
                serialisationType, comparisonValueSupplier, marginFraction);
    }
}
