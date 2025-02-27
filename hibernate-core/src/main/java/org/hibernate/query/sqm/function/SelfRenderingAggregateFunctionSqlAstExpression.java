/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import java.util.List;

import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.JdbcMappingContainer;
import org.hibernate.metamodel.model.domain.AllowableFunctionReturnType;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.AggregateFunctionExpression;
import org.hibernate.sql.ast.tree.predicate.Predicate;

/**
 * Representation of an aggregate function call in the SQL AST for impls that know how to
 * render themselves.
 *
 * @author Christian Beikov
 */
public class SelfRenderingAggregateFunctionSqlAstExpression extends SelfRenderingFunctionSqlAstExpression
		implements AggregateFunctionExpression {

	private final Predicate filter;

	public SelfRenderingAggregateFunctionSqlAstExpression(
			String functionName,
			FunctionRenderingSupport renderer,
			List<SqlAstNode> sqlAstArguments,
			Predicate filter,
			AllowableFunctionReturnType<?> type,
			JdbcMappingContainer expressable) {
		super( functionName, renderer, sqlAstArguments, type, expressable );
		this.filter = filter;
	}

	@Override
	public Predicate getFilter() {
		return filter;
	}

	@Override
	public void renderToSql(
			SqlAppender sqlAppender,
			SqlAstTranslator<?> walker,
			SessionFactoryImplementor sessionFactory) {
		getRenderer().render( sqlAppender, getArguments(), filter, walker );
	}
}
