/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.queries.spans;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.junit.Test;

/** Tests for {@link SpanMultiTermQueryWrapper}, wrapping a few MultiTermQueries. */
public class TestSpanMultiTermQueryWrapper extends LuceneTestCase {
  private Directory directory;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    directory = newDirectory();
    RandomIndexWriter iw = new RandomIndexWriter(random(), directory);
    Document doc = new Document();
    Field field = newTextField("field", "", Field.Store.NO);
    doc.add(field);

    field.setStringValue("quick brown fox");
    iw.addDocument(doc);
    field.setStringValue("jumps over lazy broun dog");
    iw.addDocument(doc);
    field.setStringValue("jumps over extremely very lazy broxn dog");
    iw.addDocument(doc);
    reader = iw.getReader();
    iw.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    super.tearDown();
  }

  public void testWildcard() throws Exception {
    WildcardQuery wq = new WildcardQuery(new QueryTerm("field", "bro?n", 0));
    SpanQuery swq = new SpanMultiTermQueryWrapper<>(wq);
    // will only match quick brown fox
    SpanFirstQuery sfq = new SpanFirstQuery(swq, 2);
    assertEquals(1, searcher.count(sfq));
  }

  public void testPrefix() throws Exception {
    WildcardQuery wq = new WildcardQuery(new QueryTerm("field", "extrem*", 0));
    SpanQuery swq = new SpanMultiTermQueryWrapper<>(wq);
    // will only match "jumps over extremely very lazy broxn dog"
    SpanFirstQuery sfq = new SpanFirstQuery(swq, 3);
    assertEquals(1, searcher.count(sfq));
  }

  public void testFuzzy() throws Exception {
    FuzzyQuery fq = new FuzzyQuery(new QueryTerm("field", "broan", 0));
    SpanQuery sfq = new SpanMultiTermQueryWrapper<>(fq);
    // will not match quick brown fox
    SpanPositionRangeQuery sprq = new SpanPositionRangeQuery(sfq, 3, 6);
    assertEquals(2, searcher.count(sprq));
  }

  public void testFuzzy2() throws Exception {
    // maximum of 1 term expansion
    FuzzyQuery fq = new FuzzyQuery(new QueryTerm("field", "broan", 0), 1, 0, 1, false);
    SpanQuery sfq = new SpanMultiTermQueryWrapper<>(fq);
    // will only match jumps over lazy broun dog
    SpanPositionRangeQuery sprq = new SpanPositionRangeQuery(sfq, 0, 100);
    assertEquals(1, searcher.count(sprq));
  }

  public void testNoSuchMultiTermsInNear() throws Exception {
    // test to make sure non existent multiterms aren't throwing null pointer exceptions
    FuzzyQuery fuzzyNoSuch = new FuzzyQuery(new QueryTerm("field", "noSuch", 0), 1, 0, 1, false);
    SpanQuery spanNoSuch = new SpanMultiTermQueryWrapper<>(fuzzyNoSuch);
    SpanQuery term = new SpanTermQuery(new QueryTerm("field", "brown", 0));
    SpanQuery near = new SpanNearQuery(new SpanQuery[] {term, spanNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));
    // flip order
    near = new SpanNearQuery(new SpanQuery[] {spanNoSuch, term}, 1, true);
    assertEquals(0, searcher.count(near));

    WildcardQuery wcNoSuch = new WildcardQuery(new QueryTerm("field", "noSuch*", 0));
    SpanQuery spanWCNoSuch = new SpanMultiTermQueryWrapper<>(wcNoSuch);
    near = new SpanNearQuery(new SpanQuery[] {term, spanWCNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));

    RegexpQuery rgxNoSuch = new RegexpQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanRgxNoSuch = new SpanMultiTermQueryWrapper<>(rgxNoSuch);
    near = new SpanNearQuery(new SpanQuery[] {term, spanRgxNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));

    PrefixQuery prfxNoSuch = new PrefixQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanPrfxNoSuch = new SpanMultiTermQueryWrapper<>(prfxNoSuch);
    near = new SpanNearQuery(new SpanQuery[] {term, spanPrfxNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));

    // test single noSuch
    near = new SpanNearQuery(new SpanQuery[] {spanPrfxNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));

    // test double noSuch
    near = new SpanNearQuery(new SpanQuery[] {spanPrfxNoSuch, spanPrfxNoSuch}, 1, true);
    assertEquals(0, searcher.count(near));
  }

  public void testNoSuchMultiTermsInNotNear() throws Exception {
    // test to make sure non existent multiterms aren't throwing non-matching field exceptions
    FuzzyQuery fuzzyNoSuch = new FuzzyQuery(new QueryTerm("field", "noSuch", 0), 1, 0, 1, false);
    SpanQuery spanNoSuch = new SpanMultiTermQueryWrapper<>(fuzzyNoSuch);
    SpanQuery term = new SpanTermQuery(new QueryTerm("field", "brown", 0));
    SpanNotQuery notNear = new SpanNotQuery(term, spanNoSuch, 0, 0);
    assertEquals(1, searcher.count(notNear));

    // flip
    notNear = new SpanNotQuery(spanNoSuch, term, 0, 0);
    assertEquals(0, searcher.count(notNear));

    // both noSuch
    notNear = new SpanNotQuery(spanNoSuch, spanNoSuch, 0, 0);
    assertEquals(0, searcher.count(notNear));

    WildcardQuery wcNoSuch = new WildcardQuery(new QueryTerm("field", "noSuch*", 0));
    SpanQuery spanWCNoSuch = new SpanMultiTermQueryWrapper<>(wcNoSuch);
    notNear = new SpanNotQuery(term, spanWCNoSuch, 0, 0);
    assertEquals(1, searcher.count(notNear));

    RegexpQuery rgxNoSuch = new RegexpQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanRgxNoSuch = new SpanMultiTermQueryWrapper<>(rgxNoSuch);
    notNear = new SpanNotQuery(term, spanRgxNoSuch, 1, 1);
    assertEquals(1, searcher.count(notNear));

    PrefixQuery prfxNoSuch = new PrefixQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanPrfxNoSuch = new SpanMultiTermQueryWrapper<>(prfxNoSuch);
    notNear = new SpanNotQuery(term, spanPrfxNoSuch, 1, 1);
    assertEquals(1, searcher.count(notNear));
  }

  public void testNoSuchMultiTermsInOr() throws Exception {
    // test to make sure non existent multiterms aren't throwing null pointer exceptions
    FuzzyQuery fuzzyNoSuch = new FuzzyQuery(new QueryTerm("field", "noSuch", 0), 1, 0, 1, false);
    SpanQuery spanNoSuch = new SpanMultiTermQueryWrapper<>(fuzzyNoSuch);
    SpanQuery term = new SpanTermQuery(new QueryTerm("field", "brown", 0));
    SpanOrQuery near = new SpanOrQuery(new SpanQuery[] {term, spanNoSuch});
    assertEquals(1, searcher.count(near));

    // flip
    near = new SpanOrQuery(new SpanQuery[] {spanNoSuch, term});
    assertEquals(1, searcher.count(near));

    WildcardQuery wcNoSuch = new WildcardQuery(new QueryTerm("field", "noSuch*", 0));
    SpanQuery spanWCNoSuch = new SpanMultiTermQueryWrapper<>(wcNoSuch);
    near = new SpanOrQuery(new SpanQuery[] {term, spanWCNoSuch});
    assertEquals(1, searcher.count(near));

    RegexpQuery rgxNoSuch = new RegexpQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanRgxNoSuch = new SpanMultiTermQueryWrapper<>(rgxNoSuch);
    near = new SpanOrQuery(new SpanQuery[] {term, spanRgxNoSuch});
    assertEquals(1, searcher.count(near));

    PrefixQuery prfxNoSuch = new PrefixQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanPrfxNoSuch = new SpanMultiTermQueryWrapper<>(prfxNoSuch);
    near = new SpanOrQuery(new SpanQuery[] {term, spanPrfxNoSuch});
    assertEquals(1, searcher.count(near));

    near = new SpanOrQuery(new SpanQuery[] {spanPrfxNoSuch});
    assertEquals(0, searcher.count(near));

    near = new SpanOrQuery(new SpanQuery[] {spanPrfxNoSuch, spanPrfxNoSuch});
    assertEquals(0, searcher.count(near));
  }

  public void testNoSuchMultiTermsInSpanFirst() throws Exception {
    // this hasn't been a problem
    FuzzyQuery fuzzyNoSuch = new FuzzyQuery(new QueryTerm("field", "noSuch", 0), 1, 0, 1, false);
    SpanQuery spanNoSuch = new SpanMultiTermQueryWrapper<>(fuzzyNoSuch);
    SpanQuery spanFirst = new SpanFirstQuery(spanNoSuch, 10);

    assertEquals(0, searcher.count(spanFirst));

    WildcardQuery wcNoSuch = new WildcardQuery(new QueryTerm("field", "noSuch*", 0));
    SpanQuery spanWCNoSuch = new SpanMultiTermQueryWrapper<>(wcNoSuch);
    spanFirst = new SpanFirstQuery(spanWCNoSuch, 10);
    assertEquals(0, searcher.count(spanFirst));

    RegexpQuery rgxNoSuch = new RegexpQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanRgxNoSuch = new SpanMultiTermQueryWrapper<>(rgxNoSuch);
    spanFirst = new SpanFirstQuery(spanRgxNoSuch, 10);
    assertEquals(0, searcher.count(spanFirst));

    PrefixQuery prfxNoSuch = new PrefixQuery(new QueryTerm("field", "noSuch", 0));
    SpanQuery spanPrfxNoSuch = new SpanMultiTermQueryWrapper<>(prfxNoSuch);
    spanFirst = new SpanFirstQuery(spanPrfxNoSuch, 10);
    assertEquals(0, searcher.count(spanFirst));
  }

  @Test
  public void testWrappedQueryIsNotModified() {
    final PrefixQuery pq = new PrefixQuery(new QueryTerm("field", "test", 0));
    int pqHash = pq.hashCode();
    SpanMultiTermQueryWrapper<PrefixQuery> wrapper = new SpanMultiTermQueryWrapper<>(pq);
    assertEquals(pqHash, pq.hashCode());
    wrapper.setRewriteMethod(
        new SpanMultiTermQueryWrapper.SpanRewriteMethod() {
          @Override
          public SpanQuery rewrite(IndexReader reader, MultiTermQuery query) throws IOException {
            return null;
          }
        });
    assertEquals(pqHash, pq.hashCode());
  }
}
