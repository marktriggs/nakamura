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
package org.sakaiproject.nakamura.files.search;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides common functionality for extracting information about a search request that
 * is used for Pooled Content searches.
 */
public class SearchRequestUtils {
  
  /**
   * Get the user of the request. This will be the authenticated user of
   * the request unless the 'userid' query parameter is specified.
   * 
   * @param request
   * @return
   */
  public static String getUser(final SlingHttpServletRequest request) {
    String user = request.getRemoteUser();
    final RequestParameter useridParam = request.getRequestParameter("userid");
    if (useridParam != null) {
      user = useridParam.getString();
    }
    return user;
  }
  

  /**
   * Get all groups to which the user has access, at a recursion of {@code levels}
   * 
   * @param session
   * @param authorizable
   * @return An empty list if the user cannot be found. Values will be solr query escaped.
   */
  public static Set<String> getPrincipals(final Session session, final String authorizable, int levels) {
    final Set<String> viewerAndManagerPrincipals = new HashSet<String>();
    try {
      final AuthorizableManager authManager = session.getAuthorizableManager();
      final Authorizable anAuthorizable = authManager.findAuthorizable(authorizable);
      if (anAuthorizable != null) {
        if (levels > 0) {
          levels--;
          for (final String principal : anAuthorizable.getPrincipals()) {
            if (!Group.EVERYONE.equals(principal)) {
              viewerAndManagerPrincipals.addAll(getPrincipals(session, principal, levels));
            }
          }
        }
        viewerAndManagerPrincipals.add(ClientUtils.escapeQueryChars(authorizable));
        viewerAndManagerPrincipals.remove(Group.EVERYONE);
      }
    } catch (StorageClientException e) {
      throw new IllegalStateException(e);
    } catch (AccessDeniedException e) {
      // quietly trap access denied exceptions
    }
    return viewerAndManagerPrincipals;
  }
}
