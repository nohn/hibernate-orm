/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.internal;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.metamodel.EmbeddableRepresentationStrategy;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.type.descriptor.java.JavaType;

/**
 * @author Steve Ebersole
 */
public abstract class AbstractEmbeddableRepresentationStrategy implements EmbeddableRepresentationStrategy {
	private final JavaType<?> embeddableJavaTypeDescriptor;

	private final int propertySpan;
	private final PropertyAccess[] propertyAccesses;
	private final boolean hasCustomAccessors;

	private final Map<String,Integer> attributeNameToPositionMap;

	public AbstractEmbeddableRepresentationStrategy(
			Component bootDescriptor,
			JavaType<?> embeddableJavaTypeDescriptor,
			RuntimeModelCreationContext creationContext) {
		this.propertySpan = bootDescriptor.getPropertySpan();
		this.embeddableJavaTypeDescriptor = embeddableJavaTypeDescriptor;

		this.propertyAccesses = new PropertyAccess[ propertySpan ];
		this.attributeNameToPositionMap = new ConcurrentHashMap<>( propertySpan );

		boolean foundCustomAccessor = false;
		Iterator itr = bootDescriptor.getPropertyIterator();
		int i = 0;
		while ( itr.hasNext() ) {
			final Property prop = ( Property ) itr.next();
			propertyAccesses[i] = buildPropertyAccess( prop );
			attributeNameToPositionMap.put( prop.getName(), i );

			if ( !prop.isBasicPropertyAccessor() ) {
				foundCustomAccessor = true;
			}

			i++;
		}

		hasCustomAccessors = foundCustomAccessor;
	}

	protected abstract PropertyAccess buildPropertyAccess(Property bootAttributeDescriptor);

	public JavaType<?> getEmbeddableJavaTypeDescriptor() {
		return embeddableJavaTypeDescriptor;
	}

	@Override
	public JavaType<?> getMappedJavaTypeDescriptor() {
		return getEmbeddableJavaTypeDescriptor();
	}

	public int getPropertySpan() {
		return propertySpan;
	}

	public PropertyAccess[] getPropertyAccesses() {
		return propertyAccesses;
	}

	public boolean hasCustomAccessors() {
		return hasCustomAccessors;
	}

	@Override
	public PropertyAccess resolvePropertyAccess(Property bootAttributeDescriptor) {
		return propertyAccesses[ attributeNameToPositionMap.get( bootAttributeDescriptor.getName() ) ];
	}
}
