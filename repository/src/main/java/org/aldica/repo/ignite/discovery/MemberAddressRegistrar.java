/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.discovery;

/**
 * Instances of this interface handle the registration of data grid member address details.
 *
 * @author Axel Faust
 */
public interface MemberAddressRegistrar
{

    /**
     * Updates the data grid member registrations. This operation may not perform any write operation in the underlying database if there
     * are no changes in the grid topology or if another member in the grid is currently processing the same operation.
     */
    void updateMemberRegistration();
}
