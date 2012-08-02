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

import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class JSONUtils {

  public static boolean arrayContains(JSONArray array, String value) throws JSONException {
    for ( int i = 0; i < array.length(); i++ ) {
      String s = array.getString(i);
      if ( s.equals(value)) {
        return true;
      }
    }
    return false;
  }

  public static void stripJCRNodes(JSONObject object) throws JSONException {
    Iterator<String> keys = object.keys();
    List<String> toRemove = new ArrayList<String>();
    while (keys.hasNext()) {
      String key = keys.next();
      Object child = object.get(key);
      if (child instanceof JSONObject) {
        stripJCRNodes((JSONObject) child);
      } else if (child instanceof JSONArray) {
        JSONArray array = (JSONArray) child;
        for (int i = 0; i < array.length(); i++) {
          Object element = array.get(i);
          if (element instanceof JSONObject) {
            stripJCRNodes((JSONObject) element);
          }
        }
      } else {
        if (JcrUtils.isJCRProperty(key)) {
          toRemove.add(key);
        }
      }
    }
    for (String key : toRemove) {
      object.remove(key);
    }
  }

  /**
   * Given a JSONArray, extract its array elements into a Java array. All elements of the
   * array will be recursively converted to Java objects if they are JSON objects as well.
   * 
   * @param array
   * @return
   * @throws JSONException
   */
  public static Object[] toArray(JSONArray array) throws JSONException {
    if (array == null)
      return null;

    int length = array.length();
    if (length == 0)
      return new Object[0];
    
    Object[] result = new Object[length];
    for (int i = 0; i < length; i++) {
      result[i] = toJavaObject(array.get(i));
    }
    
    return result;
  }
  
  /**
   * Given a JSONObject, convert it into a java Map. All elements of the array will be
   * recursively converted to Java objects if they are JSON objects as well.
   * 
   * @param json
   * @return
   * @throws JSONException
   */
  public static Map<String, Object> toMap(JSONObject json) throws JSONException {
    if (json == null)
      return null;
    
    Map<String, Object> result = new HashMap<String, Object>();
    Iterator<String> i = json.keys();
    while (i.hasNext()) {
      String key = i.next();
      result.put(key, toJavaObject(json.get(key)));
    }
    return result;
  }
  
  /**
   * Given an arbitrary object, if it is JSON, convert it to its associated Java representation.
   * <p>
   * For a JSONObject, it will be converted to a Map<String, Object>. For a JSONArray, it will be
   * converted into an Object[]
   * </p>
   * @param maybeJson
   * @return
   * @throws JSONException
   */
  private static Object toJavaObject(Object maybeJson) throws JSONException {
    if (maybeJson == null)
      return null;
    if (maybeJson instanceof JSONObject) {
      return toMap((JSONObject) maybeJson);
    } else if (maybeJson instanceof JSONArray) {
      return toArray((JSONArray) maybeJson);
    } else {
      return maybeJson;
    }
  }
}
