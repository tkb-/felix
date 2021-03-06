/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.felix.http.base.internal.whiteboard.tracker;

import javax.annotation.Nonnull;

import org.apache.felix.http.base.internal.runtime.ServletContextHelperInfo;
import org.apache.felix.http.base.internal.whiteboard.ServletContextHelperManager;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.service.http.context.ServletContextHelper;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.util.tracker.ServiceTracker;

/**
 * Tracks all {@link ServletContextHelper} services.
 * Only services with the required properties are tracker, services missing these
 * properties are ignored.
 */
public final class ServletContextHelperTracker extends ServiceTracker<ServletContextHelper, ServiceReference<ServletContextHelper>>
{
    private final ServletContextHelperManager contextManager;

    private static org.osgi.framework.Filter createFilter(final BundleContext btx)
    {
        try
        {
            return btx.createFilter(String.format("(&(objectClass=%s)(%s=*)(%s=*))",
                    ServletContextHelper.class.getName(),
                    HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_NAME,
                    HttpWhiteboardConstants.HTTP_WHITEBOARD_CONTEXT_PATH));
        }
        catch ( final InvalidSyntaxException ise)
        {
            // we can safely ignore it as the above filter is a constant
        }
        return null; // we never get here - and if we get an NPE which is fine
    }

    public ServletContextHelperTracker(@Nonnull final BundleContext context, @Nonnull final ServletContextHelperManager manager)
    {
        super(context, createFilter(context), null);
        this.contextManager = manager;
    }

    @Override
    public final ServiceReference<ServletContextHelper> addingService(@Nonnull final ServiceReference<ServletContextHelper> ref)
    {
        this.added(ref);
        return ref;
    }

    @Override
    public final void modifiedService(@Nonnull final ServiceReference<ServletContextHelper> ref, @Nonnull final ServiceReference<ServletContextHelper> service)
    {
        this.removed(ref);
        this.added(ref);
    }

    @Override
    public final void removedService(@Nonnull final ServiceReference<ServletContextHelper> ref, @Nonnull final ServiceReference<ServletContextHelper> service)
    {
        this.removed(ref);
    }

    private void added(@Nonnull final ServiceReference<ServletContextHelper> ref)
    {
        final ServletContextHelperInfo info = new ServletContextHelperInfo(ref);
        this.contextManager.addContextHelper(info);
    }

    private void removed(@Nonnull final ServiceReference<ServletContextHelper> ref)
    {
        final ServletContextHelperInfo info = new ServletContextHelperInfo(ref);
        this.contextManager.removeContextHelper(info);
    }
}
