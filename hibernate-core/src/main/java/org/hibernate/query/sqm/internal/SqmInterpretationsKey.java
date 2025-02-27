/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.internal;

import org.hibernate.LockOptions;
import org.hibernate.query.QueryParameter;
import org.hibernate.query.ResultListTransformer;
import org.hibernate.query.TupleTransformer;
import org.hibernate.query.spi.QueryInterpretationCache;

/**
 * @author Steve Ebersole
 */
public class SqmInterpretationsKey implements QueryInterpretationCache.Key {
	@SuppressWarnings("WeakerAccess")
	public static SqmInterpretationsKey generateFrom(QuerySqmImpl<?> query) {
		if ( ! isCacheable( query ) ) {
			return null;
		}

		return new SqmInterpretationsKey(
				query.getQueryString(),
				query.getResultType(),
				query.getLockOptions(),
				query.getQueryOptions().getTupleTransformer(),
				query.getQueryOptions().getResultListTransformer()
		);
	}

	@SuppressWarnings("WeakerAccess")
	public static QueryInterpretationCache.Key generateNonSelectKey(QuerySqmImpl<?> query) {
		// todo (6.0) : do we want to cache non-select plans?  If so, what requirements?
		//		- very minimum is that it be a "simple" (non-multi-table) statement
		//
		// for now... no caching of non-select plans
		return null;
	}

	@SuppressWarnings("RedundantIfStatement")
	private static boolean isCacheable(QuerySqmImpl<?> query) {
		assert query.getQueryOptions().getAppliedGraph() != null;

		if ( QuerySqmImpl.CRITERIA_HQL_STRING.equals( query.getQueryString() ) ) {
			// for now at least, skip caching Criteria-based plans
			//		- especially wrt parameters atm; this works with HQL because the parameters
			//			are part of the query string; with Criteria, they are not.
			return false;
		}

		if ( query.getSession().getLoadQueryInfluencers().hasEnabledFilters() ) {
			// At the moment we cannot cache query plan if there is filter enabled.
			return false;
		}

		if ( query.getQueryOptions().getAppliedGraph().getSemantic() != null ) {
			// At the moment we cannot cache query plan if there is an
			// EntityGraph enabled.
			return false;
		}

		if ( query.getQueryParameterBindings().hasAnyMultiValuedBindings()
				|| query.getParameterMetadata().hasAnyMatching( QueryParameter::allowsMultiValuedBinding ) ) {
			// cannot cache query plans if there are multi-valued param bindings
			// todo (6.0) : this one may be ok because of how I implemented multi-valued param handling
			//		- the expansion is done per-execution based on the "static" SQM
			//  - Note from Christian: The call to domainParameterXref.clearExpansions() in ConcreteSqmSelectQueryPlan is a concurrency issue when cached
			//  - This could be solved by using a method-local clone of domainParameterXref when multi-valued params exist
			return false;
		}

		return true;
	}

	private final String query;
	private final Class<?> resultType;
	private final LockOptions lockOptions;
	private final TupleTransformer<?> tupleTransformer;
	private final ResultListTransformer resultListTransformer;

	private SqmInterpretationsKey(
			String query,
			Class<?> resultType,
			LockOptions lockOptions,
			TupleTransformer<?> tupleTransformer,
			ResultListTransformer resultListTransformer) {
		this.query = query;
		this.resultType = resultType;
		this.lockOptions = lockOptions;
		this.tupleTransformer = tupleTransformer;
		this.resultListTransformer = resultListTransformer;
	}

	@Override
	public QueryInterpretationCache.Key prepareForStore() {
		return new SqmInterpretationsKey(
				query,
				resultType,
				// Since lock options are mutable, we need a copy for the cache key
				lockOptions.makeCopy(),
				tupleTransformer,
				resultListTransformer
		);
	}

	@Override
	public String getQueryString() {
		return query;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || getClass() != o.getClass() ) {
			return false;
		}

		final SqmInterpretationsKey that = (SqmInterpretationsKey) o;
		return query.equals( that.query )
				&& areEqual( resultType, that.resultType )
				&& areEqual( lockOptions, that.lockOptions )
				&& areEqual( tupleTransformer, that.tupleTransformer )
				&& areEqual( resultListTransformer, that.resultListTransformer );
	}

	private <T> boolean areEqual(T o1, T o2) {
		if ( o1 == null ) {
			return o2 == null;
		}
		else {
			return o1.equals( o2 );
		}
	}

	@Override
	public int hashCode() {
		int result = query.hashCode();
		result = 31 * result + ( resultType != null ? resultType.hashCode() : 0 );
		result = 31 * result + ( lockOptions != null ? lockOptions.hashCode() : 0 );
		result = 31 * result + ( tupleTransformer != null ? tupleTransformer.hashCode() : 0 );
		result = 31 * result + ( resultListTransformer != null ? resultListTransformer.hashCode() : 0 );
		return result;
	}
}
