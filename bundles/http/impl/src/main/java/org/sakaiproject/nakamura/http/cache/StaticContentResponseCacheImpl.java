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
package org.sakaiproject.nakamura.http.cache;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.http.api.ExtHttpService;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.cache.CacheConfig;
import org.sakaiproject.nakamura.api.http.cache.StaticContentResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component(metatype = true, label = "%staticcontentresponsecache.name",
    description = "%staticcontentresponsecache.description")
@Service
@Properties(value = {
    @Property(name = "service.description", value = "Nakamura Static Response Cache"),
    @Property(name = "service.vendor", value = "The Sakai Foundation")})
public class StaticContentResponseCacheImpl implements Filter, StaticContentResponseCache {

  private static final Logger LOGGER = LoggerFactory.getLogger(StaticContentResponseCacheImpl.class);

  @Property(value = {
      "root;.*(js|css)$;172800",
      "root;.*html$;172800",
      "var;^/var/search/public/.*$;900",
      "var;^/var/widgets.json$;7200",
      "system;^/system/staticfiles$;86400"})
  static final String SAKAI_CACHE_PATTERNS = "sakai.cache.patterns";

  @Property(value = {
      "dev;31536000",
      "devwidgets;31536000"})
  static final String SAKAI_CACHE_PATHS = "sakai.cache.paths";

  @Property(intValue = 5)
  static final String FILTER_PRIORITY_CONF = "filter.priority";

  @Property(boolValue = false)
  static final String DISABLE_CACHE_FOR_UI_DEV = "disable.cache.for.dev.mode";

  @Property(boolValue = true)
  static final String BYPASS_CACHE_FOR_LOCALHOST = "bypass.cache.for.localhost";

  @Reference
  protected ExtHttpService extHttpService;

  @Reference
  protected CacheManagerService cacheManagerService;

  private Map<String, CacheConfig> subPaths;

  private Map<String, Map<Pattern, CacheConfig>> subPathPatterns;

  private Map<Pattern, CacheConfig> rootPathPatterns;

  private Cache<CachedResponse> cache;

  private boolean bypassForLocalhost;

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#destroy()
   */
  public void destroy() {
  }

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest srequest = (HttpServletRequest) request;
    HttpServletResponse sresponse = (HttpServletResponse) response;

    if (bypassForLocalhost && ("localhost".equals(request.getServerName())
        || "127.0.0.1".equals(request.getServerName()))) {
      // bypass for developer convenience
      chain.doFilter(request, response);
      return;
    }

    String path = srequest.getPathInfo();

    if (HttpConstants.METHOD_GET.equals(srequest.getMethod())) {
      if (!responseWasFiltered(srequest, sresponse, chain, getCacheConfig(path))) {
        chain.doFilter(request, response);
      }
    } else {
      chain.doFilter(request, response);
    }
  }

  private CacheConfig getCacheConfig(String path) {

    // get the Path and then the first 2 elements (2 so that we can tell if this is root
    // or not
    String[] elements = StringUtils.split(path, "/", 2);

    if (elements.length == 0) { // odd request
      return null;
    } else if (elements.length == 1) { // root request eg /index.html
      for (Entry<Pattern, CacheConfig> p : rootPathPatterns.entrySet()) {
        if (p.getKey().matcher(path).matches()) {
          return p.getValue();
        }
      }
    } else { // subtree //p/index.html

      // check if there is a subtree with a setting
      CacheConfig subPathExactMatch = subPaths.get(elements[0]);
      if (subPathExactMatch != null) {
        return subPathExactMatch;
      }

      // or a set of patterns for the subtree
      Map<Pattern, CacheConfig> patterns = subPathPatterns.get(elements[0]);
      if (patterns != null) {
        for (Entry<Pattern, CacheConfig> p : patterns.entrySet()) {
          if (p.getKey().matcher(path).matches()) {
            return p.getValue();
          }
        }
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(FilterConfig filterConfig) throws ServletException {

  }

  @Activate
  protected void activate(ComponentContext componentContext) throws ServletException {
    @SuppressWarnings("unchecked")
    Dictionary<String, Object> properties = componentContext.getProperties();
    String[] sakaiCachePaths = PropertiesUtil.toStringArray(properties.get(SAKAI_CACHE_PATHS));
    subPaths = new HashMap<String, CacheConfig>();
    if (sakaiCachePaths != null) {
      for (String sakaiCachePath : sakaiCachePaths) {
        String[] cp = StringUtils.split(sakaiCachePath, ';');
        CacheConfig config = new CacheConfig(Integer.valueOf(cp[1]), cp[0], null);
        subPaths.put(cp[0], config);
      }
    }
    String[] sakaiCachePatternPaths = PropertiesUtil.toStringArray(properties.get(SAKAI_CACHE_PATTERNS));
    subPathPatterns = new HashMap<String, Map<Pattern, CacheConfig>>();
    if (sakaiCachePatternPaths != null) {
      for (String sakaiCachePatternPath : sakaiCachePatternPaths) {
        String[] cp = StringUtils.split(sakaiCachePatternPath, ';');
        if (subPathPatterns.containsKey(cp[0])) {
          CacheConfig config = new CacheConfig(Integer.valueOf(cp[2]), cp[0], Pattern.compile(cp[1]));
          subPathPatterns.get(cp[0]).put(Pattern.compile(cp[1]), config);
        } else {
          Map<Pattern, CacheConfig> patternMap = new HashMap<Pattern, CacheConfig>();
          CacheConfig config = new CacheConfig(Integer.valueOf(cp[2]), cp[0], Pattern.compile(cp[1]));
          patternMap.put(Pattern.compile(cp[1]), config);
          subPathPatterns.put(cp[0], patternMap);
        }
      }
    }
    rootPathPatterns = subPathPatterns.get("root");
    if (rootPathPatterns == null) {
      rootPathPatterns = new HashMap<Pattern, CacheConfig>();
    }

    int filterPriority = PropertiesUtil.toInteger(properties.get(FILTER_PRIORITY_CONF), 0);
    boolean disableForDevMode = PropertiesUtil.toBoolean(properties.get(DISABLE_CACHE_FOR_UI_DEV), false);
    bypassForLocalhost = PropertiesUtil.toBoolean(properties.get(BYPASS_CACHE_FOR_LOCALHOST), true);

    cache = cacheManagerService.getCache(StaticContentResponseCache.class.getName() + "-cache",
        CacheScope.INSTANCE);

    if (disableForDevMode) {
      extHttpService.unregisterFilter(this);
    } else {
      extHttpService.registerFilter(this, ".*", null, filterPriority, null);
    }

  }

  @Override
  public void clear() {
    if (cache != null) {
      cache.clear();
    }
  }

  @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
  @Deactivate
  public void deactivate(ComponentContext componentContext) {
    extHttpService.unregisterFilter(this);
    cache.clear();
  }

  private boolean responseWasFiltered(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain, CacheConfig cacheConfig)
      throws IOException, ServletException {
    if (!HttpConstants.METHOD_GET.equals(request.getMethod()) || cacheConfig == null || cacheConfig.getMaxAge() <= 0) {
      return false; // only GET is ever cacheable, and cacheConfig must exist and have a nonzero maxAge
    }

    CachedResponse cachedResponse = getCachedResponse(request);

    if (cachedResponse != null && cachedResponse.isValid()) {
      TelemetryCounter.incrementValue("http", "StaticContentResponseCacheImpl-hit", getCacheKey(request));
      cachedResponse.replay(response);
      return true;
    }

    Long expires = System.currentTimeMillis() + (cacheConfig.getMaxAge() * 1000L);
    response.setDateHeader("Expires", expires);
    FilterResponseWrapper filterResponseWrapper = new FilterResponseWrapper(response, false, false, true);
    filterChain.doFilter(request, filterResponseWrapper);
    filterResponseWrapper.setDateHeader("Expires", expires);
    saveCachedResponse(request, filterResponseWrapper.getResponseOperation(), cacheConfig);
    return true;
  }

  private String getCacheKey(HttpServletRequest request) {
    return request.getPathInfo() + "?" + request.getQueryString();
  }

  private CachedResponse getCachedResponse(HttpServletRequest request) {
    CachedResponse cachedResponse;
    String key = getCacheKey(request);
    cachedResponse = cache.get(key);
    if (cachedResponse != null && !cachedResponse.isValid()) {
      cachedResponse = null;
      cache.remove(key);
    }
    return cachedResponse;
  }

  private void saveCachedResponse(HttpServletRequest request,
                                  OperationResponseCapture responseOperation, CacheConfig cacheConfig) {
    try {
      if (responseOperation.canCache()) {
        String key = getCacheKey(request);
        cache.put(key, new CachedResponse(responseOperation, cacheConfig.getMaxAge()));
        TelemetryCounter.incrementValue("http", "StaticContentResponseCacheImpl-save", key);
      }
    } catch (IOException e) {
      LOGGER.info("Failed to save response in cache ", e);
    }
  }

}
