/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite;

import java.util.Arrays;

import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.plugin.security.SecurityCredentials;
import org.hamcrest.core.StringStartsWith;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

/**
 *
 * @author Axel Faust
 */
public class SimpleSecurityPluginTest extends GridTestsBase
{

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void joinWithNewNodeMissingCredentials()
    {
        try
        {
            final SecurityCredentials credentials = new SecurityCredentials("login", "pass");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials);
            final IgniteConfiguration conf2 = createConfiguration(2, true);

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithExistingNodeMissingSecurityConfig()
    {
        try
        {
            final SecurityCredentials credentials = new SecurityCredentials("login", "pass");

            final IgniteConfiguration conf1 = createConfiguration(1, false);
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials);
            final IgniteConfiguration conf3 = createConfiguration(3, false);

            Ignition.start(conf1);


            // with existing node missing any security configuration, additional nodes cannot join since the security processor differs
            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));

            // with existing node missing any security configuration, additional nodes can always join
            Ignition.start(conf2);
            Ignition.start(conf3);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithValidCredentials()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login1", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login2", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1, Arrays.asList(credentials1, credentials2));
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials1, Arrays.asList(credentials1, credentials2));
            final IgniteConfiguration conf3 = createConfiguration(3, true, credentials2, Arrays.asList(credentials1, credentials2));

            Ignition.start(conf1);
            Ignition.start(conf2);
            Ignition.start(conf3);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithValidCredentialsSameTier()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login1", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login2", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKey", "test-tier", Arrays.asList("test-tier"));
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKey", "test-tier", Arrays.asList("test-tier"));
            final IgniteConfiguration conf3 = createConfiguration(3, true, credentials2, Arrays.asList(credentials1, credentials2),
                    "tierKey", "test-tier", Arrays.asList("test-tier"));

            Ignition.start(conf1);
            Ignition.start(conf2);
            Ignition.start(conf3);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithValidCredentialsDifferentTier()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login1", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login2", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKey", "test-tier-1", Arrays.asList("test-tier-1"));
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKey", "test-tier-2", Arrays.asList("test-tier-2"));

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithValidCredentialsDifferentTierKey()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login1", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login2", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKeyA", "test-tier", Arrays.asList("test-tier"));
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials1, Arrays.asList(credentials1, credentials2),
                    "tierKeyB", "test-tier", Arrays.asList("test-tier"));

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }

    @Test
    public void joinWithInvalidCredentials()
    {
        try
        {
            final SecurityCredentials credentials1 = new SecurityCredentials("login", "pass1");
            final SecurityCredentials credentials2 = new SecurityCredentials("login", "pass2");

            final IgniteConfiguration conf1 = createConfiguration(1, false, credentials1);
            final IgniteConfiguration conf2 = createConfiguration(2, true, credentials2);

            Ignition.start(conf1);

            this.thrown.expect(IgniteException.class);
            this.thrown.expectMessage(StringStartsWith.startsWith("Failed to start manager:"));
            // ExpectedException.expectCause only works for first-level causes - SecurityException is nested multiple times
            Ignition.start(conf2);
        }
        finally
        {
            Ignition.stopAll(true);
        }
    }
}
