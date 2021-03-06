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
package org.apache.felix.http.base.internal.service;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletException;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;

import org.apache.felix.http.api.ExtHttpService;
import org.apache.felix.http.base.internal.context.ExtServletContext;
import org.apache.felix.http.base.internal.handler.FilterHandler;
import org.apache.felix.http.base.internal.handler.PerContextHandlerRegistry;
import org.apache.felix.http.base.internal.handler.ServletHandler;
import org.apache.felix.http.base.internal.logger.SystemLogger;
import org.apache.felix.http.base.internal.runtime.FilterInfo;
import org.apache.felix.http.base.internal.runtime.ServletInfo;
import org.osgi.framework.Bundle;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.NamespaceException;

public final class HttpServiceImpl implements ExtHttpService
{
    private final Bundle bundle;
    private final PerContextHandlerRegistry handlerRegistry;
    private final Set<Servlet> localServlets = new HashSet<Servlet>();
    private final Set<Filter> localFilters = new HashSet<Filter>();
    private final ServletContextManager contextManager;

    private final Map<String, ServletHandler> aliasMap = new HashMap<String, ServletHandler>();

    public HttpServiceImpl(final Bundle bundle,
            final ServletContext context,
            final PerContextHandlerRegistry handlerRegistry,
            final ServletContextAttributeListener servletAttributeListener,
            final boolean sharedContextAttributes,
            final ServletRequestListener reqListener,
            final ServletRequestAttributeListener reqAttrListener)
    {
        this.bundle = bundle;
        this.handlerRegistry = handlerRegistry;
        this.contextManager = new ServletContextManager(this.bundle, context,
                servletAttributeListener, sharedContextAttributes,
                reqListener, reqAttrListener);
    }

    @Override
    public HttpContext createDefaultHttpContext()
    {
        return new DefaultHttpContext(this.bundle);
    }

    /**
     * @see org.apache.felix.http.api.ExtHttpService#registerFilter(javax.servlet.Filter, java.lang.String, java.util.Dictionary, int, org.osgi.service.http.HttpContext)
     */
    @Override
    public void registerFilter(Filter filter, String pattern, Dictionary initParams, int ranking, HttpContext context) throws ServletException
    {
        if (filter == null)
        {
            throw new IllegalArgumentException("Filter must not be null");
        }

        final Map<String, String> paramMap = new HashMap<String, String>();
        if ( initParams != null && initParams.size() > 0 )
        {
            Enumeration e = initParams.keys();
            while (e.hasMoreElements())
            {
                Object key = e.nextElement();
                Object value = initParams.get(key);

                if ((key instanceof String) && (value instanceof String))
                {
                    paramMap.put((String) key, (String) value);
                }
            }
        }

        final FilterInfo filterInfo = new FilterInfo(null, pattern, ranking, paramMap);
        if ( !filterInfo.isValid() )
        {
            throw new ServletException("Invalid registration information for filter.");
        }

        final ExtServletContext httpContext = getServletContext(context);

        FilterHandler handler = new FilterHandler(null, httpContext, filter, filterInfo);
        try {
            this.handlerRegistry.addFilter(handler);
        } catch (ServletException e) {
            // TODO create failure DTO
        }
        this.localFilters.add(filter);
    }

    /**
     * No need to sync this method, syncing is done via {@link #registerServlet(String, Servlet, Dictionary, HttpContext)}
     * @see org.osgi.service.http.HttpService#registerResources(java.lang.String, java.lang.String, org.osgi.service.http.HttpContext)
     */
    @Override
    public void registerResources(final String alias, final String name, final HttpContext context) throws NamespaceException
    {
        if (!isNameValid(name))
        {
            throw new IllegalArgumentException("Malformed resource name [" + name + "]");
        }

        // TODO - check validity of alias
        try
        {
            Servlet servlet = new ResourceServlet(name);
            registerServlet(alias, servlet, null, context);
        }
        catch (ServletException e)
        {
            SystemLogger.error("Failed to register resources", e);
        }
    }

    /**
     * @see org.osgi.service.http.HttpService#registerServlet(java.lang.String, javax.servlet.Servlet, java.util.Dictionary, org.osgi.service.http.HttpContext)
     */
    @Override
    public void registerServlet(String alias, Servlet servlet, Dictionary initParams, HttpContext context)
    throws ServletException, NamespaceException
    {
        if (servlet == null)
        {
            throw new IllegalArgumentException("Servlet must not be null");
        }
        if (!isAliasValid(alias))
        {
            throw new IllegalArgumentException("Malformed servlet alias [" + alias + "]");
        }

        final Map<String, String> paramMap = new HashMap<String, String>();
        if ( initParams != null && initParams.size() > 0 )
        {
            Enumeration e = initParams.keys();
            while (e.hasMoreElements())
            {
                Object key = e.nextElement();
                Object value = initParams.get(key);

                if ((key instanceof String) && (value instanceof String))
                {
                    paramMap.put((String) key, (String) value);
                }
            }
        }

        final ServletInfo servletInfo = new ServletInfo(null, alias, 0, paramMap);
        final ExtServletContext httpContext = getServletContext(context);

        final ServletHandler handler = new ServletHandler(null,
                httpContext,
                servletInfo,
                servlet);

        synchronized ( this.aliasMap )
        {
        	if ( this.aliasMap.containsKey(alias) )
        	{
        	    throw new NamespaceException("Alias " + alias + " is already in use.");
        	}
        	if ( this.localServlets.contains(servlet) )
        	{
                throw new ServletException("Servlet instance " + handler.getName() + " already registered");
        	}

            this.handlerRegistry.addServlet(handler);

            this.aliasMap.put(alias, handler);
            this.localServlets.add(servlet);
        }
    }

    /**
     * @see org.osgi.service.http.HttpService#unregister(java.lang.String)
     */
    @Override
    public void unregister(final String alias)
    {
        synchronized ( this.aliasMap )
        {
		    final ServletHandler handler = this.aliasMap.remove(alias);
	        if ( handler == null )
	        {
	        	throw new IllegalArgumentException("Nothing registered at " + alias);
	        }
	        final Servlet servlet = this.handlerRegistry.removeServlet(handler.getServletInfo(), true);
	        if (servlet != null)
	        {
	        	this.localServlets.remove(servlet);
	        }
		}
    }

    public void unregisterAll()
    {
        final Set<Servlet> servlets = new HashSet<Servlet>(this.localServlets);
        for (final Servlet servlet : servlets)
        {
            unregisterServlet(servlet, false);
        }

        final Set<Filter> filters = new HashSet<Filter>(this.localFilters);
        for (final Filter fiter : filters)
        {
            unregisterFilter(fiter, false);
        }
    }

    /**
     * Old whiteboard support
     * @see org.apache.felix.http.api.ExtHttpService#unregisterFilter(javax.servlet.Filter)
     */
    @Override
    public void unregisterFilter(Filter filter)
    {
        unregisterFilter(filter, true);
    }

    /**
     * Old whiteboard support
     * @see org.apache.felix.http.api.ExtHttpService#unregisterServlet(javax.servlet.Servlet)
     */
    @Override
    public void unregisterServlet(final Servlet servlet)
    {
        this.unregisterServlet(servlet, true);
    }

    private void unregisterServlet(final Servlet servlet, final boolean destroy)
    {
        if ( servlet != null )
        {
            this.handlerRegistry.removeServlet(servlet, destroy);
            synchronized ( this.aliasMap )
            {
            	final Iterator<Map.Entry<String, ServletHandler>> i = this.aliasMap.entrySet().iterator();
            	while ( i.hasNext() )
            	{
            		final Map.Entry<String, ServletHandler> entry = i.next();
            		if ( entry.getValue().getServlet() == servlet )
            		{
            			i.remove();
            			break;
            		}

            	}
            	this.localServlets.remove(servlet);
            }
        }
    }

    private ExtServletContext getServletContext(HttpContext context)
    {
        if (context == null)
        {
            context = createDefaultHttpContext();
        }

        return this.contextManager.getServletContext(context);
    }

    private boolean isNameValid(String name)
    {
        if (name == null)
        {
            return false;
        }

        if (!name.equals("/") && name.endsWith("/"))
        {
            return false;
        }

        return true;
    }

    private void unregisterFilter(Filter filter, final boolean destroy)
    {
        if (filter != null)
        {
            this.handlerRegistry.removeFilter(filter, destroy);
            this.localFilters.remove(filter);
        }
    }

    private boolean isAliasValid(final String alias)
    {
        if (alias == null)
        {
            return false;
        }

        if (!alias.equals("/") && (!alias.startsWith("/") || alias.endsWith("/")))
        {
            return false;
        }

        return true;
    }
}
