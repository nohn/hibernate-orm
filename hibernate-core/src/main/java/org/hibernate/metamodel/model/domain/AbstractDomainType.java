/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.model.domain;

import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractDomainType<J> implements SimpleDomainType<J> {
	private final JpaMetamodel domainMetamodel;
	private final JavaType<J> javaTypeDescriptor;

	@SuppressWarnings("WeakerAccess")
	public AbstractDomainType(JavaType<J> javaTypeDescriptor, JpaMetamodel domainMetamodel) {
		this.javaTypeDescriptor = javaTypeDescriptor;
		this.domainMetamodel = domainMetamodel;
	}

	protected JpaMetamodel jpaMetamodel() {
		return domainMetamodel;
	}

	@Override
	public JavaType<J> getExpressableJavaTypeDescriptor() {
		return javaTypeDescriptor;
	}

	@Override
	public Class<J> getJavaType() {
		return getExpressableJavaTypeDescriptor().getJavaTypeClass();
	}
}
