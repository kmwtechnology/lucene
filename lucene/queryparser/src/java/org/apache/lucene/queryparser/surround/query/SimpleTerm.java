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
package org.apache.lucene.queryparser.surround.query;

import java.io.IOException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.QueryTerm;
import org.apache.lucene.search.Query;

/** Base class for queries that expand to sets of simple terms. */
public abstract class SimpleTerm extends SrndQuery implements DistanceSubQuery {
  public SimpleTerm(boolean q) {
    quoted = q;
  }

  private boolean quoted;

  boolean isQuoted() {
    return quoted;
  }

  public String getQuote() {
    return "\"";
  }

  public String getFieldOperator() {
    return "/";
  }

  public abstract String toStringUnquoted();

  protected void suffixToString(StringBuilder r) {} /* override for prefix query */

  @Override
  public String toString() {
    StringBuilder r = new StringBuilder();
    if (isQuoted()) {
      r.append(getQuote());
    }
    r.append(toStringUnquoted());
    if (isQuoted()) {
      r.append(getQuote());
    }
    suffixToString(r);
    weightToString(r);
    return r.toString();
  }

  public abstract void visitMatchingTerms(
      IndexReader reader, String fieldName, MatchingTermVisitor mtv) throws IOException;

  /**
   * Callback to visit each matching term during "rewrite" in {@link #visitMatchingTerm(QueryTerm)}
   */
  public interface MatchingTermVisitor {
    void visitMatchingTerm(QueryTerm t) throws IOException;
  }

  @Override
  public String distanceSubQueryNotAllowed() {
    return null;
  }

  @Override
  public void addSpanQueries(final SpanNearClauseFactory sncf) throws IOException {
    visitMatchingTerms(
        sncf.getIndexReader(),
        sncf.getFieldName(),
        new MatchingTermVisitor() {
          @Override
          public void visitMatchingTerm(QueryTerm term) throws IOException {
            sncf.addTermWeighted(term, getWeight());
          }
        });
  }

  @Override
  public Query makeLuceneQueryFieldNoBoost(final String fieldName, final BasicQueryFactory qf) {
    return new SimpleTermRewriteQuery(this, fieldName, qf);
  }
}
