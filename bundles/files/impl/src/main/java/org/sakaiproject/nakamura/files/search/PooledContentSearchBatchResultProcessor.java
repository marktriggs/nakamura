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
package org.sakaiproject.nakamura.files.search;

import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.sakaiproject.nakamura.api.files.FileUtils;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchBatchResultProcessor;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

@Component(immediate = true, metatype=true)
@Properties(value = {
    @Property(name = "service.vendor", value = "The Sakai Foundation"),
    @Property(name = SolrSearchConstants.REG_BATCH_PROCESSOR_NAMES, value = "PooledContentFiles"),
    @Property(name = SolrSearchBatchResultProcessor.DEFAULT_BATCH_PROCESSOR_PROP, boolValue = true)
})
@Service(value = SolrSearchBatchResultProcessor.class)
public class PooledContentSearchBatchResultProcessor implements
SolrSearchBatchResultProcessor {

  @Reference
  protected SolrSearchServiceFactory searchServiceFactory;
  @Reference
  private Repository repository;

  public static final Logger LOGGER = LoggerFactory
      .getLogger(LiteMeManagerFileSearchBatchResultProcessor.class);

  /**
   * The non component constructor
   * @param searchServiceFactory
   */
  PooledContentSearchBatchResultProcessor(SolrSearchServiceFactory searchServiceFactory) {
    if ( searchServiceFactory == null ) {
      throw new IllegalArgumentException("Search Service Factory must be set when not using as a component");
    }
    this.searchServiceFactory = searchServiceFactory;
  }


  /**
   * Component Constructor.
   */
  public PooledContentSearchBatchResultProcessor() {
  }


  public void writeResults(SlingHttpServletRequest request, JSONWriter write,
      Iterator<Result> iterator) throws JSONException {


    long nitems = SolrSearchUtil.longRequestParameter(request,
        PARAMS_ITEMS_PER_PAGE, DEFAULT_PAGED_ITEMS);
    javax.jcr.Session jcrSession = request.getResourceResolver().adaptTo(
        javax.jcr.Session.class);
    final Session session = StorageClientUtils.adaptToSession(jcrSession);


    for (long i = 0; i < nitems && iterator.hasNext(); i++) {
      Result result = iterator.next();
      String contentPath = result.getPath();
      Content content;
      try {
        content = session.getContentManager().get(contentPath);
        if (content != null) {
          write.object();
          int traversalDepth = SearchUtil.getTraversalDepth(request, -1);
          ExtendedJSONWriter.writeContentTreeToWriter(write, content, true, traversalDepth);
          FileUtils.writeCommentCountProperty(content, session, write, repository);
          write.endObject();
        }
      } catch (StorageClientException e) {
        throw new JSONException(e);
      } catch (AccessDeniedException e) {
        LOGGER.error("can't access " + contentPath ,e);      
      }
    }
  }


  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request, Query query) throws SolrSearchException {
    return searchServiceFactory.getSearchResultSet(request, query);  }

}
