/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a @MappedSuperclass.
 * A @MappedSuperclass can be a superclass of an @Entity (root or not)
 *
 * This class primary goal is to give a representation to @MappedSuperclass
 * in the metamodel in order to reflect them in the JPA 2 metamodel.
 *
 * Do not use outside this use case.
 *
 * 
 * A proper redesign will be evaluated in Hibernate 4
 *
 * Implementation details:
 * properties are copies of their closest sub-persistentClass versions
 *
 * @author Emmanuel Bernard
 */
public class MappedSuperclass {
	private final MappedSuperclass superMappedSuperclass;
	private final PersistentClass superPersistentClass;
	private final List declaredProperties;
	private Class mappedClass;
	private Property identifierProperty;
	private Property version;
	private Component identifierMapper;

	public MappedSuperclass(MappedSuperclass superMappedSuperclass, PersistentClass superPersistentClass) {
		this.superMappedSuperclass = superMappedSuperclass;
		this.superPersistentClass = superPersistentClass;
		this.declaredProperties = new ArrayList();
	}

	/**
	 * Returns the first superclass marked as @MappedSuperclass or null if:
	 *  - none exists
	 *  - or the first persistent superclass found is an @Entity
	 *
	 * @return the super MappedSuperclass
	 */
	public MappedSuperclass getSuperMappedSuperclass() {
		return superMappedSuperclass;
	}

	public boolean hasIdentifierProperty() {
		return getIdentifierProperty() != null;
	}

	public boolean isVersioned() {
		return getVersion() != null;
	}

	/**
	 * Returns the PersistentClass of the first superclass marked as @Entity
	 * or null if none exists
	 *
	 * @return the PersistentClass of the superclass
	 */
	public PersistentClass getSuperPersistentClass() {
		return superPersistentClass;
	}

	public Iterator getDeclaredPropertyIterator() {
		return declaredProperties.iterator();
	}

	public void addDeclaredProperty(Property p) {
		//Do not add duplicate properties
		//TODO is it efficient enough?
		String name = p.getName();
		Iterator it = declaredProperties.iterator();
		while (it.hasNext()) {
			if ( name.equals( ((Property)it.next()).getName() ) ) {
				return;
			}
		}
		declaredProperties.add(p);
	}

	public Class getMappedClass() {
		return mappedClass;
	}

	public void setMappedClass(Class mappedClass) {
		this.mappedClass = mappedClass;
	}

	public Property getIdentifierProperty() {
		//get direct identifierMapper or the one from the super mappedSuperclass
		// or the one from the super persistentClass
		Property propagatedIdentifierProp = identifierProperty;
		if ( propagatedIdentifierProp == null ) {
			if ( superMappedSuperclass != null ) {
				propagatedIdentifierProp = superMappedSuperclass.getIdentifierProperty();
			}
			if (propagatedIdentifierProp == null && superPersistentClass != null){
				propagatedIdentifierProp = superPersistentClass.getIdentifierProperty();
			}
		}
		return propagatedIdentifierProp;
	}

	public Property getDeclaredIdentifierProperty() {
		return identifierProperty;
	}

	public void setDeclaredIdentifierProperty(Property prop) {
		this.identifierProperty = prop;
	}

	public Property getVersion() {
		//get direct version or the one from the super mappedSuperclass
		// or the one from the super persistentClass
		Property propagatedVersion = version;
		if (propagatedVersion == null) {
			if ( superMappedSuperclass != null ) {
				propagatedVersion = superMappedSuperclass.getVersion();
			}
			if (propagatedVersion == null && superPersistentClass != null){
				propagatedVersion = superPersistentClass.getVersion();
			}
		}
		return propagatedVersion;
	}

	public Property getDeclaredVersion() {
		return version;
	}

	public void setDeclaredVersion(Property prop) {
		this.version = prop;
	}

	public Component getIdentifierMapper() {
		//get direct identifierMapper or the one from the super mappedSuperclass
		// or the one from the super persistentClass
		Component propagatedMapper = identifierMapper;
		if ( propagatedMapper == null ) {
			if ( superMappedSuperclass != null ) {
				propagatedMapper = superMappedSuperclass.getIdentifierMapper();
			}
			if (propagatedMapper == null && superPersistentClass != null){
				propagatedMapper = superPersistentClass.getIdentifierMapper();
			}
		}
		return propagatedMapper;
	}

	public Component getDeclaredIdentifierMapper() {
		return identifierMapper;
	}

	public void setDeclaredIdentifierMapper(Component identifierMapper) {
		this.identifierMapper = identifierMapper;
	}

	/**
	 * Check to see if this MappedSuperclass defines a property with the given name.
	 *
	 * @param name The property name to check
	 *
	 * @return {@code true} if a property with that name exists; {@code false} if not
	 */
	@SuppressWarnings("WeakerAccess")
	public boolean hasProperty(String name) {
		final Iterator itr = getDeclaredPropertyIterator();
		while ( itr.hasNext() ) {
			final Property property = (Property) itr.next();
			if ( property.getName().equals( name ) ) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Check to see if a property with the given name exists in this MappedSuperclass
	 * or in any of its super hierarchy.
	 *
	 * @param name The property name to check
	 *
	 * @return {@code true} if a property with that name exists; {@code false} if not
	 */
	@SuppressWarnings({"WeakerAccess", "RedundantIfStatement"})
	public boolean isPropertyDefinedInHierarchy(String name) {
		if ( hasProperty( name ) ) {
			return true;
		}

		if ( getSuperMappedSuperclass() != null
				&& getSuperMappedSuperclass().isPropertyDefinedInHierarchy( name ) ) {
			return true;
		}

		if ( getSuperPersistentClass() != null
				&& getSuperPersistentClass().isPropertyDefinedInHierarchy( name ) ) {
			return true;
		}

		return false;
	}

	@SuppressWarnings( "unchecked" )
	public void prepareForMappingModel() {
		( (List<Property>) declaredProperties ).sort( Comparator.comparing( Property::getName ) );
	}
}
