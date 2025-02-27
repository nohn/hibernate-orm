/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.spi;

import java.util.List;
import jakarta.persistence.CacheRetrieveMode;
import jakarta.persistence.CacheStoreMode;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.LockOptions;
import org.hibernate.graph.spi.AppliedGraph;
import org.hibernate.query.Limit;
import org.hibernate.query.ResultListTransformer;
import org.hibernate.query.TupleTransformer;

/**
 * @author Christian Beikov
 */
public class DelegatingQueryOptions implements QueryOptions {

	private final QueryOptions queryOptions;

	public DelegatingQueryOptions(QueryOptions queryOptions) {
		this.queryOptions = queryOptions;
	}

	@Override
	public Integer getTimeout() {
		return queryOptions.getTimeout();
	}

	@Override
	public FlushMode getFlushMode() {
		return queryOptions.getFlushMode();
	}

	@Override
	public Boolean isReadOnly() {
		return queryOptions.isReadOnly();
	}

	@Override
	public AppliedGraph getAppliedGraph() {
		return queryOptions.getAppliedGraph();
	}

	@Override
	public TupleTransformer getTupleTransformer() {
		return queryOptions.getTupleTransformer();
	}

	@Override
	public ResultListTransformer getResultListTransformer() {
		return queryOptions.getResultListTransformer();
	}

	@Override
	public Boolean isResultCachingEnabled() {
		return queryOptions.isResultCachingEnabled();
	}

	@Override
	public CacheRetrieveMode getCacheRetrieveMode() {
		return queryOptions.getCacheRetrieveMode();
	}

	@Override
	public CacheStoreMode getCacheStoreMode() {
		return queryOptions.getCacheStoreMode();
	}

	@Override
	public CacheMode getCacheMode() {
		return queryOptions.getCacheMode();
	}

	@Override
	public String getResultCacheRegionName() {
		return queryOptions.getResultCacheRegionName();
	}

	@Override
	public LockOptions getLockOptions() {
		return queryOptions.getLockOptions();
	}

	@Override
	public String getComment() {
		return queryOptions.getComment();
	}

	@Override
	public List<String> getDatabaseHints() {
		return queryOptions.getDatabaseHints();
	}

	@Override
	public Integer getFetchSize() {
		return queryOptions.getFetchSize();
	}

	@Override
	public Limit getLimit() {
		return queryOptions.getLimit();
	}

	@Override
	public Integer getFirstRow() {
		return queryOptions.getFirstRow();
	}

	@Override
	public Integer getMaxRows() {
		return queryOptions.getMaxRows();
	}

	@Override
	public Limit getEffectiveLimit() {
		return queryOptions.getEffectiveLimit();
	}

	@Override
	public boolean hasLimit() {
		return queryOptions.hasLimit();
	}
}
