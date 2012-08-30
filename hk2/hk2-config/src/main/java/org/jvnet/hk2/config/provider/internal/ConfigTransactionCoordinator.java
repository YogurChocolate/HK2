/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2011 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
package org.jvnet.hk2.config.provider.internal;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.jvnet.hk2.annotations.Optional;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.ComponentException;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.component.Inhabitant;
import org.jvnet.hk2.component.InhabitantActivator;
import org.jvnet.hk2.component.MultiMap;
import org.glassfish.hk2.api.ActiveDescriptor;
import org.glassfish.hk2.api.PreDestroy;
import org.glassfish.hk2.utilities.AliasDescriptor;
import org.glassfish.hk2.utilities.BuilderHelper;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.ConfiguredBy;
import org.jvnet.hk2.config.provider.ConfigTransaction;
import org.jvnet.hk2.config.provider.ConfigTransactionFactory;

import com.sun.hk2.component.InhabitantsParser;

/**
 * Default implementation for {@link ConfigTransactionFactory}.
 * 
 * @author Jeff Trent
 */
@Service
public class ConfigTransactionCoordinator 
    implements ConfigTransactionFactory, PreDestroy {

  private Logger logger = Logger.getLogger(ConfigTransactionCoordinator.class.getName());
  
  @Inject
  private Habitat habitat;

  @Inject @Optional
  private InhabitantActivator activator;
  
  private ConfigTransaction theActiveOne;

  private Map<Object, MultiMap<String, ConfigByInhabitant>> beanToManagedConfigBy = new HashMap<Object, MultiMap<String, ConfigByInhabitant>>();
  
  @Override
  public synchronized ConfigTransaction getActiveTransaction(boolean create) {
    if (create && null == theActiveOne) {
      theActiveOne = new ConfigTransactionImpl(this);
    }
    
    return theActiveOne;
  }
  
  synchronized void finishedTransaction(ConfigTransaction txn) {
    assert(theActiveOne == txn);
    theActiveOne = null;
  }

  @Override
  public void preDestroy() {
    if (null != theActiveOne) {
      theActiveOne.rollback();
      assert(null == theActiveOne);
    }
  }

  boolean has(Object bean) {
    return beanToManagedConfigBy.containsKey(bean);
  }

  MultiMap<String, ConfigByInhabitant> get(Object bean) {
    // return a copy so that Transaction has there own working area
    MultiMap<String, ConfigByInhabitant> orig = beanToManagedConfigBy.get(bean);
    return (null == orig) ? null : new MultiMap<String, ConfigByInhabitant>();
  }

  // TODO: look for a faster implementation
  @SuppressWarnings("unchecked")
  Collection<ConfigByMetaInhabitant> getConfiguredByInhabitants(Class<?> clazz) {
    HashSet<Class<?>> configured = new HashSet<Class<?>>();

    if (Proxy.isProxyClass(clazz)) {
      for (Class<?> iface : clazz.getInterfaces()) {
        if (null != iface.getAnnotation(Configured.class)) {
          configured.add(iface);
        }
      }
    }
    
    while (Object.class != clazz) {
      String name = clazz.getName();
      if (null != name && null != clazz.getAnnotation(Configured.class)) {
        configured.add(clazz);
      }
      clazz = clazz.getSuperclass();
    }
    
    ArrayList<ConfigByMetaInhabitant> configuredByList = null;
    for (Inhabitant<?> i : habitat.getInhabitantsByContract(ConfiguredBy.class.getName())) {
      ConfigByMetaInhabitant configuredBy = (ConfigByMetaInhabitant)i;
      if (configured.contains(configuredBy.getConfiguredBy())) {
        if (null == configuredByList) {
          configuredByList = new ArrayList<ConfigByMetaInhabitant>();
        }
        configuredByList.add(configuredBy);
      }
    }
    
    return (null == configuredByList) ? Collections.EMPTY_LIST : Collections.unmodifiableList(configuredByList);
  }

  /**
   * Called when we are committing the txn to manage new beans and the services inhabitants for those beans
   */
  void manageBob(Object bean, Set<Class<?>> beanContracts, String name, MultiMap<String, ConfigByInhabitant> managed) {
    if (logger.isLoggable(Level.FINE)) {
      logger.log(Level.FINE, "managing: {0}, {1}, {2}, {3}", new Object[] {bean, name, beanContracts, managed});
    }
    
    // put the bean itself into the habitat
    if (null != beanContracts && !beanContracts.isEmpty()) {
      for (Class<?> beanContract : beanContracts) {
          ServiceLocatorUtilities.addOneConstant(habitat, bean, name, beanContract);
      }
    }

    // put the configured-by instances into the habitat
    for (Entry<String, List<ConfigByInhabitant>> cbi : managed.entrySet()) {
      String index = cbi.getKey();
      if (null != index) {
        StringBuilder sb = new StringBuilder();
        index = InhabitantsParser.parseIndex(index, sb);
        if (null == name) {
          name = (0 == sb.length()) ? null : sb.toString();
        }
      }
      
      for (ConfigByInhabitant i : cbi.getValue()) {
          ActiveDescriptor<Object> added = ServiceLocatorUtilities.addOneDescriptor(habitat, i);
          
        if (null == index) {
          // intentionally do not register byType since that is reserved for the meta inhabitant entry
//          habitat.add(i);
            AliasDescriptor<Object> alias = new AliasDescriptor<Object>(habitat, added, i.getImplementation(), name);
            
            ServiceLocatorUtilities.addOneDescriptor(habitat, alias);
        } else {
            AliasDescriptor<Object> alias = new AliasDescriptor<Object>(habitat, added, index, name);
            
            ServiceLocatorUtilities.addOneDescriptor(habitat, alias);
        }

        // force activation
        if (null == activator) {
          Object service = i.get();
          assert(null != service);
        } else {
          activator.activate(i);
        }
      }
    }

    beanToManagedConfigBy.put(bean, managed);

    if (null != activator) {
      try {
        activator.awaitCompletion();
      } catch (Exception e) {
        throw new ComponentException(e);
      }
    }
  }

  /**
   * Called when we are committing a txn to remove the beans and the associated services for those beans from the habitat
   */
  void unmanage(Object bean, Set<Class<?>> beanContracts, String name) {
    if (logger.isLoggable(Level.FINE)) {
      logger.log(Level.FINE, "unmanaging: {0}, {1}, {2}", new Object[] {bean, name, beanContracts});
    }
    
    // remove the configured-by services related to this bean
    MultiMap<String, ConfigByInhabitant> managed = beanToManagedConfigBy.remove(bean);
    for (Entry<String, List<ConfigByInhabitant>> cbi : managed.entrySet()) {
      String index = cbi.getKey();
      
      if (null != index) {
          index = InhabitantsParser.parseIndex(index, null);
      }
      
      for (ConfigByInhabitant i : cbi.getValue()) {
        if (null == index) {
          // intentionally do not register byType since that is reserved for the meta inhabitant entry
//          habitat.add(i);
            ServiceLocatorUtilities.removeFilter(habitat, BuilderHelper.createNameAndContractFilter(
                    i.getImplementation(), name));
        } else {
            ServiceLocatorUtilities.removeFilter(habitat, BuilderHelper.createNameAndContractFilter(
                    index, name));
        }
        
        ServiceLocatorUtilities.removeOneDescriptor(habitat, i);
      }
    }
    
    // remove the bean itself from the habitat
    if (null != beanContracts) {
      for (Class<?> beanContract : beanContracts) {
          ServiceLocatorUtilities.removeFilter(habitat, BuilderHelper.createNameAndContractFilter(
                  beanContract.getName(), name));
        
        // TODO: should we release it too?
      }
    }
  }
  
}
