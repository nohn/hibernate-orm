/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import org.hibernate.collection.internal.PersistentSet;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.CollectionHelper;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.type.spi.TypeConfiguration;

public class SetType extends CollectionType {

	public SetType(TypeConfiguration typeConfiguration, String role, String propertyRef) {
		super( typeConfiguration, role, propertyRef );
	}

	@Override
	public PersistentCollection instantiate(SharedSessionContractImplementor session, CollectionPersister persister, Object key) {
		return new PersistentSet( session );
	}

	@Override
	public Class getReturnedClass() {
		return java.util.Set.class;
	}

	@Override
	public PersistentCollection wrap(SharedSessionContractImplementor session, Object collection) {
		return new PersistentSet( session, (java.util.Set) collection );
	}

	@Override
	public Object instantiate(int anticipatedSize) {
		return anticipatedSize <= 0
				? CollectionHelper.set()
				: CollectionHelper.setOfSize( anticipatedSize );
	}

}
