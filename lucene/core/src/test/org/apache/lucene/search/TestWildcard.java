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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.automaton.Operations;

/** TestWildcard tests the '*' and '?' wildcard characters. */
public class TestWildcard extends LuceneTestCase {

  @SuppressWarnings("unlikely-arg-type")
  public void testEquals() {
    WildcardQuery wq1 = new WildcardQuery(new QueryTerm("field", "b*a", 0));
    WildcardQuery wq2 = new WildcardQuery(new QueryTerm("field", "b*a", 0));
    WildcardQuery wq3 = new WildcardQuery(new QueryTerm("field", "b*a", 0));

    // reflexive?
    assertEquals(wq1, wq2);
    assertEquals(wq2, wq1);

    // transitive?
    assertEquals(wq2, wq3);
    assertEquals(wq1, wq3);

    assertFalse(wq1.equals(null));

    FuzzyQuery fq = new FuzzyQuery(new QueryTerm("field", "b*a", 0));
    assertFalse(wq1.equals(fq));
    assertFalse(fq.equals(wq1));
  }

  /**
   * Tests if a WildcardQuery that has no wildcard in the term is rewritten to a single TermQuery.
   * The boost should be preserved, and the rewrite should return a ConstantScoreQuery if the
   * WildcardQuery had a ConstantScore rewriteMethod.
   */
  public void testTermWithoutWildcard() throws IOException {
    Directory indexStore = getIndexStore("field", new String[] {"nowildcard", "nowildcardx"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);

    MultiTermQuery wq = new WildcardQuery(new QueryTerm("field", "nowildcard", 0));
    assertMatches(searcher, wq, 1);

    Query q =
        searcher.rewrite(
            new WildcardQuery(
                new QueryTerm("field", "nowildcard", 0),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.SCORING_BOOLEAN_REWRITE));
    assertTrue(q instanceof TermQuery);

    q =
        searcher.rewrite(
            new WildcardQuery(
                new QueryTerm("field", "nowildcard", 0),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.CONSTANT_SCORE_REWRITE));
    assertTrue(q instanceof MultiTermQueryConstantScoreWrapper);

    q =
        searcher.rewrite(
            new WildcardQuery(
                new QueryTerm("field", "nowildcard", 0),
                Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
                MultiTermQuery.CONSTANT_SCORE_BOOLEAN_REWRITE));
    assertTrue(q instanceof ConstantScoreQuery);
    reader.close();
    indexStore.close();
  }

  /** Tests if a WildcardQuery with an empty term is rewritten to an empty BooleanQuery */
  public void testEmptyTerm() throws IOException {
    Directory indexStore = getIndexStore("field", new String[] {"nowildcard", "nowildcardx"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);

    MultiTermQuery wq =
        new WildcardQuery(
            new QueryTerm("field", "", 0),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT,
            MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    assertMatches(searcher, wq, 0);
    Query q = searcher.rewrite(wq);
    assertTrue(q instanceof MatchNoDocsQuery);
    reader.close();
    indexStore.close();
  }

  /**
   * Tests if a WildcardQuery that has only a trailing * in the term is rewritten to a single
   * PrefixQuery. The boost and rewriteMethod should be preserved.
   */
  public void testPrefixTerm() throws IOException {
    Directory indexStore = getIndexStore("field", new String[] {"prefix", "prefixx"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);

    MultiTermQuery wq = new WildcardQuery(new QueryTerm("field", "prefix*", 0));
    assertMatches(searcher, wq, 2);

    wq = new WildcardQuery(new QueryTerm("field", "*", 0));
    assertMatches(searcher, wq, 2);
    Terms terms = MultiTerms.getTerms(searcher.getIndexReader(), "field");
    assertFalse(wq.getTermsEnum(terms).getClass().getSimpleName().contains("AutomatonTermsEnum"));
    reader.close();
    indexStore.close();
  }

  /** Tests Wildcard queries with an asterisk. */
  public void testAsterisk() throws IOException {
    Directory indexStore = getIndexStore("body", new String[] {"metal", "metals"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);
    Query query1 = new TermQuery(new QueryTerm("body", "metal", 0));
    Query query2 = new WildcardQuery(new QueryTerm("body", "metal*", 0));
    Query query3 = new WildcardQuery(new QueryTerm("body", "m*tal", 0));
    Query query4 = new WildcardQuery(new QueryTerm("body", "m*tal*", 0));
    Query query5 = new WildcardQuery(new QueryTerm("body", "m*tals", 0));

    BooleanQuery.Builder query6 = new BooleanQuery.Builder();
    query6.add(query5, BooleanClause.Occur.SHOULD);

    BooleanQuery.Builder query7 = new BooleanQuery.Builder();
    query7.add(query3, BooleanClause.Occur.SHOULD);
    query7.add(query5, BooleanClause.Occur.SHOULD);

    // Queries do not automatically lower-case search terms:
    Query query8 = new WildcardQuery(new QueryTerm("body", "M*tal*", 0));

    assertMatches(searcher, query1, 1);
    assertMatches(searcher, query2, 2);
    assertMatches(searcher, query3, 1);
    assertMatches(searcher, query4, 2);
    assertMatches(searcher, query5, 1);
    assertMatches(searcher, query6.build(), 1);
    assertMatches(searcher, query7.build(), 2);
    assertMatches(searcher, query8, 0);
    assertMatches(searcher, new WildcardQuery(new QueryTerm("body", "*tall", 0)), 0);
    assertMatches(searcher, new WildcardQuery(new QueryTerm("body", "*tal", 0)), 1);
    assertMatches(searcher, new WildcardQuery(new QueryTerm("body", "*tal*", 0)), 2);
    reader.close();
    indexStore.close();
  }

  /**
   * Tests Wildcard queries with a question mark.
   *
   * @throws IOException if an error occurs
   */
  public void testQuestionmark() throws IOException {
    Directory indexStore =
        getIndexStore("body", new String[] {"metal", "metals", "mXtals", "mXtXls"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);
    Query query1 = new WildcardQuery(new QueryTerm("body", "m?tal", 0));
    Query query2 = new WildcardQuery(new QueryTerm("body", "metal?", 0));
    Query query3 = new WildcardQuery(new QueryTerm("body", "metals?", 0));
    Query query4 = new WildcardQuery(new QueryTerm("body", "m?t?ls", 0));
    Query query5 = new WildcardQuery(new QueryTerm("body", "M?t?ls", 0));
    Query query6 = new WildcardQuery(new QueryTerm("body", "meta??", 0));

    assertMatches(searcher, query1, 1);
    assertMatches(searcher, query2, 1);
    assertMatches(searcher, query3, 0);
    assertMatches(searcher, query4, 3);
    assertMatches(searcher, query5, 0);
    assertMatches(searcher, query6, 1); // Query: 'meta??' matches 'metals' not 'metal'
    reader.close();
    indexStore.close();
  }

  /** Tests if wildcard escaping works */
  public void testEscapes() throws Exception {
    Directory indexStore =
        getIndexStore(
            "field", new String[] {"foo*bar", "foo??bar", "fooCDbar", "fooSOMETHINGbar", "foo\\"});
    IndexReader reader = DirectoryReader.open(indexStore);
    IndexSearcher searcher = newSearcher(reader);

    // without escape: matches foo??bar, fooCDbar, foo*bar, and fooSOMETHINGbar
    WildcardQuery unescaped = new WildcardQuery(new QueryTerm("field", "foo*bar", 0));
    assertMatches(searcher, unescaped, 4);

    // with escape: only matches foo*bar
    WildcardQuery escaped = new WildcardQuery(new QueryTerm("field", "foo\\*bar", 0));
    assertMatches(searcher, escaped, 1);

    // without escape: matches foo??bar and fooCDbar
    unescaped = new WildcardQuery(new QueryTerm("field", "foo??bar", 0));
    assertMatches(searcher, unescaped, 2);

    // with escape: matches foo??bar only
    escaped = new WildcardQuery(new QueryTerm("field", "foo\\?\\?bar", 0));
    assertMatches(searcher, escaped, 1);

    // check escaping at end: lenient parse yields "foo\"
    WildcardQuery atEnd = new WildcardQuery(new QueryTerm("field", "foo\\", 0));
    assertMatches(searcher, atEnd, 1);

    reader.close();
    indexStore.close();
  }

  private Directory getIndexStore(String field, String[] contents) throws IOException {
    Directory indexStore = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), indexStore);
    for (int i = 0; i < contents.length; ++i) {
      Document doc = new Document();
      doc.add(newTextField(field, contents[i], Field.Store.YES));
      writer.addDocument(doc);
    }
    writer.close();

    return indexStore;
  }

  private void assertMatches(IndexSearcher searcher, Query q, int expectedMatches)
      throws IOException {
    ScoreDoc[] result = searcher.search(q, 1000).scoreDocs;
    assertEquals(expectedMatches, result.length);
  }

  /**
   * Test that wild card queries are parsed to the correct type and are searched correctly. This
   * test looks at both parsing and execution of wildcard queries. Although placed here, it also
   * tests prefix queries, verifying that prefix queries are not parsed into wild card queries, and
   * vice-versa.
   */
  public void testParsingAndSearching() throws Exception {
    String field = "content";
    String[] docs = {
      "\\ abcdefg1", "\\79 hijklmn1", "\\\\ opqrstu1",
    };

    // queries that should find all docs
    Query[] matchAll = {
      new WildcardQuery(new QueryTerm(field, "*", 0)),
      new WildcardQuery(new QueryTerm(field, "*1", 0)),
      new WildcardQuery(new QueryTerm(field, "**1", 0)),
      new WildcardQuery(new QueryTerm(field, "*?", 0)),
      new WildcardQuery(new QueryTerm(field, "*?1", 0)),
      new WildcardQuery(new QueryTerm(field, "?*1", 0)),
      new WildcardQuery(new QueryTerm(field, "**", 0)),
      new WildcardQuery(new QueryTerm(field, "***", 0)),
      new WildcardQuery(new QueryTerm(field, "\\\\*", 0))
    };

    // queries that should find no docs
    Query[] matchNone = {
      new WildcardQuery(new QueryTerm(field, "a*h", 0)),
      new WildcardQuery(new QueryTerm(field, "a?h", 0)),
      new WildcardQuery(new QueryTerm(field, "*a*h", 0)),
      new WildcardQuery(new QueryTerm(field, "?a", 0)),
      new WildcardQuery(new QueryTerm(field, "a?", 0))
    };

    PrefixQuery[][] matchOneDocPrefix = {
      {
        new PrefixQuery(new QueryTerm(field, "a", 0)),
        new PrefixQuery(new QueryTerm(field, "ab", 0)),
        new PrefixQuery(new QueryTerm(field, "abc", 0))
      }, // these should find only doc 0
      {
        new PrefixQuery(new QueryTerm(field, "h", 0)),
        new PrefixQuery(new QueryTerm(field, "hi", 0)),
        new PrefixQuery(new QueryTerm(field, "hij", 0)),
        new PrefixQuery(new QueryTerm(field, "\\7", 0))
      }, // these should find only doc 1
      {
        new PrefixQuery(new QueryTerm(field, "o", 0)),
        new PrefixQuery(new QueryTerm(field, "op", 0)),
        new PrefixQuery(new QueryTerm(field, "opq", 0)),
        new PrefixQuery(new QueryTerm(field, "\\\\", 0))
      }, // these should find only doc 2
    };

    WildcardQuery[][] matchOneDocWild = {
      {
        new WildcardQuery(new QueryTerm(field, "*a*", 0)), // these should find only doc 0
        new WildcardQuery(new QueryTerm(field, "*ab*", 0)),
        new WildcardQuery(new QueryTerm(field, "*abc**", 0)),
        new WildcardQuery(new QueryTerm(field, "ab*e*", 0)),
        new WildcardQuery(new QueryTerm(field, "*g?", 0)),
        new WildcardQuery(new QueryTerm(field, "*f?1", 0))
      },
      {
        new WildcardQuery(new QueryTerm(field, "*h*", 0)), // these should find only doc 1
        new WildcardQuery(new QueryTerm(field, "*hi*", 0)),
        new WildcardQuery(new QueryTerm(field, "*hij**", 0)),
        new WildcardQuery(new QueryTerm(field, "hi*k*", 0)),
        new WildcardQuery(new QueryTerm(field, "*n?", 0)),
        new WildcardQuery(new QueryTerm(field, "*m?1", 0)),
        new WildcardQuery(new QueryTerm(field, "hij**", 0))
      },
      {
        new WildcardQuery(new QueryTerm(field, "*o*", 0)), // these should find only doc 2
        new WildcardQuery(new QueryTerm(field, "*op*", 0)),
        new WildcardQuery(new QueryTerm(field, "*opq**", 0)),
        new WildcardQuery(new QueryTerm(field, "op*q*", 0)),
        new WildcardQuery(new QueryTerm(field, "*u?", 0)),
        new WildcardQuery(new QueryTerm(field, "*t?1", 0)),
        new WildcardQuery(new QueryTerm(field, "opq**", 0))
      }
    };

    // prepare the index
    Directory dir = newDirectory();
    RandomIndexWriter iw =
        new RandomIndexWriter(
            random(),
            dir,
            newIndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(newLogMergePolicy()));
    for (int i = 0; i < docs.length; i++) {
      Document doc = new Document();
      doc.add(newTextField(field, docs[i], Field.Store.NO));
      iw.addDocument(doc);
    }
    iw.close();

    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);

    // test queries that must find all
    for (Query q : matchAll) {
      if (VERBOSE) System.out.println("matchAll: q=" + q + " " + q.getClass().getName());
      ScoreDoc[] hits = searcher.search(q, 1000).scoreDocs;
      assertEquals(docs.length, hits.length);
    }

    // test queries that must find none
    for (Query q : matchNone) {
      if (VERBOSE) System.out.println("matchNone: q=" + q + " " + q.getClass().getName());
      ScoreDoc[] hits = searcher.search(q, 1000).scoreDocs;
      assertEquals(0, hits.length);
    }

    // thest the prefi queries find only one doc
    for (int i = 0; i < matchOneDocPrefix.length; i++) {
      for (int j = 0; j < matchOneDocPrefix[i].length; j++) {
        Query q = matchOneDocPrefix[i][j];
        if (VERBOSE)
          System.out.println(
              "match 1 prefix: doc=" + docs[i] + " q=" + q + " " + q.getClass().getName());
        ScoreDoc[] hits = searcher.search(q, 1000).scoreDocs;
        assertEquals(1, hits.length);
        assertEquals(i, hits[0].doc);
      }
    }

    // test the wildcard queries find only one doc
    for (int i = 0; i < matchOneDocWild.length; i++) {
      for (int j = 0; j < matchOneDocWild[i].length; j++) {
        Query q = matchOneDocWild[i][j];
        if (VERBOSE)
          System.out.println(
              "match 1 wild: doc=" + docs[i] + " q=" + q + " " + q.getClass().getName());
        ScoreDoc[] hits = searcher.search(q, 1000).scoreDocs;
        assertEquals(1, hits.length);
        assertEquals(i, hits[0].doc);
      }
    }

    reader.close();
    dir.close();
  }
}
