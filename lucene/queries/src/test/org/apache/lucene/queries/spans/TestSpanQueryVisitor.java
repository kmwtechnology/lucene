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

import static org.hamcrest.CoreMatchers.equalTo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanQueryVisitor extends LuceneTestCase {

  private static final Query query =
      new BooleanQuery.Builder()
          .add(new TermQuery(new QueryTerm("field1", "t1", 0)), BooleanClause.Occur.MUST)
          .add(
              new BooleanQuery.Builder()
                  .add(new TermQuery(new QueryTerm("field1", "tm2", 0)), BooleanClause.Occur.SHOULD)
                  .add(
                      new BoostQuery(new TermQuery(new QueryTerm("field1", "tm3", 0)), 2),
                      BooleanClause.Occur.SHOULD)
                  .build(),
              BooleanClause.Occur.MUST)
          .add(
              new BoostQuery(
                  new PhraseQuery.Builder()
                      .add(new QueryTerm("field1", "term4", 0))
                      .add(new QueryTerm("field1", "term5", 0))
                      .build(),
                  3),
              BooleanClause.Occur.MUST)
          .add(
              new SpanNearQuery(
                  new SpanQuery[] {
                    new SpanTermQuery(new QueryTerm("field1", "term6", 0)),
                    new SpanTermQuery(new QueryTerm("field1", "term7", 0))
                  },
                  2,
                  true),
              BooleanClause.Occur.MUST)
          .add(new TermQuery(new QueryTerm("field1", "term8", 0)), BooleanClause.Occur.MUST_NOT)
          .add(new PrefixQuery(new QueryTerm("field1", "term9", 0)), BooleanClause.Occur.SHOULD)
          .add(
              new BoostQuery(
                  new BooleanQuery.Builder()
                      .add(
                          new BoostQuery(new TermQuery(new QueryTerm("field2", "term10", 0)), 3),
                          BooleanClause.Occur.MUST)
                      .build(),
                  2),
              BooleanClause.Occur.SHOULD)
          .build();

  public void testExtractTermsEquivalent() {
    Set<Term> terms = new HashSet<>();
    Set<Term> expected =
        new HashSet<>(
            Arrays.asList(
                new QueryTerm("field1", "t1", 0), new QueryTerm("field1", "tm2", 0),
                new QueryTerm("field1", "tm3", 0), new QueryTerm("field1", "term4", 0),
                new QueryTerm("field1", "term5", 0), new QueryTerm("field1", "term6", 0),
                new QueryTerm("field1", "term7", 0), new QueryTerm("field2", "term10", 0)));
    query.visit(QueryVisitor.termCollector(terms));
    assertThat(terms, equalTo(expected));
  }
}
