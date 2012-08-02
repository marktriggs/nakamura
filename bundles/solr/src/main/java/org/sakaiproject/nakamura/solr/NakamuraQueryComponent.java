/*
 * Copyright 2012, Mark Triggs
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sakaiproject.nakamura.solr;

import org.apache.solr.handler.component.QueryComponent;
import org.apache.solr.handler.component.ResponseBuilder;

import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.ConcurrentLRUCache;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.io.IOException;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.OpenBitSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;

/**
 * <p>
 * Nakamura specific query component for Solr. This takes a `readers` property as a single
 * term with a comma-delimited value. This also allows us to cache these filters.
 * </p>
 * 
 * <p>
 * <em>(From an email with Mark Triggs)</em> Just to frame this a bit, if you imagine a
 * query like:
 * </p>
 * 
 * <pre>
 * q=search term
 * fq=readers:(group1 OR group2 OR group3 OR ...)
 * </pre>
 * 
 * <p>
 * Then Lucene processes this query by:
 * </p>
 * <ul>
 * <li>Doing a binary search for "search", and another for "term", getting back a list of
 * docids associated with each term and unioning the two sets (assuming it's an OR query).
 * </li>
 * 
 * <li>Does a binary search for each group in the list of readers, getting back a list of
 * the docids of documents that each group is allowed to read and unioning them all
 * together.</li>
 * 
 * <li>Calculates the intersection of the two sets, limiting the set of matched docids to
 * only those that can be read by one of the readers.</li>
 * </ul>
 * </p>
 * 
 * <p>
 * For a big list of readers, the cost of calculating the filter in the second step is
 * going to dwarf the cost of processing the query itself. No surprises there. And you'll
 * recall that we use filters for the parts of the query we expect to be reusable: since a
 * user's list of readers generally doesn't change between queries, putting it in a filter
 * allows Solr to cache it in the filterCache and avoid having to recalculate step #2 in
 * the above for multiple requests with the same filter.
 * </p>
 * 
 * <p>
 * All of this caching works well until the data changes. Since it's a general purpose
 * platform, Solr makes some promises about cache consistency--not serving out stale data
 * from the caches and that kind of thing. As a result, whenever a commit is performed
 * (soft or hard) Solr flushes its caches, and subsequent queries are going to have to
 * recalculate their filters and re-cache them.
 * </p>
 * 
 * <p>
 * So that's the backstory. My query component tries to improve the situation by making
 * some trade-offs that make sense for our particular use cases. It hooks into the Solr
 * query processing pipeline and creates a new query syntax that is tailored to our use
 * case:
 * </p>
 * 
 * <pre>
 * q=search term
 * readers=group1,group2,group3,...
 * </pre>
 * 
 * <p>
 * For that list of readers, it does exactly what I described in step #2 above (using the
 * Lucene classes to interrogate the index directly), and builds up a filter (a bit set)
 * corresponding to the list of docids that the list of readers is allowed to view. That
 * filter can then be cached in the query component for subsequent queries.
 * </p>
 * 
 * <p>
 * OK, so Solr is building filters and caching them, and now I'm building filters and
 * caching them. But the critical difference: I don't ever flush my caches. Crazy! So you
 * might expect I would have problems with my cached filters missing documents that have
 * been added after the fact, or containing documents that have since been deleted.
 * </p>
 * 
 * <p>
 * The trick is to take advantage of the per-segment improvements made in recent versions
 * of Lucene. A Lucene index directory is made up of a number of index segments--each
 * being a self-contained portion of the overall index. When searching an index, Lucene is
 * actually searching each of these individual segments and then merging the results.
 * </p>
 * 
 * <p>
 * I define a filter with something like:
 * </p>
 * 
 * <pre>
 * Filter f = new Filter () {
 *   public DocIdSet getDocIdSet (IndexReader.AtomicReaderContext context) {
 *     // code for calculating the list of docids for our reader list
 *   }
 * }
 * </pre>
 * 
 * <p>
 * When Lucene does a search, it will call myfilter.getDocIdSet() for every segment, and
 * my filter returns a list of the documents in that segment that match it. And in fact,
 * if I used this filter directly, Lucene would call getDocIdSet() for *every*
 * query--recalculating the list of matched documents for each segment over and over
 * again.
 * </p>
 * 
 * <p>
 * So after I've created my filter, you'll see I wrap it in a CachingWrapperFilter. This
 * will cache the result from getDocIdSet for each segment, so we only need to do the
 * calculations once per segment. This gives the behaviour we need for when documents are
 * added to the index: the new documents get added as new index segments, which won't be
 * in the CachingWrapperFilter's cache, and so my filter gets a chance to calculate the
 * filter list for the new segment.
 * </p>
 * 
 * <p>
 * Essentially, the combination of CachingWrapperFilter and this per-segment behaviour
 * gives us a magic filter that we can cache indefinitely which will automatically update
 * itself as new documents get added. Hooray!
 * </p>
 * 
 * <p>
 * That only leaves deletes: our filter might contain docids that have since been removed
 * from the index. And the great thing is: we don't care! The main query will take
 * deletions into account anyway, so there's no harm in our filter being willing to accept
 * a document that it will never see in practice.
 * </p>
 */
public class NakamuraQueryComponent extends QueryComponent {

  private static final Logger LOGGER = LoggerFactory
      .getLogger(NakamuraQueryComponent.class);
  private static final Set<String> cachedParams = ImmutableSet.of("readers"); //, "deletes");

  private final ConcurrentLRUCache<String, Query> filterCache;

  // used for unit testing because part of the call chain can't be mocked in
  // prepare(ResponseBuilder)
  boolean testing;

  public NakamuraQueryComponent() {
    filterCache = new ConcurrentLRUCache<String, Query>(16384, 512);
  }

  private ConstantScoreQuery buildFilterForPrincipals(final String[] principals) {
    Filter f = new Filter() {
      public DocIdSet getDocIdSet(IndexReader.AtomicReaderContext context) {
        long start = System.currentTimeMillis();

        IndexReader rdr = context.reader;
        OpenBitSet bits = new OpenBitSet(rdr.maxDoc());

        for (String principal : principals) {
          try {
            DocsEnum td = rdr.termDocsEnum(null, "readers",
                new BytesRef(principal.trim()));

            if (td == null) {
              continue;
            }

            while (td.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
              bits.set(td.docID());
            }

          } catch (IOException e) {
            return null;
          }
        }

        LOGGER.info("\n\nBuilding {}-bit filter for segment [{}] took: {} msecs",
            new Object[] { rdr.maxDoc(), rdr, (System.currentTimeMillis() - start) });

        return bits;
      }
    };

    return new ConstantScoreQuery(new CachingWrapperFilter(f));
  }

  /**
   * {@inheritDoc}
   * 
   * Override the prepare method to inject our own filter for ACL control before calling
   * <code>super.prepare(rb)</code>. This saves from sending over a term per user to
   * filter on.
   * 
   * @see org.apache.solr.handler.component.QueryComponent#prepare(org.apache.solr.handler.component.ResponseBuilder)
   */
  @Override
  public void prepare(ResponseBuilder rb) throws IOException {
    for (String cachedParam : cachedParams) {
      processPrincipals(rb, cachedParam);
    }

    if (!testing) {
      super.prepare(rb);
    }
  }

  /**
   * Process comma-delimited principal strings found in the request. If no parameter is
   * found matching <code>paramName</code>, no filter is added.
   * 
   * @param rb
   * @param paramName
   */
  private void processPrincipals(ResponseBuilder rb, String paramName) {
    SolrQueryRequest req = rb.req;
    SolrParams params = req.getParams();

    // only process if the parameter is found in the request
    String principalsString = params.get(paramName);
    if (StringUtils.isNotBlank(principalsString)) {

      String[] principals = StringUtils.split(principalsString, ",");
      Arrays.sort(principals);

      // sort the principals to make a predictable and more repeatable cache key
      String key = Arrays.toString(principals);
      Query f = filterCache.get(key);

      // if the filter isn't cached, build a new one and cache it.
      if (f == null) {
        synchronized (filterCache) {
          // check again since there's a very tiny chance that another thread entered this
          // sync block before we did and already added this key. it's cheaper to get
          // twice than to wrapping in a sync on every call to this.
          f = filterCache.get(key);
          if (f == null) {
            f = buildFilterForPrincipals(principals);
            filterCache.put(key, f);
          }
        }
      }

      // add our filter to the response builder
      addFilter(rb, f);
    }
  }

  /**
   * @param rb
   * @param f
   */
  private void addFilter(ResponseBuilder rb, Query f) {
    List<Query> filters = rb.getFilters();

    if (filters == null) {
      filters = new ArrayList<Query>();
    }

    filters.add(f);
    rb.setFilters(filters);
  }
}
