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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.StringUtils;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component(metatype = true, label = "%dynamiccontentresponsecache.name",
    description = "%dynamiccontentresponsecache.description")
@Properties(value = {
    @Property(name = "service.description", value = "Nakamura Dynamic Response Cache"),
    @Property(name = "service.vendor", value = "The Sakai Foundation")})
@Service
public class DynamicContentResponseCacheImpl implements DynamicContentResponseCache {

  @Property(boolValue = false)
  static final String DISABLE_CACHE_FOR_UI_DEV = "disable.cache.for.dev.mode";

  @Property(boolValue = true)
  static final String BYPASS_CACHE_FOR_LOCALHOST = "bypass.cache.for.localhost";

  @Reference
  protected CacheManagerService cacheManagerService;

  private Cache<String> cache;

  private boolean disableForDevMode;

  private boolean bypassForLocalhost;

  @SuppressWarnings("UnusedParameters")
  @Activate
  protected void activate(ComponentContext componentContext) throws ServletException {
    @SuppressWarnings("unchecked")
    Dictionary<String, Object> properties = componentContext.getProperties();

    cache = cacheManagerService.getCache(DynamicContentResponseCache.class.getName() + "-cache",
        CacheScope.INSTANCE);

    disableForDevMode = PropertiesUtil.toBoolean(properties.get(DISABLE_CACHE_FOR_UI_DEV), false);
    bypassForLocalhost = PropertiesUtil.toBoolean(properties.get(BYPASS_CACHE_FOR_LOCALHOST), true);
  }

  @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
  public void deactivate(ComponentContext componentContext) {
    cache.clear();
  }

  @Override
  public void recordResponse(String cacheCategory, HttpServletRequest request, HttpServletResponse response) {
    if (isDisabled(request)) {
      return;
    }
    String key = buildCacheKey(cacheCategory, request.getRemoteUser());
    String etag = cache.get(key);
    if (etag == null) {
      etag = buildETag(request);
      cache.put(key, etag);
      TelemetryCounter.incrementValue("http", "DynamicContentResponseCache-save", cacheCategory);
    }
    setHeaders(response, etag);
  }

  @Override
  public void invalidate(String cacheCategory, String userID) {
    if (disableForDevMode) {
      return;
    }
    String key = buildCacheKey(cacheCategory, userID);
    if (cache.containsKey(key)) {
      cache.remove(key);
      TelemetryCounter.incrementValue("http", "DynamicContentResponseCache-invalidation", cacheCategory);
    }
    String wildcardKey = buildCacheKey("*", userID);
    if (cache.containsKey(wildcardKey)) {
      cache.remove(wildcardKey);
      TelemetryCounter.incrementValue("http", "DynamicContentResponseCache-invalidation", "*");
    }
  }

  @Override
  public boolean send304WhenClientHasFreshETag(String cacheCategory, HttpServletRequest request, HttpServletResponse response) {
    if (isDisabled(request)) {
      return false;
    }
    // examine client request for If-None-Match http header. compare that against the etag.
    String clientEtag = request.getHeader("If-None-Match");
    String serverEtag = cache.get(buildCacheKey(cacheCategory, request.getRemoteUser()));
    if (clientEtag != null && clientEtag.equals(serverEtag)) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      setHeaders(response, serverEtag);
      TelemetryCounter.incrementValue("http", "DynamicContentResponseCache-hit", cacheCategory);
      return true;
    }
    return false;
  }

  @Override
  public void clear() {
    if (cache != null) {
      cache.clear();
    }
  }

  private String buildETag(HttpServletRequest request) {
    String rawTag = request.getRemoteUser() + ':' + request.getPathInfo() + ':' + request.getQueryString()
        + ':' + System.nanoTime();
    try {
      return StringUtils.sha1Hash(rawTag);
    } catch (UnsupportedEncodingException e) {
      return rawTag;
    } catch (NoSuchAlgorithmException e) {
      return rawTag;
    }
  }

  String buildCacheKey(String cacheCategory, String userID) {
    return userID + ':' + cacheCategory;
  }

  private boolean isDisabled(HttpServletRequest request) {
    return disableForDevMode || (bypassForLocalhost && ("localhost".equals(request.getServerName())
        || "127.0.0.1".equals(request.getServerName())));
  }

  private void setHeaders(HttpServletResponse response, String etag) {
    response.setHeader("ETag", etag);
    response.setHeader("Cache-Control", "private, must-revalidate, max-age=0");
  }
}
