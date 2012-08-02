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

package org.sakaiproject.nakamura.api.http.cache;

import java.util.regex.Pattern;

public class CacheConfig {

  private int maxAge;

  private String path;

  private Pattern pattern;

  public CacheConfig(int maxAge, String path, Pattern pattern) {
    this.maxAge = maxAge;
    this.path = path;
    this.pattern = pattern;
  }

  public int getMaxAge() {
    return maxAge;
  }

  public String getPath() {
    return path;
  }

  public Pattern getPattern() {
    return pattern;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CacheConfig config = (CacheConfig) o;

    if (path != null ? !path.equals(config.path) : config.path != null) return false;
    if (pattern != null ? !pattern.equals(config.pattern) : config.pattern != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = path != null ? path.hashCode() : 0;
    result = 31 * result + (pattern != null ? pattern.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "CacheConfig{" +
        "maxAge=" + maxAge +
        ", path='" + path + '\'' +
        ", pattern=" + pattern +
        '}';
  }

}
