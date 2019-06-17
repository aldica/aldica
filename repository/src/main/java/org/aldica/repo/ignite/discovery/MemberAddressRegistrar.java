/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.discovery;

/**
 * Instances of this interface handle the registration of the address details of the local data grid member / server.
 *
 * @author Axel Faust
 */
public interface MemberAddressRegistrar
{

    /**
     * Refreshes the registered address details of the local data grid member. This operation will automatically clear any old registrations
     * if they can be determined to belong to / have originated from the local data grid member
     */
    void refreshAddressRegistration();

    /**
     * Stores the address details of the local data grid member.
     */
    void registerAddresses();

    /**
     * Removes any registered address details of the local data grid member.
     */
    void removeAddressRegistrations();
}
