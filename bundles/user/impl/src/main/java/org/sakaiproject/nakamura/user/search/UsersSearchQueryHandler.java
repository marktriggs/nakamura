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

import com.google.common.collect.ImmutableMap;
import java.util.Formatter;
import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.GroupParams;
import org.sakaiproject.nakamura.api.connections.ConnectionManager;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.search.SearchConstants;
import org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Result;
import org.sakaiproject.nakamura.api.user.BasicUserInfoService;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Properties({
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value="UsersSearchQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "UsersSearchQueryHandler")
})
public class UsersSearchQueryHandler extends DomainObjectSearchQueryHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(UsersSearchQueryHandler.class);
  private static final String Q_FORMAT =
      "name:(%s) OR firstName:(%<s) OR lastName:(%<s) OR email:(%<s) OR ngram:(%<s) OR edgengram:(%<s)";
  private static Map<String, Object> FULLPROFILE_QUERY_OPTIONS_MAP = ImmutableMap.<String, Object> of(
      GroupParams.GROUP, Boolean.TRUE,
      GroupParams.GROUP_FIELD, "returnpath",
      GroupParams.GROUP_TOTAL_COUNT, Boolean.TRUE
  );

  public enum REQUEST_PARAMS {
    fullprofile,
    q
  }

  @Reference
  BasicUserInfoService basicUserInfoService;

  @Reference
  ConnectionManager connectionManager;

  @Override
  public String getResourceTypeClause(Map<String, String> parametersMap) {
    // fq=type:u&fq=resourceType:(authorizable OR profile)
    StringBuilder sb = new StringBuilder("type:u AND resourceType:(authorizable");
    if (isFullProfile(parametersMap)) {
      sb.append(" OR profile");
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public void refineQuery(Map<String, String> parametersMap, Query query) {
    Map<String, Object> queryOptions = query.getOptions();
    queryOptions.put(CommonParams.FL, "path");

    // If both Authorizable and Profile records will be searched, collapse them
    // into a single result for a single person.
    if (isFullProfile(parametersMap)) {
      queryOptions.putAll(FULLPROFILE_QUERY_OPTIONS_MAP);
    }
  }

  @Override
  public String buildCustomQString(Map<String, String> parametersMap) {
    // Did the client specify a text search?
    String customQuery = null;
    StringBuilder qBuilder = new StringBuilder();
    String qParam = getSearchParam(parametersMap, REQUEST_PARAMS.q.toString());
    if (qParam != null) {
      qBuilder.append("(");
      (new Formatter(qBuilder)).format(Q_FORMAT, qParam);
      if (isFullProfile(parametersMap)) {
        qBuilder.append(" OR profile:(").append(qParam).append(")");
      }
      qBuilder.append(")");
      customQuery = qBuilder.toString();
    }
    return customQuery;
  }

  @Override
  public void writeResult(Session session, Map<String, String> parametersMap, JSONWriter jsonWriter, Result result)
      throws JSONException {
    String authorizableId = result.getPath();
    ExtendedJSONWriter writer = (ExtendedJSONWriter) jsonWriter;
    try {
      AuthorizableManager authorizableManager = session.getAuthorizableManager();
      Authorizable authorizable = authorizableManager.findAuthorizable(authorizableId);
      if (authorizable != null) {
        jsonWriter.object();
        Map<String,Object> map = basicUserInfoService.getProperties(authorizable);
        ExtendedJSONWriter.writeValueMapInternals(writer, map);
        connectionManager.writeConnectionInfo(writer, session, session.getUserId(), authorizableId);
        jsonWriter.endObject();
      }
    } catch (StorageClientException e) {
      LOGGER.error(e.getMessage(), e);
    } catch (AccessDeniedException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  /**
   * Even if a full profile search has been requested, there is no need to
   * include Profile records unless the client specified a text search.
   * A full wildcard search can be confined to Authorizable records.
   */
  private boolean isFullProfile(Map<String, String> parametersMap) {
    return ("true".equals(parametersMap.get(REQUEST_PARAMS.fullprofile.toString())) &&
        (getSearchParam(parametersMap, REQUEST_PARAMS.q.toString()) != null));
  }
}
