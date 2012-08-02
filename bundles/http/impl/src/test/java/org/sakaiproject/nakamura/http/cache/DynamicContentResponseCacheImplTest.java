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

package org.sakaiproject.nakamura.http.cache;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;

import java.util.Dictionary;
import java.util.Hashtable;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class DynamicContentResponseCacheImplTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private ComponentContext componentContext;

  @Mock
  private Cache<Object> cache;

  private DynamicContentResponseCacheImpl dynamicContentResponseCache;

  @Before
  public void setup() throws ServletException {
    dynamicContentResponseCache = new DynamicContentResponseCacheImpl();
    dynamicContentResponseCache.cacheManagerService = mock(CacheManagerService.class);
    when(dynamicContentResponseCache.cacheManagerService.getCache(DynamicContentResponseCache.class.getName() + "-cache", CacheScope.INSTANCE)).thenReturn(cache);


    Dictionary<String, Object> properties = new Hashtable<String, Object>();
    when(componentContext.getProperties()).thenReturn(properties);

    dynamicContentResponseCache.activate(componentContext);
  }

  @Test
  public void recordResponseAndInvalidate() {
    String cat = "TestCat";
    String user = "joe";
    when(request.getPathInfo()).thenReturn("/foo/bar/baz");
    when(request.getRemoteUser()).thenReturn(user);

    when(cache.containsKey(dynamicContentResponseCache.buildCacheKey(cat, user))).thenReturn(false);
    dynamicContentResponseCache.recordResponse(cat, request, response);
    when(cache.get(dynamicContentResponseCache.buildCacheKey(cat, user))).thenReturn("foo");
    dynamicContentResponseCache.recordResponse(cat, request, response);
    verify(cache, times(2)).get(anyString());
    verify(cache, atMost(1)).put(anyString(), anyString());
    verify(response, times(4)).setHeader(anyString(), anyString());
    when(cache.containsKey(dynamicContentResponseCache.buildCacheKey(cat, user))).thenReturn(true);
    dynamicContentResponseCache.invalidate(cat, user);
    verify(cache).remove(anyString());
  }

  @Test
  public void clientHasFreshETag() {
    when(request.getHeader("If-None-Match")).thenReturn("myetag");
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertTrue(dynamicContentResponseCache.send304WhenClientHasFreshETag("cat", request, response));
    verify(response).setStatus(304);
  }

  @Test
  public void clientLacksETag() {
    when(request.getHeader("If-None-Match")).thenReturn(null);
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertFalse(dynamicContentResponseCache.send304WhenClientHasFreshETag("cat", request, response));
    verify(response, never()).setStatus(304);
  }

  @Test
  public void clientHasOldETag() {
    when(request.getHeader("If-None-Match")).thenReturn("oldetag");
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertFalse(dynamicContentResponseCache.send304WhenClientHasFreshETag("cat", request, response));
    verify(response, never()).setStatus(304);
  }

  @Test
  public void clear() {
    dynamicContentResponseCache.clear();
    verify(cache).clear();
  }

}
