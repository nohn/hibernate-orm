/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hibernate.query.sqm.sql.BaseSqmToSqlAstConverter.SqmAliasedNodeCollector;
import org.hibernate.query.sqm.sql.ConversionException;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlAstProcessingState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.expression.SqlSelectionExpression;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Implementation of ProcessingState used on its own as the impl for
 * DML statements and as the base for QuerySpec state
 *
 * @author Steve Ebersole
 */
public class SqlAstProcessingStateImpl
		implements SqlAstProcessingState, SqlExpressionResolver, SqmAliasedNodeCollector {
	private final SqlAstProcessingState parentState;
	private final SqlAstCreationState creationState;
	private final SqlExpressionResolver expressionResolver;
	private final Supplier<Clause> currentClauseAccess;

	private final Map<String, Expression> expressionMap = new HashMap<>();

	public SqlAstProcessingStateImpl(
			SqlAstProcessingState parentState,
			SqlAstCreationState creationState,
			Supplier<Clause> currentClauseAccess) {
		this.parentState = parentState;
		this.creationState = creationState;
		this.expressionResolver = this;
		this.currentClauseAccess = currentClauseAccess;
	}

	public SqlAstProcessingStateImpl(
			SqlAstProcessingState parentState,
			SqlAstCreationState creationState,
			Function<SqlExpressionResolver, SqlExpressionResolver> expressionResolverDecorator,
			Supplier<Clause> currentClauseAccess) {
		this.parentState = parentState;
		this.creationState = creationState;
		this.expressionResolver = expressionResolverDecorator.apply( this );
		this.currentClauseAccess = currentClauseAccess;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// ProcessingState

	@Override
	public SqlAstProcessingState getParentState() {
		return parentState;
	}

	@Override
	public SqlExpressionResolver getSqlExpressionResolver() {
		return expressionResolver;
	}

	@Override
	public SqlAstCreationState getSqlAstCreationState() {
		return creationState;
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlExpressionResolver

	protected Map<Expression, SqlSelection> sqlSelectionMap() {
		return Collections.emptyMap();
	}

	@Override
	public Expression resolveSqlExpression(
			String key,
			Function<SqlAstProcessingState,Expression> creator) {
		final Expression existing = expressionMap.get( key );

		final Expression expression;
		if ( existing != null ) {
			expression = existing;
		}
		else {
			expression = creator.apply( this );
			expressionMap.put( key, expression );
		}

		return normalize( expression );
	}

	@SuppressWarnings("WeakerAccess")
	protected Expression normalize(Expression expression) {
		final Clause currentClause = currentClauseAccess.get();
		if ( currentClause == Clause.ORDER
				|| currentClause == Clause.GROUP ) {
			// see if this (Sql)Expression is used as a selection, and if so
			// wrap the (Sql)Expression in a special wrapper with access to both
			// the (Sql)Expression and the SqlSelection.
			//
			// This is used for databases which prefer to use the position of a
			// selected expression (within the select-clause) as the
			// order-by, group-by or having expression
			final SqlSelection selection = sqlSelectionMap().get( expression );
			if ( selection != null ) {
				return new SqlSelectionExpression( selection );
			}
		}

		return expression;
	}

//	@Override
//	public Expression resolveSqlExpression(NonQualifiableSqlExpressable sqlSelectable) {
//		final Expression expression = normalize( sqlSelectable.createExpression() );
//		final Consumer<Expression> expressionConsumer = resolvedExpressionConsumerAccess.get();
//		if ( expressionConsumer != null ) {
//			expressionConsumer.accept( expression );
//		}
//		return expression;
//	}

	@Override
	public SqlSelection resolveSqlSelection(
			Expression expression,
			JavaType javaTypeDescriptor,
			TypeConfiguration typeConfiguration) {
		throw new ConversionException( "Unexpected call to resolve SqlSelection outside of QuerySpec processing" );
	}

	@Override
	public void next() {
		// nothing to do
		int i = 1;
	}

	@Override
	public List<SqlSelection> getSelections(int position) {
		throw new UnsupportedOperationException();
	}
}
