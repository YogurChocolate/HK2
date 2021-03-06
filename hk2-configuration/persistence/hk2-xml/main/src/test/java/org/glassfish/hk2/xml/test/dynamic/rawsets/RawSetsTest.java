/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2016 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.hk2.xml.test.dynamic.rawsets;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.inject.Singleton;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.configuration.hub.api.BeanDatabase;
import org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener;
import org.glassfish.hk2.configuration.hub.api.Change;
import org.glassfish.hk2.configuration.hub.api.Change.ChangeCategory;
import org.glassfish.hk2.configuration.hub.api.Hub;
import org.glassfish.hk2.configuration.hub.api.Instance;
import org.glassfish.hk2.xml.api.XmlHk2ConfigurationBean;
import org.glassfish.hk2.xml.api.XmlRootHandle;
import org.glassfish.hk2.xml.api.XmlService;
import org.glassfish.hk2.xml.test.basic.beans.Commons;
import org.glassfish.hk2.xml.test.basic.beans.Museum;
import org.glassfish.hk2.xml.test.beans.AuthorizationProviderBean;
import org.glassfish.hk2.xml.test.beans.DomainBean;
import org.glassfish.hk2.xml.test.beans.MachineBean;
import org.glassfish.hk2.xml.test.beans.SSLManagerBean;
import org.glassfish.hk2.xml.test.beans.SSLManagerBeanCustomizer;
import org.glassfish.hk2.xml.test.beans.SecurityManagerBean;
import org.glassfish.hk2.xml.test.dynamic.merge.MergeTest;
import org.glassfish.hk2.xml.test.utilities.Utilities;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class RawSetsTest {
    public final static String MUSEUM2_FILE = "museum2.xml";
    
    public final static String MUSEUM_TYPE = "/museum";
    public final static String MUSEUM_INSTANCE = "museum";
    
    
    
    public final static String AGE_TAG = "age";
    
    public final static int ONE_OH_ONE_INT = 101;
    
    /**
     * Just verifies that the original state of the Museum
     * object from the file is as expected
     */
    @SuppressWarnings("unchecked")
    public static void verifyPreState(XmlRootHandle<Museum> rootHandle, Hub hub) {
        Museum museum = rootHandle.getRoot();
        
        Assert.assertEquals(Commons.HUNDRED_INT, museum.getId());
        Assert.assertEquals(Commons.BEN_FRANKLIN, museum.getName());
        Assert.assertEquals(Commons.HUNDRED_TEN_INT, museum.getAge());
        
        Instance instance = hub.getCurrentDatabase().getInstance(MUSEUM_TYPE, MUSEUM_INSTANCE);
        Map<String, Object> beanLikeMap = (Map<String, Object>) instance.getBean();
        
        Assert.assertEquals(Commons.BEN_FRANKLIN, beanLikeMap.get(Commons.NAME_TAG));
        Assert.assertEquals(Commons.HUNDRED_INT, beanLikeMap.get(Commons.ID_TAG));
        Assert.assertEquals(Commons.HUNDRED_TEN_INT, beanLikeMap.get(AGE_TAG));
    }
    
    /**
     * Tests that single fields can be modified
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    // @org.junit.Ignore
    public void testModifySingleProperty() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        UpdateListener listener = locator.getService(UpdateListener.class);
        
        URL url = getClass().getClassLoader().getResource(Commons.MUSEUM1_FILE);
        
        XmlRootHandle<Museum> rootHandle = xmlService.unmarshal(url.toURI(), Museum.class);
        
        verifyPreState(rootHandle, hub);
        
        Museum museum = rootHandle.getRoot();
        
        // All above just verifying the pre-state
        museum.setAge(ONE_OH_ONE_INT);  // getting younger?
        
        Assert.assertEquals(Commons.HUNDRED_INT, museum.getId());
        Assert.assertEquals(Commons.BEN_FRANKLIN, museum.getName());
        Assert.assertEquals(ONE_OH_ONE_INT, museum.getAge());
        
        Instance instance = hub.getCurrentDatabase().getInstance(MUSEUM_TYPE, MUSEUM_INSTANCE);
        Map<String, Object> beanLikeMap = (Map<String, Object>) instance.getBean();
        
        Assert.assertEquals(Commons.BEN_FRANKLIN, beanLikeMap.get(Commons.NAME_TAG));
        Assert.assertEquals(Commons.HUNDRED_INT, beanLikeMap.get(Commons.ID_TAG));
        Assert.assertEquals(ONE_OH_ONE_INT, beanLikeMap.get(AGE_TAG));  // The test
        
        List<Change> changes = listener.changes;
        Assert.assertNotNull(changes);
        
        Assert.assertEquals(1, changes.size());
        
        for (Change change : changes) {
            Assert.assertEquals(ChangeCategory.MODIFY_INSTANCE, change.getChangeCategory());
        }
    }
    
    /**
     * Tests that a direct type can be added via set and then used dynamically
     * 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    @Test
    // @org.junit.Ignore
    public void testAddDirectTypeWithSet() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        DomainBean domain = rootHandle.getRoot();
        SecurityManagerBean securityManager = domain.getSecurityManager();
        
        SSLManagerBean sslManager = xmlService.createBean(SSLManagerBean.class);
        
        securityManager.setSSLManager(sslManager);
        
        sslManager = securityManager.getSSLManager();
        
        Assert.assertEquals(securityManager, ((XmlHk2ConfigurationBean) sslManager)._getParent());
        Assert.assertEquals(SSLManagerBean.FORT_KNOX, sslManager.getSSLPrivateKeyLocation());
        
        Assert.assertEquals(sslManager, locator.getService(SSLManagerBean.class));
        
        Instance instance = hub.getCurrentDatabase().getInstance(MergeTest.SSL_MANAGER_TYPE, MergeTest.SSL_MANAGER_INSTANCE_NAME);
        Map<String, Object> beanLikeMap = (Map<String, Object>) instance.getBean();
        
        Assert.assertNotNull(beanLikeMap);
        Assert.assertTrue(beanLikeMap.isEmpty());
    }
    
    /**
     * Tests that a direct type can be removed via set
     * 
     * @throws Exception
     */
    @Test
    // @org.junit.Ignore
    public void testRemoveDirectTypeWithSet() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        DomainBean domain = rootHandle.getRoot();
        
        domain.setSecurityManager(null);
        
        Assert.assertNull(domain.getSecurityManager());
        Assert.assertNull(locator.getService(SecurityManagerBean.class));
        
        Assert.assertNull(locator.getService(AuthorizationProviderBean.class));
        
        Assert.assertNull(hub.getCurrentDatabase().getInstance(MergeTest.SECURITY_MANAGER_TYPE, MergeTest.SECURITY_MANAGER_INSTANCE));
        Assert.assertNull(hub.getCurrentDatabase().getInstance(MergeTest.AUTHORIZATION_PROVIDER_TYPE, MergeTest.RSA_ATZ_PROV_NAME));
    }
    
    /**
     * Tests that an attempt to set an already set direct child will
     * fail.  Note this test should be removed if we ever decide to
     * support some sort of automatic merge in this case
     * 
     * @throws Exception
     */
    @Test
    // @org.junit.Ignore
    public void testChangeDirectTypeWithSetFails() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        DomainBean domain = rootHandle.getRoot();
        
        SecurityManagerBean newOne = xmlService.createBean(SecurityManagerBean.class);
        
        try {
            domain.setSecurityManager(newOne);
            Assert.fail("Should have failed trying to change an existing bean");
        }
        catch (IllegalStateException ise) {
            // Expected
        }
        
        // Verify nothing was changed
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
    }
    
    /**
     * Tests that setting null back to null works
     * 
     * @throws Exception
     */
    @Test
    // @org.junit.Ignore
    public void testNullToNull() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        DomainBean domain = rootHandle.getRoot();
        SecurityManagerBean securityManagerBean = domain.getSecurityManager();
        
        // Null to null
        securityManagerBean.setSSLManager(null);
        
        // Verify nothing was changed
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
    }
    
    /**
     * Tests that setting a bean to itself is ok (one
     * case of set to set that works)
     * 
     * @throws Exception
     */
    @Test
    // @org.junit.Ignore
    public void testSameToSame() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        DomainBean domain = rootHandle.getRoot();
        SecurityManagerBean securityManagerBean = domain.getSecurityManager();
        
        // Setting it back to itself
        domain.setSecurityManager(securityManagerBean);
        
        // Verify nothing was changed
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
    }
    
    /**
     * Tests that setting a bean to itself is ok (one
     * case of set to set that works)
     * 
     * @throws Exception
     */
    @Test
    // @org.junit.Ignore
    public void testListSetToDifferentFails() throws Exception {
        ServiceLocator locator = Utilities.createLocator(UpdateListener.class,
                SSLManagerBeanCustomizer.class);
        XmlService xmlService = locator.getService(XmlService.class);
        Hub hub = locator.getService(Hub.class);
        
        URL url = getClass().getClassLoader().getResource(MergeTest.DOMAIN1_FILE);
        
        XmlRootHandle<DomainBean> rootHandle = xmlService.unmarshal(url.toURI(), DomainBean.class);
        DomainBean domain = rootHandle.getRoot();
        
        MergeTest.verifyDomain1Xml(rootHandle, hub, locator);
        
        List<MachineBean> newBeans = new LinkedList<MachineBean>();
        
        try {
           domain.setMachines(newBeans);
           Assert.fail("Should not be able to set machines at this time");
        }
        catch (IllegalStateException ise) {
            // expected
        }
        
        try {
            domain.setMachines(null);
            Assert.fail("Should not be able to set machines at this time");
        }
        catch (IllegalStateException ise) {
            // expected
        }
        
        List<MachineBean> oldBeans = domain.getMachines();
         
        try {
            domain.setMachines(oldBeans);
            Assert.fail("Should not be able to set machines at this time");
        }
        catch (IllegalStateException ise) {
            // expected
        }
    }
    
    @Singleton
    public static class UpdateListener implements BeanDatabaseUpdateListener {
        private List<Change> changes;

        /* (non-Javadoc)
         * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#prepareDatabaseChange(org.glassfish.hk2.configuration.hub.api.BeanDatabase, org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
         */
        @Override
        public void prepareDatabaseChange(BeanDatabase currentDatabase,
                BeanDatabase proposedDatabase, Object commitMessage,
                List<Change> changes) {
            // TODO Auto-generated method stub
            
        }

        /* (non-Javadoc)
         * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#commitDatabaseChange(org.glassfish.hk2.configuration.hub.api.BeanDatabase, org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
         */
        @Override
        public void commitDatabaseChange(BeanDatabase oldDatabase,
                BeanDatabase currentDatabase, Object commitMessage,
                List<Change> changes) {
            this.changes = changes;
            
        }

        /* (non-Javadoc)
         * @see org.glassfish.hk2.configuration.hub.api.BeanDatabaseUpdateListener#rollbackDatabaseChange(org.glassfish.hk2.configuration.hub.api.BeanDatabase, org.glassfish.hk2.configuration.hub.api.BeanDatabase, java.lang.Object, java.util.List)
         */
        @Override
        public void rollbackDatabaseChange(BeanDatabase currentDatabase,
                BeanDatabase proposedDatabase, Object commitMessage,
                List<Change> changes) {
            // TODO Auto-generated method stub
            
        }
        
        public List<Change> getChanges() {
            return changes;
        }
        
    }

}
