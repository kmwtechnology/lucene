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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestExtractors extends LuceneTestCase {

  private static final QueryAnalyzer treeBuilder = new QueryAnalyzer();

  private Set<Term> collectTerms(Query query) {
    Set<Term> terms = new HashSet<>();
    QueryTree tree = treeBuilder.buildTree(query, TermWeightor.DEFAULT);
    tree.collectTerms((f, b) -> terms.add(new QueryTerm(f, b, 0)));
    return terms;
  }

  public void testConstantScoreQueryExtractor() {

    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    bq.add(new TermQuery(new QueryTerm("f", "q1", 0)), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new QueryTerm("f", "q2", 0)), BooleanClause.Occur.SHOULD);

    Query csqWithQuery = new ConstantScoreQuery(bq.build());
    Set<Term> expected = Collections.singleton(new QueryTerm("f", "q1", 0));
    assertEquals(expected, collectTerms(csqWithQuery));
  }

  public void testPhraseQueryExtractor() {

    PhraseQuery.Builder pq = new PhraseQuery.Builder();
    pq.add(new QueryTerm("f", "hello", 0));
    pq.add(new QueryTerm("f", "encyclopedia", 0));

    Set<Term> expected = Collections.singleton(new QueryTerm("f", "encyclopedia", 0));
    assertEquals(expected, collectTerms(pq.build()));
  }

  public void testBoostQueryExtractor() {

    BooleanQuery.Builder bq = new BooleanQuery.Builder();
    bq.add(new TermQuery(new QueryTerm("f", "q1", 0)), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new QueryTerm("f", "q2", 0)), BooleanClause.Occur.SHOULD);

    Query boostQuery = new BoostQuery(bq.build(), 0.5f);
    Set<Term> expected = Collections.singleton(new QueryTerm("f", "q1", 0));
    assertEquals(expected, collectTerms(boostQuery));
  }

  public void testDisjunctionMaxExtractor() {

    Query query =
        new DisjunctionMaxQuery(
            Arrays.asList(
                new TermQuery(new QueryTerm("f", "t1", 0)),
                new TermQuery(new QueryTerm("f", "t2", 0))),
            0.1f);
    Set<Term> expected =
        new HashSet<>(Arrays.asList(new QueryTerm("f", "t1", 0), new QueryTerm("f", "t2", 0)));
    assertEquals(expected, collectTerms(query));
  }

  public void testBooleanExtractsFilter() {
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new QueryTerm("f", "must", 0)), BooleanClause.Occur.MUST)
            .add(new TermQuery(new QueryTerm("f", "filter", 0)), BooleanClause.Occur.FILTER)
            .build();
    Set<Term> expected =
        Collections.singleton(new QueryTerm("f", "filter", 0)); // it's longer, so it wins
    assertEquals(expected, collectTerms(q));
  }
}
