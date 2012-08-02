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
package org.sakaiproject.nakamura.user.search;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.common.params.CommonParams;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultProcessor;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Component
@Properties({
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value="PeopleGroupsAutocompleteQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "PeopleGroupsAutocompleteQueryHandler")
})
public class PeopleGroupsAutocompleteQueryHandler extends DomainObjectSearchQueryHandler
    implements SolrSearchPropertyProvider, SolrSearchResultProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(PeopleGroupsAutocompleteQueryHandler.class);
  private static final String Q_FORMAT = "(name:(%s) OR title:(%<s) OR ngram:(%<s) OR edgengram:(%<s) OR firstName:(%<s) OR lastName:(%<s))";

  public enum REQUEST_PARAMS {
    q
  }

  @Reference(target = "(sakai.search.processor=UsersSearchQueryHandler)")
  DomainObjectSearchQueryHandler usersSearchQueryHandler;

  @Reference
  BasicUserInfoService basicUserInfoService;

  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    return "resourceType:authorizable";
  }

  @Override
  public void refineQuery(Map<String, String> parametersMap, Query query) {
    query.getOptions().put(CommonParams.FL, "path,type");
  }

  @Override
  public String buildCustomQString(Map<String, String> parametersMap) {
    // Did the client specify a text search?
    String customQuery = null;
    String qParam = getSearchParam(parametersMap, REQUEST_PARAMS.q.toString());
    if (qParam != null) {
      customQuery = String.format(Q_FORMAT, qParam);
    }
    return customQuery;
  }

  @Override
  public void writeResult(Session session, Map<String, String> parametersMap, JSONWriter jsonWriter, Result result)
      throws JSONException {
    String authorizableType = result.getFirstValue("type").toString();
    if ("u".equals(authorizableType)) {
      usersSearchQueryHandler.writeResult(session, parametersMap, jsonWriter, result);
    } else if ("g".equals(authorizableType)) {
      // TODO This will obviously relocate when the world searches are refactored...
      String authorizableId = result.getPath();
      ExtendedJSONWriter writer = (ExtendedJSONWriter) jsonWriter;
      try {
        AuthorizableManager authorizableManager = session.getAuthorizableManager();
        Authorizable authorizable = authorizableManager.findAuthorizable(authorizableId);
        if (authorizable != null) {
          jsonWriter.object();
          Map<String,Object> map = basicUserInfoService.getProperties(authorizable);
          ExtendedJSONWriter.writeValueMapInternals(writer, map);
          jsonWriter.endObject();
        }
      } catch (StorageClientException e) {
        LOGGER.error(e.getMessage(), e);
      } catch (AccessDeniedException e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }
}
