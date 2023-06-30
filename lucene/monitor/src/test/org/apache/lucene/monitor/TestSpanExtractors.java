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

package org.apache.lucene.monitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.FieldMaskingSpanQuery;
import org.apache.lucene.queries.spans.SpanContainingQuery;
import org.apache.lucene.queries.spans.SpanFirstQuery;
import org.apache.lucene.queries.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanOrQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.queries.spans.SpanWithinQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanExtractors extends LuceneTestCase {

  private static final QueryAnalyzer treeBuilder = new QueryAnalyzer();

  private Set<Term> collectTerms(Query q) {
    QueryTree tree = treeBuilder.buildTree(q, TermWeightor.DEFAULT);
    Set<Term> terms = new HashSet<>();
    tree.collectTerms((f, b) -> terms.add(new QueryTerm(f, b, 0)));
    return terms;
  }

  public void testOrderedNearExtractor() {
    SpanNearQuery q =
        new SpanNearQuery(
            new SpanQuery[] {
              new SpanTermQuery(new QueryTerm("field1", "term1", 0)),
              new SpanTermQuery(new QueryTerm("field1", "term", 0))
            },
            0,
            true);

    Set<Term> expected = Collections.singleton(new QueryTerm("field1", "term1", 0));
    assertEquals(expected, collectTerms(q));
  }

  public void testOrderedNearWithWildcardExtractor() {
    SpanNearQuery q =
        new SpanNearQuery(
            new SpanQuery[] {
              new SpanMultiTermQueryWrapper<>(
                  new RegexpQuery(new QueryTerm("field", "super.*cali.*", 0))),
              new SpanTermQuery(new QueryTerm("field", "is", 0))
            },
            0,
            true);

    Set<Term> expected = Collections.singleton(new QueryTerm("field", "is", 0));
    assertEquals(expected, collectTerms(q));
  }

  public void testSpanOrExtractor() {
    SpanOrQuery or =
        new SpanOrQuery(
            new SpanTermQuery(new QueryTerm("field", "term1", 0)),
            new SpanTermQuery(new QueryTerm("field", "term2", 0)));
    Set<Term> expected =
        new HashSet<>(
            Arrays.asList(new QueryTerm("field", "term1", 0), new QueryTerm("field", "term2", 0)));
    assertEquals(expected, collectTerms(or));
  }

  public void testSpanMultiTerms() {
    SpanQuery q =
        new SpanMultiTermQueryWrapper<>(new RegexpQuery(new QueryTerm("field", "term.*", 0)));
    Set<Term> terms = collectTerms(q);
    assertEquals(1, terms.size());
    assertEquals(TermFilteredPresearcher.ANYTOKEN_FIELD, terms.iterator().next().field());
  }

  public void testSpanWithin() {
    QueryTerm t1 = new QueryTerm("field", "term1", 0);
    QueryTerm t2 = new QueryTerm("field", "term22", 0);
    QueryTerm t3 = new QueryTerm("field", "term333", 0);
    SpanWithinQuery swq =
        new SpanWithinQuery(
            SpanNearQuery.newOrderedNearQuery("field")
                .addClause(new SpanTermQuery(t1))
                .addClause(new SpanTermQuery(t2))
                .build(),
            new SpanTermQuery(t3));

    assertEquals(Collections.singleton(t3), collectTerms(swq));
  }

  public void testSpanContains() {
    QueryTerm t1 = new QueryTerm("field", "term1", 0);
    QueryTerm t2 = new QueryTerm("field", "term22", 0);
    QueryTerm t3 = new QueryTerm("field", "term333", 0);
    SpanContainingQuery swq =
        new SpanContainingQuery(
            SpanNearQuery.newOrderedNearQuery("field")
                .addClause(new SpanTermQuery(t1))
                .addClause(new SpanTermQuery(t2))
                .build(),
            new SpanTermQuery(t3));

    assertEquals(Collections.singleton(t3), collectTerms(swq));
  }

  public void testFieldMaskingSpanQuery() {
    QueryTerm t1 = new QueryTerm("field", "term1", 0);
    FieldMaskingSpanQuery q = new FieldMaskingSpanQuery(new SpanTermQuery(t1), "field2");
    assertEquals(Collections.singleton(t1), collectTerms(q));
  }

  public void testSpanPositionQuery() {
    QueryTerm t1 = new QueryTerm("field", "term", 0);
    Query q = new SpanFirstQuery(new SpanTermQuery(t1), 10);
    assertEquals(Collections.singleton(t1), collectTerms(q));
  }
}
