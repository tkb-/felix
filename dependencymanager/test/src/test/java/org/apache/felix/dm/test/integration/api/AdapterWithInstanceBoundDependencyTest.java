/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.felix.dm.test.integration.api;

import org.apache.felix.dm.Component;
import org.apache.felix.dm.DependencyManager;
import org.apache.felix.dm.test.components.Ensure;
import org.apache.felix.dm.test.integration.common.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;

@RunWith(PaxExam.class)
public class AdapterWithInstanceBoundDependencyTest extends TestBase {
    @Test
    public void testInstanceBoundDependency() {
        DependencyManager m = new DependencyManager(context);
        // helper class that ensures certain steps get executed in sequence
        Ensure e = new Ensure();
        // create a service provider and consumer
        Component sp = m.createComponent()
            .setInterface(ServiceInterface.class.getName(), null)
            .setImplementation(new ServiceProvider(e));
        Component sp2 = m.createComponent()
        .setInterface(ServiceInterface2.class.getName(), null)
            .setImplementation(new ServiceProvider2(e));
        Component sc = m.createComponent()
            .setImplementation(new ServiceConsumer())
            .add(m.createServiceDependency()
                .setService(ServiceInterface3.class)
                .setRequired(true));
        Component sa = m.createAdapterService(ServiceInterface.class, null)
            .setInterface(ServiceInterface3.class.getName(), null)
            .setImplementation(new ServiceAdapter(e));
        m.add(sc);
        m.add(sp);
        m.add(sp2);
        m.add(sa);
        e.waitForStep(5, 15000);
        m.remove(sa);
        m.remove(sp2);
        m.remove(sp);
        m.remove(sc);
    }
    
    static interface ServiceInterface {
        public void invoke();
    }
    
    static interface ServiceInterface2 {
        public void invoke();
    }
    
    static interface ServiceInterface3 {
        public void invoke();
    }
    
    static class ServiceProvider2 implements ServiceInterface2 {
        private final Ensure m_ensure;

        public ServiceProvider2(Ensure ensure) {
            m_ensure = ensure;
        }

        public void invoke() {
            m_ensure.step(4);
        }
    }

    static class ServiceProvider implements ServiceInterface {
        private final Ensure m_ensure;
        public ServiceProvider(Ensure e) {
            m_ensure = e;
        }
        public void invoke() {
            m_ensure.step(5);
        }
    }
    
    static class ServiceAdapter implements ServiceInterface3 {
        private Ensure m_ensure;
        private volatile ServiceInterface m_originalService;
        private volatile ServiceInterface2 m_injectedService;
        private volatile Component m_service;
        private volatile DependencyManager m_manager;
        
        public ServiceAdapter(Ensure e) {
            m_ensure = e;
        }
        public void init() {
            m_ensure.step(1);
            m_service.add(m_manager.createServiceDependency().setInstanceBound(true).setRequired(true).setService(ServiceInterface2.class));
        }
        public void start() {
            m_ensure.step(2);
        }
        public void invoke() {
            m_ensure.step(3);
            m_injectedService.invoke();
            m_originalService.invoke();
        }
        
        public void stop() {
            m_ensure.step(6);
        }
    }

    static class ServiceConsumer implements Runnable {
        private volatile ServiceInterface3 m_service;
        
        public void init() {
            Thread t = new Thread(this);
            t.start();
        }
        
        public void run() {
            m_service.invoke();
        }
    }
}


