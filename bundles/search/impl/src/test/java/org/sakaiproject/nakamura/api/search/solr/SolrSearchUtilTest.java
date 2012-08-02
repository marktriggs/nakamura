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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.DEFAULT_PAGED_ITEMS;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_ITEMS_PER_PAGE;
import static org.sakaiproject.nakamura.api.search.solr.SolrSearchConstants.PARAMS_PAGE;

import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.request.RequestParameter;
import org.junit.Test;

import java.util.Map;

public class SolrSearchUtilTest extends Assert {

  @Test
  public void getOffsetAndSizeFromRequest() {
    SlingHttpServletRequest request = mock(SlingHttpServletRequest.class);
    RequestParameter items = mock(RequestParameter.class);

    when(items.getString()).thenReturn("15");
    when(request.getRequestParameter(PARAMS_ITEMS_PER_PAGE)).thenReturn(items);

    long[] offSetAndSize = SolrSearchUtil.getOffsetAndSize(request, null);
    assertEquals(0, offSetAndSize[0]);
    assertEquals(15, offSetAndSize[1]);

    RequestParameter page = mock(RequestParameter.class);
    when(page.getString()).thenReturn("3");
    when(request.getRequestParameter(PARAMS_PAGE)).thenReturn(page);

    offSetAndSize = SolrSearchUtil.getOffsetAndSize(request, null);
    assertEquals(45, offSetAndSize[0]);
    assertEquals(15, offSetAndSize[1]);

    when(items.getString()).thenReturn("unparseableyou");
    when(page.getString()).thenReturn("unparseableyou");
    offSetAndSize = SolrSearchUtil.getOffsetAndSize(request, null);
    assertEquals(0, offSetAndSize[0]);
    assertEquals(DEFAULT_PAGED_ITEMS, offSetAndSize[1]);
  }

  @Test
  public void getOffsetAndSizeFromOptions() {
    Map<String, Object> options = ImmutableMap.<String,Object>of(PARAMS_ITEMS_PER_PAGE, 15, PARAMS_PAGE, 0);
    long[] offSetAndSize = SolrSearchUtil.getOffsetAndSize(null, options);
    assertEquals(0, offSetAndSize[0]);
    assertEquals(15, offSetAndSize[1]);

    Map<String, Object> options2 = ImmutableMap.<String,Object>of(PARAMS_ITEMS_PER_PAGE, 15, PARAMS_PAGE, 3);
    offSetAndSize = SolrSearchUtil.getOffsetAndSize(null, options2);
    assertEquals(45, offSetAndSize[0]);
    assertEquals(15, offSetAndSize[1]);
  }

}
