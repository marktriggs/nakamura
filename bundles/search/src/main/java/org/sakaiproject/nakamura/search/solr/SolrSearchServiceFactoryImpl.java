package org.sakaiproject.nakamura.search.solr;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.util.Version;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.schema.TextField;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.search.solr.Query;
import org.sakaiproject.nakamura.api.search.solr.Query.Type;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchException;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchResultSet;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchServiceFactory;
import org.sakaiproject.nakamura.api.search.solr.SolrSearchUtil;
import org.sakaiproject.nakamura.api.solr.SolrServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

@Component(immediate = true, metatype = true)
@Service
public class SolrSearchServiceFactoryImpl implements SolrSearchServiceFactory {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(SolrSearchServiceFactoryImpl.class);
  @Reference
  private SolrServerService solrSearchService;

  @Property(name = "defaultMaxResults", intValue = 100)
  private int defaultMaxResults = 100; // set to 100 to allow testing

  @Activate
  protected void activate(Map<?, ?> props) {
    defaultMaxResults = OsgiUtil.toInteger(props.get("defaultMaxResults"),
        defaultMaxResults);
  }

  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      Query query, boolean asAnon) throws SolrSearchException {
    try {
      SolrSearchResultSet rs = null;
      if (query.getType() == Type.SOLR) {
        rs = processSolrQuery(request, query, asAnon);
      } else if (query.getType() == Type.SPARSE) {
        rs = processSparseQuery(request, query, asAnon);
      }
      return rs;
    } catch (SolrServerException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new SolrSearchException(500, e.getMessage());
    } catch (ParseException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new SolrSearchException(500, e.getMessage());
    } catch (StorageClientException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new SolrSearchException(500, e.getMessage());
    } catch (AccessDeniedException e) {
      LOGGER.warn(e.getMessage(), e);
      throw new SolrSearchException(403, e.getMessage());
    }
  }

  public SolrSearchResultSet getSearchResultSet(SlingHttpServletRequest request,
      Query query) throws SolrSearchException {
    return getSearchResultSet(request, query, false);
  }

  /**
   * Process a query string to search using Solr.
   *
   * @param request
   * @param query
   * @param asAnon
   * @param rs
   * @return
   * @throws SolrSearchException
   */
  private SolrSearchResultSet processSolrQuery(SlingHttpServletRequest request,
      Query query, boolean asAnon) throws StorageClientException, AccessDeniedException, SolrServerException {
    String queryString = query.getQueryString();
    // apply readers restrictions.
    if (asAnon) {
      queryString = "(" + queryString + ")  AND readers:" + User.ANON_USER;
    } else {
      Session session = StorageClientUtils.adaptToSession(request.getResourceResolver().adaptTo(javax.jcr.Session.class));
      if (!User.ADMIN_USER.equals(session.getUserId())) {
        AuthorizableManager am = session.getAuthorizableManager();
        Authorizable user = am.findAuthorizable(session.getUserId());
        Set<String> readers = Sets.newHashSet();
        for (Iterator<Group> gi = user.memberOf(am); gi.hasNext();) {
          readers.add(ClientUtils.escapeQueryChars(gi.next().getId()));
        }
        readers.add(session.getUserId());
        queryString = "(" + queryString + ") AND readers:(" + StringUtils.join(readers," OR ") + ")";
      }
    }

    SolrQuery solrQuery = buildQuery(request, queryString, query.getOptions());

    SolrServer solrServer = solrSearchService.getServer();
    try {
      LOGGER.info("Performing Query {} ", URLDecoder.decode(solrQuery.toString(),"UTF-8"));
    } catch (UnsupportedEncodingException e) {
    }
    QueryResponse response = solrServer.query(solrQuery);
    SolrSearchResultSetImpl rs = new SolrSearchResultSetImpl(response);
    LOGGER.info("Got {} hits in {} ms", rs.getSize(), response.getElapsedTime());
    return rs;
  }

  /**
   * Process properties to query sparse content directly.
   *
   * @param request
   * @param query
   * @param asAnon
   * @return
   * @throws StorageClientException
   * @throws AccessDeniedException
   */
  private SolrSearchResultSet processSparseQuery(SlingHttpServletRequest request,
      Query query, boolean asAnon) throws StorageClientException, AccessDeniedException,
      ParseException {
    // use solr parsing to get the terms from the query string
    QueryParser parser = new QueryParser(Version.LUCENE_40, "id",
        new TextField().getQueryAnalyzer());
    org.apache.lucene.search.Query luceneQuery = parser.parse(query.getQueryString());

    Map<String, Object> props = Maps.newHashMap();
    if (luceneQuery instanceof BooleanQuery) {
      BooleanQuery boolLucQuery = (BooleanQuery) luceneQuery;

      int orCount = 0;
      List<BooleanClause> clauses = boolLucQuery.clauses();
      for (BooleanClause clause : clauses) {
        org.apache.lucene.search.Query clauseQuery = clause.getQuery();
        Map<String, Object> subOrs = Maps.newHashMap();
        // we support 1 level of nesting for OR clauses
        if (clauseQuery instanceof BooleanQuery) {
          for (BooleanClause subclause : ((BooleanQuery) clauseQuery).clauses()) {
            org.apache.lucene.search.Query subclauseQuery = subclause.getQuery();
            extractTerms(subclause, subclauseQuery, props, subOrs);
          }
          props.put("orset" + orCount, subOrs);
          orCount++;
        } else {
          extractTerms(clause, clauseQuery, props, subOrs);
          if (!subOrs.isEmpty()) {
            props.put("orset" + orCount, subOrs);
            orCount++;
          }
        }
      }
    } else {
      extractTerms(null, luceneQuery, props, null);
    }

    // add the options to the parameters but prepend _ to avoid collision
    for (Entry<String, String> option : query.getOptions().entrySet()) {
      props.put("_" + option.getKey(), option.getValue());
    }

    Session session = StorageClientUtils.adaptToSession(request.getResourceResolver()
        .adaptTo(javax.jcr.Session.class));
    ContentManager cm = session.getContentManager();
    Iterable<Content> items = cm.find(props);
    SolrSearchResultSet rs = new SparseSearchResultSet(items, defaultMaxResults);
    return rs;
  }

  /**
   * @param clause
   * @param clauseQuery
   * @param ands
   * @param ors
   */
  private void extractTerms(BooleanClause clause,
      org.apache.lucene.search.Query clauseQuery, Map<String, Object> ands,
      Map<String, Object> ors) {
    Set<Term> terms = Sets.newHashSet();
    clauseQuery.extractTerms(terms);

    for (Term term : terms) {
      if (clause != null && clause.getOccur() == Occur.SHOULD) {
        accumulateValue(ors, term.field(), term.text());
      } else {
        accumulateValue(ands, term.field(), term.text());
      }
    }
  }

  private void accumulateValue(Map<String, Object> map, String key, Object val) {
    Object o = map.get(key);
    if (o != null) {
      if (o instanceof Collection) {
        ((Collection) o).add(val);
      } else {
        List<Object> os = Lists.newArrayList(o, val);
        map.put(key, os);
      }
    } else {
      map.put(key, val);
    }
  }
  /**
   * @param request
   * @param query
   * @param queryString
   * @return
   */
  private SolrQuery buildQuery(SlingHttpServletRequest request, String queryString,
      Map<String, String> options) {
    // build the query
    SolrQuery solrQuery = new SolrQuery(queryString);
    long[] ranges = SolrSearchUtil.getOffsetAndSize(request, options);
    solrQuery.setStart((int) ranges[0]);
    solrQuery.setRows((int) ranges[1]);

    // add in some options
    if (options != null) {
      for (Entry<String, String> option : options.entrySet()) {
        String key = option.getKey();
        String val = option.getValue();
        if (CommonParams.SORT.equals(key)) {
          parseSort(solrQuery, val);
        } else {
          solrQuery.set(key, val);
        }
      }
    }
    return solrQuery;
  }

  /**
   * @param options
   * @param solrQuery
   * @param val
   */
  private void parseSort(SolrQuery solrQuery, String val) {
    String[] sort = StringUtils.split(val);
    // we don't support score sorting at all yet
    if ("score".equals(sort[0])) {
    	return;
    }
    switch (sort.length) {
      case 1:
      solrQuery.setSortField(sort[0], ORDER.asc);
      break;
    case 2:
      String sortOrder = sort[1].toLowerCase();
      ORDER o = ORDER.asc;
      try {
        o = ORDER.valueOf(sortOrder);
      } catch ( IllegalArgumentException a) {
        if ( sortOrder.startsWith("d") ) {
          o = ORDER.desc;
        } else {
          o = ORDER.asc;
        }
      }
      solrQuery.setSortField(sort[0], o);
      break;
    default:
      LOGGER.warn("Expected the sort option to be 1 or 2 terms. Found: {}", val);
    }
  }
}
