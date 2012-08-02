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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Provides default functionality for pooled-content search entry-points.
 */
@Component(componentAbstract=true, inherit=true)
public abstract class AbstractContentSearchQueryHandler extends DomainObjectSearchQueryHandler
    implements SolrSearchResultProcessor, SolrSearchPropertyProvider {
  
  private final static Logger LOGGER = LoggerFactory.getLogger(
      AbstractContentSearchQueryHandler.class);

  private final static String Q_TEMPLATE = "(content:(%s) OR filename:(%<s) OR " +
      "tag:(%<s) OR description:(%<s) OR ngram:(%<s) OR edgengram:(%<s) OR widgetdata:(%<s))";

  public enum REQUEST_PARAMETERS {
    q,
    mimetype,
    levels,
    userid,
    role
  }
  
  @Reference
  protected Repository repository;

  public AbstractContentSearchQueryHandler() {
    
  }

  public AbstractContentSearchQueryHandler(SolrSearchServiceFactory searchServiceFactory, Repository repository) {
    super(searchServiceFactory);
    this.repository = repository;
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#getResourceTypeClause(java.util.Map)
   */
  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    /*
     * If there is a query string parameter 'q', then we need to search widget data content
     * for that match as well.
     */
    if (hasGeneralQuery(parametersMap)) {
      return "resourceType:(sakai/pooled-content OR sakai/widget-data)";
    } else {
      return "resourceType:sakai/pooled-content";
    }
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#refineQuery(java.util.Map, org.sakaiproject.nakamura.api.search.solr.Query)
   */
  @Override
  public void refineQuery(Map<String, String> parametersMap, Query query) {
    query.getOptions().put(CommonParams.FL, "path");
    
    /*
     * If there is a query string 'q' specified, then we will also need to search on
     * widget data contents. Because of this, to avoid duplicate content (e.g, multiple
     * widgets of a pooled content item match on the content), we need to group by the
     * widget content "returnpath". See also the #getResourceTypeClause(Map) method to
     * see how the widget data resourceType is dynamically included in the query.
     */
    if (hasGeneralQuery(parametersMap)) {
      query.getOptions().put(GroupParams.GROUP, Boolean.TRUE);
      query.getOptions().put(GroupParams.GROUP_FIELD, "returnpath");
      
      // record the number "groups" matched to give accurate total of elements
      // returned. Here, one GROUP is one actual result, instead of all the
      // identical elements aggregated in those groups.
      query.getOptions().put(GroupParams.GROUP_TOTAL_COUNT, Boolean.TRUE);
    }
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#getSearchResultSet(org.apache.sling.api.SlingHttpServletRequest, org.sakaiproject.nakamura.api.search.solr.Query)
   */
  @Override
  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    LOGGER.debug("Input Query configuration = {}", query);
    Map<String, String> parametersMap = loadParametersMap(request);
    configureQuery(parametersMap, query);
    return searchServiceFactory.getSearchResultSet(request, query);
  }
  
  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#writeResult(org.sakaiproject.nakamura.api.lite.Session, java.util.Map, org.apache.sling.commons.json.io.JSONWriter, org.sakaiproject.nakamura.api.search.solr.Result)
   */
  @Override
  public void writeResult(Session session, Map<String, String> parametersMap, JSONWriter jsonWriter, Result result)
      throws JSONException {
    String path = result.getPath();
    Content content;
    try {
      content = session.getContentManager().get(path);
      if (content != null) {
        jsonWriter.object();
        // This search defaults to traversing the full tree of hierarchical content,
        // equivalent to a selector of "infinity".
        int traversalDepth = -1;
        if (parametersMap.containsKey(REQUEST_PARAMETERS_PROPS._traversalDepth.toString())) {
          String traversalDepthValue = parametersMap.get(REQUEST_PARAMETERS_PROPS._traversalDepth.toString());
          try {
            traversalDepth = Integer.parseInt(traversalDepthValue);
          } catch (NumberFormatException e) {
            LOGGER.error(e.getMessage(), e);
          }
        }
        ExtendedJSONWriter.writeContentTreeToWriter(jsonWriter, content, true, traversalDepth);
        FileUtils.writeCommentCountProperty(content, session, jsonWriter, repository);
        jsonWriter.endObject();
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * Determine whether or not general text is being queried on in this search (i.e., a 'q'
   * query string parameters was provided).
   * 
   * @param parametersMap
   * @return
   */
  protected boolean hasGeneralQuery(Map<String, String> parametersMap) {
    return getSearchParam(parametersMap, REQUEST_PARAMETERS.q.toString()) != null;
  }
  
  /**
   * Apply the 'search by mimetype' filter to the lucene query string.
   * @param parametersMap
   * @param queryString
   */
  protected void buildSearchByMimetype(Map<String, String> parametersMap,
      List<String> filters) {
    String mimeType = getSearchParam(parametersMap, REQUEST_PARAMETERS.mimetype.toString());
    if (mimeType != null) {
      filters.add(String.format("mimeType:%s", mimeType));
    }
  }

  /**
   * Apply the 'search by general text' filter to the lucene query string.
   * 
   * @param parametersMap
   * @param queryString
   */
  protected void buildSearchByGeneralQuery(Map<String, String> parametersMap,
      List<String> filters) {
    String q = getSearchParam(parametersMap, REQUEST_PARAMETERS.q.toString());
    if (q != null) {
      filters.add(String.format(Q_TEMPLATE, q));
    }
  }
  
  
}
