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
package org.sakaiproject.nakamura.util;

import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility operations based around Nakamura core content operations.
 */
public class ContentUtils {


  /**
   * Creates a tree of content using the given content manager, at the path specified. The content
   * is loaded from a JSON file identified by the given {@code classLoader} and
   * {@code classpathResource} identifier.
   * <p>
   * This <b>will not</b> overwrite existing content.
   * 
   * @param contentManager
   * @param path
   * @param classLoader
   * @param classpathResource
   * @throws IOException If there is an issue readying from the classpath resource.
   * @throws JSONException If there is an issue parsing the JSON in the classpath resource.
   * @throws AccessDeniedException If the user does not have sufficient access to create the
   * content tree.
   * @throws StorageClientException If there is an issue persisting the content to the storage, or
   * if the target content path already exists.
   */
  public final static void createContentFromJsonResource(ContentManager contentManager,
      String path, ClassLoader classLoader, String classpathResource) throws IOException,
      JSONException, AccessDeniedException, StorageClientException {
    String json = ResourceLoader.readResource(String.format("res://%s", classpathResource), classLoader);
    Map<String, Object> map = JSONUtils.toMap(new JSONObject(json));
    createTree(contentManager, path, map);
  }

  private final static void createTree(ContentManager contentManager, String path, Map<String, Object> content)
      throws AccessDeniedException, StorageClientException {
    Map<String, Object> children = new HashMap<String, Object>();
    Map<String, Object> properties = new HashMap<String, Object>();
    for (Map.Entry<String, Object> entry : content.entrySet()) {
      String k = entry.getKey();
      Object v = entry.getValue();
      if (v instanceof Map) {
        children.put(k, v);
      } else {
        properties.put(k, v);
      }
    }
    
    contentManager.update(new Content(path, properties));
    for (Map.Entry<String, Object> entry : children.entrySet()) {
      String childPath = StorageClientUtils.newPath(path, entry.getKey());
      @SuppressWarnings("unchecked")
      Map<String, Object> childProperties = (Map<String, Object>) entry.getValue();
      createTree(contentManager, childPath, childProperties);
    }
  }
  
}
