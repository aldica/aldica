/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.misc;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.aldica.repo.ignite.binary.base.AbstractCustomBinarySerializer;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.RepositoryAuthenticatedUser;
import org.alfresco.repo.security.authentication.RepositoryAuthenticationDao;
import org.alfresco.service.cmr.repository.NodeRef;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;

import net.sf.acegisecurity.GrantedAuthority;
import net.sf.acegisecurity.GrantedAuthorityImpl;
import net.sf.acegisecurity.UserDetails;
import net.sf.acegisecurity.providers.dao.User;

/**
 * Instances of this class handle (de-)serialisations of the {@link RepositoryAuthenticationDao} nested {@code CacheEntry} instances. This
 * serialiser optimises the serial form by aggregating various boolean flags into a more efficient bit mask and inlining the constituent
 * {@link RepositoryAuthenticatedUser} fields.
 *
 * @author Axel Faust
 */
public class AuthenticationCacheEntryBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String FLAGS = "flags";

    private static final String NODE_REF = "nodeRef";

    private static final String USER_NAME = "userName";

    private static final String PASSWORD = "password";

    private static final String AUTHORITIES = "authorities";

    private static final String HASH_INDICATOR = "hashIndicator";

    private static final String SALT = "salt";

    private static final String EXPIRY_DATE = "expiryDate";

    // cannot use a literal because class has default visibility
    private static final Class<?> CACHE_ENTRY_CLASS;

    private static final Field CACHE_ENTRY_NODE_REF;

    private static final Field CACHE_ENTRY_USER_DETAILS;

    private static final Field CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE;

    private static final Field USER_USER_NAME;

    private static final Field USER_PASSWORD;

    private static final Field USER_AUTHORITIES;

    private static final Field USER_HASH_INDICATOR;

    private static final Field USER_SALT;

    static
    {
        try
        {
            CACHE_ENTRY_CLASS = Class.forName("org.alfresco.repo.security.authentication.RepositoryAuthenticationDao$CacheEntry");
            CACHE_ENTRY_NODE_REF = CACHE_ENTRY_CLASS.getDeclaredField(NODE_REF);
            CACHE_ENTRY_USER_DETAILS = CACHE_ENTRY_CLASS.getDeclaredField("userDetails");
            CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE = CACHE_ENTRY_CLASS.getDeclaredField("credentialExpiryDate");

            USER_USER_NAME = User.class.getDeclaredField("username");
            USER_PASSWORD = User.class.getDeclaredField(PASSWORD);
            USER_AUTHORITIES = User.class.getDeclaredField(AUTHORITIES);
            USER_HASH_INDICATOR = RepositoryAuthenticatedUser.class.getDeclaredField(HASH_INDICATOR);
            USER_SALT = RepositoryAuthenticatedUser.class.getDeclaredField(SALT);

            // though fields are public, we still follow our pattern of setting accessible flag
            CACHE_ENTRY_NODE_REF.setAccessible(true);
            CACHE_ENTRY_USER_DETAILS.setAccessible(true);
            CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.setAccessible(true);

            // these fields are private as is the default in Alfresco
            USER_USER_NAME.setAccessible(true);
            USER_PASSWORD.setAccessible(true);
            USER_AUTHORITIES.setAccessible(true);
            USER_HASH_INDICATOR.setAccessible(true);
            USER_SALT.setAccessible(true);
        }
        catch (ClassNotFoundException | NoSuchFieldException e)
        {
            throw new AlfrescoRuntimeException("Failed to lookup cache entry class / fields");
        }
    }

    private static final byte FLAG_NO_EXPIRY = 1;

    private static final byte FLAG_ENABLED = 2;

    private static final byte FLAG_ACCOUNT_NOT_EXPIRED = 4;

    private static final byte FLAG_CREDENTIALS_NOT_EXPIRED = 8;

    private static final byte FLAG_ACCOUNT_NOT_LOCKED = 16;

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void writeBinary(final Object obj, final BinaryWriter writer) throws BinaryObjectException
    {
        final Class<? extends Object> cls = obj.getClass();
        if (!cls.equals(CACHE_ENTRY_CLASS))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        try
        {
            final NodeRef nodeRef = (NodeRef) CACHE_ENTRY_NODE_REF.get(obj);
            final RepositoryAuthenticatedUser userDetails = (RepositoryAuthenticatedUser) CACHE_ENTRY_USER_DETAILS.get(obj);
            final Date expiryDate = (Date) CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.get(obj);

            final byte flags = this.determineFlags(userDetails, expiryDate);

            if (this.useRawSerialForm)
            {
                final BinaryRawWriter rawWriter = writer.rawWriter();

                rawWriter.writeByte(flags);
                rawWriter.writeObject(nodeRef);
                this.write(userDetails.getUsername(), rawWriter);
                this.write(userDetails.getPassword(), rawWriter);

                final GrantedAuthority[] authorities = userDetails.getAuthorities();
                this.write(authorities.length, true, rawWriter);
                for (final GrantedAuthority authority : authorities)
                {
                    this.write(authority.getAuthority(), rawWriter);
                }

                final List<String> hashIndicator = userDetails.getHashIndicator();
                this.write(hashIndicator.size(), true, rawWriter);
                for (final String indicator : hashIndicator)
                {
                    this.write(indicator, rawWriter);
                }

                rawWriter.writeObject(userDetails.getSalt());

                if (expiryDate != null)
                {
                    this.write(expiryDate.getTime(), true, rawWriter);
                }
            }
            else
            {
                writer.writeByte(FLAGS, flags);
                writer.writeObject(NODE_REF, nodeRef);
                writer.writeString(USER_NAME, userDetails.getUsername());
                writer.writeString(PASSWORD, userDetails.getPassword());

                final GrantedAuthority[] grantedAuthorities = userDetails.getAuthorities();
                final String[] authorities = new String[grantedAuthorities.length];
                for (int i = 0; i < authorities.length; i++)
                {
                    authorities[i] = grantedAuthorities[i].getAuthority();
                }
                writer.writeStringArray(AUTHORITIES, authorities);

                final String[] hashIndicator = userDetails.getHashIndicator().toArray(new String[0]);
                writer.writeStringArray(HASH_INDICATOR, hashIndicator);
                writer.writeObject(SALT, userDetails.getSalt());

                if (expiryDate != null)
                {
                    writer.writeLong(EXPIRY_DATE, expiryDate.getTime());
                }
            }
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to retrieve fields to write", iae);
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
        if (!cls.equals(CACHE_ENTRY_CLASS))
        {
            throw new BinaryObjectException(cls + " is not supported by this serializer");
        }

        byte flags;
        NodeRef nodeRef;
        String userName;
        String password;
        String[] authorities;
        String[] hashIndicator;
        Serializable salt;
        Date expiryDate = null;

        if (this.useRawSerialForm)
        {
            final BinaryRawReader rawReader = reader.rawReader();
            flags = rawReader.readByte();
            nodeRef = rawReader.readObject();
            userName = this.readString(rawReader);
            password = this.readString(rawReader);

            authorities = new String[this.readInt(true, rawReader)];
            for (int i = 0; i < authorities.length; i++)
            {
                authorities[i] = this.readString(rawReader);
            }
            hashIndicator = new String[this.readInt(true, rawReader)];
            for (int i = 0; i < hashIndicator.length; i++)
            {
                hashIndicator[i] = this.readString(rawReader);
            }
            salt = rawReader.readObject();

            if ((flags & FLAG_NO_EXPIRY) == 0)
            {
                expiryDate = new Date(this.readLong(true, rawReader));
            }
        }
        else
        {
            flags = reader.readByte(FLAGS);
            nodeRef = reader.readObject(NODE_REF);
            userName = reader.readString(USER_NAME);
            password = reader.readString(PASSWORD);
            authorities = reader.readStringArray(AUTHORITIES);
            hashIndicator = reader.readStringArray(HASH_INDICATOR);
            salt = reader.readObject(SALT);

            if ((flags & FLAG_NO_EXPIRY) == 0)
            {
                expiryDate = new Date(reader.readLong(EXPIRY_DATE));
            }
        }

        final GrantedAuthority[] grantedAuthorities = new GrantedAuthority[authorities.length];
        for (int i = 0; i < grantedAuthorities.length; i++)
        {
            grantedAuthorities[i] = new GrantedAuthorityImpl(authorities[i]);
        }

        final UserDetails ud = new RepositoryAuthenticatedUser(userName, password, (flags & FLAG_ENABLED) == FLAG_ENABLED,
                (flags & FLAG_ACCOUNT_NOT_EXPIRED) == FLAG_ACCOUNT_NOT_EXPIRED,
                (flags & FLAG_CREDENTIALS_NOT_EXPIRED) == FLAG_CREDENTIALS_NOT_EXPIRED,
                (flags & FLAG_ACCOUNT_NOT_LOCKED) == FLAG_ACCOUNT_NOT_LOCKED, grantedAuthorities, Arrays.asList(hashIndicator), salt);

        try
        {
            CACHE_ENTRY_NODE_REF.set(obj, nodeRef);
            CACHE_ENTRY_USER_DETAILS.set(obj, ud);
            CACHE_ENTRY_CREDENTIALS_EXPIRY_DATE.set(obj, expiryDate);
        }
        catch (final IllegalAccessException iae)
        {
            throw new BinaryObjectException("Failed to write deserialised field values", iae);
        }
    }

    /**
     * Determines the flags to use when serialising a specific handled instance.
     *
     * @param userDetails
     *            the user details contained in the handled instance
     * @param expiryDate
     *            the expiration date contained in the handled instance
     * @return the flag bit mask to write in the serial form
     */
    protected byte determineFlags(final RepositoryAuthenticatedUser userDetails, final Date expiryDate)
    {
        final boolean enabled = userDetails.isEnabled();
        final boolean accountNotExpired = userDetails.isAccountNonExpired();
        final boolean credentialsNotExpired = userDetails.isCredentialsNonExpired();
        final boolean accountNotLocked = userDetails.isAccountNonLocked();

        byte flags = 0;

        if (expiryDate == null)
        {
            flags |= FLAG_NO_EXPIRY;
        }

        if (enabled)
        {
            flags |= FLAG_ENABLED;
        }

        if (accountNotExpired)
        {
            flags |= FLAG_ACCOUNT_NOT_EXPIRED;
        }

        if (credentialsNotExpired)
        {
            flags |= FLAG_CREDENTIALS_NOT_EXPIRED;
        }

        if (accountNotLocked)
        {
            flags |= FLAG_ACCOUNT_NOT_LOCKED;
        }
        return flags;
    }

}
