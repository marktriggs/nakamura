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
package org.sakaiproject.nakamura.migratejcr;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableMap.Builder;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permission;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.Value;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.security.AccessControlEntry;
import javax.jcr.security.AccessControlList;
import javax.jcr.security.AccessControlPolicy;
import javax.jcr.security.Privilege;

/**
 *
 */
@Component
public class MigrateJcr {
  private Logger LOGGER = LoggerFactory.getLogger(MigrateJcr.class);

  @Reference(target = "(name=presparse)")
  private SlingRepository slingRepository;

  @Reference
  private Repository sparseRepository;

  private Set<String> ignoreProps = ImmutableSet.of("jcr:content", "jcr:data",
      "jcr:mixinTypes", "rep:policy");

  @Activate
  protected void activate(ComponentContext componentContext) {
    @SuppressWarnings("rawtypes")
    Dictionary componentProps = componentContext.getProperties();
    if (shouldMigrate(componentProps)) {
      try {
        migrateAuthorizables();
        migrateContentPool();
        cleanup();
      } catch (Exception e) {
        LOGGER.error("Failed data migration from JCR to Sparse.", e);
      }
    }
  }

  private void cleanup() {
    // TODO Auto-generated method stub

  }

  @SuppressWarnings("deprecation")
  private void migrateContentPool() throws Exception {
    LOGGER.info("beginning users and groups migration.");
    String contentPoolQuery = "//element(*, sakai:pooled-content) order by @jcr:score descending";
    javax.jcr.Session jcrSession = null;
    Session sparseSession = null;
    try {
      jcrSession = slingRepository.loginAdministrative("default");
      sparseSession = sparseRepository.loginAdministrative();
      QueryManager qm = jcrSession.getWorkspace().getQueryManager();
      Query q = qm.createQuery(contentPoolQuery, Query.XPATH);
      QueryResult result = q.execute();
      NodeIterator resultNodes = result.getNodes();
      String nodeWord = resultNodes.getSize() == 1 ? "node" : "nodes";
      LOGGER.info("Found {} pooled content {} in Jackrabbit.", resultNodes.getSize(),
          nodeWord);
      while (resultNodes.hasNext()) {
        Node contentNode = resultNodes.nextNode();
        LOGGER.info(contentNode.getPath());
        copyNodeToSparse(contentNode, contentNode.getName(), sparseSession);
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
      if (sparseSession != null) {
        sparseSession.logout();
      }
    }

  }

  private void copyNodeToSparse(Node contentNode, String path, Session session)
      throws Exception {
    ContentManager contentManager = session.getContentManager();
    if (contentManager.exists(path)) {
      LOGGER
          .warn("Ignoring migration of content at path which already exists in sparsemap: "
              + path);
      return;
    }
    PropertyIterator propIter = contentNode.getProperties();
    Builder<String, Object> propBuilder = ImmutableMap.builder();
    while (propIter.hasNext()) {
      Property prop = propIter.nextProperty();
      if (ignoreProps.contains(prop.getName())) {
        continue;
      }
      Object value;
      if (prop.isMultiple()) {
        Value[] values = prop.getValues();
        if (values.length > 0 && values[0].getType() == PropertyType.STRING) {
          String[] valueStrings = new String[values.length];
          for (int i = 0; i < values.length; i++) {
            valueStrings[i] = values[i].getString();
          }
          value = valueStrings;
        } else {
          // TODO handle multi-value properties of other types
          continue;
        }
      } else {
        switch (prop.getType()) {
        case PropertyType.BINARY:
          value = prop.getBinary();
          break;
        case PropertyType.BOOLEAN:
          value = prop.getBoolean();
          break;
        case PropertyType.DATE:
          value = prop.getDate();
          break;
        case PropertyType.DECIMAL:
          value = prop.getDecimal();
          break;
        case PropertyType.DOUBLE:
          value = prop.getDouble();
          break;
        case PropertyType.LONG:
          value = prop.getLong();
          break;
        case PropertyType.STRING:
          value = prop.getString();
          break;
        default:
          value = "";
          break;
        }
      }
      propBuilder.put(prop.getName(), value);
    }
    Content sparseContent = new Content(path, propBuilder.build());
    if (contentNode.hasNode("jcr:content")) {
      Node fileContentNode = contentNode.getNode("jcr:content");
      Binary binaryData = fileContentNode.getProperty("jcr:data").getBinary();
      try {
        InputStream binaryStream = binaryData.getStream();
        contentManager.update(sparseContent);
        contentManager.writeBody(sparseContent.getPath(), binaryStream);
      } catch (Exception e) {
        LOGGER.error("Unable to write binary content for JCR path: "
            + fileContentNode.getPath());
      }
    } else {
      contentManager.update(sparseContent);
    }
    AccessControlManager sparseAccessControl = session.getAccessControlManager();
    List<AclModification> aclModifications = new ArrayList<AclModification>();
    LOGGER.info("Reading access control policies for " + contentNode.getPath());
    javax.jcr.security.AccessControlManager accessManager = AccessControlUtil
        .getAccessControlManager(contentNode.getSession());
    AccessControlPolicy[] accessPolicies = accessManager.getEffectivePolicies(contentNode
        .getPath());
    for (AccessControlPolicy policy : accessPolicies) {
      if (policy instanceof AccessControlList) {
        for (AccessControlEntry ace : ((AccessControlList) policy)
            .getAccessControlEntries()) {
          String principal = ace.getPrincipal().getName();
          String permission = AccessControlUtil.isAllow(ace) ? AclModification
              .grantKey(principal) : AclModification.denyKey(principal);
          for (Privilege priv : ace.getPrivileges()) {
            Permission sparsePermission = Permissions.parse(priv.getName());
            if (sparsePermission != null) {
              aclModifications.add(new AclModification(permission, sparsePermission
                  .getPermission(), Operation.OP_AND));
              LOGGER.debug("translating jcr permission to sparse: " + permission + " -- "
                  + priv.getName());
            }
          }
        }
      }
    }
    sparseAccessControl.setAcl(Security.ZONE_CONTENT, path,
        aclModifications.toArray(new AclModification[aclModifications.size()]));
    // make recursive call for child nodes
    // depth-first traversal
    NodeIterator nodeIter = contentNode.getNodes();
    while (nodeIter.hasNext()) {
      Node childNode = nodeIter.nextNode();
      if (ignoreProps.contains(childNode.getName())) {
        continue;
      }
      copyNodeToSparse(childNode, path + "/" + childNode.getName(), session);
    }

  }

  @SuppressWarnings("deprecation")
  private void migrateAuthorizables() throws Exception {
    LOGGER.info("beginning users and groups migration.");
    javax.jcr.Session jcrSession = null;
    try {
      jcrSession = slingRepository.loginAdministrative("default");
      String usersQuery = "//*[@sling:resourceType='sakai/user-home'] order by @jcr:score descending";
      QueryManager qm = jcrSession.getWorkspace().getQueryManager();
      Query q = qm.createQuery(usersQuery, Query.XPATH);
      QueryResult result = q.execute();
      NodeIterator resultNodes = result.getNodes();
      String folderWord = resultNodes.getSize() == 1 ? "folder" : "folders";
      LOGGER.info("found {} user home {} in Jackrabbit.", resultNodes.getSize(),
          folderWord);
      while (resultNodes.hasNext()) {
        Node authHomeNode = resultNodes.nextNode();
        LOGGER.info(authHomeNode.getPath());
        moveAuthorizableToSparse(authHomeNode,
            AccessControlUtil.getUserManager(jcrSession));
      }

      String groupsQuery = "//*[@sling:resourceType='sakai/group-home'] order by @jcr:score descending";
      q = qm.createQuery(groupsQuery, Query.XPATH);
      result = q.execute();
      resultNodes = result.getNodes();
      folderWord = resultNodes.getSize() == 1 ? "folder" : "folders";
      LOGGER.info("found {} group home {} in Jackrabbit.", resultNodes.getSize(),
          folderWord);
      while (resultNodes.hasNext()) {
        Node groupHomeNode = resultNodes.nextNode();
        LOGGER.info(groupHomeNode.getPath());
        moveAuthorizableToSparse(groupHomeNode,
            AccessControlUtil.getUserManager(jcrSession));
      }
    } finally {
      if (jcrSession != null) {
        jcrSession.logout();
      }
    }

  }

  private void moveAuthorizableToSparse(Node authHomeNode, UserManager userManager)
      throws Exception {
    Session sparseSession = null;
    try {
      sparseSession = sparseRepository.loginAdministrative();
      AuthorizableManager authManager = sparseSession.getAuthorizableManager();
      boolean isUser = "sakai/user-home".equals(authHomeNode.getProperty(
          "sling:resourceType").getString());
      Node profileNode = authHomeNode.getNode("public/authprofile");
      if (isUser) {
        String userId = profileNode.getProperty("rep:userId").getString();
        Node propNode = profileNode.getNode("basic/elements/firstName");
        String firstName = propNode.getProperty("value").getString();
        propNode = profileNode.getNode("basic/elements/lastName");
        String lastName = propNode.getProperty("value").getString();
        propNode = profileNode.getNode("basic/elements/email");
        String email = propNode.getProperty("value").getString();
        if (authManager.createUser(userId, userId, "testuser", ImmutableMap.of(
            "firstName", (Object) firstName, "lastName", (Object) lastName, "email",
            (Object) email))) {
          LOGGER.info("Adding user home folder for " + userId);
          copyNodeToSparse(authHomeNode, "a:" + userId, sparseSession);
          // TODO do we care about the password?
          LOGGER.info(userId + " " + firstName + " " + lastName + " " + email);
        } else {
          LOGGER.info("User {} exists in sparse. Skipping it.", userId);
        }
      } else {
        // handling a group
        String groupTitle = profileNode.getProperty("sakai:group-title").getString();
        String groupId = profileNode.getProperty("sakai:group-id").getString();
        if (authManager.createGroup(groupId, groupTitle, null)) {
          Authorizable sparseGroup = authManager.findAuthorizable(groupId);
          org.apache.jackrabbit.api.security.user.Authorizable group = userManager.getAuthorizable(groupId);
          if (group instanceof Group) {
            // add all memberships
            Iterator<org.apache.jackrabbit.api.security.user.Authorizable> members = ((Group)group).getMembers();
            while (members.hasNext()) {
              org.apache.jackrabbit.api.security.user.Authorizable member = members.next();
              sparseGroup.addPrincipal(member.getID());
            }
            authManager.updateAuthorizable(sparseGroup);
          }
          // TODO find out if the DefaultPostProcessor is being applied
          // to set managers, etc. on the new group
          LOGGER.info("Adding group home folder for " + groupId);
          copyNodeToSparse(authHomeNode, "a:" + groupId, sparseSession);
        } else {
          LOGGER.info("Group {} exists in sparse. Skipping it.", groupId);
        }
      }
    } catch (Exception e) {
      LOGGER.error("Failed getting basic profile information from "
          + authHomeNode.getPath());
    } finally {
      if (sparseSession != null) {
        sparseSession.logout();
      }
    }

  }

  @SuppressWarnings("unchecked")
  private boolean shouldMigrate(Dictionary componentProps) {
    // TODO determine whether there is any migrating to do
    return true;
  }

}
