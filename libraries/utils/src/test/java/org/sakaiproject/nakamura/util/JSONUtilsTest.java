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

import junit.framework.Assert;

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Verifies the method within the {@link org.sakaiproject.nakamura.util.JSONUtils} class.
 */
public class JSONUtilsTest {

  /**
   * Verify serialization happens properly for both objects and arrays at the toMap entry-point.
   * 
   * @throws Exception
   */
  @Test
  public void testToMap() throws Exception {
    Assert.assertNull(JSONUtils.toMap(null));
    Assert.assertEquals(0, JSONUtils.toMap(new JSONObject("{}")).size());
    
    Map<String, Object> json = JSONUtils.toMap(loadJsonObject("org/sakaiproject/nakamura/util/jsonObject.json"));
    Assert.assertNotNull(json);
    Assert.assertEquals("simple-value", json.get("simple-key"));
    
    Object[] array = (Object[]) json.get("array");
    Assert.assertEquals("first", array[0]);
    Assert.assertEquals("second", array[1]);
    Assert.assertEquals("third", array[2]);
    
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>)json.get("object");
    Assert.assertEquals("val1", map.get("key1"));
    Assert.assertEquals("val2", map.get("key2"));
  }
  
  /**
   * Verify serialization happens properly for both objects and arrays at the toArray entry-point.
   * 
   * @throws Exception
   */
  @Test
  public void testToArray() throws Exception {
    Assert.assertNull(JSONUtils.toArray(null));
    Assert.assertEquals(0, JSONUtils.toArray(new JSONArray("[]")).length)
    ;
    Object[] json = JSONUtils.toArray(loadJsonArray("org/sakaiproject/nakamura/util/jsonArray.json"));
    Assert.assertNotNull(json);
    Assert.assertEquals("first", json[0]);
    Assert.assertEquals("second", json[1]);
    
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>)json[2];
    Assert.assertEquals("val1", map.get("key1"));
    Assert.assertEquals("val2", map.get("key2"));
  }
  
  /**
   * Load an json array from a JSON classpath resource.
   * 
   * @param classpathResource
   * @return
   * @throws IOException
   * @throws JSONException
   */
  private JSONArray loadJsonArray(String classpathResource) throws IOException, JSONException {
    InputStream in = null;
    try {
      in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource);
      String jsonString = IOUtils.readFully(in, "UTF-8");
      return new JSONArray(jsonString);
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
  
  /**
   * Load a json object from a JSON classpath resource.
   * 
   * @param classpathResource
   * @return
   * @throws IOException
   * @throws JSONException
   */
  private JSONObject loadJsonObject(String classpathResource) throws IOException, JSONException {
    InputStream in = null;
    try {
      in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathResource);
      String jsonString = IOUtils.readFully(in, "UTF-8");
      return new JSONObject(jsonString);
    } finally {
      if (in != null) {
        in.close();
      }
    }
  }
}
