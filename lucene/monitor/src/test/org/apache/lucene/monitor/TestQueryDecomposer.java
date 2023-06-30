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
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

// FIXME-TEMP-IGNORE
// @Ignore
public class TestQueryDecomposer extends MonitorTestBase {

  private static final QueryDecomposer decomposer = new QueryDecomposer();

  public void testConjunctionsAreNotDecomposed() {
    Query q = parse("+hello world");
    Set<Query> expected = Collections.singleton(parse("+hello world"));
    assertEquals(expected, decomposer.decompose(q));
  }

  public void testSimpleDisjunctions() {
    Query q = parse("hello world");
    Set<Query> expected = new HashSet<>(Arrays.asList(parse("hello"), parse("world", 6)));
    assertEquals(expected, decomposer.decompose(q));
  }

  public void testNestedDisjunctions() {
    Query q = parse("(hello goodbye) world");
    //               012345678901234567890
    Set<Query> expected =
        new HashSet<>(Arrays.asList(parse("hello", 1), parse("goodbye", 7), parse("world", 16)));
    assertEquals(expected, decomposer.decompose(q));
  }

  public void testExclusions() {
    Set<Query> expected;
    // need to construct this manually to distribute the query term offsets correctly
    Query expected1 =
        new BooleanQuery.Builder()
            .add(new BooleanClause(new TermQuery(new QueryTerm("field", "hello", 0)), Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 13)), Occur.MUST_NOT))
            .build();
    Query expected2 =
        new BooleanQuery.Builder()
            .add(new BooleanClause(new TermQuery(new QueryTerm("field", "world", 6)), Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 13)), Occur.MUST_NOT))
            .build();
    expected = new HashSet<>(Arrays.asList(expected1, expected2));
    assertEquals(expected, decomposer.decompose(parse("hello world -goodbye")));
    //                                                 012345678901234567890
  }

  public void testNestedExclusions() {
    Set<Query> expected =
        new HashSet<>(
            Arrays.asList(
                parse("+(+hello -goodbye) -greeting"), parse("+(+world -goodbye) -greeting")));

    // need to construct this manually to distribute the query term offsets correctly
    Query expected1 =
        new BooleanQuery.Builder()
            .add(
                new BooleanClause(
                    new BooleanQuery.Builder()
                        .add(
                            new BooleanClause(
                                new TermQuery(new QueryTerm("field", "hello", 2)), Occur.MUST))
                        .add(
                            new BooleanClause(
                                new TermQuery(new QueryTerm("field", "goodbye", 16)),
                                Occur.MUST_NOT))
                        .build(),
                    Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "greeting", 26)), Occur.MUST_NOT))
            .build();
    Query expected2 =
        new BooleanQuery.Builder()
            .add(
                new BooleanClause(
                    new BooleanQuery.Builder()
                        .add(
                            new BooleanClause(
                                new TermQuery(new QueryTerm("field", "world", 8)), Occur.MUST))
                        .add(
                            new BooleanClause(
                                new TermQuery(new QueryTerm("field", "goodbye", 16)),
                                Occur.MUST_NOT))
                        .build(),
                    Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "greeting", 26)), Occur.MUST_NOT))
            .build();
    expected = new HashSet<>(Arrays.asList(expected1, expected2));
    Set<Query> decompose = decomposer.decompose(parse("((hello world) -goodbye) -greeting"));
    //                                                 0123456789012345678901234567890
    assertEquals(expected, decompose);
  }

  public void testSingleValuedConjunctions() {
    Set<Query> expected = new HashSet<>(Arrays.asList(parse("hello", 2), parse("world", 8)));
    assertEquals(expected, decomposer.decompose(parse("+(hello world)")));
  }

  public void testSingleValuedConjunctWithExclusions() {
    Set<Query> expected;
    // need to construct this manually to distribute the query term offsets correctly
    Query expected1 =
        new BooleanQuery.Builder()
            .add(new BooleanClause(new TermQuery(new QueryTerm("field", "hello", 2)), Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 16)), Occur.MUST_NOT))
            .build();
    Query expected2 =
        new BooleanQuery.Builder()
            .add(new BooleanClause(new TermQuery(new QueryTerm("field", "world", 8)), Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 16)), Occur.MUST_NOT))
            .build();
    expected = new HashSet<>(Arrays.asList(expected1, expected2));
    Set<Query> decompose = decomposer.decompose(parse("+(hello world) -goodbye"));
    //                                                 012345678901234567890
    assertEquals(expected, decompose);
  }

  public void testBoostsArePreserved() {
    //    Query test = parse("+(hello world)^0.7 +(foo bar) -(baz bam)");
    //    //                  01234567890123456789012345678901234567890
    //    //                    2     8            21  25     32  36
    //    Query test2 = parse("+(hello world)^0.7 (+(foo bar) -(baz bam))");
    //    //                   01234567890123456789012345678901234567890
    //    //                     2     8             22  26     33  37

    Set<Query> expected =
        new HashSet<>(Arrays.asList(parse("hello^0.7", 2), parse("world^0.7", 8)));
    Set<Query> decompose = decomposer.decompose(parse("+(hello world)^0.7"));
    //                                                 0123456789
    assertEquals(expected, decompose);

    // need to construct this manually to distribute the query term offsets correctly
    Query expected1 =
        new BooleanQuery.Builder()
            .add(
                new BooleanClause(
                    new BoostQuery(new TermQuery(new QueryTerm("field", "hello", 2)), 0.7f),
                    Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 20)), Occur.MUST_NOT))
            .build();
    Query expected2 =
        new BooleanQuery.Builder()
            .add(
                new BooleanClause(
                    new BoostQuery(new TermQuery(new QueryTerm("field", "world", 8)), 0.7f),
                    Occur.MUST))
            .add(
                new BooleanClause(
                    new TermQuery(new QueryTerm("field", "goodbye", 20)), Occur.MUST_NOT))
            .build();
    expected = new HashSet<>(Arrays.asList(expected1, expected2));
    Query toDecomp = parse("+(hello world)^0.7 -goodbye");
    //                      012345678901234567890123456
    decompose = decomposer.decompose(toDecomp);
    assertEquals(expected, decompose);

    expected = new HashSet<>(Arrays.asList(parse("(hello^0.5)^0.8", 1), parse("world^0.8", 12)));
    decompose = decomposer.decompose(parse("+(hello^0.5 world)^0.8"));
    //                                      012345678901234567890
    //                                        2         12
    assertEquals(expected, decompose);
  }

  public void testDisjunctionMaxDecomposition() {
    Query q =
        new DisjunctionMaxQuery(
            Arrays.asList(
                new TermQuery(new QueryTerm("f", "t1", 2)),
                new TermQuery(new QueryTerm("f", "t2", 2))),
            0.1f);
    Set<Query> expected = new HashSet<>(Arrays.asList(parse("f:t1"), parse("f:t2")));
    Set<Query> decompose = decomposer.decompose(q);
    assertEquals(expected, decompose);
  }

  public void testNestedDisjunctionMaxDecomposition() {
    Query q = new DisjunctionMaxQuery(Arrays.asList(parse("hello goodbye"), parse("world")), 0.1f);
    Set<Query> expected =
        new HashSet<>(Arrays.asList(parse("hello"), parse("goodbye", 6), parse("world")));
    Set<Query> decompose = decomposer.decompose(q);
    assertEquals(expected, decompose);
  }

  public void testFilterAndShouldClause() {
    final Query shouldTermQuery = new TermQuery(new QueryTerm("f", "should", 0));
    final Query filterTermQuery = new TermQuery(new QueryTerm("f", "filter", 0));
    Query q =
        new BooleanQuery.Builder()
            .add(shouldTermQuery, Occur.SHOULD)
            .add(filterTermQuery, Occur.FILTER)
            .build();

    assertEquals(Collections.singleton(q), decomposer.decompose(q));
  }
}
