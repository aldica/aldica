/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.binary.entity;

import org.aldica.repo.ignite.binary.base.NoOpBinarySerializer;

/**
 * Instances of this class provide a no-op implementation for custom serialisation. Since the companion app will never (de-)serialise any
 * cache values, it technically does not require any custom serialiser at all. But in order to succeed in Ignite's configuration checks
 * within a distributed grid, the same serialiser classes need to be configured for the same key and value classes.
 *
 * @author Axel Faust
 */
public class ContentUrlEntityBinarySerializer extends NoOpBinarySerializer
{
    // NO-OP
}
