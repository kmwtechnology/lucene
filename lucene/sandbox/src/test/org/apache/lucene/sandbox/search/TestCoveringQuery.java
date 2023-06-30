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
package org.apache.lucene.sandbox.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.LongValuesSource;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.QueryUtils;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCoveringQuery extends LuceneTestCase {

  public void testEquals() {
    TermQuery tq1 = new TermQuery(new QueryTerm("foo", "bar", 0));
    TermQuery tq2 = new TermQuery(new QueryTerm("foo", "quux", 0));
    LongValuesSource vs = LongValuesSource.fromLongField("field");

    CoveringQuery q1 = new CoveringQuery(Arrays.asList(tq1, tq2), vs);
    CoveringQuery q2 = new CoveringQuery(Arrays.asList(tq1, tq2), vs);
    QueryUtils.checkEqual(q1, q2);

    // order does not matter
    CoveringQuery q3 = new CoveringQuery(Arrays.asList(tq2, tq1), vs);
    QueryUtils.checkEqual(q1, q3);

    // values source matters
    CoveringQuery q4 =
        new CoveringQuery(Arrays.asList(tq2, tq1), LongValuesSource.fromLongField("other_field"));
    QueryUtils.checkUnequal(q1, q4);

    // duplicates matter
    CoveringQuery q5 = new CoveringQuery(Arrays.asList(tq1, tq1, tq2), vs);
    CoveringQuery q6 = new CoveringQuery(Arrays.asList(tq1, tq2, tq2), vs);
    QueryUtils.checkUnequal(q5, q6);

    // query matters
    CoveringQuery q7 = new CoveringQuery(Arrays.asList(tq1), vs);
    CoveringQuery q8 = new CoveringQuery(Arrays.asList(tq2), vs);
    QueryUtils.checkUnequal(q7, q8);
  }

  public void testRewrite() throws IOException {
    PhraseQuery pq = new PhraseQuery("foo", new int[] {0}, "bar");
    TermQuery tq = new TermQuery(new QueryTerm("foo", "bar", 0));
    LongValuesSource vs = LongValuesSource.fromIntField("field");
    assertEquals(
        new CoveringQuery(Collections.singleton(tq), vs),
        new CoveringQuery(Collections.singleton(pq), vs).rewrite(new MultiReader()));
  }

  public void testToString() {
    TermQuery tq1 = new TermQuery(new QueryTerm("foo", "bar", 0));
    TermQuery tq2 = new TermQuery(new QueryTerm("foo", "quux", 0));
    LongValuesSource vs = LongValuesSource.fromIntField("field");
    CoveringQuery q = new CoveringQuery(Arrays.asList(tq1, tq2), vs);
    assertEquals(
        "CoveringQuery(queries=[foo:bar, foo:quux], minimumNumberMatch=long(field))", q.toString());
    assertEquals(
        "CoveringQuery(queries=[bar, quux], minimumNumberMatch=long(field))", q.toString("foo"));
  }

  public void testRandom() throws IOException {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    int numDocs = atLeast(50);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      if (random().nextBoolean()) {
        doc.add(new StringField("field", "A", Store.NO));
      }
      if (random().nextBoolean()) {
        doc.add(new StringField("field", "B", Store.NO));
      }
      if (random().nextDouble() > 0.9) {
        doc.add(new StringField("field", "C", Store.NO));
      }
      if (random().nextDouble() > 0.1) {
        doc.add(new StringField("field", "D", Store.NO));
      }
      doc.add(new NumericDocValuesField("min_match", random().nextInt(6)));
      w.addDocument(doc);
    }

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher searcher = new IndexSearcher(r);
    w.close();

    int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      List<Query> queries = new ArrayList<>();
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "A", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "B", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "C", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "D", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "E", 0)));
      }

      Query q = new CoveringQuery(queries, LongValuesSource.fromLongField("min_match"));
      QueryUtils.check(random(), q, searcher);

      for (int i = 1; i < 4; ++i) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(i);
        for (Query query : queries) {
          builder.add(query, Occur.SHOULD);
        }
        Query q1 = builder.build();
        Query q2 = new CoveringQuery(queries, LongValuesSource.constant(i));
        assertSameMatches(searcher, q1, q2, true);
        assertEquals(searcher.count(q1), searcher.count(q2));
      }

      Query filtered =
          new BooleanQuery.Builder()
              .add(q, Occur.MUST)
              .add(new TermQuery(new QueryTerm("field", "A", 0)), Occur.MUST)
              .build();
      QueryUtils.check(random(), filtered, searcher);
    }

    r.close();
    dir.close();
  }

  public void testRandomWand() throws IOException {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig());
    int numDocs = atLeast(50);
    for (int i = 0; i < numDocs; ++i) {
      Document doc = new Document();
      if (random().nextBoolean()) {
        doc.add(new StringField("field", "A", Store.NO));
      }
      if (random().nextBoolean()) {
        doc.add(new StringField("field", "B", Store.NO));
      }
      if (random().nextDouble() > 0.9) {
        doc.add(new StringField("field", "C", Store.NO));
      }
      if (random().nextDouble() > 0.1) {
        doc.add(new StringField("field", "D", Store.NO));
      }
      doc.add(new NumericDocValuesField("min_match", 1));
      w.addDocument(doc);
    }

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher searcher = new IndexSearcher(r);
    w.close();

    int iters = atLeast(10);
    for (int iter = 0; iter < iters; ++iter) {
      List<Query> queries = new ArrayList<>();
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "A", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "B", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "C", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "D", 0)));
      }
      if (random().nextBoolean()) {
        queries.add(new TermQuery(new QueryTerm("field", "E", 0)));
      }

      Query q = new CoveringQuery(queries, LongValuesSource.fromLongField("min_match"));
      QueryUtils.check(random(), q, searcher);

      for (int i = 1; i < 4; ++i) {
        BooleanQuery.Builder builder = new BooleanQuery.Builder().setMinimumNumberShouldMatch(i);
        for (Query query : queries) {
          builder.add(query, Occur.SHOULD);
        }
        Query q1 = builder.build();
        Query q2 = new CoveringQuery(queries, LongValuesSource.constant(i));
        assertSameMatches(searcher, q1, q2, true);
        assertEquals(searcher.count(q1), searcher.count(q2));
      }

      Query filtered =
          new BooleanQuery.Builder()
              .add(q, Occur.MUST)
              .add(new TermQuery(new QueryTerm("field", "A", 0)), Occur.MUST)
              .build();
      QueryUtils.check(random(), filtered, searcher);
    }

    r.close();
    dir.close();
  }

  private void assertSameMatches(IndexSearcher searcher, Query q1, Query q2, boolean scores)
      throws IOException {
    final int maxDoc = searcher.getIndexReader().maxDoc();
    final TopDocs td1 = searcher.search(q1, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    final TopDocs td2 = searcher.search(q2, maxDoc, scores ? Sort.RELEVANCE : Sort.INDEXORDER);
    assertEquals(td1.totalHits.value, td2.totalHits.value);
    for (int i = 0; i < td1.scoreDocs.length; ++i) {
      assertEquals(td1.scoreDocs[i].doc, td2.scoreDocs[i].doc);
      if (scores) {
        assertEquals(td1.scoreDocs[i].score, td2.scoreDocs[i].score, 10e-7);
      }
    }
  }
}
