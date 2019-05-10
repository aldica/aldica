/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package org.aldica.common.ignite.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;

import org.alfresco.util.ParameterCheck;

/**
 * @author Axel Faust
 */
public class CombinedExpiryPolicy implements ExpiryPolicy
{

    /**
     * Obtains a {@link Factory} for a combined {@link ExpiryPolicy}.
     *
     * @param policyFactories
     *            the policy factories for the constituent policies
     * @return a {@link Factory} for a combined {@link ExpiryPolicy}.
     */
    public static Factory<ExpiryPolicy> factoryOf(final Collection<Factory<? extends ExpiryPolicy>> policyFactories)
    {
        return new FactoryBuilder.SingletonFactory<>(new CombinedExpiryPolicy(policyFactories));
    }

    protected final Collection<ExpiryPolicy> policies;

    public CombinedExpiryPolicy(final Collection<Factory<? extends ExpiryPolicy>> policyFactories)
    {
        ParameterCheck.mandatoryCollection("policyFactories", policyFactories);
        this.policies = new ArrayList<>();
        policyFactories.forEach(factory -> {
            this.policies.add(factory.create());
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForCreation()
    {
        final AtomicReference<Duration> shortestDuration = new AtomicReference<>();
        final long currentTime = System.currentTimeMillis();

        this.policies.forEach(policy -> {
            final Duration duration = policy.getExpiryForCreation();
            if (duration != null)
            {
                final Duration currentShortest = shortestDuration.get();
                if (currentShortest == null || (currentShortest.getAdjustedTime(currentTime) > duration.getAdjustedTime(currentTime)))
                {
                    shortestDuration.set(duration);
                }
            }
        });

        return shortestDuration.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForAccess()
    {
        final AtomicReference<Duration> shortestDuration = new AtomicReference<>();
        final long currentTime = System.currentTimeMillis();

        this.policies.forEach(policy -> {
            final Duration duration = policy.getExpiryForAccess();
            if (duration != null)
            {
                final Duration currentShortest = shortestDuration.get();
                if (currentShortest == null || (currentShortest.getAdjustedTime(currentTime) > duration.getAdjustedTime(currentTime)))
                {
                    shortestDuration.set(duration);
                }
            }
        });

        return shortestDuration.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Duration getExpiryForUpdate()
    {
        final AtomicReference<Duration> shortestDuration = new AtomicReference<>();
        final long currentTime = System.currentTimeMillis();

        this.policies.forEach(policy -> {
            final Duration duration = policy.getExpiryForUpdate();
            if (duration != null)
            {
                final Duration currentShortest = shortestDuration.get();
                if (currentShortest == null || (currentShortest.getAdjustedTime(currentTime) > duration.getAdjustedTime(currentTime)))
                {
                    shortestDuration.set(duration);
                }
            }
        });

        return shortestDuration.get();
    }

}
