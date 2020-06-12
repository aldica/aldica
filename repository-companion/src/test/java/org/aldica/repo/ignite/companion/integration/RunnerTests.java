/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.companion.integration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.aldica.repo.ignite.companion.Runner;
import org.junit.Test;

/**
 *
 * @author Axel Faust
 */
public class RunnerTests
{

    private static final Map<String, String> COMMON_CONFIGURATION;
    static
    {
        final Map<String, String> m = new HashMap<>();

        m.put("aldica.core.initialMembers", "localhost:47110");
        m.put("aldica.core.local.comm.port", "47200");
        m.put("aldica.core.local.disco.port", "47210");
        m.put("aldica.core.local.time.port", "47220");
        m.put("aldica.core.public.host", "host.docker.internal");

        COMMON_CONFIGURATION = Collections.unmodifiableMap(m);
    }

    /**
     * This test checks whether the application can successfully start and connect with the Docker-based Repository node using the default
     * configuration.
     */
    @Test
    public void defaultConfigStartAndStop()
    {
        final Runner runner = new Runner(COMMON_CONFIGURATION);
        runner.start();

        runner.stop();
    }
}
