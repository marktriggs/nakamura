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
package org.sakaiproject.nakamura.api.search.solr;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.apache.sling.api.request.RequestParameterMap;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(componentAbstract=true)
@Service({
    DomainObjectSearchQueryHandler.class,
    SolrSearchPropertyProvider.class,
    SolrSearchResultProcessor.class
})
public abstract class DomainObjectSearchQueryHandler
    implements SolrSearchPropertyProvider, SolrSearchResultProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DomainObjectSearchQueryHandler.class);

  private static Map<String, Object> QUERY_OPTIONS_MAP = ImmutableMap.<String, Object> of(
      FacetParams.FACET, Boolean.TRUE,
      FacetParams.FACET_FIELD, "tagname",
      FacetParams.FACET_MINCOUNT, 1
  );

  private static Pattern TWO_OR_MORE_STARS = Pattern.compile("\\*{2,}");

  public enum DEFAULT_REQUEST_PARAMS {
    q,
    tags,
    sortOn,
    sortOrder
  }

  /**
   * Preserves backwards compatibility with the old search template approach
   * until it can be discarded.
   */
  public enum TEMPLATE_PROPS {
    _q
  }

  /**
   * Most entries in the search parameters map come from HTTP request parameters.
   * These are filled in from other properties of the request.
   */
  public enum REQUEST_PARAMETERS_PROPS {
    _requestPath,
    _traversalDepth,
    _userId
  }

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;

  public DomainObjectSearchQueryHandler() {
    
  }
  
  public DomainObjectSearchQueryHandler(SolrSearchServiceFactory searchServiceFactory) {
    this.searchServiceFactory = searchServiceFactory;
  }
  
  /**
   * Return the search clause corresponding to the domain object. This will
   * typically be used as a Filter Query and as a fallback replacement for
   * match-all wildcards.
   */
  abstract public String getResourceTypeClause(Map<String, String> parametersMap);

  /**
   * Write the JSON object (if any) corresponding to the given result.
   */
  abstract public void writeResult(Session session, Map<String, String> parametersMap, JSONWriter jsonWriter, Result result)
      throws JSONException;

    /**
    * If the base query string would be empty, use this as the default.
    */
  public String getDefaultQueryString(Map<String, String> parametersMap) {
    // Before 4.0, the usual "*:*" pass-through would incur unexpected cost.
    // As of 4.0, filter queries are processed in parallel with the main query
    // and the performance profiles should be closer.
    return getResourceTypeClause(parametersMap);
  }

  /**
   * Return the default sort configuration for the query.
   */
  public String getDefaultSort() {
    return "score desc";
  }

  /**
   * Set up standard query option values and then give subclasses a chance
   * to refine it.
   */
  public void configureQuery(Map<String, String> parametersMap, Query query) {
    Map<String, Object> queryOptions = query.getOptions();

    // Configure the standard Filter Queries.
    Set<String> filterQueries = new HashSet<String>();
    filterQueries.add(getResourceTypeClause(parametersMap));
    queryOptions.put(CommonParams.FQ, filterQueries);

    // Configure sorting.
    final String sort;
    String sortKey = parametersMap.get(DEFAULT_REQUEST_PARAMS.sortOn.toString());
    String sortOrder = parametersMap.get(DEFAULT_REQUEST_PARAMS.sortOrder.toString());
    if ((sortKey == null) || (sortOrder == null)) {
      sort = getDefaultSort();
    } else {
      sort = sortKey + " " + sortOrder;
    }
    queryOptions.put(CommonParams.SORT, sort);

    // Configure default faceting.
    queryOptions.putAll(QUERY_OPTIONS_MAP);

    // And now let the domain-specific code have its say.
    refineQuery(parametersMap, query);
  }

  /**
   * Format the basic query string, with a hook allowing subclasses to refine it.
   */
  public String configureQString(Map<String, String> parametersMap) {
    StringBuilder qBuilder = new StringBuilder();

    String tagsParam = getSearchParam(parametersMap, DEFAULT_REQUEST_PARAMS.tags.toString());
    if (tagsParam != null) {
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      qBuilder.append("tag:(").append(tagsParam).append(")");
    }

    String customQueryString = buildCustomQString(parametersMap);
    
    if (customQueryString != null && !customQueryString.isEmpty()) {
      // append the custom query string to the solr query if one has been provided
      if (qBuilder.length() > 0) {
        qBuilder.append(" AND ");
      }
      qBuilder.append("(").append(customQueryString).append(")");
    }
    
    if (qBuilder.length() == 0) {
      qBuilder.append(getDefaultQueryString(parametersMap));
    }
    return qBuilder.toString();
  }

  /**
   * Utility method to eliminate blank and pure-wildcard parameter values.
   */
  public String getSearchParam(Map<String, String> parametersMap, String key) {
    String param = StringUtils.stripToNull(parametersMap.get(key));
    // solr hates long sequences of asterisks, so compress repeated *'s to single *
    if ( param != null ) {
      param = TWO_OR_MORE_STARS.matcher(param).replaceAll("*");
    }
    if ("*".equals(param) || "*:*".equals(param)) {
      return null;
    } else {
      return param;
    }
  }

  /**
   * Translate servlet request parameters into a properly escaped map.
   * TODO Refactor out of SolrSearchServlet for re-use.
   */
  public Map<String, String> loadParametersMap(SlingHttpServletRequest request) {
    Map<String, String> propertiesMap = new HashMap<String, String>();

    // 0. load authorizable (user) information
    String userId = request.getRemoteUser();
    propertiesMap.put(REQUEST_PARAMETERS_PROPS._userId.toString(), ClientUtils.escapeQueryChars(userId));

    // Remember the requested path, since it sometimes determines the type of query or results handling.
    propertiesMap.put(REQUEST_PARAMETERS_PROPS._requestPath.toString(), request.getRequestURI());

    // If a recursion level was specified for hierarchical results, pass it along.
    Integer traversalDepth = SearchUtil.getTraversalDepthSelector(request);
    if (traversalDepth != null) {
      propertiesMap.put(REQUEST_PARAMETERS_PROPS._traversalDepth.toString(), traversalDepth.toString());
    }

    // 2. load in properties from the request
    RequestParameterMap params = request.getRequestParameterMap();
    for (Map.Entry<String, RequestParameter[]> entry : params.entrySet()) {
      RequestParameter[] vals = entry.getValue();
      String requestValue = vals[0].getString();
      if (StringUtils.stripToNull(requestValue) != null) {
        String key = entry.getKey();
        String val = SearchUtil.escapeString(requestValue, Query.SOLR);
        propertiesMap.put(key, val);
      }
    }
    return propertiesMap;
  }

  /**
   * Add domain-specific options to the query configuration.
   */
  public void refineQuery(Map<String, String> parametersMap, Query query) {
  }

  /**
   * Add domain-specific clauses to the base query string.
   */
  public String buildCustomQString(Map<String, String> parametersMap) {
    return null;
  }

  /**
   * Preserves backwards compatibility with the old search template approach
   * until it can be discarded.
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request, Map<String, String> propertiesMap) {
    propertiesMap.put(TEMPLATE_PROPS._q.toString(), configureQString(propertiesMap));
  }

  /**
   * Preserves backwards compatibility with the old search template approach
   * until it can be discarded.
   */
  @Override
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    LOGGER.debug("Input Query configuration = {}", query);
    Map<String, String> parametersMap = loadParametersMap(request);
    configureQuery(parametersMap, query);
    return searchServiceFactory.getSearchResultSet(request, query);
  }

  /**
   * Preserves backwards compatibility with the old search template approach
   * until it can be discarded.
   */
  @Override
  public void writeResult(SlingHttpServletRequest request, JSONWriter jsonWriter, Result result) throws JSONException {
    Session session = StorageClientUtils.adaptToSession(request
        .getResourceResolver().adaptTo(javax.jcr.Session.class));
    Map<String, String> parametersMap = loadParametersMap(request);
    writeResult(session, parametersMap, jsonWriter, result);
  }

}
