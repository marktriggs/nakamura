/*
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
package org.sakaiproject.nakamura.activity.migration;

import com.google.common.collect.ImmutableMap;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.PropertyMigrator;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * A migrator that handles migrating the old activity functionality to the new. Before this migrator (1.4.0),
 * activities were delivered to destinations by copying the entire activity content. This change allows for
 * the activity to instead be indexed by a 'sakai:routes' property, that indicates from which locations it
 * should be found.
 * 
 * To be backward compatible, the migrator must add a "sakai:routes" element to each sakai/activity item. The
 * sole route should be its current location -- this is because this activity has *already* been copied to the
 * route, therefore its own location *is* the route.
 */
@Service
@Component
public class ActivityRouteMigrator implements PropertyMigrator {

  private final static Logger LOGGER = LoggerFactory.getLogger(ActivityRouteMigrator.class); 
  
  @Reference
  Repository repository;
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.lite.PropertyMigrator#migrate(java.lang.String, java.util.Map)
   */
  @Override
  public boolean migrate(String rid, Map<String, Object> properties) {
    boolean changeMade = false;
    Object pathObj = properties.get(Content.PATH_FIELD);
    if (pathObj != null && pathObj instanceof String) {
      String path = (String) pathObj;
      if (ActivityConstants.ACTIVITY_ITEM_RESOURCE_TYPE.equals(properties.get(
          Content.SLING_RESOURCE_TYPE_FIELD))) {
        
        // this grabs the a:userId/private/activityFeed path, which is the route of this activity item.
        String routePath = StorageClientUtils.getParentObjectPath(path);
        
        // create the route info and lock it down to admin
        String routesFieldPath = StorageClientUtils.newPath(path, ActivityConstants.PARAM_ROUTES);
        
        Session session = null;
        try {
          session = repository.loginAdministrative();
          ContentManager cm = session.getContentManager();
          if (!cm.exists(routesFieldPath)) {
            
            // no one can access this sakai:routes context except admin.
            session.getAccessControlManager().setAcl(Security.ZONE_CONTENT, routesFieldPath, new AclModification[] {
                new AclModification(AclModification.denyKey(User.ANON_USER), Permissions.ALL.getPermission(), Operation.OP_REPLACE),
                new AclModification(AclModification.denyKey(Group.EVERYONE), Permissions.ALL.getPermission(), Operation.OP_REPLACE)
              });
            
            changeMade = true;
            
            // create the sakai:routes context beneath the activity
            Content route = new Content(routesFieldPath, new HashMap<String, Object>());
            route.setProperty(ActivityConstants.PARAM_ROUTES, new String[] { routePath });
            cm.update(route);
            
            LOGGER.info("Migrated activity record '{}' with route: '{}'", path, routePath);
            
          }
        } catch (StorageClientException e) {
          LOGGER.warn("Skipping migration of activity record '"+path+"' due to exception.", e);
        } catch (AccessDeniedException e) {
          LOGGER.warn("Skipping migration of activity record '"+path+"' due to exception.", e);
        } finally {
          SparseUtils.logoutQuietly(session);
        } 
      }
    }
    
    return changeMade;
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.lite.PropertyMigrator#getDependencies()
   */
  @Override
  public String[] getDependencies() {
    return new String[0];
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.lite.PropertyMigrator#getName()
   */
  @Override
  public String getName() {
    return getClass().getName();
  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.lite.PropertyMigrator#getOptions()
   */
  @Override
  public Map<String, String> getOptions() {
    return ImmutableMap.of(PropertyMigrator.OPTION_RUNONCE, Boolean.TRUE.toString());
  }
  
}
