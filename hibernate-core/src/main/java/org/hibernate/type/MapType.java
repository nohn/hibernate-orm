/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.HibernateException;
import org.hibernate.collection.internal.PersistentMap;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.type.spi.TypeConfiguration;


public class MapType extends CollectionType {

	public MapType(TypeConfiguration typeConfiguration, String role, String propertyRef) {
		super( typeConfiguration, role, propertyRef );
	}

	@Override
	public PersistentCollection instantiate(
			SharedSessionContractImplementor session,
			CollectionPersister persister,
			Object key) {
		return new PersistentMap( session );
	}

	@Override
	public Class getReturnedClass() {
		return Map.class;
	}

	@Override
	public Iterator getElementsIterator(Object collection) {
		return ( (Map) collection ).values().iterator();
	}

	@Override
	public PersistentCollection wrap(SharedSessionContractImplementor session, Object collection) {
		return new PersistentMap( session, (Map) collection );
	}

	@Override
	public Object instantiate(int anticipatedSize) {
		return anticipatedSize <= 0
				? new HashMap()
				: new HashMap( anticipatedSize + (int) ( anticipatedSize * .75f ), .75f );
	}

	@Override
	public Object replaceElements(
			final Object original,
			final Object target,
			final Object owner,
			final Map copyCache,
			final SharedSessionContractImplementor session) throws HibernateException {
		CollectionPersister cp = session.getFactory().getMetamodel().collectionPersister( getRole() );

		Map result = (Map) target;
		result.clear();

		for ( Object o : ( (Map) original ).entrySet() ) {
			Map.Entry me = (Map.Entry) o;
			Object key = cp.getIndexType().replace( me.getKey(), null, session, owner, copyCache );
			Object value = cp.getElementType().replace( me.getValue(), null, session, owner, copyCache );
			result.put( key, value );
		}

		return result;

	}

	@Override
	public Object indexOf(Object collection, Object element) {
		for ( Object o : ( (Map) collection ).entrySet() ) {
			Map.Entry me = (Map.Entry) o;
			//TODO: proxies!
			if ( me.getValue() == element ) {
				return me.getKey();
			}
		}
		return null;
	}

}
