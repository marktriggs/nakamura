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
package org.sakaiproject.nakamura.solr;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.solr.common.params.MapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.ResponseBuilder;
import org.apache.solr.handler.component.SearchComponent;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class NakamuraQueryComponentTest {
  
  @Mock
  SolrQueryRequest req;
  
  @Mock
  SolrQueryResponse resp;
  
  private NakamuraQueryComponent queryComponent;
  private ResponseBuilder rb;
  private List<SearchComponent> components;
  private Map<String, String> params;
  private SolrParams solrParams;
  
  @Before
  public void setUp() {
    params = Maps.newHashMap();
    solrParams = new MapSolrParams(params);
    when(req.getParams()).thenReturn(solrParams);
    
    components = Lists.newArrayList();
    rb = new ResponseBuilder(req, resp, components);
    
    queryComponent = new NakamuraQueryComponent();
    queryComponent.testing = true;
  }

  /**
   * Test to verify the query component doesn't explode with no parameters.
   * 
   * @throws Exception
   */
  @Test
  public void testPrepareNoReaders() throws Exception {
    queryComponent.prepare(rb);
    assertNull(rb.getFilters());
    
    params.put("readers", "");
    queryComponent.prepare(rb);
    assertNull(rb.getFilters());
  }

  /**
   * Test to verify the query component picks up the readers parameter.
   * 
   * @throws Exception
   */
  @Test
  public void testPrepareWithReaders() throws Exception {
    String readers = "1234";
    params.put("readers", readers);
    queryComponent.prepare(rb);
    assertEquals(1, rb.getFilters().size());
    rb.getFilters().clear();
    
    readers = "1,2,3,4";
    params.put("readers", readers);
    queryComponent.prepare(rb);
    
    assertEquals(1, rb.getFilters().size());
  }
  
  /**
   * Test to verify the query component ignores extraneous parameters.
   * 
   * @throws Exception
   */
  @Test
  public void testPrepareWithExtraField() throws Exception {
    String readers = "1,2,3,4";
    params.put("readers", readers);
    params.put("random", readers);
    queryComponent.prepare(rb);
    
    assertEquals(1, rb.getFilters().size());
  }
}
