/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.produce.function;

import org.hibernate.query.sqm.tree.SqmTypedNode;

import java.util.List;

/**
 * @author Steve Ebersole
 */
public interface ArgumentsValidator {
	/**
	 * The main (functional) operation defining validation
	 */
	void validate(List<? extends SqmTypedNode<?>> arguments);

	default String getSignature() {
		return "( ... )";
	}

}
