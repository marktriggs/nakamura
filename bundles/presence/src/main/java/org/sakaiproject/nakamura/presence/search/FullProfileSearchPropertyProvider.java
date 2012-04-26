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
package org.sakaiproject.nakamura.presence.search;

import static org.sakaiproject.nakamura.api.search.SearchConstants.REG_PROVIDER_NAMES;

import java.util.Map;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchPropertyProvider;

/**
 *
 */
@Component
@Service
@Property(name = REG_PROVIDER_NAMES, value="FullProfile")
public class FullProfileSearchPropertyProvider implements SolrSearchPropertyProvider {

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.SearchPropertyProvider#loadUserProperties(org.apache.sling.api.SlingHttpServletRequest, java.util.Map)
   */
  @Override
  public void loadUserProperties(SlingHttpServletRequest request,
      Map<String, String> propertiesMap) {
    String val = "";
    String valOr = "";
    String valAnd = "";
    if (Boolean.parseBoolean(request.getParameter("fullprofile"))) {
      // Since the request parameters are collected before property providers are
      // processed, we can get the query from the properties maps.
      String q = propertiesMap.get("q");
      val = "profile:(" + q + ")";
      valOr = " OR " + val;
      valAnd = " AND " + val;
    }
    propertiesMap.put("_fullProfile", val);
    propertiesMap.put("_fullProfileOr", valOr);
    propertiesMap.put("_fullProfileAnd", valAnd);
  }
}
