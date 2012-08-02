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
package org.sakaiproject.nakamura.resource.lite.servlet.post.operations;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.HtmlResponse;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.apache.sling.servlets.post.Modification;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.AbstractSparsePostOperation;
import org.sakaiproject.nakamura.api.resource.lite.SparsePostOperation;
import org.sakaiproject.nakamura.api.user.AuthorizableCountChanger;
import org.sakaiproject.nakamura.api.user.UserConstants;
import org.sakaiproject.nakamura.api.user.counts.CountProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

@Component
@Service(value = SparsePostOperation.class)
@Property(name = "sling.post.operation", value = "delete")
public class DeleteOperation extends AbstractSparsePostOperation {

  private final static Logger LOGGER = LoggerFactory.getLogger(DeleteOperation.class);
  
  @Reference
  protected Repository repository;
  
  @Reference
  protected AuthorizableCountChanger authorizableCountChanger;

  @Override
  protected void doRun(SlingHttpServletRequest request, HtmlResponse response,
      ContentManager contentManager, List<Modification> changes, String contentPath)
          throws StorageClientException, AccessDeniedException, IOException {
    Iterator<Resource> res = getApplyToResources(request);
    if (res == null) {

      Resource resource = request.getResource();
      if (contentPath == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND, "Missing source contentPath for delete");
        return;
      }

      if(resource.isResourceType("sakai/pooled-content")){
        //notify to reset content count
        Content node = resource.adaptTo(Content.class);
        Map<String, Object> properties = node.getProperties();
        String[] managers = PropertiesUtil.toStringArray(properties.get("sakai:pooled-content-manager"), new String[0]);
        String[] editors = PropertiesUtil.toStringArray(properties.get("sakai:pooled-content-editor"), new String[0]);
        String[] viewers = PropertiesUtil.toStringArray(properties.get("sakai:pooled-content-viewer"), new String[0]);
        
        Set<String> directAuthorizables = new HashSet<String>();
        directAuthorizables.addAll(Arrays.asList(managers));
        directAuthorizables.addAll(Arrays.asList(editors));
        directAuthorizables.addAll(Arrays.asList(viewers));
        
        Set<String> allAuthorizables = new HashSet<String>();
        
        // if the content has sakai:showalways == true, then we need to also push count
        // changes to indirect members
        if (Boolean.TRUE.toString().equals(String.valueOf(node.getProperty(
            FilesConstants.POOLED_CONTENT_SHOW_ALWAYS)))) {
          // remove ignored authids to avoid the risk of something like 'everyone' returning all members in the system
          directAuthorizables.removeAll(CountProvider.IGNORE_AUTHIDS);
          
          Session adminSession = repository.loginAdministrative();
          try {
            aggregateAuthorizables(adminSession.getAuthorizableManager(),
                directAuthorizables.toArray(new String[directAuthorizables.size()]),
                allAuthorizables);
          } finally {
            adminSession.logout();
          }
        } else {
          allAuthorizables = directAuthorizables;
        }
        
        long time = System.currentTimeMillis();
        this.authorizableCountChanger.notify(UserConstants.CONTENT_ITEMS_PROP, allAuthorizables);
        time = System.currentTimeMillis() - time;
        
        LOGGER.info("Updated {} counts in a DeleteOperation in {}ms", allAuthorizables.size(), time);
      }

      StorageClientUtils.deleteTree(contentManager, contentPath);
      changes.add(Modification.onDeleted(resource.getPath()));

    } else {

      while (res.hasNext()) {
        Resource resource = res.next();
        Content contentItem = resource.adaptTo(Content.class);
        if (contentItem != null) {
          StorageClientUtils.deleteTree(contentManager, contentItem.getPath());
          changes.add(Modification.onDeleted(resource.getPath()));
        }
      }

    }

  }
  
  /**
   * Given a group, aggregate all its members (may also be groups) into the given
   * set of {@code aggregated} authorizable ids.
   * 
   * @param am
   * @param group
   * @param aggregated
   */
  private void aggregateAuthorizables(AuthorizableManager am, String[] memberIds, Set<String> aggregated) {
    for (String memberId : memberIds) {
      try {
        Authorizable member = am.findAuthorizable(memberId);
        // avoid infinite recursion by only considering members that have not been aggregated yet
        if (member != null && !aggregated.contains(memberId)) {
          aggregated.add(memberId);
          if (member.isGroup()) {
            aggregateAuthorizables(am, ((Group) member).getMembers(), aggregated);
          }
        }
      } catch (AccessDeniedException e) {
        LOGGER.warn("Error while aggregating authorizables for count updates.", e);
      } catch (StorageClientException e) {
        LOGGER.warn("Error while aggregating authorizables for count updates.", e);
      }
    }
  }
}
