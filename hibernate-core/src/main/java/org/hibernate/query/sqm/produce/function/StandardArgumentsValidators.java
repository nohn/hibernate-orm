/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.function;

import org.hibernate.QueryException;
import org.hibernate.query.sqm.tree.SqmTypedNode;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * @author Steve Ebersole
 */
public final class StandardArgumentsValidators {
	/**
	 * Disallow instantiation
	 */
	private StandardArgumentsValidators() {
	}

	/**
	 * Static validator for performing no validation
	 */
	public static final ArgumentsValidator NONE = new ArgumentsValidator() {
		@Override
		public void validate(List<? extends SqmTypedNode<?>> arguments) {}

		@Override
		public String getSignature() {
			return "([arg0[, ...]])";
		}
	};

	/**
	 * Static validator for verifying that we have no arguments
	 */
	public static final ArgumentsValidator NO_ARGS = new ArgumentsValidator() {
		@Override
		public void validate(List<? extends SqmTypedNode<?>> arguments) {
			if (!arguments.isEmpty()) {
				throw new QueryException("Expecting no arguments, but found " + arguments.size());
			}
		}

		@Override
		public String getSignature() {
			return "()";
		}
	};

	public static ArgumentsValidator min(int minNumOfArgs) {
		if (minNumOfArgs<1) {
			throw new IllegalArgumentException();
		}
		return new ArgumentsValidator() {
			@Override
			public void validate(List<? extends SqmTypedNode<?>> arguments) {
				if (arguments.size() < minNumOfArgs) {
					throw new QueryException(
							String.format(
									Locale.ROOT,
									"Function requires %d or more arguments, but only %d found",
									minNumOfArgs,
									arguments.size()
							)
					);
				}
			}

			@Override
			public String getSignature() {
				StringBuilder sig = new StringBuilder("(");
				for (int i=0; i<minNumOfArgs; i++) {
					if (i!=0) {
						sig.append(", ");
					}
					sig.append("arg").append(i);
				}
				sig.append("[, arg");
				sig.append(minNumOfArgs);
				sig.append("[, ...]])");
				return sig.toString();
			}
		};
	}

	public static ArgumentsValidator exactly(int number) {
		return new ArgumentsValidator() {
			@Override
			public void validate(List<? extends SqmTypedNode<?>> arguments) {
				if (arguments.size() != number) {
					throw new QueryException(
							String.format(
									Locale.ROOT,
									"Function requires %d arguments, but %d found",
									number,
									arguments.size()
							)
					);
				}
			}

			@Override
			public String getSignature() {
				StringBuilder sig = new StringBuilder("(");
				for (int i=0; i<number; i++) {
					if (i!=0) {
						sig.append(", ");
					}
					sig.append("arg");
					if (number>1) {
						sig.append(i);
					}
				}
				sig.append(")");
				return sig.toString();
			}

		};
	}

	public static ArgumentsValidator max(int maxNumOfArgs) {
		return new ArgumentsValidator() {
			@Override
			public void validate(List<? extends SqmTypedNode<?>> arguments) {
				if (arguments.size() > maxNumOfArgs) {
					throw new QueryException(
							String.format(
									Locale.ROOT,
									"Function requires %d or fewer arguments, but %d found",
									maxNumOfArgs,
									arguments.size()
							)
					);
				}
			}

			@Override
			public String getSignature() {
				StringBuilder sig = new StringBuilder("([");
				for (int i=0; i<maxNumOfArgs; i++) {
					if (i!=0) {
						sig.append(", ");
					}
					sig.append("arg").append(i);
				}
				sig.append("])");
				return sig.toString();
			}
		};
	}

	public static ArgumentsValidator between(int minNumOfArgs, int maxNumOfArgs) {
		return new ArgumentsValidator() {
			@Override
			public void validate(List<? extends SqmTypedNode<?>> arguments) {
				if (arguments.size() < minNumOfArgs || arguments.size() > maxNumOfArgs) {
					throw new QueryException(
							String.format(
									Locale.ROOT,
									"Function requires between %d and %d arguments, but %d found",
									minNumOfArgs,
									maxNumOfArgs,
									arguments.size()
							)
					);
				}
			}

			@Override
			public String getSignature() {
				StringBuilder sig = new StringBuilder("(");
				for (int i=0; i<maxNumOfArgs; i++) {
					if (i==minNumOfArgs) {
						sig.append("[");
					}
					if (i!=0) {
						sig.append(", ");
					}
					sig.append("arg").append(i);
				}
				sig.append("])");
				return sig.toString();
			}
		};
	}

	public static ArgumentsValidator of(Class<?> javaType) {
		return arguments -> arguments.forEach(
				arg -> {
					Class<?> argType = arg.getNodeJavaTypeDescriptor().getJavaTypeClass();
					if ( !javaType.isAssignableFrom( argType ) ) {
						throw new QueryException(
								String.format(
										Locale.ROOT,
										"Function expects arguments to be of type %s, but %s found",
										javaType.getName(),
										argType.getName()
								)
						);
					}
				}
		);
	}

	public static ArgumentsValidator composite(ArgumentsValidator... validators) {
		return composite( Arrays.asList( validators ) );
	}

	public static ArgumentsValidator composite(List<ArgumentsValidator> validators) {
		return arguments -> validators.forEach(
				individualValidator -> individualValidator.validate( arguments )
		);
	}
}
