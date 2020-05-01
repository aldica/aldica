/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.repo.ignite.job;

import org.aldica.repo.ignite.discovery.MemberAddressRegistrar;

import de.acosix.alfresco.utility.repo.job.GenericJob;
import de.acosix.alfresco.utility.repo.job.JobUtilities;

/**
 * Instances of this class execute the simple job of {@link MemberAddressRegistrar#updateMemberRegistration() updating the member
 * registrations} with regards to addresses of the active grid members.
 *
 * @author Axel Faust
 */
public class MemberAddressRegistrationRefreshJob implements GenericJob
{

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final Object jobExecutionContext)
    {
        final MemberAddressRegistrar memberAddressRegistrar = JobUtilities.getJobDataValue(jobExecutionContext, "memberAddressRegistrar",
                MemberAddressRegistrar.class);
        memberAddressRegistrar.updateMemberRegistration();
    }

}
