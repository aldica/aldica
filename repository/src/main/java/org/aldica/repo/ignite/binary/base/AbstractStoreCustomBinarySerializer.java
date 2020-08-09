/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.base;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.util.Pair;
import org.alfresco.util.ParameterCheck;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.binary.BinaryRawWriter;
import org.apache.ignite.binary.BinaryReader;
import org.apache.ignite.binary.BinaryWriter;
import org.jetbrains.annotations.NotNull;

/**
 * Instances of this class support (de-)serialisations of values instances into serial form that use {@link StoreRef store
 * reference}-related elements, primarily {@link StoreRef#getProtocol() protocol} and {@link StoreRef#getIdentifier() identifier}
 * information. This implementation primarily aims to optimise handling of well-known values as part of a store's identity.
 *
 * @author Axel Faust
 */
public abstract class AbstractStoreCustomBinarySerializer extends AbstractCustomBinarySerializer
{

    private static final String TYPE = "type";

    private static final String PROTOCOL = "protocol";

    private static final String IDENTIFIER = "identifier";

    private static final String PROTOCOL_USER = "user";

    private static final String PROTOCOL_SYSTEM = "system";

    // relevant protocols - no support for legacy avm or any test-like ones (test/deleted)
    private static final String[] PROTOCOLS = { PROTOCOL_USER, PROTOCOL_SYSTEM, StoreRef.PROTOCOL_ARCHIVE, StoreRef.PROTOCOL_WORKSPACE };

    private static final byte CUSTOM_PROTOCOL = (byte) PROTOCOLS.length;

    private static final String IDENTIFIER_ALFRESCO_USER_STORE = "alfrescoUserStore";

    private static final String IDENTIFIER_SYSTEM = "system";

    private static final String IDENTIFIER_LIGHT_WEIGHT_VERSION_STORE = "lightWeightVersionStore";

    private static final String IDENTIFIER_VERSION_2_STORE = "version2Store";

    private static final String IDENTIFIER_SPACES_STORE = "SpacesStore";

    // relevant default stores - no support for tenant-specific store identifiers
    private static final String[] IDENTIFIERS = { IDENTIFIER_ALFRESCO_USER_STORE, IDENTIFIER_SYSTEM, IDENTIFIER_LIGHT_WEIGHT_VERSION_STORE,
            IDENTIFIER_VERSION_2_STORE, IDENTIFIER_SPACES_STORE };

    private static final byte CUSTOM_IDENTIFIER = (byte) IDENTIFIERS.length;

    private static final Map<String, Byte> KNOWN_PROTOCOLS;

    private static final Map<String, Byte> KNOWN_IDENTIFIERS;

    static
    {
        final Map<String, Byte> knownProtocols = new HashMap<>(4);
        for (int idx = 0; idx < PROTOCOLS.length; idx++)
        {
            knownProtocols.put(PROTOCOLS[idx], (byte) idx);
        }
        KNOWN_PROTOCOLS = Collections.unmodifiableMap(knownProtocols);

        final Map<String, Byte> knownIdentifiers = new HashMap<>(4);
        for (int idx = 0; idx < IDENTIFIERS.length; idx++)
        {
            knownIdentifiers.put(IDENTIFIERS[idx], (byte) idx);
        }
        KNOWN_IDENTIFIERS = Collections.unmodifiableMap(knownIdentifiers);
    }

    /**
     * Writes the identity details of a store in the regular serialised form.
     *
     * @param protocol
     *            the protocol to write
     * @param identifier
     *            the identifier to write
     * @param writer
     *            the serial writer to use
     */
    protected final void writeStore(@NotNull final String protocol, @NotNull final String identifier, @NotNull final BinaryWriter writer)
    {
        ParameterCheck.mandatory("protocol", protocol);
        ParameterCheck.mandatory("identifier", identifier);
        ParameterCheck.mandatory("writer", writer);

        final byte protocolType = KNOWN_PROTOCOLS.getOrDefault(protocol, CUSTOM_PROTOCOL);
        final byte identifierType = KNOWN_IDENTIFIERS.getOrDefault(identifier, CUSTOM_IDENTIFIER);
        // we have 5 and 6 values respectively, so can use an aggregated byte
        final byte type = (byte) (protocolType | (identifierType << 3));

        writer.writeByte(TYPE, type);
        if (protocolType == CUSTOM_PROTOCOL)
        {
            writer.writeString(PROTOCOL, protocol);
        }
        if (identifierType == CUSTOM_IDENTIFIER)
        {
            writer.writeString(IDENTIFIER, identifier);
        }
    }

    /**
     * Writes the identity details of a store in the raw serialised form.
     *
     * @param protocol
     *            the protocol to write
     * @param identifier
     *            the identifier to write
     * @param rawWriter
     *            the raw serial writer to use
     */
    protected final void writeStore(@NotNull final String protocol, @NotNull final String identifier,
            @NotNull final BinaryRawWriter rawWriter)
    {
        ParameterCheck.mandatory("protocol", protocol);
        ParameterCheck.mandatory("identifier", identifier);
        ParameterCheck.mandatory("rawWriter", rawWriter);

        final byte protocolType = KNOWN_PROTOCOLS.getOrDefault(protocol, CUSTOM_PROTOCOL);
        final byte identifierType = KNOWN_IDENTIFIERS.getOrDefault(identifier, CUSTOM_IDENTIFIER);
        // we have 5 and 6 values respectively, so can use an aggregated byte
        final byte type = (byte) (protocolType | (identifierType << 3));

        rawWriter.writeByte(type);
        if (protocolType == CUSTOM_PROTOCOL)
        {
            this.write(protocol, rawWriter);
        }
        if (identifierType == CUSTOM_IDENTIFIER)
        {
            this.write(identifier, rawWriter);
        }
    }

    /**
     * Reads the identity details of a store from the regular serialised form.
     *
     * @param reader
     *            the reader to use
     * @return a pair of protocol and identifier
     */
    @NotNull
    protected final Pair<String, String> readStore(@NotNull final BinaryReader reader)
    {
        ParameterCheck.mandatory("reader", reader);

        final byte type = reader.readByte(TYPE);
        final byte protocolType = (byte) (type & 0x07);
        final byte identifierType = (byte) (type >> 3);
        String protocol = null;
        String identifier = null;

        if (protocolType == CUSTOM_PROTOCOL)
        {
            protocol = reader.readString(PROTOCOL);
        }
        if (identifierType == CUSTOM_IDENTIFIER)
        {
            identifier = reader.readString(IDENTIFIER);
        }

        protocol = AbstractStoreCustomBinarySerializer.determineEffectiveProtocol(protocolType, protocol);
        identifier = AbstractStoreCustomBinarySerializer.determineEffectiveIdentifier(identifierType, identifier);

        return new Pair<>(protocol, identifier);
    }

    /**
     * Reads the identity details of a store from the raw serialised form.
     *
     * @param rawReader
     *            the raw reader to use
     * @return a pair of protocol and identifier
     */
    @NotNull
    protected final Pair<String, String> readStore(@NotNull final BinaryRawReader rawReader)
    {
        ParameterCheck.mandatory("rawReader", rawReader);

        final byte type = rawReader.readByte();
        final byte protocolType = (byte) (type & 0x07);
        final byte identifierType = (byte) (type >> 3);
        String protocol = null;
        String identifier = null;

        if (protocolType == CUSTOM_PROTOCOL)
        {
            protocol = this.readString(rawReader);
        }
        if (identifierType == CUSTOM_IDENTIFIER)
        {
            identifier = this.readString(rawReader);
        }

        protocol = AbstractStoreCustomBinarySerializer.determineEffectiveProtocol(protocolType, protocol);
        identifier = AbstractStoreCustomBinarySerializer.determineEffectiveIdentifier(identifierType, identifier);

        return new Pair<>(protocol, identifier);
    }

    private static final String determineEffectiveProtocol(final byte protocolType, final String protocol)
    {
        String effectiveProtocol = protocol;
        if (protocolType > CUSTOM_PROTOCOL || protocolType < 0)
        {
            throw new BinaryObjectException("Read unsupported protocol flag value " + protocolType);
        }
        else if (protocolType != CUSTOM_PROTOCOL)
        {
            effectiveProtocol = PROTOCOLS[protocolType];
        }
        return effectiveProtocol;
    }

    private static final String determineEffectiveIdentifier(final byte identifierType, final String identifier)
    {
        String effectiveIdentifier = identifier;
        if (identifierType > CUSTOM_IDENTIFIER || identifierType < 0)
        {
            throw new BinaryObjectException("Read unsupported identifier flag value " + identifierType);
        }
        else if (identifierType != CUSTOM_IDENTIFIER)
        {
            effectiveIdentifier = IDENTIFIERS[identifierType];
        }
        return effectiveIdentifier;
    }
}
