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

import com.google.common.base.Joiner;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.user.AuthorizableUtil;
import org.sakaiproject.nakamura.util.SparseUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The {@code MeQueryHandler} backs the search functionality for the role-based content
 * search entry-points. Specifically:
 * <p><ul>
 * <li>/var/search/pool/me/manager-all.json (DEPRECATED)</li>
 * <li>/var/search/pool/me/manager.json (DEPRECATED)</li>
 * <li>/var/search/pool/me/viewer-all.json (DEPRECATED)</li>
 * <li>/var/search/pool/me/viewer.json (DEPRECATED)</li>
 * <li>/var/search/pool/me/role.json</li>
 * </ul>
 */
@Service(value={ SolrSearchPropertyProvider.class, SolrSearchResultProcessor.class })
@Component(inherit=true)
@Properties(value={
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "AccessScopedContentQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "AccessScopedContentQueryHandler")
  })
public class AccessScopedContentQueryHandler extends AbstractContentSearchQueryHandler {

  private final static String ROLE_TEMPLATE = "(%s:(%s))";
  
  private final static Joiner JOINER_OR = Joiner.on(" OR ");
  
  public static enum SearchableRole {
    manager,
    editor,
    viewer
  }
  
  public AccessScopedContentQueryHandler() {
    
  }
  
  public AccessScopedContentQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory, repository);
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest, java.util.Map)
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
    
    // verify that we can determine a userid
    String userid = SearchRequestUtils.getUser(request);
    if (userid == null) {
      userid = User.ANON_USER;
    }
    
    // verify there is a valid role specified
    String roleStr = propertiesMap.get(REQUEST_PARAMETERS.role.toString());
    if (roleStr != null) {
      try {
        SearchableRole.valueOf(roleStr);
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Provided role parameter is not a valid role: "+roleStr, e);
      }
    } else {
      throw new MissingParameterException("Required parameter 'role' was not found.");
    }
    
    // need to ensure non-escaped parameters get set for userid
    propertiesMap.put(REQUEST_PARAMETERS.userid.toString(), userid);
    propertiesMap.put(TEMPLATE_PROPS._q.toString(), configureQString(propertiesMap));

  }

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#configureQString(java.util.Map)
   */
  @Override
  public String buildCustomQString(Map<String, String> parametersMap) {
    String customQuery = null;
    List<String> filters = new LinkedList<String>();
    
    buildSearchByRoleQuery(parametersMap, filters);
    buildSearchByGeneralQuery(parametersMap, filters);
    buildSearchByMimetype(parametersMap, filters);
    
    if (!filters.isEmpty()) {
      customQuery = Joiner.on(" AND ").join(filters);
    }
    
    return customQuery;
  }

  /**
   * Apply the 'search by role' filter to the lucene query string.
   * 
   * @param parametersMap
   * @param queryString
   */
  protected void buildSearchByRoleQuery(Map<String, String> parametersMap,
      List<String> filters) {
    SearchableRole role = SearchableRole.valueOf(getSearchParam(parametersMap, REQUEST_PARAMETERS.role.toString()));
    String userid = getSearchParam(parametersMap, REQUEST_PARAMETERS.userid.toString());
    AuthorizableManager authorizableManager = null;
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      authorizableManager = adminSession.getAuthorizableManager();
      Authorizable au = authorizableManager.findAuthorizable(userid);
      List<Authorizable> groups = AuthorizableUtil.getUserFacingGroups(au, authorizableManager);
      groups.add(au);
      
      List<String> groupStrs = new ArrayList<String>(groups.size());
      for (Authorizable memberAuthz : groups) {
        groupStrs.add(ClientUtils.escapeQueryChars(memberAuthz.getId()));
      }
      
      filters.add(String.format(ROLE_TEMPLATE, role.toString(), JOINER_OR.join(groupStrs)));
      adminSession.logout();
    } catch (ClientPoolException e) {
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      throw new RuntimeException(e);
    } catch (AccessDeniedException e) {
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(adminSession);
    }
  }

}
