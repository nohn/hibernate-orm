/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.function;

import org.hibernate.query.sqm.produce.function.ArgumentsValidator;
import org.hibernate.query.sqm.produce.function.FunctionReturnTypeResolver;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.tree.SqlAstNode;
import org.hibernate.sql.ast.tree.expression.Distinct;
import org.hibernate.sql.ast.tree.expression.Star;
import org.hibernate.sql.ast.tree.predicate.Predicate;

import java.util.List;
import java.util.Locale;

/**
 * Provides a standard implementation that supports the majority of the HQL
 * functions that are translated to SQL. The Dialect and its sub-classes use
 * this class to provide details required for processing of the associated
 * function.
 *
 * @author David Channon
 * @author Steve Ebersole
 */
public class NamedSqmFunctionDescriptor
		extends AbstractSqmSelfRenderingFunctionDescriptor {
	private final String functionName;
	private final boolean useParenthesesWhenNoArgs;
	private final String argumentListSignature;
	private final SqlAstNodeRenderingMode argumentRenderingMode;

	public NamedSqmFunctionDescriptor(
			String functionName,
			boolean useParenthesesWhenNoArgs,
			ArgumentsValidator argumentsValidator,
			FunctionReturnTypeResolver returnTypeResolver) {
		this(
				functionName,
				useParenthesesWhenNoArgs,
				argumentsValidator,
				returnTypeResolver,
				functionName,
				FunctionKind.NORMAL,
				null,
				SqlAstNodeRenderingMode.DEFAULT
		);
	}

	public NamedSqmFunctionDescriptor(
			String functionName,
			boolean useParenthesesWhenNoArgs,
			ArgumentsValidator argumentsValidator,
			FunctionReturnTypeResolver returnTypeResolver,
			String name,
			FunctionKind functionKind,
			String argumentListSignature,
			SqlAstNodeRenderingMode argumentRenderingMode) {
		super( name, functionKind, argumentsValidator, returnTypeResolver );

		this.functionName = functionName;
		this.useParenthesesWhenNoArgs = useParenthesesWhenNoArgs;
		this.argumentListSignature = argumentListSignature;
		this.argumentRenderingMode = argumentRenderingMode;
	}

	/**
	 * Function name accessor
	 *
	 * @return The function name.
	 */
	public String getName() {
		return functionName;
	}

	@Override
	public String getArgumentListSignature() {
		return argumentListSignature == null ? super.getArgumentListSignature() : argumentListSignature;
	}

	@Override
	public boolean alwaysIncludesParentheses() {
		return useParenthesesWhenNoArgs;
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<SqlAstNode> sqlAstArguments,
			SqlAstTranslator<?> translator) {
		render( sqlAppender, sqlAstArguments, null, translator );
	}

	@Override
	public void render(
			SqlAppender sqlAppender,
			List<SqlAstNode> sqlAstArguments,
			Predicate filter,
			SqlAstTranslator<?> translator) {
		final boolean useParens = useParenthesesWhenNoArgs || !sqlAstArguments.isEmpty();
		final boolean caseWrapper = filter != null && !translator.supportsFilterClause();

		sqlAppender.appendSql( functionName );
		if ( useParens ) {
			sqlAppender.appendSql( "(" );
		}

		boolean firstPass = true;
		for ( SqlAstNode arg : sqlAstArguments ) {
			if ( !firstPass ) {
				sqlAppender.appendSql( "," );
			}
			if ( caseWrapper && !( arg instanceof Distinct ) ) {
				sqlAppender.appendSql( "case when " );
				filter.accept( translator );
				sqlAppender.appendSql( " then " );
				if ( ( arg instanceof Star ) ) {
					sqlAppender.appendSql( "1" );
				}
				else {
					translator.render( arg, argumentRenderingMode );
				}
				sqlAppender.appendSql( " else null end" );
			}
			else {
				translator.render( arg, argumentRenderingMode );
			}
			firstPass = false;
		}

		if ( useParens ) {
			sqlAppender.appendSql( ")" );
		}

		if ( filter != null && !caseWrapper ) {
			sqlAppender.appendSql( " filter (where " );
			filter.accept( translator );
			sqlAppender.appendSql( ')' );
		}
	}

	@Override
	public String toString() {
		return String.format(
				Locale.ROOT,
				"NamedSqmFunctionTemplate(%s)",
				functionName
		);
	}

}
