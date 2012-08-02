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
import org.sakaiproject.nakamura.api.search.SearchConstants;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Component(inherit=true)
@Properties({
    @Property(name = SearchConstants.REG_PROVIDER_NAMES, value="ContentSearchQueryHandler"),
    @Property(name = SearchConstants.REG_PROCESSOR_NAMES, value = "ContentSearchQueryHandler")
})
public class ContentSearchQueryHandler extends AbstractContentSearchQueryHandler {

  /**
   * {@inheritDoc}
   * @see org.sakaiproject.nakamura.api.search.solr.DomainObjectSearchQueryHandler#buildCustomQString(java.util.Map)
   */
  @Override
  public String buildCustomQString(Map<String, String> parametersMap) {
    String customQuery = null;
    List<String> filters = new LinkedList<String>();
    
    buildSearchByGeneralQuery(parametersMap, filters);
    buildSearchByMimetype(parametersMap, filters);
    
    if (filters.size() > 0) {
      customQuery = Joiner.on(" AND ").join(filters); 
    }
    
    return customQuery; 
  }
}
