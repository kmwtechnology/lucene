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
package org.apache.lucene.queryparser.simple;

import static org.apache.lucene.queryparser.simple.SimpleQueryParser.AND_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.ESCAPE_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.FUZZY_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.NEAR_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.NOT_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.OR_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.PHRASE_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.PRECEDENCE_OPERATORS;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.PREFIX_OPERATOR;
import static org.apache.lucene.queryparser.simple.SimpleQueryParser.WHITESPACE_OPERATOR;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.automaton.LevenshteinAutomata;

/** Tests for {@link SimpleQueryParser} */
public class TestSimpleQueryParser extends LuceneTestCase {

  /**
   * helper to parse a query with whitespace+lowercase analyzer across "field", with default
   * operator of MUST
   */
  private Query parse(String text) {
    Analyzer analyzer = new MockAnalyzer(random());
    SimpleQueryParser parser = new SimpleQueryParser(analyzer, "field");
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  /**
   * helper to parse a query with whitespace+lowercase analyzer across "field", with default
   * operator of MUST
   */
  private Query parse(String text, int flags) {
    Analyzer analyzer = new MockAnalyzer(random());
    SimpleQueryParser parser =
        new SimpleQueryParser(analyzer, Collections.singletonMap("field", 1f), flags);
    parser.setDefaultOperator(Occur.MUST);
    return parser.parse(text);
  }

  /** test a simple term */
  public void testTerm() throws Exception {
    Query expected = new TermQuery(new QueryTerm("field", "foobar", 0));

    assertEquals(expected, parse("foobar"));
  }

  /** test a fuzzy query */
  public void testFuzzy() throws Exception {
    Query regular = new TermQuery(new QueryTerm("field", "foobar", 0));
    Query expected = new FuzzyQuery(new QueryTerm("field", "foobar", 0), 2);

    Query parse = parse("foobar~2");
    assertEquals(expected, parse);
    assertEquals(expected, parse("foobar~"));
    assertEquals(regular, parse("foobar~a"));
    assertEquals(regular, parse("foobar~1a"));

    BooleanQuery.Builder bool = new BooleanQuery.Builder();
    FuzzyQuery fuzzy =
        new FuzzyQuery(
            new QueryTerm("field", "foo", 0), LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE);
    bool.add(fuzzy, Occur.MUST);
    bool.add(new TermQuery(new QueryTerm("field", "bar", 7)), Occur.MUST);

    Query parse1 = parse("foo~" + LevenshteinAutomata.MAXIMUM_SUPPORTED_DISTANCE + 1 + " bar");
    BooleanQuery build = bool.build();
    assertEquals(build, parse1);
  }

  /** test a simple phrase */
  public void testPhrase() throws Exception {
    PhraseQuery expected = new PhraseQuery("field", new int[] {1, 5}, "foo", "bar");

    assertEquals(expected, parse("\"foo bar\""));
  }

  /** test a simple phrase with various slop settings */
  public void testPhraseWithSlop() throws Exception {
    PhraseQuery expectedWithSlop = new PhraseQuery(2, "field", new int[] {1, 5}, "foo", "bar");

    Query parse = parse("\"foo bar\"~2");
    assertEquals(expectedWithSlop, parse);

    PhraseQuery expectedWithMultiDigitSlop =
        new PhraseQuery(10, "field", new int[] {1, 5}, "foo", "bar");

    assertEquals(expectedWithMultiDigitSlop, parse("\"foo bar\"~10"));

    PhraseQuery expectedNoSlop = new PhraseQuery("field", new int[] {1, 5}, "foo", "bar");

    assertEquals("Ignore trailing tilde with no slop", expectedNoSlop, parse("\"foo bar\"~"));
    assertEquals("Ignore non-numeric trailing slop", expectedNoSlop, parse("\"foo bar\"~a"));
    assertEquals("Ignore non-numeric trailing slop", expectedNoSlop, parse("\"foo bar\"~1a"));
    assertEquals("Ignore negative trailing slop", expectedNoSlop, parse("\"foo bar\"~-1"));

    PhraseQuery pq = new PhraseQuery(12, "field", new int[] {1, 5}, "foo", "bar");

    BooleanQuery.Builder expectedBoolean = new BooleanQuery.Builder();
    expectedBoolean.add(pq, Occur.MUST);
    expectedBoolean.add(new TermQuery(new QueryTerm("field", "baz", 13)), Occur.MUST);

    assertEquals(expectedBoolean.build(), parse("\"foo bar\"~12 baz"));
  }

  /** test a simple prefix */
  public void testPrefix() throws Exception {
    PrefixQuery expected = new PrefixQuery(new QueryTerm("field", "foobar", 0));

    assertEquals(expected, parse("foobar*"));
  }

  /** test some AND'd terms using '+' operator */
  public void testAND() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "foo", 0)), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "bar", 4)), Occur.MUST);

    Query parse = parse("foo+bar");
    BooleanQuery build = expected.build();
    assertEquals(build, parse);
  }

  /** test some AND'd phrases using '+' operator */
  public void testANDPhrase() throws Exception {
    PhraseQuery phrase1 = new PhraseQuery("field", new int[] {1, 5}, "foo", "bar");
    PhraseQuery phrase2 = new PhraseQuery("field", new int[] {11, 16}, "star", "wars");
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(phrase1, Occur.MUST);
    expected.add(phrase2, Occur.MUST);

    BooleanQuery build = expected.build();
    Query parse = parse("\"foo bar\"+\"star wars\"");
    assertEquals(build, parse);
  }

  /** test some AND'd terms (just using whitespace) */
  public void testANDImplicit() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "foo", 0)), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "bar", 4)), Occur.MUST);

    assertEquals(expected.build(), parse("foo bar"));
  }

  /** test some OR'd terms */
  public void testOR() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "foo", 0)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "bar", 4)), Occur.SHOULD);

    BooleanQuery.Builder expectedDoubleOr = new BooleanQuery.Builder();
    expectedDoubleOr.add(new TermQuery(new QueryTerm("field", "foo", 0)), Occur.SHOULD);
    expectedDoubleOr.add(new TermQuery(new QueryTerm("field", "bar", 5)), Occur.SHOULD);

    Query parse = parse("foo|bar");
    BooleanQuery build = expected.build();
    assertEquals(build, parse);
    assertEquals(expectedDoubleOr.build(), parse("foo||bar"));
  }

  /** test some OR'd terms (just using whitespace) */
  public void testORImplicit() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "foo", 0)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "bar", 4)), Occur.SHOULD);

    SimpleQueryParser parser = new SimpleQueryParser(new MockAnalyzer(random()), "field");
    assertEquals(expected.build(), parser.parse("foo bar"));
  }

  /** test some OR'd phrases using '|' operator */
  public void testORPhrase() throws Exception {
    PhraseQuery phrase1 = new PhraseQuery("field", new int[] {1, 5}, "foo", "bar");
    PhraseQuery phrase2 = new PhraseQuery("field", new int[] {11, 16}, "star", "wars");
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(phrase1, Occur.SHOULD);
    expected.add(phrase2, Occur.SHOULD);

    assertEquals(expected.build(), parse("\"foo bar\"|\"star wars\""));
  }

  /** test negated term */
  public void testNOT() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "foo", 1)), Occur.MUST_NOT);
    expected.add(new MatchAllDocsQuery(), Occur.SHOULD);
    BooleanQuery.Builder expectedParen = new BooleanQuery.Builder();
    expectedParen.add(new TermQuery(new QueryTerm("field", "foo", 2)), Occur.MUST_NOT);
    expectedParen.add(new MatchAllDocsQuery(), Occur.SHOULD);
    BooleanQuery.Builder expected3 = new BooleanQuery.Builder();
    expected3.add(new TermQuery(new QueryTerm("field", "foo", 3)), Occur.MUST_NOT);
    expected3.add(new MatchAllDocsQuery(), Occur.SHOULD);
    assertEquals(expected.build(), parse("-foo"));
    assertEquals(expectedParen.build(), parse("-(foo)"));
    assertEquals(expected3.build(), parse("---foo"));
  }

  /** test crazy prefixes with multiple asterisks */
  public void testCrazyPrefixes1() throws Exception {
    Query expected = new PrefixQuery(new QueryTerm("field", "st*ar", 0));

    Query parse = parse("st*ar*");
    assertEquals(expected, parse);
  }

  /** test prefixes with some escaping */
  public void testCrazyPrefixes2() throws Exception {
    Query expected = new PrefixQuery(new QueryTerm("field", "st*ar\\*", 0));

    Query parse = parse("st*ar\\\\**");
    assertEquals(expected, parse);
  }

  /** not a prefix query! the prefix operator is escaped */
  public void testTermInDisguise() throws Exception {
    Query expected = new TermQuery(new QueryTerm("field", "st*ar\\*", 0));

    assertEquals(expected, parse("sT*Ar\\\\\\*"));
  }

  // a number of test cases here have garbage/errors in
  // the syntax passed in to test that the query can
  // still be interpreted as a guess to what the human
  // input was trying to be

  public void testGarbageTerm() throws Exception {
    Query expected = new TermQuery(new QueryTerm("field", "star", 0));
    Query expectedLeading1 = new TermQuery(new QueryTerm("field", "star", 5));
    Query expectedLeading2 = new TermQuery(new QueryTerm("field", "star", 9));

    assertEquals(expected, parse("star"));
    assertEquals(expected, parse("star\n"));
    assertEquals(expected, parse("star\r"));
    assertEquals(expected, parse("star\t"));
    assertEquals(expected, parse("star("));
    assertEquals(expected, parse("star)"));
    assertEquals(expected, parse("star\""));
    assertEquals(expectedLeading1, parse("\t \r\n\nstar   \n \r \t "));
    assertEquals(expectedLeading2, parse("- + \"\" - star \\"));
  }

  public void testGarbageEmpty() throws Exception {
    MatchNoDocsQuery expected = new MatchNoDocsQuery();

    assertEquals(expected, parse(""));
    assertEquals(expected, parse("  "));
    assertEquals(expected, parse("  "));
    assertEquals(expected, parse("\\ "));
    assertEquals(expected, parse("\\ \\ "));
    assertEquals(expected, parse("\"\""));
    assertEquals(expected, parse("\" \""));
    assertEquals(expected, parse("\" \"|\" \""));
    assertEquals(expected, parse("(\" \"|\" \")"));
    assertEquals(expected, parse("\" \" \" \""));
    assertEquals(expected, parse("(\" \" \" \")"));
  }

  public void testGarbageAND() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);

    BooleanQuery.Builder expectedSpaces = new BooleanQuery.Builder();
    expectedSpaces.add(new TermQuery(new QueryTerm("field", "star", 5)), Occur.MUST);
    expectedSpaces.add(new TermQuery(new QueryTerm("field", "wars", 14)), Occur.MUST);

    BooleanQuery.Builder expectedFloatingPlus = new BooleanQuery.Builder();
    expectedFloatingPlus.add(new TermQuery(new QueryTerm("field", "star", 5)), Occur.MUST);
    expectedFloatingPlus.add(new TermQuery(new QueryTerm("field", "wars", 15)), Occur.MUST);

    BooleanQuery.Builder expectedMultipleFloaters = new BooleanQuery.Builder();
    expectedMultipleFloaters.add(new TermQuery(new QueryTerm("field", "star", 8)), Occur.MUST);
    expectedMultipleFloaters.add(new TermQuery(new QueryTerm("field", "wars", 21)), Occur.MUST);

    assertEquals(expected.build(), parse("star wars"));
    assertEquals(expected.build(), parse("star+wars"));
    assertEquals(expectedSpaces.build(), parse("     star     wars   "));
    assertEquals(expectedFloatingPlus.build(), parse("     star +    wars   "));
    assertEquals(expectedMultipleFloaters.build(), parse("  |     star + + |   wars   "));
    assertEquals(expectedMultipleFloaters.build(), parse("  |     star + + |   wars   \\"));
  }

  public void testGarbageOR() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.SHOULD);

    BooleanQuery.Builder expected2 = new BooleanQuery.Builder();
    expected2.add(new TermQuery(new QueryTerm("field", "star", 5)), Occur.SHOULD);
    expected2.add(new TermQuery(new QueryTerm("field", "wars", 15)), Occur.SHOULD);

    BooleanQuery.Builder expected3 = new BooleanQuery.Builder();
    expected3.add(new TermQuery(new QueryTerm("field", "star", 8)), Occur.SHOULD);
    expected3.add(new TermQuery(new QueryTerm("field", "wars", 21)), Occur.SHOULD);

    assertEquals(expected.build(), parse("star|wars"));
    assertEquals(expected2.build(), parse("     star |    wars   "));
    assertEquals(expected3.build(), parse("  |     star | + |   wars   "));
    assertEquals(expected3.build(), parse("  +     star | + +   wars   \\"));
  }

  public void testGarbageNOT() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST_NOT);
    expected.add(new MatchAllDocsQuery(), Occur.SHOULD);
    BooleanQuery.Builder expected2 = new BooleanQuery.Builder();
    expected2.add(new TermQuery(new QueryTerm("field", "star", 3)), Occur.MUST_NOT);
    expected2.add(new MatchAllDocsQuery(), Occur.SHOULD);

    assertEquals(expected.build(), parse("-star"));
    assertEquals(expected2.build(), parse("---star"));
    assertEquals(expected2.build(), parse("- -star -"));
  }

  public void testGarbagePhrase() throws Exception {
    PhraseQuery expected = new PhraseQuery("field", new int[] {1, 6}, "star", "wars");
    PhraseQuery expected2 = new PhraseQuery("field", new int[] {6, 11}, "star", "wars");
    PhraseQuery expected3 = new PhraseQuery("field", new int[] {11, 16}, "star", "wars");

    assertEquals(expected, parse("\"star wars\""));
    assertEquals(expected, parse("\"star wars\\ \""));
    assertEquals(expected2, parse("\"\" | \"star wars\""));
    assertEquals(expected3, parse("          \"star wars\"        \"\"\\"));
  }

  public void testGarbageSubquery() throws Exception {
    Query expected = new TermQuery(new QueryTerm("field", "star", 1));
    Query expected2 = new TermQuery(new QueryTerm("field", "star", 2));
    Query expected3 = new TermQuery(new QueryTerm("field", "star", 9));
    Query expected4 = new TermQuery(new QueryTerm("field", "star", 19));

    assertEquals(expected, parse("(star)"));
    assertEquals(expected, parse("(star))"));
    assertEquals(expected2, parse("((star)"));
    assertEquals(expected3, parse("     -()(star)        \n\n\r     "));
    assertEquals(expected4, parse("| + - ( + - |      star    \n      ) \n"));
  }

  public void testCompoundAnd() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 10)), Occur.MUST);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    expectedPlus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.MUST);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "star", 5)), Occur.MUST);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "wars", 10)), Occur.MUST);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 15)), Occur.MUST);
    assertEquals(expected.build(), parse("star wars empire"));
    assertEquals(expectedPlus.build(), parse("star+wars + empire"));
    assertEquals(expectedGarbage.build(), parse(" | --star wars empire \n\\"));
  }

  public void testCompoundOr() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 10)), Occur.SHOULD);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    expectedPlus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "star", 5)), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "wars", 10)), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 15)), Occur.SHOULD);

    assertEquals(expected.build(), parse("star|wars|empire"));
    assertEquals(expectedPlus.build(), parse("star|wars | empire"));
    assertEquals(expectedGarbage.build(), parse(" | --star|wars|empire \n\\"));
  }

  public void testComplex00() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    inner.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.SHOULD);
    inner.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.SHOULD);
    BooleanQuery builtInner = inner.build();
    expected.add(builtInner, Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 10)), Occur.MUST);

    BooleanQuery.Builder expectedFloatingPlus = new BooleanQuery.Builder();
    expectedFloatingPlus.add(builtInner, Occur.MUST);
    expectedFloatingPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.MUST);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder innerWithJunk = new BooleanQuery.Builder();
    innerWithJunk.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.SHOULD);
    innerWithJunk.add(new TermQuery(new QueryTerm("field", "wars", 8)), Occur.SHOULD);
    expectedGarbage.add(innerWithJunk.build(), Occur.MUST);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 19)), Occur.MUST);

    assertEquals(expected.build(), parse("star|wars empire"));
    assertEquals(expectedFloatingPlus.build(), parse("star|wars + empire"));
    assertEquals(expectedGarbage.build(), parse("star| + wars + ----empire |"));
  }

  public void testComplex01() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    inner.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    inner.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);
    expected.add(inner.build(), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder innerPlus = new BooleanQuery.Builder();
    innerPlus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    innerPlus.add(new TermQuery(new QueryTerm("field", "wars", 7)), Occur.MUST);
    expectedPlus.add(innerPlus.build(), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder innerGarbage = new BooleanQuery.Builder();
    innerGarbage.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    innerGarbage.add(new TermQuery(new QueryTerm("field", "wars", 9)), Occur.MUST);
    expectedGarbage.add(innerGarbage.build(), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 20)), Occur.SHOULD);

    assertEquals(expected.build(), parse("star wars | empire"));
    assertEquals(expectedPlus.build(), parse("star + wars|empire"));
    assertEquals(expectedGarbage.build(), parse("star + | wars | ----empire +"));
  }

  public void testComplex02() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    inner.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    inner.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);
    expected.add(inner.build(), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "strikes", 21)), Occur.SHOULD);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder innerPlus = new BooleanQuery.Builder();
    innerPlus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    innerPlus.add(new TermQuery(new QueryTerm("field", "wars", 7)), Occur.MUST);
    expectedPlus.add(innerPlus.build(), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "strikes", 21)), Occur.SHOULD);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder innerGarbage = new BooleanQuery.Builder();
    innerGarbage.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    innerGarbage.add(new TermQuery(new QueryTerm("field", "wars", 9)), Occur.MUST);
    expectedGarbage.add(innerGarbage.build(), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 20)), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "strikes", 33)), Occur.SHOULD);

    assertEquals(expected.build(), parse("star wars | empire | strikes"));
    assertEquals(expectedPlus.build(), parse("star + wars|empire | strikes"));
    assertEquals(expectedGarbage.build(), parse("star + | wars | ----empire | + --strikes \\"));
    //                                                01234567890123456789012345678901234567890
  }

  public void testComplex03() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2 = new BooleanQuery.Builder();
    inner2.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    inner2.add(new TermQuery(new QueryTerm("field", "wars", 5)), Occur.MUST);
    inner.add(inner2.build(), Occur.SHOULD);
    inner.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);
    inner.add(new TermQuery(new QueryTerm("field", "strikes", 21)), Occur.SHOULD);
    expected.add(inner.build(), Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "back", 29)), Occur.MUST);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder innerPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Plus = new BooleanQuery.Builder();
    inner2Plus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    inner2Plus.add(new TermQuery(new QueryTerm("field", "wars", 7)), Occur.MUST);
    innerPlus.add(inner2Plus.build(), Occur.SHOULD);
    innerPlus.add(new TermQuery(new QueryTerm("field", "empire", 12)), Occur.SHOULD);
    innerPlus.add(new TermQuery(new QueryTerm("field", "strikes", 21)), Occur.SHOULD);
    expectedPlus.add(innerPlus.build(), Occur.MUST);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "back", 31)), Occur.MUST);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder innerGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Garbage = new BooleanQuery.Builder();
    inner2Garbage.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);
    inner2Garbage.add(new TermQuery(new QueryTerm("field", "wars", 9)), Occur.MUST);
    innerGarbage.add(inner2Garbage.build(), Occur.SHOULD);
    innerGarbage.add(new TermQuery(new QueryTerm("field", "empire", 20)), Occur.SHOULD);
    innerGarbage.add(new TermQuery(new QueryTerm("field", "strikes", 33)), Occur.SHOULD);
    expectedGarbage.add(innerGarbage.build(), Occur.MUST);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "back", 47)), Occur.MUST);

    assertEquals(expected.build(), parse("star wars | empire | strikes back"));
    assertEquals(expectedPlus.build(), parse("star + wars|empire | strikes + back"));
    assertEquals(
        expectedGarbage.build(), parse("star + | wars | ----empire | + --strikes + | --back \\"));
    //                                         012345678901234567890123456789012345678901234567890
  }

  public void testComplex04() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2 = new BooleanQuery.Builder();
    inner.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    inner.add(new TermQuery(new QueryTerm("field", "wars", 6)), Occur.MUST);
    inner2.add(new TermQuery(new QueryTerm("field", "strikes", 24)), Occur.MUST);
    inner2.add(new TermQuery(new QueryTerm("field", "back", 32)), Occur.MUST);
    expected.add(inner.build(), Occur.SHOULD);
    expected.add(new TermQuery(new QueryTerm("field", "empire", 14)), Occur.SHOULD);
    expected.add(inner2.build(), Occur.SHOULD);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder innerPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Plus = new BooleanQuery.Builder();
    innerPlus.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    innerPlus.add(new TermQuery(new QueryTerm("field", "wars", 8)), Occur.MUST);
    inner2Plus.add(new TermQuery(new QueryTerm("field", "strikes", 25)), Occur.MUST);
    inner2Plus.add(new TermQuery(new QueryTerm("field", "back", 35)), Occur.MUST);
    expectedPlus.add(innerPlus.build(), Occur.SHOULD);
    expectedPlus.add(new TermQuery(new QueryTerm("field", "empire", 15)), Occur.SHOULD);
    expectedPlus.add(inner2Plus.build(), Occur.SHOULD);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder innerGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Garbage = new BooleanQuery.Builder();
    innerGarbage.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    innerGarbage.add(new TermQuery(new QueryTerm("field", "wars", 10)), Occur.MUST);
    inner2Garbage.add(new TermQuery(new QueryTerm("field", "strikes", 38)), Occur.MUST);
    inner2Garbage.add(new TermQuery(new QueryTerm("field", "back", 52)), Occur.MUST);
    expectedGarbage.add(innerGarbage.build(), Occur.SHOULD);
    expectedGarbage.add(new TermQuery(new QueryTerm("field", "empire", 24)), Occur.SHOULD);
    expectedGarbage.add(inner2Garbage.build(), Occur.SHOULD);

    assertEquals(expected.build(), parse("(star wars) | empire | (strikes back)"));
    assertEquals(expectedPlus.build(), parse("(star + wars) |empire | (strikes + back)"));
    assertEquals(
        expectedGarbage.build(),
        parse("(star + | wars |) | ----empire | + --(strikes + | --back) \\"));
    //
    // 0123456789012345678901234567890123456789012345678901234567 890
  }

  public void testComplex05() throws Exception {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1 = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2 = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3 = new BooleanQuery.Builder();
    BooleanQuery.Builder inner4 = new BooleanQuery.Builder();

    inner1.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    inner1.add(new TermQuery(new QueryTerm("field", "wars", 6)), Occur.MUST);

    inner2.add(new TermQuery(new QueryTerm("field", "empire", 15)), Occur.SHOULD);

    inner3.add(new TermQuery(new QueryTerm("field", "strikes", 25)), Occur.MUST);
    inner3.add(new TermQuery(new QueryTerm("field", "back", 33)), Occur.MUST);

    inner4.add(new TermQuery(new QueryTerm("field", "jarjar", 39)), Occur.MUST_NOT);
    inner4.add(new MatchAllDocsQuery(), Occur.SHOULD);

    inner3.add(inner4.build(), Occur.MUST);
    inner2.add(inner3.build(), Occur.SHOULD);

    expected.add(inner1.build(), Occur.SHOULD);
    expected.add(inner2.build(), Occur.SHOULD);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1Plus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Plus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3Plus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner4Plus = new BooleanQuery.Builder();

    inner1Plus.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    inner1Plus.add(new TermQuery(new QueryTerm("field", "wars", 8)), Occur.MUST);

    inner2Plus.add(new TermQuery(new QueryTerm("field", "empire", 16)), Occur.SHOULD);

    inner3Plus.add(new TermQuery(new QueryTerm("field", "strikes", 26)), Occur.MUST);
    inner3Plus.add(new TermQuery(new QueryTerm("field", "back", 36)), Occur.MUST);

    inner4Plus.add(new TermQuery(new QueryTerm("field", "jarjar", 42)), Occur.MUST_NOT);
    inner4Plus.add(new MatchAllDocsQuery(), Occur.SHOULD);

    inner3Plus.add(inner4Plus.build(), Occur.MUST);
    inner2Plus.add(inner3Plus.build(), Occur.SHOULD);

    expectedPlus.add(inner1Plus.build(), Occur.SHOULD);
    expectedPlus.add(inner2Plus.build(), Occur.SHOULD);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1Garbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Garbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3Garbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner4Garbage = new BooleanQuery.Builder();

    inner1Garbage.add(new TermQuery(new QueryTerm("field", "star", 1)), Occur.MUST);
    inner1Garbage.add(new TermQuery(new QueryTerm("field", "wars", 10)), Occur.MUST);

    inner2Garbage.add(new TermQuery(new QueryTerm("field", "empire", 25)), Occur.SHOULD);

    inner3Garbage.add(new TermQuery(new QueryTerm("field", "strikes", 39)), Occur.MUST);
    inner3Garbage.add(new TermQuery(new QueryTerm("field", "back", 53)), Occur.MUST);

    inner4Garbage.add(new TermQuery(new QueryTerm("field", "jarjar", 61)), Occur.MUST_NOT);
    inner4Garbage.add(new MatchAllDocsQuery(), Occur.SHOULD);

    inner3Garbage.add(inner4Garbage.build(), Occur.MUST);
    inner2Garbage.add(inner3Garbage.build(), Occur.SHOULD);

    expectedGarbage.add(inner1Garbage.build(), Occur.SHOULD);
    expectedGarbage.add(inner2Garbage.build(), Occur.SHOULD);

    assertEquals(expected.build(), parse("(star wars) | (empire | (strikes back -jarjar))"));
    assertEquals(
        expectedPlus.build(), parse("(star + wars) |(empire | (strikes + back -jarjar) () )"));
    //                                         012345678901234567890123456789012345678901234567890
    Query parse =
        parse("(star + | wars |) | --(--empire | + --(strikes + | --back + -jarjar) \"\" ) \"");
    //                        012345678901234567890123456789012345678901234567890123456789012345678
    // 9 0123 456789
    //                        0         1         2         3         4         5         6
    BooleanQuery build = expectedGarbage.build();
    assertEquals(build, parse);
  }

  public void testComplex06() {
    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1 = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2 = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3 = new BooleanQuery.Builder();

    expected.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);

    inner1.add(new TermQuery(new QueryTerm("field", "wars", 6)), Occur.SHOULD);

    inner3.add(new TermQuery(new QueryTerm("field", "empire", 14)), Occur.SHOULD);
    inner3.add(new TermQuery(new QueryTerm("field", "strikes", 23)), Occur.SHOULD);
    inner2.add(inner3.build(), Occur.MUST);

    inner2.add(new TermQuery(new QueryTerm("field", "back", 31)), Occur.MUST);
    inner2.add(new TermQuery(new QueryTerm("field", "jar+|jar", 36)), Occur.MUST);
    inner1.add(inner2.build(), Occur.SHOULD);

    expected.add(inner1.build(), Occur.MUST);

    BooleanQuery.Builder expectedPlus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1Plus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Plus = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3Plus = new BooleanQuery.Builder();

    expectedPlus.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);

    inner1Plus.add(new TermQuery(new QueryTerm("field", "wars", 8)), Occur.SHOULD);

    inner3Plus.add(new TermQuery(new QueryTerm("field", "empire", 15)), Occur.SHOULD);
    inner3Plus.add(new TermQuery(new QueryTerm("field", "strikes", 24)), Occur.SHOULD);
    inner2Plus.add(inner3Plus.build(), Occur.MUST);

    inner2Plus.add(new TermQuery(new QueryTerm("field", "back", 34)), Occur.MUST);
    inner2Plus.add(new TermQuery(new QueryTerm("field", "jar+|jar", 39)), Occur.MUST);
    inner1Plus.add(inner2Plus.build(), Occur.SHOULD);

    expectedPlus.add(inner1Plus.build(), Occur.MUST);

    BooleanQuery.Builder expectedGarbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner1Garbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner2Garbage = new BooleanQuery.Builder();
    BooleanQuery.Builder inner3Garbage = new BooleanQuery.Builder();

    expectedGarbage.add(new TermQuery(new QueryTerm("field", "star", 0)), Occur.MUST);

    inner1Garbage.add(new TermQuery(new QueryTerm("field", "wars", 10)), Occur.SHOULD);

    inner3Garbage.add(new TermQuery(new QueryTerm("field", "empire", 24)), Occur.SHOULD);
    inner3Garbage.add(new TermQuery(new QueryTerm("field", "strikes", 37)), Occur.SHOULD);
    inner2Garbage.add(inner3Garbage.build(), Occur.MUST);

    inner2Garbage.add(new TermQuery(new QueryTerm("field", "back", 51)), Occur.MUST);
    inner2Garbage.add(new TermQuery(new QueryTerm("field", "jar+|jar", 58)), Occur.MUST);
    inner1Garbage.add(inner2Garbage.build(), Occur.SHOULD);

    expectedGarbage.add(inner1Garbage.build(), Occur.MUST);
    assertEquals(expected.build(), parse("star (wars | (empire | strikes back jar\\+\\|jar))"));
    assertEquals(
        expectedPlus.build(), parse("star + (wars |(empire | strikes + back jar\\+\\|jar) () )"));
    assertEquals(
        expectedGarbage.build(),
        parse("star + (| wars | | --(--empire | + --strikes + | --back + jar\\+\\|jar) \"\" ) \""));
  }

  /** test a term with field weights */
  public void testWeightedTerm() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field0", 5f);
    weights.put("field1", 10f);

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    Query field0 = new TermQuery(new QueryTerm("field0", "foo", 0));
    field0 = new BoostQuery(field0, 5f);
    expected.add(field0, Occur.SHOULD);
    Query field1 = new TermQuery(new QueryTerm("field1", "foo", 0));
    field1 = new BoostQuery(field1, 10f);
    expected.add(field1, Occur.SHOULD);

    Analyzer analyzer = new MockAnalyzer(random());
    SimpleQueryParser parser = new SimpleQueryParser(analyzer, weights);
    assertEquals(expected.build(), parser.parse("foo"));
  }

  /** test a more complex query with field weights */
  public void testWeightedOR() throws Exception {
    Map<String, Float> weights = new LinkedHashMap<>();
    weights.put("field0", 5f);
    weights.put("field1", 10f);

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    BooleanQuery.Builder foo = new BooleanQuery.Builder();
    Query field0 = new TermQuery(new QueryTerm("field0", "foo", 0));
    field0 = new BoostQuery(field0, 5f);
    foo.add(field0, Occur.SHOULD);
    Query field1 = new TermQuery(new QueryTerm("field1", "foo", 0));
    field1 = new BoostQuery(field1, 10f);
    foo.add(field1, Occur.SHOULD);
    expected.add(foo.build(), Occur.SHOULD);

    BooleanQuery.Builder bar = new BooleanQuery.Builder();
    field0 = new TermQuery(new QueryTerm("field0", "bar", 4));
    field0 = new BoostQuery(field0, 5f);
    bar.add(field0, Occur.SHOULD);
    field1 = new TermQuery(new QueryTerm("field1", "bar", 4));
    field1 = new BoostQuery(field1, 10f);
    bar.add(field1, Occur.SHOULD);
    expected.add(bar.build(), Occur.SHOULD);

    Analyzer analyzer = new MockAnalyzer(random());
    SimpleQueryParser parser = new SimpleQueryParser(analyzer, weights);
    assertEquals(expected.build(), parser.parse("foo|bar"));
  }

  /** helper to parse a query with keyword analyzer across "field" */
  private Query parseKeyword(String text, int flags) {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    SimpleQueryParser parser =
        new SimpleQueryParser(analyzer, Collections.singletonMap("field", 1f), flags);
    return parser.parse(text);
  }

  /** test the ability to enable/disable phrase operator */
  public void testDisablePhrase() {
    Query expected = new TermQuery(new QueryTerm("field", "\"test\"", 0));
    assertEquals(expected, parseKeyword("\"test\"", ~PHRASE_OPERATOR));
  }

  /** test the ability to enable/disable prefix operator */
  public void testDisablePrefix() {
    Query expected = new TermQuery(new QueryTerm("field", "test*", 0));
    assertEquals(expected, parseKeyword("test*", ~PREFIX_OPERATOR));
  }

  /** test the ability to enable/disable AND operator */
  public void testDisableAND() {
    Query expected = new TermQuery(new QueryTerm("field", "foo+bar", 0));
    assertEquals(expected, parseKeyword("foo+bar", ~AND_OPERATOR));
    expected = new TermQuery(new QueryTerm("field", "+foo+bar", 0));
    assertEquals(expected, parseKeyword("+foo+bar", ~AND_OPERATOR));
  }

  /** test the ability to enable/disable OR operator */
  public void testDisableOR() {
    Query expected = new TermQuery(new QueryTerm("field", "foo|bar", 0));
    assertEquals(expected, parseKeyword("foo|bar", ~OR_OPERATOR));
    expected = new TermQuery(new QueryTerm("field", "|foo|bar", 0));
    assertEquals(expected, parseKeyword("|foo|bar", ~OR_OPERATOR));
  }

  /** test the ability to enable/disable NOT operator */
  public void testDisableNOT() {
    Query expected = new TermQuery(new QueryTerm("field", "-foo", 0));
    assertEquals(expected, parseKeyword("-foo", ~NOT_OPERATOR));
  }

  /** test the ability to enable/disable precedence operators */
  public void testDisablePrecedence() {
    Query expected = new TermQuery(new QueryTerm("field", "(foo)", 0));
    assertEquals(expected, parseKeyword("(foo)", ~PRECEDENCE_OPERATORS));
    expected = new TermQuery(new QueryTerm("field", ")foo(", 0));
    assertEquals(expected, parseKeyword(")foo(", ~PRECEDENCE_OPERATORS));
  }

  /** test the ability to enable/disable escape operators */
  public void testDisableEscape() {
    Query expected = new TermQuery(new QueryTerm("field", "foo\\bar", 0));
    Query expectedParens = new TermQuery(new QueryTerm("field", "foo\\bar", 1));
    Query expectedQuery = new TermQuery(new QueryTerm("field", "foo\\bar", 1));
    assertEquals(expected, parseKeyword("foo\\bar", ~ESCAPE_OPERATOR));
    assertEquals(expectedParens, parseKeyword("(foo\\bar)", ~ESCAPE_OPERATOR));
    assertEquals(expectedQuery, parseKeyword("\"foo\\bar\"", ~ESCAPE_OPERATOR));
  }

  public void testDisableWhitespace() {
    Query expected = new TermQuery(new QueryTerm("field", "foo foo", 0));
    assertEquals(expected, parseKeyword("foo foo", ~WHITESPACE_OPERATOR));
    expected = new TermQuery(new QueryTerm("field", " foo foo\n ", 0));
    assertEquals(expected, parseKeyword(" foo foo\n ", ~WHITESPACE_OPERATOR));
    expected = new TermQuery(new QueryTerm("field", "\t\tfoo foo foo", 0));
    assertEquals(expected, parseKeyword("\t\tfoo foo foo", ~WHITESPACE_OPERATOR));
  }

  public void testDisableFuzziness() {
    Query expected = new TermQuery(new QueryTerm("field", "foo~1", 0));
    assertEquals(expected, parseKeyword("foo~1", ~FUZZY_OPERATOR));
  }

  public void testDisableSlop() {
    PhraseQuery expectedPhrase = new PhraseQuery("field", new int[] {1, 5}, "foo", "bar");

    BooleanQuery.Builder expected = new BooleanQuery.Builder();
    expected.add(expectedPhrase, Occur.MUST);
    expected.add(new TermQuery(new QueryTerm("field", "~2", 9)), Occur.MUST);
    Query parse = parse("\"foo bar\"~2", ~NEAR_OPERATOR);
    assertEquals(expected.build(), parse);
  }

  // we aren't supposed to barf on any input...
  public void testRandomQueries() throws Exception {
    for (int i = 0; i < 1000; i++) {
      String query = TestUtil.randomUnicodeString(random());
      parse(query); // no exception
      parseKeyword(query, TestUtil.nextInt(random(), 0, 1024)); // no exception
    }
  }

  public void testRandomQueries2() throws Exception {
    char[] chars = new char[] {'a', '1', '|', '&', ' ', '(', ')', '"', '-', '~'};
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 1000; i++) {
      sb.setLength(0);
      int queryLength = random().nextInt(20);
      for (int j = 0; j < queryLength; j++) {
        sb.append(chars[random().nextInt(chars.length)]);
      }
      parse(sb.toString()); // no exception
      parseKeyword(sb.toString(), TestUtil.nextInt(random(), 0, 1024)); // no exception
    }
  }

  public void testStarBecomesMatchAll() throws Exception {
    Query q = parse("*");
    assertEquals(q, new MatchAllDocsQuery());
    q = parse(" *   ");
    assertEquals(q, new MatchAllDocsQuery());
  }
}
