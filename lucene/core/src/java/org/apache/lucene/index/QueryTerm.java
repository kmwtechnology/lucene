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

package org.apache.lucene.index;

import org.apache.lucene.util.BytesRef;

/**
 * A variation on term that tracks information valuable to parsers or other query generation
 * mechanisms. The information tracked (such as the term start offset within the query) text is not
 * expected to be valuable to manually constructed queries, and not useful for query execution or
 * indexing. Therefore, such information is only maintained until rewrite() is called on the query,
 * therefore parsers should do any related customization or record this information before actually
 * executing the query. Manually constructed queries can safely zero out the additional information
 * if it is not available, and it will be ignored since it only exists for the benefit of automated
 * query construction code and doesn't change the query result at all.
 */
public class QueryTerm extends Term {

  private int startOffset;

  /**
   * Create a new query term from an arbitrary term.
   *
   * @param term the source term
   */
  public QueryTerm(Term term) {
    this(term.field, term.bytes);
  }

  /**
   * Convert a generic term to a QueryTerm. This is only appropriate for use within or after
   * rewriting has begun, or in unit tests where offset of the query term can be set to zero.
   *
   * @param term a term to convert if required
   * @return the same object as the supplied parameter if it was already a QueryTerm or a new
   *     QueryTerm based on the supplied term
   */
  public static QueryTerm asQueryTerm(Term term) {
    QueryTerm t = term instanceof QueryTerm ? (QueryTerm) term : new QueryTerm(term);
    return t;
  }

  /**
   * Get the original offset of the term in the parsed query.
   *
   * @return the distance from the start of the first term
   */
  public int getStartOffset() {
    return startOffset;
  }

  private QueryTerm(String fld, BytesRef bytes) {
    super(fld);
    this.bytes = bytes == null ? null : BytesRef.deepCopyOf(bytes);
    this.startOffset = 0;
  }

  /**
   * Constructs a Term with the given field, bytes and offset.
   *
   * <p>Note that a null field or null bytes value results in undefined behavior for most Lucene
   * APIs that accept a Term parameter.
   *
   * <p>The provided BytesRef is copied when it is non null.
   *
   * @param fld the field this term relates to
   * @param bytes the bytes representing the text of this term
   * @param startOffset the position in the parsed query where the term was found. For queries
   *     constructed programmatically this could be set safely to 0. It is primarily useful when
   *     attempting to correlate the results of analysis with the initial position in a parsed query
   *     string. For example SOLR-16804. Note that term position information is NOT guaranteed to be
   *     retained after the query's rewrite() method is called. After rewrite some or all offsets
   *     may be set to zero.
   */
  public QueryTerm(String fld, BytesRef bytes, int startOffset) {
    super(fld, bytes);
    this.startOffset = startOffset;
  }

  /**
   * Constructs a Term with the given field and text at the specified offset.
   *
   * <p>Note that a null field or null text value results in undefined behavior for most Lucene APIs
   * that accept a Term parameter.
   */
  public QueryTerm(String fld, String text, int startOffset) {
    this(fld, new BytesRef(text), startOffset);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    QueryTerm other = (QueryTerm) obj;
    if (field == null) {
      if (other.field != null) return false;
    } else if (!field.equals(other.field)) return false;
    if (bytes == null) {
      if (other.bytes != null) return false;
    } else if (!bytes.equals(other.bytes)) return false;
    if (startOffset != other.startOffset) return false;
    return true;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((bytes == null) ? 0 : bytes.hashCode());
    result = prime * result + Integer.valueOf(startOffset).hashCode();
    return result;
  }

  @Override
  public String toString() {
    return field + ":" + text() + "[" + startOffset + "]";
  }
}
