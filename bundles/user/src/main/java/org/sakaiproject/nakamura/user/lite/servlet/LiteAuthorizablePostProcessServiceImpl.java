/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.user.lite.servlet;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.ReferenceStrategy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.ServiceUtil;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.ModificationType;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessService;
import org.sakaiproject.nakamura.api.user.LiteAuthorizablePostProcessor;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.user.lite.resource.LiteAuthorizableResourceProvider;
import org.sakaiproject.nakamura.util.osgi.AbstractOrderedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;



@Component(immediate=true, metatype=true)
@Service(value=LiteAuthorizablePostProcessService.class)
@Reference(name="authorizablePostProcessor",
    target="(!default=true)",
		cardinality=ReferenceCardinality.OPTIONAL_MULTIPLE,
		policy=ReferencePolicy.DYNAMIC,
		strategy=ReferenceStrategy.EVENT,
		referenceInterface=LiteAuthorizablePostProcessor.class,
		bind="bindAuthorizablePostProcessor",
		unbind="unbindAuthorizablePostProcessor")
public class LiteAuthorizablePostProcessServiceImpl extends AbstractOrderedService<LiteAuthorizablePostProcessor> implements LiteAuthorizablePostProcessService {

  private static final Logger LOGGER = LoggerFactory.getLogger(LiteAuthorizablePostProcessServiceImpl.class);

  @Reference
  protected Repository repository;

  @Reference
  protected EventAdmin eventAdmin;

  @Reference(target="(default=true)")
  protected LiteAuthorizablePostProcessor defaultPostProcessor;

  private LiteAuthorizablePostProcessor[] orderedServices = new LiteAuthorizablePostProcessor[0];

  
  public LiteAuthorizablePostProcessServiceImpl() {
  }

  public void process(Authorizable authorizable, Session session,
      ModificationType change, Map<String, Object[]> parameters) throws Exception {
    // Set up the Modification argument.

    if ( Boolean.valueOf(String.valueOf(authorizable.getProperty(UserConstants.PROP_BARE_AUTHORIZABLE)))) {
      return; // bare authorizables have no extra objects
    }

    final String pathPrefix = (authorizable instanceof Group) ?
        LiteAuthorizableResourceProvider.SYSTEM_USER_MANAGER_GROUP_PREFIX :
          LiteAuthorizableResourceProvider.SYSTEM_USER_MANAGER_USER_PREFIX;
    Modification modification = new Modification(change, pathPrefix + authorizable.getId(), null);

    if (change != ModificationType.DELETE) {
      defaultPostProcessor.process(authorizable, session, modification, parameters);
    }
    for ( LiteAuthorizablePostProcessor processor : orderedServices ) {
      processor.process(authorizable, session, modification, parameters);
    }
    if (change == ModificationType.DELETE) {
      defaultPostProcessor.process(authorizable, session, modification, parameters);
    }
  }

  protected Comparator<LiteAuthorizablePostProcessor> getComparator(final Map<LiteAuthorizablePostProcessor, Map<String, Object>> propertiesMap) {
    return new Comparator<LiteAuthorizablePostProcessor>() {
      public int compare(LiteAuthorizablePostProcessor o1, LiteAuthorizablePostProcessor o2) {
        Map<String, Object> props1 = propertiesMap.get(o1);
        Map<String, Object> props2 = propertiesMap.get(o2);

        return ServiceUtil.getComparableForServiceRanking(props1).compareTo(props2);
      }
    };
  }

  protected void bindAuthorizablePostProcessor(LiteAuthorizablePostProcessor service, Map<String, Object> properties) {
    LOGGER.debug("Adding service: {}", service);
    addService(service, properties);
  }

  protected void unbindAuthorizablePostProcessor(LiteAuthorizablePostProcessor service, Map<String, Object> properties) {
    LOGGER.debug("Removing service: {}", service);
    removeService(service, properties);
  }

  protected void saveArray(List<LiteAuthorizablePostProcessor> serviceList) {
    orderedServices = serviceList.toArray(new LiteAuthorizablePostProcessor[serviceList.size()]);
  }

}
