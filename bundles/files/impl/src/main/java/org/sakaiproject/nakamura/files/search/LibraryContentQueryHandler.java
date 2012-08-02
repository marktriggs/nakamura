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
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.MissingParameterException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.SparseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code MyLibraryQueryHandler} backs the searching functionality for querying
 * content that belongs in users' My Library page.
 */
@Component(inherit=true)
@Service(value={ SolrSearchResultProcessor.class, SolrSearchPropertyProvider.class })
@Properties(value={
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "LibraryContentQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "LibraryContentQueryHandler")
  })
public class LibraryContentQueryHandler extends AbstractContentSearchQueryHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(LibraryContentQueryHandler.class);
  
  private static final String AU_TEMPLATE = "(manager:(%s) OR editor:(%<s) OR viewer:(%<s))";
  private static final String ALL_TEMPLATE = "(showalways:true AND (manager:(%s) OR editor:(%<s) OR viewer:(%<s)))";
  
  /**
   * The maximum number of steps to recurse when looking for principals.
   */
  private static final int PRINCIPAL_MAX_DEPTH = 6;
  
  public LibraryContentQueryHandler() {
    
  }
  
  public LibraryContentQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory, repository);
  }
  
  /**
   * This method will aggregate properties into the {@code propertiesMap} that will feed into
   * the template located in {@code auth-all.json}.
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request, Map<String, String> propertiesMap) {

    // determine the user to use for the search
    String user = SearchRequestUtils.getUser(request);
    if (user == null || User.ANON_USER.equals(user)) {
      throw new MissingParameterException("Cannot search with anonymous user. " +
          "Must authenticate, or specify 'userid' parameter.");
    }
    
    // determine the number of levels deep to look
    int levels = 0;
    try {
      if (request.getParameter(REQUEST_PARAMETERS.levels.toString()) != null) {
        String levelsStr = request.getParameter(REQUEST_PARAMETERS.levels.toString());
        levels = Math.min(Integer.parseInt(levelsStr),
            PRINCIPAL_MAX_DEPTH);
      }
    } catch (NumberFormatException nfe) {
      throw new IllegalArgumentException("The levels parameter must be parseable to an integer.", nfe);
    }
    
    // need to set these parameters to ensure they are not query-escaped
    // TODO: Maybe the parameters should only be query-escaped at the time that they are put into the lucene query (i.e., in buildCustomQString())
    propertiesMap.put(REQUEST_PARAMETERS.userid.toString(), user);
    propertiesMap.put(REQUEST_PARAMETERS.levels.toString(), String.valueOf(levels));
    
    // finally, build the query string with the above parameters
    propertiesMap.put(TEMPLATE_PROPS._q.toString(), configureQString(propertiesMap));
  }

  @Override
  public String buildCustomQString(Map<String, String> parametersMap) {
    String customQuery = null;
    List<String> filters = new LinkedList<String>();
    
    buildSearchByGeneralQuery(parametersMap, filters);
    buildSearchByMimetype(parametersMap, filters);
    buildSearchByAssociation(parametersMap, filters);
    
    if (!filters.isEmpty()) {
      customQuery = Joiner.on(" AND ").join(filters);
    }
    
    return customQuery;
  }
  
  private void buildSearchByAssociation(Map<String, String> parametersMap, List<String> filters) {
    String user = parametersMap.get(REQUEST_PARAMETERS.userid.toString());
    int levels = Integer.valueOf(parametersMap.get(REQUEST_PARAMETERS.levels.toString()));
    Session session = null;
    try {
      session = repository.loginAdministrative();
      // find all groups that the user is "closely" related to
      final Set<String> principals = SearchRequestUtils.getPrincipals(session, user, levels);
      String auOr = Joiner.on(" OR ").join(principals);
  
      // find all groups that the user is related to
      final Set<String> allPrincipals = SearchRequestUtils.getPrincipals(session, user, PRINCIPAL_MAX_DEPTH);
      String allOr = Joiner.on(" OR ").join(allPrincipals);
      
      session.logout();
      
      StringBuilder termBuilder = new StringBuilder("(");
      termBuilder.append(String.format(AU_TEMPLATE, auOr));
      termBuilder.append(" OR ");
      termBuilder.append(String.format(ALL_TEMPLATE, allOr));
      termBuilder.append(")");
      
      filters.add(termBuilder.toString());
    } catch (ClientPoolException e) {
      LOGGER.error("Error building search terms by group association", e);
      throw new RuntimeException(e);
    } catch (StorageClientException e) {
      LOGGER.error("Error building search terms by group association", e);
      throw new RuntimeException(e);
    } catch (AccessDeniedException e) {
      LOGGER.error("Error building search terms by group association", e);
      throw new RuntimeException(e);
    } finally {
      SparseUtils.logoutQuietly(session);
    }
  }

}
