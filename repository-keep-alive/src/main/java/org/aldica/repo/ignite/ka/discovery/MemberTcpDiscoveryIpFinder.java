/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.ka.discovery;

import java.util.ArrayList;
import java.util.List;

import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;

/**
 * @author Axel Faust
 */
public class MemberTcpDiscoveryIpFinder extends TcpDiscoveryVmIpFinder
{

    /**
     * @param initialMembers
     *            the initialMembers to set
     */
    public void setInitialMembers(final String initialMembers)
    {
        if (initialMembers != null && !initialMembers.trim().isEmpty())
        {
            final String[] fragments = initialMembers.split("\\s*,\\s*");
            final List<String> addresses = new ArrayList<>();
            for (final String address : fragments)
            {
                if (!address.isEmpty())
                {
                    addresses.add(address);
                }
            }
            if (!addresses.isEmpty())
            {
                this.setAddresses(addresses);
            }
        }
    }
}
