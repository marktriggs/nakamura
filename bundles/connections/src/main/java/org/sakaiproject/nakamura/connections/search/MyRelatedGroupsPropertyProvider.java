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
package org.sakaiproject.nakamura.connections.search;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.SearchUtil;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provides properties for myrelatedgroups.json. Added my groups and contact's groups,
 * titles, and tags.
 */
@Component
@Service
@Property(name = SearchConstants.REG_PROVIDER_NAMES, value = "MyRelatedGroupsPropertyProvider")
public class MyRelatedGroupsPropertyProvider implements SolrSearchPropertyProvider {
  private static final Logger LOGGER = LoggerFactory.getLogger(MyRelatedGroupsPropertyProvider.class);

  @Reference
  private SolrSearchServiceFactory searchServiceFactory;

  // @Reference
  // private Repository repo;

  public MyRelatedGroupsPropertyProvider() {
  }

  MyRelatedGroupsPropertyProvider(SolrSearchServiceFactory searchServiceFactory) {
    // this.connPropProv = connPropProv;
    this.searchServiceFactory = searchServiceFactory;
    // this.repo = repo;
  }

  /**
   * Loads properties needed for myrelatedgroups.json.
   *
   * _groupQuery == a (partial) query for the IDs of recommended groups.
   *
   * {@inheritDoc}
   *
   * @see org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest, java.util.Map)
   */
  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
    try {
      String user = request.getRemoteUser();

      LOGGER.debug("Recommending groups for: " + user);

      // Perform a MoreLikeThis query for this user's groups
      Map<String,String> mltOptions = new HashMap<String,String>();
      mltOptions.put("fl", "*,score");
      mltOptions.put("rows", "10");
      mltOptions.put("mlt", "true");
      mltOptions.put("mlt.fl", "type,readers,title,name,taguuid");
      mltOptions.put("mlt.count", "10");
      mltOptions.put("mlt.mintf", "1");
      mltOptions.put("mlt.mindf", "1");
      mltOptions.put("mlt.boost", "true");
      mltOptions.put("mlt.qf", "type^100 readers^3 name^2 taguuid^1 title^1");

      // Matches all of user's groups
      String mltQuery = String.format("type:g AND " +
                                      "resourceType:authorizable AND " +
                                      "readers:\"%s\"",
                                      ClientUtils.escapeQueryChars(user));

      SolrSearchResultSet suggestedGroups =
        searchServiceFactory.getSearchResultSet(request,
                                                new Query(mltQuery,
                                                          mltOptions));

      List<String> suggestedIds = new ArrayList<String>();

      Iterator<Result> resultIterator = suggestedGroups.getResultSetIterator();

      // Assign a descending weight to each matched ID to preserve the original
      // score ordering.
      long weight = (suggestedGroups.getSize() + 1);

      while (resultIterator.hasNext()) {
        Result result = resultIterator.next();
        Map<String, Collection<Object>> props = result.getProperties();

        for (Object id : props.get("id")) {
          suggestedIds.add("\"" +
                           SearchUtil.escapeString((String) id, Query.SOLR) +
                           "\"^" +
                           weight);
          weight--;
        }
      }

      if (suggestedIds.size() > 0) {
        propertiesMap.put("_groupQuery",
                          " AND id:(" +
                          StringUtils.join(suggestedIds, " OR ") +
                          ")" +
                          String.format(" AND -readers:\"%s\"",
                                        ClientUtils.escapeQueryChars(user)));
      } else {
        propertiesMap.put("_groupQuery", "");
      }

      LOGGER.debug("Query: " + propertiesMap.get("_groupQuery"));
    } catch (SolrSearchException e) {
      LOGGER.error(e.getMessage(), e);
      throw new IllegalStateException(e);
    }
  }
}
