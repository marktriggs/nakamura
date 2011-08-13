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
package org.sakaiproject.nakamura.migratetov1;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.ImmutableMap.Builder;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Bundle;

import java.lang.reflect.Field;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.commons.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.base.util.AccessControlUtil;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AclModification.Operation;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.cluster.ClusterTrackingService;

import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import javax.servlet.ServletException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import javax.jcr.Binary;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;

import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.apache.sling.jcr.resource.JcrResourceConstants;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.sakaiproject.nakamura.api.lite.Configuration;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;

// Forbidden classes!
import org.sakaiproject.nakamura.lite.RepositoryImpl;
import org.sakaiproject.nakamura.lite.OSGiStoreListener;
import org.sakaiproject.nakamura.lite.CachingManager;
import org.sakaiproject.nakamura.lite.storage.StorageClient;
import org.sakaiproject.nakamura.lite.storage.DisposableIterator;
import org.sakaiproject.nakamura.lite.storage.StorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClientPool;
import org.sakaiproject.nakamura.lite.storage.jdbc.JDBCStorageClient;
import org.sakaiproject.nakamura.lite.types.Types;

import java.util.concurrent.atomic.AtomicBoolean;


@SlingServlet(methods = "GET", paths = "/system/migratetov1", generateComponent=true)
public class Migrate extends SlingSafeMethodsServlet {
  private Logger LOGGER = LoggerFactory.getLogger(Migrate.class);

  @Reference
  private Repository targetRepository;

  @Reference
  StorageClientPool targetConnectionPool;

  @Reference
  ClusterTrackingService clusterTrackingService;

  String sourceSlingDir = "sling-migrateme";
  String sourceStoreDir = "store-migrateme";

  private RepositoryImpl sourceRepository;

  private Configuration configuration;
  private JDBCStorageClientPool sourceConnectionPool;

  private Map sharedCache;

  // The source repository
  Session sourceSession;
  AuthorizableManager sourceAM;
  ContentManager sourceCM;
  AccessControlManager sourceACL;

  // The destination repository
  Session targetSession;
  AuthorizableManager targetAM;
  ContentManager targetCM;
  AccessControlManager targetACL;

  // Dashboard bits
  AtomicBoolean migrationRunning = new AtomicBoolean(false);
  volatile long usersCreated = 0;
  volatile long usersMigrated = 0;
  volatile long pooledContentMigrated = 0;
  volatile long groupsMigrated = 0;
  volatile String migrationStatus = "";
  volatile Date migrationStartTime = null;
  volatile Date migrationFinishTime = null;


  private Set<String> includedGroups = new HashSet<String>();;


  private Map<String,Object> mapOf(Object ... stuff)
  {
    Map<String,Object> result = new HashMap<String,Object>();

    for (int i = 0; i < stuff.length; i += 2) {
      result.put((String)stuff[i], (Object)stuff[i + 1]);
    }

    return result;
  }


  private String getParam(SlingHttpServletRequest request, String name, String defaultValue)
  {
    String param = request.getParameter(name);

    return (param != null) ? param : defaultValue;
  }


  private void connectToSourceRepository(SlingHttpServletRequest request)
    throws Exception
  {
    if (!new File(sourceSlingDir).exists()) {
      throw new RuntimeException ("Couldn't open source sling directory: " + sourceSlingDir);
    }

    if (!new File(sourceStoreDir).exists()) {
      throw new RuntimeException ("Couldn't open source store directory: " + sourceStoreDir);
    }



    sourceRepository = new RepositoryImpl();

    Field configField = RepositoryImpl.class.getDeclaredField("configuration");
    configField.setAccessible(true);
    configuration = (Configuration)configField.get(targetRepository);

    sourceConnectionPool = new JDBCStorageClientPool();
    ImmutableMap.Builder<String, Object> connectionProps = ImmutableMap.builder();
    connectionProps.put("jdbc-url", getParam(request, "jdbc-url", "jdbc:derby:" + sourceSlingDir + "/sparsemap/db"));
    connectionProps.put("jdbc-driver", getParam(request, "jdbc-driver", "org.apache.derby.jdbc.EmbeddedDriver"));
    connectionProps.put("store-base-dir", sourceStoreDir);
    connectionProps.put("username", getParam(request, "username", "sa"));
    connectionProps.put("password", getParam(request, "password", ""));
    connectionProps.put("max-wait", 10);
    connectionProps.put("max-idle", 5);
    connectionProps.put("test-on-borrow", true);
    connectionProps.put("test-on-return", true);
    connectionProps.put("time-between-eviction-run", 60000);
    connectionProps.put("num-tests-per-eviction-run", 1000);
    connectionProps.put("min-evictable-idle-time-millis", 10000);
    connectionProps.put("test-while-idle", false);
    connectionProps.put("when-exhausted-action", "grow");
    connectionProps.put("long-string-size", 0);
    connectionProps.put("org.sakaiproject.nakamura.api.lite.Configuration", configuration);
    sourceConnectionPool.activate(connectionProps.build());

    sourceRepository.setConfiguration(configuration);
    sourceRepository.setConnectionPool(sourceConnectionPool);
    sourceRepository.setStorageListener(new OSGiStoreListener());


    sourceSession = sourceRepository.loginAdministrative();
    sourceAM = sourceSession.getAuthorizableManager();
    sourceCM = sourceSession.getContentManager();
    sourceACL = sourceSession.getAccessControlManager();

    targetSession = targetRepository.loginAdministrative();
    targetAM = targetSession.getAuthorizableManager();
    targetCM = targetSession.getContentManager();
    targetACL = targetSession.getAccessControlManager();
    targetCM.setMaintanenceMode(true);

    Field cacheField = CachingManager.class.getDeclaredField("sharedCache");
    cacheField.setAccessible(true);
    sharedCache = (Map)cacheField.get(targetCM);
  }


  private void loadIncludedGroups()
  {
  }

  private boolean isGroupIncluded(String group)
  {
    return (includedGroups == null ||
            group.startsWith("g-contacts") ||
            includedGroups.contains(group) ||
            includedGroups.contains(group + "-managers"));
  }


  private boolean isBoringUser(String userId) {
    return (userId.equals(User.ADMIN_USER) ||
            userId.equals(User.ANON_USER) ||
            userId.equals(User.SYSTEM_USER));
  }


  private void importJson(String path, String jsonString) throws Exception
  {
    JSONObject json = new JSONObject(jsonString);
    new LiteJsonImporter().importContent(targetCM, json, path, true, true, true, targetACL);
  }


  private Content makeContent(String path, Map<String,Object> properties) throws Exception
  {
    Map<String,Object> props = new HashMap<String,Object>(properties);
    props.remove("_id");
    props.remove("_versionHistoryId");
    props.remove("_readOnly");

    return new Content(path, props);
  }

  private Content makeContent(Content content) throws Exception
  {
    return makeContent(content.getPath(), content.getProperties());
  }


  private void migrateContentACL(Content content) throws Exception
  {
    migrateACL("n", "CO", content.getPath(), true);
  }


  private void setSparseMapValue(String keySpace,
                                 String columnFamily,
                                 String mapKey,
                                 String property,
                                 Object newValue) throws Exception
  {
    StorageClient storageClient = targetConnectionPool.getClient();

    Map<String,Object> props = storageClient.get("n", "cn", mapKey);

    props.put(property, newValue);

    storageClient.insert("n", "cn", mapKey, props, false);

    // Invalidate the content manager's cache to avoid serving back stale data.
    sharedCache.clear();
  }


  private void saveVersionWithTimestamp(String path, Object timestamp) throws Exception
  {
      String newVersionId = targetCM.saveVersion(path);
      
      // We've now got a new version, but its version ID will have been
      // clobbered with the current timestamp.  Set it back.
      setSparseMapValue("n", "cn", (String)targetCM.get(path).getProperty("_versionHistoryId"),
                        newVersionId, timestamp);
      setSparseMapValue("n", "cn", newVersionId, "_versionNumber", timestamp);
  }


  private boolean migrateContent(Content content) throws Exception
  {
    if (content == null) {
      // We may get a null content object when an item is deleted but not
      // properly cleaned up from the repo.  I think I've seen this when
      // deleting content objects with multiple versions...
      return false;
    }

    LOGGER.info("Migrating content object: {}", content);

    // Migrate any versions of this object prior to the latest version
    int versionCount = 0;
    for (String versionId : sourceCM.getVersionHistory(content.getPath())) {
      LOGGER.info("Migrating version {} of content object {}", versionId, content.getPath());

      Content version = sourceCM.getVersion(content.getPath(), versionId);

      Map<String,Object> props = new HashMap<String,Object>(version.getOriginalProperties());

      // props.remove("_readOnly");
      version = makeContent(version.getPath(), props);

      versionCount++;

      targetCM.update(makeContent(version));

      InputStream versionInputStream = sourceCM.getVersionInputStream(content.getPath(), versionId);

      if (versionInputStream != null) {
        targetCM.writeBody(content.getPath(), versionInputStream);
        versionInputStream.close();
      }

      saveVersionWithTimestamp(content.getPath(), version.getProperty("_versionNumber"));
    }

    targetCM.update(makeContent(content));
    if (sourceCM.hasBody(content.getPath(), null)) {
      InputStream is = sourceCM.getInputStream(content.getPath());
      targetCM.writeBody(content.getPath(), is);
      is.close();
    }

    migrateContentACL(content);

    // migrate any comments
    String commentsPath = content.getPath() + "/comments";
    Content sourceComments = sourceCM.get(commentsPath);
    if (sourceComments != null) {
      Content targetComments = new Content(commentsPath, new HashMap<String, Object>());
      targetCM.update(targetComments);
      allChildren(sourceCM.get(commentsPath), new ContentVisitor() {
          public void visit(Content obj) throws Exception {
            targetCM.update(makeContent(obj.getPath(), obj.getOriginalProperties()));
          }
        });
    }
    
    return true;
  }


  interface ContentVisitor {
    public void visit(Content obj) throws Exception;
  }


  private void allChildrenInternal(ContentVisitor visitor, LinkedList<Content> queue) throws Exception
  {
    while (!queue.isEmpty()) {
      Content obj = queue.poll();
      visitor.visit(obj);

      Iterator<Content> it = obj.listChildren().iterator();
      while (it.hasNext()) {
        queue.offer(it.next());
      }
    }
  }

  
  private void allChildren(Content content, ContentVisitor visitor) throws Exception
  {
    if (content != null) {
      LinkedList<Content> queue = new LinkedList<Content>();
      queue.add(content);

      allChildrenInternal(visitor, queue);
    }
  }


  private void syncMembers(Authorizable sourceGroup, Authorizable targetGroup) throws Exception
  {
    for (String userId : ((Group)sourceGroup).getMembers()) {
      ((Group)targetGroup).addMember(userId);
    }

    targetAM.updateAuthorizable(targetGroup);
  }


  private String generateWidgetId()
  {
    return ("id" + Math.round(Math.random() * 10000000));
  }


  private String readResource(String name) throws Exception
  {
    InputStream is = this.getClass().getClassLoader().getResourceAsStream(name);
    byte buf[] = new byte[4096];

    StringBuilder sb = new StringBuilder();
    while (true) {
      int count = is.read(buf);

      if (count < 0) {
        break;
      }

      sb.append(new String(buf, 0, count));
    }

    return sb.toString();
  }


  private String fillOutTemplate(String templatePath) throws Exception
  {
    String s = readResource(templatePath);

    for (int i = 0; i < 1000; i++) {
      s = s.replaceAll(("__ID_" + i + "__"),
                       generateWidgetId());
    }

    return s;
  }


  private boolean createUser(Authorizable user) throws Exception
  {
    String userId = user.getId();
    String userPath = "a:" + userId;

    LOGGER.info("Creating user: {} ({})", userId, userPath);

    Map<String,Object> props = new HashMap<String,Object>(user.getOriginalProperties());

    List<String> filtered = new ArrayList<String>();

    if (props.get("principals") != null) {
      for (String membership : ((String)props.get("principals")).split(";")) {

        // Only migrate groups that we've explicitly included
        if (isGroupIncluded(membership)) {
          if (membership.endsWith("-managers")) {
            // Now "-manager"
            filtered.add(membership.substring(0, membership.length() - 1));
          } else {
            filtered.add(membership);
          }
        }
      }
    }

    props.put("principals", StringUtils.join(filtered, ";"));

    return targetAM.createUser(userId, userId, "testuser", props);
  }


  // Bits common to users and groups
  private void migrateAuthorizableHome(String path) throws Exception
  {
    // home
    migrateContent(sourceCM.get(path));

    migrateContentTree(sourceCM.get(path + "/calendar"),
                       path);

    migrateContentTree(sourceCM.get(path + "/contacts"),
                       path);

    migrateContentTree(sourceCM.get(path + "/message"),
                       path);

    // profiles and authprofiles
    migrateContentTree(sourceCM.get(path + "/public"),
                       path);
  }


  private void migrateUser(Authorizable user) throws Exception
  {
    final String userId = user.getId();
    final String userPath = "a:" + userId;
    final String contactsGroup = "g-contacts-" + userId;

    LOGGER.info("Migrating user: {}", user);

    migrateAuthorizableACL(user);

    migrateAuthorizableHome(userPath);

    // Authprofile nodes
    allChildren(targetCM.get(userPath + "/public/authprofile"), new ContentVisitor() {
        public void visit(Content obj) throws Exception {
          List<AclModification> acls = new ArrayList<AclModification>();
     
          if (obj.getPath().matches("(.*authprofile$|.*authprofile/basic.*)")) {
            // Set basic profile information readable to logged in users
            AclModification.addAcl(false,
                                   Permissions.CAN_READ,
                                   User.ANON_USER,
                                   acls);
            AclModification.addAcl(true,
                                   Permissions.CAN_READ,
                                   org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE,
                                   acls);
          } else {
            // And all others to contacts only
            AclModification.addAcl(false,
                                   Permissions.CAN_READ,
                                   User.ANON_USER,
                                   acls);
            AclModification.addAcl(false,
                                   Permissions.CAN_READ,
                                   org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE,
                                   acls);
            AclModification.addAcl(true, Permissions.CAN_READ, contactsGroup, acls);
          }

          AclModification.addAcl(true, Permissions.CAN_ANYTHING, userId, acls);
          targetACL.setAcl(Security.ZONE_CONTENT, obj.getPath(), acls.toArray(new AclModification[acls.size()]));
        }
      });

    // contact groups
    targetAM.createGroup(contactsGroup, contactsGroup, null);
    Authorizable sourceGroup = sourceAM.findAuthorizable(contactsGroup);
    Authorizable targetGroup = targetAM.findAuthorizable(contactsGroup);
    if (sourceGroup != null && targetGroup != null) {
      syncMembers(sourceGroup, targetGroup);
    }

    // User pubspace
    importJson(userPath + "/public/pubspace", fillOutTemplate("user-pubspace-template.json"));

    // User privspace
    importJson(userPath + "/private/privspace", fillOutTemplate("user-privspace-template.json"));
  }


  private String getHashAlgorithm()
    throws Exception
  {
    StorageClient storageClient = targetConnectionPool.getClient();
    Field f = storageClient.getClass().getDeclaredField("rowidHash");
    f.setAccessible(true);

    return (String)f.get(storageClient);
  }


  private String rowHash(String keySpace, String columnFamily, String key)
    throws Exception
  {
    return StorageClientUtils.encode(MessageDigest.getInstance(getHashAlgorithm()).digest
                                     ((keySpace + ":" + columnFamily + ":" + key).getBytes("UTF-8")));
  }


  private void migrateRows(Connection source, Connection dest, String table, String rid, boolean force)
    throws Exception
  {
    PreparedStatement sourceRows = source.prepareStatement("select rid, b from " + table + " where rid = ?");
    PreparedStatement delete = dest.prepareStatement("delete from " + table + " where rid = ?");
    PreparedStatement insert = dest.prepareStatement("insert into " + table + " (rid, b) values (?, ?)");

    sourceRows.setString(1, rid);
    ResultSet rs = sourceRows.executeQuery();

    while (rs.next()) {
      delete.clearParameters(); delete.clearWarnings();
      insert.clearParameters(); insert.clearWarnings();

      LOGGER.info ("Migrating row {} with value {}", rs.getString(1), rs.getBytes(2));

      if (force) {
        delete.setString(1, rs.getString(1));
        delete.execute();
      }

      insert.setString(1, rs.getString(1));
      insert.setBytes(2, rs.getBytes(2));

      // FIXME: error checking would be nice
      insert.execute();
    }

    insert.close();
    delete.close();
    sourceRows.close();
  }

  // Hm.  Actually this throws dupe key errors and looks a bit like we don't
  // have to do it...
  private void migrateAuthorizableACL(Authorizable authorizable) throws Exception
  {
    migrateACL("n", "AU", authorizable.getId(), true);
  }

  
  private InputStream rewriteHashAndGroupNames(InputStream in,
                                               String old_rid,
                                               String new_rid,
                                               String columnFamily,
                                               String authId)
    throws Exception
  {
    Map<String, Object> map = new HashMap<String, Object>();

    Types.loadFromStream(old_rid, map, in, columnFamily);

    Map<String,Object> filtered = new HashMap<String,Object>();

    for (String key : map.keySet()) {
      // *sigh* hacky.  The manager group got renamed from -managers to -manager
      // *so the existing ACLs will be using the wrong name.
      filtered.put(key.replaceAll("-managers@", "-manager@"),
                   map.get(key));

      // ... and we now have a member group that kicks in where the group
      // membership previously would have.  Add the members to the ACL list as
      // well.
      if (key.startsWith(authId + "@")) {
        filtered.put(key.replaceAll(authId + "@", authId + "-member@"),
                     map.get(key));
      }
    }

    return Types.storeMapToStream(new_rid, filtered, columnFamily);
  }


  private void migrateACL(String keySpace, String columnFamily, String id, boolean force)
    throws Exception
  {
    String old_rid;

    LOGGER.info("MIGRATING ACL FOR: {}", id);

    if (id != null && id.startsWith("/")) {
      old_rid = rowHash(keySpace, "ac", columnFamily + id);
    } else {
      old_rid = rowHash(keySpace, "ac", columnFamily + "/" + id);
    }

    String new_rid = rowHash(keySpace, "ac", columnFamily + ";" + id);

    Connection source = ((JDBCStorageClientPool)sourceConnectionPool).getConnection();
    Connection dest = ((JDBCStorageClientPool)targetConnectionPool).getConnection();

    PreparedStatement sourceRows = source.prepareStatement("select rid, b from AC_CSS_B where rid = ?");
    PreparedStatement delete = dest.prepareStatement("delete from AC_CSS_B where rid = ?");
    PreparedStatement insert = dest.prepareStatement("insert into AC_CSS_B (rid, b) values (?, ?)");

    sourceRows.setString(1, old_rid);
    ResultSet rs = sourceRows.executeQuery();

    while (rs.next()) {
      delete.clearParameters(); delete.clearWarnings();
      insert.clearParameters(); insert.clearWarnings();

      LOGGER.info ("Migrating row {} with value {}", rs.getString(1), rs.getBytes(2));

      if (force) {
        delete.setString(1, new_rid);
        delete.execute();
      }

      insert.setString(1, new_rid);
      InputStream rewritten = rewriteHashAndGroupNames(rs.getBinaryStream(2), old_rid, new_rid, "ac", id);
      insert.setBinaryStream(2, rewritten, rewritten.available());

      // FIXME: error checking would be nice
      insert.execute();
    }

    insert.close();
    delete.close();
    sourceRows.close();
  }


  private List<String> createAllUsers() throws Exception
  {
    List<String> createdUsers = new ArrayList<String>();

    int page = 0;

    while (true) {
      Iterator<Authorizable> it = sourceAM.findAuthorizable("_page", String.valueOf(page),
                                                            User.class);
      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Authorizable user = it.next();

        if (!isBoringUser(user.getId())) {
          if (createUser(user)) {
            createdUsers.add(user.getId());
            usersCreated++;
          }
        }
      }

      if (processed == 0) {
        break;
      }

      page++;
    }

    return createdUsers;
  }


  private void migrateUsers(List<String> createdUsers) throws Exception
  {
    for (String userId : createdUsers) {
      Authorizable user = targetAM.findAuthorizable(userId);

      if (user != null) {
        migrateUser(user);
        usersMigrated++;

      }
    }
  }


  private void migratePooledContent() throws Exception
  {
    LOGGER.info("Migrating pooled content");

    int page = 0;

    while (true) {
      LOGGER.info("** Migrating content page: " + page);

      JDBCStorageClient storageClient = (JDBCStorageClient)sourceConnectionPool.getClient();
      DisposableIterator<Map<String,Object>> it = storageClient.find("n", "cn",
                                                                             mapOf("sling:resourceType", (Object)"sakai/pooled-content",
                                                                                   "_page", String.valueOf(page)));

      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Map<String,Object> contentMap = it.next();

        if ("Y".equals((String)contentMap.get("_readOnly"))) {
          // Content objects that are read-only will be revisions of some other
          // content object, and we'll get them when migrating that.
          LOGGER.info("Skipped read-only object: {}", contentMap);
          continue;
        }

        try {
          if (targetCM.get((String)contentMap.get("_path")) == null) {
            migrateContent(sourceCM.get((String)contentMap.get("_path")));
            pooledContentMigrated++;
          }
        } catch (Exception e) {
          LOGGER.warn("Hit problems migrating: {}", contentMap);
          e.printStackTrace();
        }
      }

      it.close();

      if (processed == 0) {
        break;
      }

      page++;
    }

    LOGGER.info("DONE: Migrating pooled content");
  }


  private void setWorldReadableGroupWritable(String poolId, Authorizable group, String objectType)
    throws Exception
  {
    setWorldReadable(poolId);

    List<AclModification> acls = new ArrayList<AclModification>();

    AclModification.addAcl(true, Permissions.CAN_READ, group.getId() + "-member", acls);
    AclModification.addAcl(true, Permissions.ALL, group.getId() + "-manager", acls);

    targetACL.setAcl(objectType, poolId, acls.toArray(new AclModification[acls.size()]));
  }


  private void setWorldReadable(String poolId) throws Exception
  {
    List<AclModification> acls = new ArrayList<AclModification>();
     
    AclModification.addAcl(true, Permissions.CAN_READ, User.ANON_USER, acls);
    AclModification.addAcl(true, Permissions.CAN_READ, org.sakaiproject.nakamura.api.lite.authorizable.Group.EVERYONE, acls);

    targetACL.setAcl(Security.ZONE_CONTENT, poolId, acls.toArray(new AclModification[acls.size()]));
  }


  private void buildDocstructure(Authorizable group) throws Exception
  {
    String libraryPoolId = clusterTrackingService.getClusterUniqueId();
    String participantsPoolId = clusterTrackingService.getClusterUniqueId();

    importJson(libraryPoolId,
               readResource("group-library.json").replaceAll("__GROUP__", group.getId()));
    LOGGER.info("... done.\n");

    importJson(participantsPoolId,
               readResource("group-participants.json").replaceAll("__GROUP__", group.getId()));

    setWorldReadable(libraryPoolId);
    setWorldReadable(participantsPoolId);

    importJson("a:" + group.getId() + "/docstructure",
               (readResource("group-docstructure.json")
                .replaceAll("__LIBRARY_POOLID__", libraryPoolId)
                .replaceAll("__PARTICIPANTS_POOLID__", participantsPoolId)));
  }


  private String getMembersString(String groupName, String[] members)
  {
    List<String> filtered = new ArrayList<String>();

    if (members != null) {
      for (String member : members) {
        if (!member.equals(groupName + "-managers")) {
          filtered.add(member);
        }
      }
    }

    return StringUtils.join(filtered, ";");
  }


  private void addToDocstructure(Authorizable group, String pageId, String title, String poolId)
    throws Exception
  {
    String groupId = group.getId();
    String groupPath = "a:" + groupId;
    Content content = targetCM.get(groupPath + "/docstructure");
    JSONObject structure = new JSONObject((String)content.getProperty("structure0"));

    int maxOrder = -1;
    for (Iterator<String> k = structure.keys(); k.hasNext();) {
      int order = structure.getJSONObject(k.next()).getInt("_order");
      if (order > maxOrder) {
        maxOrder = order;
      }
    }

    JSONObject newNode = new JSONObject();
    newNode.put("_title", title);
    newNode.put("_order", maxOrder + 1);
    newNode.put("_nonEditable", false);
    newNode.put("_pid", poolId);
    newNode.put("_view", "[\"everyone\",\"anonymous\",\"-member\"]");
    newNode.put("_edit", "[\"-manager\"]");

    structure.put(pageId, newNode);

    content.setProperty("structure0", structure.toString());

    targetCM.update(content);
  }


  private String getPageContent(Content content) throws Exception
  {
    String pageContent = null;

    for (Content obj : content.listChildren()) {
      if ("sakai/pagecontent".equals(obj.getProperty("sling:resourceType"))) {
        pageContent = (String)obj.getProperty("sakai:pagecontent");
      }
    }

    if (pageContent == null) {
      LOGGER.error("Couldn't find page content for: {}", content);
      return "";
    }

    return pageContent;
  }


  private void migrateContentTree(Content rootNode, String newRoot)
    throws Exception
  {
    if (rootNode == null) {
      LOGGER.warn("I wanted to migrate a tree to '{}' but the object was null.", newRoot);
      return;
    }

    int lastSlash = rootNode.getPath().lastIndexOf("/");
    String oldRoot = rootNode.getPath().substring(0, lastSlash + 1);
    String nodeName = rootNode.getPath().substring(lastSlash + 1);
    String newPath = newRoot + "/" + nodeName;

    targetCM.update(makeContent(newPath, rootNode.getProperties()));

    for (Content child : rootNode.listChildren()) {
      migrateContentTree(child, newPath);
    }
  }


  private void migrateWidgetData(Authorizable group, final String poolId)
    throws Exception
  {
    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    allChildren(sourceCM.get(groupPath + "/pages/_widgets"), new ContentVisitor() {
        public void visit(Content obj) throws Exception {
          if (obj.getPath().matches("^.*/id[0-9]+$")) {
            // THINKE: this is going to link every embedded piece of content
            // for a group to every page in the group.  Does this matter?
            // Embedded content used to all get put into a shared pool under
            // the embedcontent widget, but now they're added to a node
            // under the page's pool id.  Not sure how to figure out which
            // embedded content belongs to which page without parsing the
            // page source...
            migrateContentTree(obj, poolId);
          }
        }
      });
  }

  private void migratePage(Authorizable group, Content content) throws Exception
  {
    String path =  content.getPath();
    String pageId = path.substring(path.lastIndexOf("/") + 1);
    String creator = (String)content.getProperty("_createdBy");
    String contentId = generateWidgetId();
    String poolId = clusterTrackingService.getClusterUniqueId();

    LOGGER.info("Migrating page with PoolId: {}", poolId);

    String structure = "{\"__PAGE_ID__\":{\"_title\":\"__PAGE_TITLE__\",\"_order\":0,\"_ref\":\"__CONTENT_ID__\",\"_nonEditable\":false,\"main\":{\"_title\":\"__PAGE_TITLE__\",\"_order\":0,\"_ref\":\"__CONTENT_ID__\",\"_nonEditable\":false}}}";
    structure = (structure
                 .replaceAll("__PAGE_ID__", pageId)
                 .replaceAll("__PAGE_TITLE__", (String)content.getProperty("pageTitle"))
                 .replaceAll("__CONTENT_ID__", contentId));

    // THINKE: does "permissions" need to be cleverer?
    targetCM.update(makeContent(poolId,
                                mapOf("sakai:copyright", "creativecommons",
                                      "sakai:custom-mimetype", "x-sakai/document",
                                      "sakai:description", "",
                                      "sakai:permissions"," public",
                                      "sakai:pool-content-created-for", creator,
                                      "sakai:pooled-content-file-name", pageId,
                                      "sakai:pooled-content-manager", new String[] { group.getId() + "-manager", creator },
                                      "sakai:pooled-content-viewer", new String[] { "anonymous", "everyone", group.getId() + "-member" },
                                      "sling:resourceType", "sakai/pooled-content",
                                      "structure0", structure)));

    setWorldReadableGroupWritable(poolId, group, Security.ZONE_CONTENT);

    String pageContent = getPageContent(content);

    if (pageContent != null) {
      targetCM.update(makeContent(poolId + "/" + contentId,
                                  mapOf("page", pageContent)));
    } else {
      LOGGER.warn("Couldn't find page content for {} (group: {})", content, group);
    }

    addToDocstructure(group, pageId, (String)content.getProperty("pageTitle"), poolId);

    migrateWidgetData(group, poolId);
  }


  private void migratePages(final Authorizable group) throws Exception
  {
    LOGGER.info ("Migrating pages for group: {}", group);

    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    allChildren(sourceCM.get(groupPath), new ContentVisitor() {
        public void visit(Content obj) throws Exception {
          if ("sakai/page".equals(obj.getProperty("sling:resourceType")) &&
              !obj.getPath().matches("^.*/(about-this-group|group-dashboard)$")) {
            LOGGER.info ("MIGRATING PAGE: {}", obj);
            migratePage(group, obj);
            LOGGER.info ("DONE\n\n");
          }
        }
      });
  }


  private void createStandardGroup(String groupId, String description, Map<String,Object> properties)
    throws Exception
  {
    String groupPath = "a:" + groupId;

    targetAM.createGroup(groupId, description, properties);

    targetCM.update(makeContent(groupPath, mapOf("sling:resourceType","sakai/group-home")));

    targetCM.update(makeContent(groupPath + "/calendar", mapOf("sling:resourceType","sakai/calendar")));

    targetCM.update(makeContent(groupPath + "/contacts", mapOf("sling:resourceType","sparse/contactstore")));

    targetCM.update(makeContent(groupPath + "/joinrequests", mapOf("sling:resourceType","sparse/joinrequests")));

    // Create a public authprofile too
    targetCM.update(makeContent(groupPath + "/public/authprofile",
                                mapOf("sling:resourceType", "sakai/group-profile",
                                      "homePath", "/~" + groupId,
                                      "sakai:group-id", groupId,
                                      "sakai:group-joinable", properties.get("sakai:group-joinable"),
                                      "sakai:group-title", description,
                                      "sakai:group-visible", properties.get("sakai:group-visible"))));
  }


  private void migrateGroup(Authorizable group) throws Exception
  {
    LOGGER.info ("Migrating group: {}", group);

    String groupId = group.getId();
    String groupPath = "a:" + groupId;

    Map<String,Object> props = new HashMap<String,Object>(group.getOriginalProperties());


    String[] requiredProperties = new String[] {"members", "rep:group-managers",
                                                "sakai:group-joinable",
                                                "sakai:group-visible"};

    for (String p : requiredProperties) {
      if (props.get(p) == null) {
        LOGGER.error("\n***************************\n" +
                     "Missing property for group: " + group + " - " + p + ". Skipped!" +
                     "\n***************************\n");
        return;
      }
    }


    props.remove("sakai:managers-group");

    props.put("members", getMembersString(groupId, ((String)props.get("members")).split(";")));

    props.put("sakai:roles", "[{\"id\":\"member\",\"roleTitle\":\"Members\",\"title\":\"Member\",\"allowManage\":false},{\"id\":\"manager\",\"roleTitle\":\"Managers\",\"title\":\"Manager\",\"allowManage\":true}]");

    List<String> managers = new ArrayList<String>();
    for (String elt : (String[])props.get("rep:group-managers")) {
      if ((groupId + "-managers").equals(elt)) {
        managers.add(groupId + "-manager");
      } else {
        managers.add(elt);
      }
    }

    props.put("rep:group-managers", managers.toArray(new String[managers.size()]));

    props.put("sakai:category", "group");
    props.put("sakai:templateid", "simplegroup");
    props.put("sakai:joinRole", "member");
    props.put("membershipsCount", 0);
    props.put("membersCount", 0);
    props.put("contentCount", 0);

    // Set the rep:group-viewers based on the group's visibility
    String visibility = (String)props.get("sakai:group-visible");

    if (visibility != null) {
      if (visibility.equals("logged-in-only")) {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     groupId});
      } else if (visibility.equals("members-only")) {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     groupId});
      } else {
        props.put("rep:group-viewers", new String[] {group + "-manager",
                                                     group + "-member",
                                                     "everyone",
                                                     "anonymous",
                                                     groupId});
      }
    }

    targetAM.createGroup(groupId, (String)group.getProperty("sakai:group-title"), props);


    createStandardGroup(groupId + "-member",
                        String.format("%s (Members)", groupId),
                        mapOf("type", "g",
                              "principals", groupId,
                              "rep:group-viewers", props.get("rep:group-viewers"),
                              "rep:group-managers", props.get("rep:group-managers"),
                              "sakai:group-joinable", props.get("sakai:group-joinable"),
                              "sakai:group-id", groupId + "-member",
                              "sakai:group-title", String.format("%s (Members)", groupId),
                              "sakai:pseudogroupparent", groupId,
                              "contentCount", 0,
                              "sakai:pseudoGroup", true,
                              "name", groupId + "-member",
                              "email", "unknown",
                              "firstName", "unknown",
                              "lastName", "unknown",
                              "membershipsCount", 1,
                              "sakai:excludeSearch", true,
                              "sakai:group-visible", props.get("sakai:group-visible"),
                              "members", getMembersString(groupId, ((Group)group).getMembers())));
    setWorldReadableGroupWritable(groupPath + "-member", group, Security.ZONE_CONTENT);
    setWorldReadableGroupWritable(groupId + "-member", group, Security.ZONE_AUTHORIZABLES);


    // Group managers
    createStandardGroup(groupId + "-manager",
                        String.format("%s (Managers)", groupId),
                        mapOf("type", "g",
                              "principals", groupId,
                              "rep:group-viewers", props.get("rep:group-viewers"),
                              "rep:group-managers", props.get("rep:group-managers"),
                              "sakai:group-joinable", props.get("sakai:group-joinable"),
                              "sakai:group-id", groupId + "-manager",
                              "sakai:group-title", String.format("%s (Managers)", groupId),
                              "sakai:pseudogroupparent", groupId,
                              "contentCount", 0,
                              "sakai:pseudoGroup", true,
                              "name", groupId + "-manager",
                              "email", "unknown",
                              "firstName", "unknown",
                              "lastName", "unknown",
                              "membershipsCount", 1,
                              "sakai:excludeSearch", true,
                              "sakai:group-visible", props.get("sakai:group-visible"),
                              "members", getMembersString(groupId, ((Group)sourceAM.findAuthorizable(groupId + "-managers")).getMembers())));

    setWorldReadableGroupWritable(groupPath + "-manager", group, Security.ZONE_CONTENT);
    setWorldReadableGroupWritable(groupId + "-manager", group, Security.ZONE_AUTHORIZABLES);

    migrateAuthorizableHome(groupPath);


    LOGGER.info("Building docstructure...");
    buildDocstructure(group);


    LOGGER.info("Migrating group pages...");
    migratePages(group);

    migrateAuthorizableACL(group);

    // tickle the indexer
    targetAM.updateAuthorizable(targetAM.findAuthorizable(groupId));
  }


  private void migrateAllGroups() throws Exception
  {
    LOGGER.info("Migrating Groups");

    int page = 0;

    while (true) {
      LOGGER.info("** Migrating group page: " + page);

      JDBCStorageClient storageClient = (JDBCStorageClient)sourceConnectionPool.getClient();
      DisposableIterator<Map<String,Object>> it = storageClient.find("n", "cn", mapOf("sling:resourceType", "sakai/group-home",
                                                                                      "_page", String.valueOf(page)));

      int processed = 0;

      while (it.hasNext()) {
        processed++;
        Map<String,Object> groupMap = it.next();

        String groupName = ((String)groupMap.get("_path")).split(":", 2)[1];

        if (includedGroups.contains(groupName)) {
          continue;
        }

        Authorizable group = sourceAM.findAuthorizable(groupName);

        if (targetAM.findAuthorizable(groupName) == null) {
          // targetAM.delete(groupName);
          // targetAM.delete(groupName + "-member");
          // targetAM.delete(groupName + "-manager");
          migrateGroup(group);
          groupsMigrated++;
        }
      }

      it.close();

      if (processed == 0) {
        break;
      }

      page++;
    }

    LOGGER.info("DONE: Migrating groups");
  }


  private void showDashboard(PrintWriter writer)
  {
    if (migrationRunning.get()) {
      writer.println("Migration started at: " + migrationStartTime);
      writer.println("");
      writer.println("Users created: " + usersCreated);
      writer.println("Users migrated: " + usersMigrated);
      writer.println("Pooled content objects migrated: " + pooledContentMigrated);
      writer.println("Groups migrated: " + groupsMigrated);
      writer.println("");
      writer.println("Current time: " + new Date());
      writer.println("");
      writer.println("Migration status: " + migrationStatus);
      writer.println("");
      writer.println("Mental health: Tattered    Mana: 91    Fatigue: 10");
      writer.println("");

      if (migrationFinishTime != null) {
        writer.println("Migration finished at: " + migrationFinishTime);
      }

    } else {
      writer.println("No migration is currently running!");
    }
  }


  protected void doGet(SlingHttpServletRequest request,
                       SlingHttpServletResponse response)
    throws ServletException, IOException
  {
    if (request.getParameter("run_migration") != null) {
      if (migrationRunning.getAndSet(true) == false) {
        migrationStartTime = new Date();

        try {
          LOGGER.info("Migrating!");

          migrationStatus = "Connecting to repository";
          connectToSourceRepository(request);

          loadIncludedGroups();


          // Annoyingly, all users need to exist before we can start linking them up
          // with connections, etc..

          migrationStatus = "Creating users";
          List<String> createdUsers = createAllUsers();

          migrationStatus = "Migrating groups";
          migrateAllGroups();

          migrationStatus = "Migrating users";
          migrateUsers(createdUsers);

          migrationStatus = "Migrating pooled content";
          migratePooledContent();

          migrationStatus = "Done!";

        } catch (Throwable e) {
          LOGGER.error("Caught a top-level error: {}", e);
          e.printStackTrace();

          migrationStatus = "FAILED: " + e;
        }

        migrationFinishTime = new Date();
      } else {
        response.getWriter().write("Migration already running!");
      }
    } else {
      showDashboard(response.getWriter());
    }
  }
}
