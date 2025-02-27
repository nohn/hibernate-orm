/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.configuration.internal.metadata.reader;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Lob;
import jakarta.persistence.MapKey;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Version;

import org.hibernate.HibernateException;
import org.hibernate.annotations.common.reflection.ReflectionManager;
import org.hibernate.annotations.common.reflection.XClass;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.envers.AuditJoinTable;
import org.hibernate.envers.AuditMappedBy;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.AuditOverrides;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.envers.boot.EnversMappingException;
import org.hibernate.envers.boot.internal.ModifiedColumnNameResolver;
import org.hibernate.envers.boot.spi.EnversMetadataBuildingContext;
import org.hibernate.envers.internal.tools.MappingTools;
import org.hibernate.envers.internal.tools.ReflectionTools;
import org.hibernate.envers.internal.tools.StringTools;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.loader.PropertyPath;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

import static org.hibernate.envers.internal.tools.Tools.newHashMap;
import static org.hibernate.envers.internal.tools.Tools.newHashSet;

/**
 * Reads persistent properties form a {@link PersistentPropertiesSource} and adds the ones that are audited to a
 * {@link AuditedPropertiesHolder}, filling all the auditing data.
 *
 * @author Adam Warski (adam at warski dot org)
 * @author Erik-Berndt Scheper
 * @author Hern&aacut;n Chanfreau
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 * @author Michal Skowronek (mskowr at o2 dot pl)
 * @author Lukasz Zuchowski (author at zuchos dot com)
 * @author Chris Cranford
 */
public class AuditedPropertiesReader {

	private static final AuditJoinTableData DEFAULT_AUDIT_JOIN_TABLE = new AuditJoinTableData();

	public static final String NO_PREFIX = "";

	private final PersistentPropertiesSource persistentPropertiesSource;
	private final AuditedPropertiesHolder auditedPropertiesHolder;
	private final EnversMetadataBuildingContext metadataBuildingContext;
	private final String propertyNamePrefix;

	private final Map<String, String> propertyAccessedPersistentProperties;
	private final Set<String> fieldAccessedPersistentProperties;
	// Mapping class field to corresponding <properties> element.
	private final Map<String, String> propertiesGroupMapping;

	private final Set<XProperty> overriddenAuditedProperties;
	private final Set<XProperty> overriddenNotAuditedProperties;
	private final Map<XProperty, AuditJoinTable> overriddenAuditedPropertiesJoinTables;

	private final Set<XClass> overriddenAuditedClasses;
	private final Set<XClass> overriddenNotAuditedClasses;

	public AuditedPropertiesReader(
			EnversMetadataBuildingContext metadataBuildingContext,
			PersistentPropertiesSource persistentPropertiesSource,
			AuditedPropertiesHolder auditedPropertiesHolder) {
		this ( metadataBuildingContext, persistentPropertiesSource, auditedPropertiesHolder, NO_PREFIX );
	}

	public AuditedPropertiesReader(
			EnversMetadataBuildingContext metadataBuildingContext,
			PersistentPropertiesSource persistentPropertiesSource,
			AuditedPropertiesHolder auditedPropertiesHolder,
			String propertyNamePrefix) {
		this.persistentPropertiesSource = persistentPropertiesSource;
		this.auditedPropertiesHolder = auditedPropertiesHolder;
		this.metadataBuildingContext = metadataBuildingContext;
		this.propertyNamePrefix = propertyNamePrefix;

		propertyAccessedPersistentProperties = newHashMap();
		fieldAccessedPersistentProperties = newHashSet();
		propertiesGroupMapping = newHashMap();

		overriddenAuditedProperties = newHashSet();
		overriddenNotAuditedProperties = newHashSet();
		overriddenAuditedPropertiesJoinTables = newHashMap();

		overriddenAuditedClasses = newHashSet();
		overriddenNotAuditedClasses = newHashSet();
	}

	public void read() {
		// First reading the access types for the persistent properties.
		readPersistentPropertiesAccess();

		if ( persistentPropertiesSource.isDynamicComponent() ) {
			addPropertiesFromDynamicComponent( persistentPropertiesSource );
		}
		else {
			// Retrieve classes and properties that are explicitly marked for auditing process by any superclass
			// of currently mapped entity or itself.
			final XClass clazz = persistentPropertiesSource.getXClass();
			readAuditOverrides( clazz );

			// Adding all properties from the given class.
			addPropertiesFromClass( clazz );
		}
	}

	/**
	 * Recursively constructs sets of audited and not audited properties and classes which behavior has been overridden
	 * using {@link AuditOverride} annotation.
	 *
	 * @param clazz Class that is being processed. Currently mapped entity shall be passed during first invocation.
	 */
	private void readAuditOverrides(XClass clazz) {
		/* TODO: Code to remove with @Audited.auditParents - start. */
		final ReflectionManager reflectionManager = metadataBuildingContext.getReflectionManager();
		final Audited allClassAudited = clazz.getAnnotation( Audited.class );
		if ( allClassAudited != null && allClassAudited.auditParents().length > 0 ) {
			for ( Class c : allClassAudited.auditParents() ) {
				final XClass parentClass = reflectionManager.toXClass( c );
				checkSuperclass( clazz, parentClass );
				if ( !overriddenNotAuditedClasses.contains( parentClass ) ) {
					// If the class has not been marked as not audited by the subclass.
					overriddenAuditedClasses.add( parentClass );
				}
			}
		}
		/* TODO: Code to remove with @Audited.auditParents - finish. */
		final List<AuditOverride> auditOverrides = computeAuditOverrides( clazz );
		for ( AuditOverride auditOverride : auditOverrides ) {
			if ( auditOverride.forClass() != void.class ) {
				final XClass overrideClass = reflectionManager.toXClass( auditOverride.forClass() );
				checkSuperclass( clazz, overrideClass );
				final String propertyName = auditOverride.name();
				if ( !StringTools.isEmpty( propertyName ) ) {
					// Override @Audited annotation on property level.
					final XProperty property = getProperty( overrideClass, propertyName );
					if ( auditOverride.isAudited() ) {
						if ( !overriddenNotAuditedProperties.contains( property ) ) {
							// If the property has not been marked as not audited by the subclass.
							overriddenAuditedProperties.add( property );
							overriddenAuditedPropertiesJoinTables.put( property, auditOverride.auditJoinTable() );
						}
					}
					else {
						if ( !overriddenAuditedProperties.contains( property ) ) {
							// If the property has not been marked as audited by the subclass.
							overriddenNotAuditedProperties.add( property );
						}
					}
				}
				else {
					// Override @Audited annotation on class level.
					if ( auditOverride.isAudited() ) {
						if ( !overriddenNotAuditedClasses.contains( overrideClass ) ) {
							// If the class has not been marked as not audited by the subclass.
							overriddenAuditedClasses.add( overrideClass );
						}
					}
					else {
						if ( !overriddenAuditedClasses.contains( overrideClass ) ) {
							// If the class has not been marked as audited by the subclass.
							overriddenNotAuditedClasses.add( overrideClass );
						}
					}
				}
			}
		}
		final XClass superclass = clazz.getSuperclass();
		if ( !clazz.isInterface() && !Object.class.getName().equals( superclass.getName() ) ) {
			readAuditOverrides( superclass );
		}
	}

	/**
	 * @param clazz Source class.
	 *
	 * @return List of @AuditOverride annotations applied at class level.
	 */
	private List<AuditOverride> computeAuditOverrides(XClass clazz) {
		final AuditOverrides auditOverrides = clazz.getAnnotation( AuditOverrides.class );
		final AuditOverride auditOverride = clazz.getAnnotation( AuditOverride.class );
		if ( auditOverrides == null && auditOverride != null ) {
			return Arrays.asList( auditOverride );
		}
		else if ( auditOverrides != null && auditOverride == null ) {
			return Arrays.asList( auditOverrides.value() );
		}
		else if ( auditOverrides != null && auditOverride != null ) {
			throw new EnversMappingException(
					"@AuditOverrides annotation should encapsulate all @AuditOverride declarations. " +
							"Please revise Envers annotations applied to class " + clazz.getName() + "."
			);
		}
		return Collections.emptyList();
	}

	/**
	 * Checks whether one class is assignable from another. If not {@link EnversMappingException} is thrown.
	 *
	 * @param child Subclass.
	 * @param parent Superclass.
	 */
	private void checkSuperclass(XClass child, XClass parent) {
		if ( !parent.isAssignableFrom( child ) ) {
			throw new EnversMappingException(
					"Class " + parent.getName() + " is not assignable from " + child.getName() + ". " +
							"Please revise Envers annotations applied to " + child.getName() + " type."
			);
		}
	}

	/**
	 * Checks whether class contains property with a given name. If not {@link EnversMappingException} is thrown.
	 *
	 * @param clazz Class.
	 * @param propertyName Property name.
	 *
	 * @return Property object.
	 */
	private XProperty getProperty(XClass clazz, String propertyName) {
		final XProperty property = ReflectionTools.getProperty( clazz, propertyName );
		if ( property == null ) {
			throw new EnversMappingException(
					"Property '" + propertyName + "' not found in class " + clazz.getName() + ". " +
							"Please revise Envers annotations applied to class " + persistentPropertiesSource.getXClass() + "."
			);
		}
		return property;
	}

	private void readPersistentPropertiesAccess() {
		final Iterator<Property> propertyIter = persistentPropertiesSource.getPropertyIterator();
		while ( propertyIter.hasNext() ) {
			final Property property = propertyIter.next();
			addPersistentProperty( property );
			// See HHH-6636
			if ( "embedded".equals( property.getPropertyAccessorName() ) && !PropertyPath.IDENTIFIER_MAPPER_PROPERTY.equals( property.getName() ) ) {
				createPropertiesGroupMapping( property );
			}
		}
	}

	private void addPersistentProperty(Property property) {
		if ( "field".equals( property.getPropertyAccessorName() ) ) {
			fieldAccessedPersistentProperties.add( property.getName() );
		}
		else {
			propertyAccessedPersistentProperties.put( property.getName(), property.getPropertyAccessorName() );
		}
	}

	@SuppressWarnings("unchecked")
	private void createPropertiesGroupMapping(Property property) {
		final Component component = (Component) property.getValue();
		final Iterator<Property> componentProperties = component.getPropertyIterator();
		while ( componentProperties.hasNext() ) {
			final Property componentProperty = componentProperties.next();
			propertiesGroupMapping.put( componentProperty.getName(), property.getName() );
		}
	}

	/**
	 * @param clazz Class which properties are currently being added.
	 *
	 * @return {@link Audited} annotation of specified class. If processed type hasn't been explicitly marked, method
	 *         checks whether given class exists in {@link AuditedPropertiesReader#overriddenAuditedClasses} collection.
	 *         In case of success, {@link Audited} configuration of currently mapped entity is returned, otherwise
	 *         {@code null}. If processed type exists in {@link AuditedPropertiesReader#overriddenNotAuditedClasses}
	 *         collection, the result is also {@code null}.
	 */
	private Audited computeAuditConfiguration(XClass clazz) {
		Audited allClassAudited = clazz.getAnnotation( Audited.class );
		// If processed class is not explicitly marked with @Audited annotation, check whether auditing is
		// forced by any of its child entities configuration (@AuditedOverride.forClass).
		if ( allClassAudited == null && overriddenAuditedClasses.contains( clazz ) ) {
			// Declared audited parent copies @Audited.modStore and @Audited.targetAuditMode configuration from
			// currently mapped entity.
			allClassAudited = persistentPropertiesSource.getXClass().getAnnotation( Audited.class );
			if ( allClassAudited == null ) {
				// If parent class declares @Audited on the field/property level.
				allClassAudited = DEFAULT_AUDITED;
			}
		}
		else if ( allClassAudited != null && overriddenNotAuditedClasses.contains( clazz ) ) {
			return null;
		}
		return allClassAudited;
	}

	private void addPropertiesFromDynamicComponent(PersistentPropertiesSource propertiesSource) {
		Audited audited = computeAuditConfiguration( propertiesSource.getXClass() );
		if ( !fieldAccessedPersistentProperties.isEmpty() ) {
			throw new EnversMappingException(
					"Audited dynamic component cannot have properties with access=\"field\" for properties: " +
							fieldAccessedPersistentProperties +
							". \n Change properties access=\"property\", to make it work)"
			);
		}
		for ( Map.Entry<String, String> entry : propertyAccessedPersistentProperties.entrySet() ) {
			String property = entry.getKey();
			String accessType = entry.getValue();
			if ( !auditedPropertiesHolder.contains( property ) ) {
				final Value propertyValue = persistentPropertiesSource.getProperty( property ).getValue();
				if ( propertyValue instanceof Component ) {
					this.addFromComponentProperty(
							new DynamicProperty( propertiesSource, property ),
							accessType,
							(Component) propertyValue,
							audited
					);
				}
				else {
					this.addFromNotComponentProperty(
							new DynamicProperty( propertiesSource, property ),
							accessType,
							audited
					);
				}
			}
		}
	}

	/**
	 * Recursively adds all audited properties of entity class and its superclasses.
	 *
	 * @param clazz Currently processed class.
	 */
	private void addPropertiesFromClass(XClass clazz) {
		final Audited allClassAudited = computeAuditConfiguration( clazz );

		//look in the class
		addFromProperties(
				clazz.getDeclaredProperties( "field" ),
				it -> "field",
				fieldAccessedPersistentProperties,
				allClassAudited
		);
		addFromProperties(
				clazz.getDeclaredProperties( "property" ),
				propertyAccessedPersistentProperties::get,
				propertyAccessedPersistentProperties.keySet(),
				allClassAudited
		);

		if ( isClassHierarchyTraversalNeeded( allClassAudited ) ) {
			final XClass superclazz = clazz.getSuperclass();
			if ( !clazz.isInterface() && !"java.lang.Object".equals( superclazz.getName() ) ) {
				addPropertiesFromClass( superclazz );
			}
		}
	}

	protected boolean isClassHierarchyTraversalNeeded(Audited allClassAudited) {
		return allClassAudited != null || !auditedPropertiesHolder.isEmpty();
	}

	private void addFromProperties(
			Iterable<XProperty> properties,
			Function<String, String> accessTypeProvider,
			Set<String> persistentProperties,
			Audited allClassAudited) {
		for ( XProperty property : properties ) {
			final String accessType = accessTypeProvider.apply( property.getName() );

			// If this is not a persistent property, with the same access type as currently checked,
			// it's not audited as well.
			// If the property was already defined by the subclass, is ignored by superclasses
			if ( persistentProperties.contains( property.getName() )
					&& !auditedPropertiesHolder.contains( property.getName() ) ) {
				final Value propertyValue = persistentPropertiesSource.getProperty( property.getName() ).getValue();
				if ( propertyValue instanceof Component ) {
					this.addFromComponentProperty( property, accessType, (Component) propertyValue, allClassAudited );
				}
				else {
					this.addFromNotComponentProperty( property, accessType, allClassAudited );
				}
			}
			else if ( propertiesGroupMapping.containsKey( property.getName() ) ) {
				// Retrieve embedded component name based on class field.
				final String embeddedName = propertiesGroupMapping.get( property.getName() );
				if ( !auditedPropertiesHolder.contains( embeddedName ) ) {
					// Manage properties mapped within <properties> tag.
					final Value propertyValue = persistentPropertiesSource.getProperty( embeddedName ).getValue();
					this.addFromPropertiesGroup(
							embeddedName,
							property,
							accessType,
							(Component) propertyValue,
							allClassAudited
					);
				}
			}
		}
	}

	private void addFromPropertiesGroup(
			String embeddedName,
			XProperty property,
			String accessType,
			Component propertyValue,
			Audited allClassAudited) {
		final ComponentAuditingData componentData = new ComponentAuditingData();
		final boolean isAudited = fillPropertyData( property, componentData, accessType, allClassAudited );
		if ( isAudited ) {
			// EntityPersister.getPropertyNames() returns name of embedded component instead of class field.
			componentData.setName( embeddedName );
			// Marking component properties as placed directly in class (not inside another component).
			componentData.setBeanName( null );

			final PersistentPropertiesSource componentPropertiesSource = PersistentPropertiesSource.forComponent(
					metadataBuildingContext,
					propertyValue
			);
			final AuditedPropertiesReader audPropReader = new AuditedPropertiesReader(
					metadataBuildingContext,
					componentPropertiesSource,
					componentData,
					propertyNamePrefix + MappingTools.createComponentPrefix( embeddedName )
			);
			audPropReader.read();

			auditedPropertiesHolder.addPropertyAuditingData( embeddedName, componentData );
		}
	}

	private void addFromComponentProperty(
			XProperty property,
			String accessType,
			Component propertyValue,
			Audited allClassAudited) {
		final ComponentAuditingData componentData = new ComponentAuditingData();
		final boolean isAudited = fillPropertyData( property, componentData, accessType, allClassAudited );

		final PersistentPropertiesSource componentPropertiesSource;
		if ( propertyValue.isDynamic() ) {
			final XClass xClass = metadataBuildingContext.getReflectionManager().toXClass( Map.class );
			componentPropertiesSource = PersistentPropertiesSource.forComponent( propertyValue, xClass, true );
		}
		else {
			componentPropertiesSource = PersistentPropertiesSource.forComponent( metadataBuildingContext, propertyValue );
		}

		final ComponentAuditedPropertiesReader audPropReader = new ComponentAuditedPropertiesReader(
				metadataBuildingContext,
				componentPropertiesSource,
				componentData,
				propertyNamePrefix + MappingTools.createComponentPrefix( property.getName() )
		);
		audPropReader.read();

		if ( isAudited ) {
			// Now we know that the property is audited
			auditedPropertiesHolder.addPropertyAuditingData( property.getName(), componentData );
		}
	}

	private void addFromNotComponentProperty(XProperty property, String accessType, Audited allClassAudited) {
		final PropertyAuditingData propertyData = new PropertyAuditingData();
		final boolean isAudited = fillPropertyData( property, propertyData, accessType, allClassAudited );

		if ( isAudited ) {
			// Now we know that the property is audited
			auditedPropertiesHolder.addPropertyAuditingData( property.getName(), propertyData );
		}
	}


	/**
	 * Checks if a property is audited and if yes, fills all of its data.
	 *
	 * @param property Property to check.
	 * @param propertyData Property data, on which to set this property's modification store.
	 * @param accessType Access type for the property.
	 *
	 * @return False if this property is not audited.
	 */
	private boolean fillPropertyData(
			XProperty property,
			PropertyAuditingData propertyData,
			String accessType,
			Audited allClassAudited) {

		// check if a property is declared as not audited to exclude it
		// useful if a class is audited but some properties should be excluded
		final NotAudited unVer = property.getAnnotation( NotAudited.class );
		if ( ( unVer != null
				&& !overriddenAuditedProperties.contains( property ) )
				|| overriddenNotAuditedProperties.contains( property ) ) {
			return false;
		}
		else {
			// if the optimistic locking field has to be unversioned and the current property
			// is the optimistic locking field, don't audit it
			if ( metadataBuildingContext.getConfiguration().isDoNotAuditOptimisticLockingField() ) {
				final Version jpaVer = property.getAnnotation( Version.class );
				if ( jpaVer != null ) {
					return false;
				}
			}
		}

		final String propertyName = propertyNamePrefix + property.getName();
		final String modifiedFlagsSuffix = metadataBuildingContext.getConfiguration().getModifiedFlagsSuffix();
		if ( !this.checkAudited( property, propertyData,propertyName, allClassAudited, modifiedFlagsSuffix ) ) {
			return false;
		}

		validateLobMappingSupport( property );

		propertyData.setName( propertyName );
		propertyData.setBeanName( property.getName() );
		propertyData.setAccessType( accessType );

		addPropertyJoinTables( property, propertyData );
		addPropertyAuditingOverrides( property, propertyData );
		if ( !processPropertyAuditingOverrides( property, propertyData ) ) {
			// not audited due to AuditOverride annotation
			return false;
		}
		addPropertyMapKey( property, propertyData );
		setPropertyAuditMappedBy( property, propertyData );
		setPropertyRelationMappedBy( property, propertyData );

		return true;
	}

	private void validateLobMappingSupport(XProperty property) {
		// HHH-9834 - Sanity check
		try {
			if ( property.isAnnotationPresent( ElementCollection.class ) ) {
				if ( property.isAnnotationPresent( Lob.class ) ) {
					if ( !property.getCollectionClass().isAssignableFrom( Map.class ) ) {
						throw new EnversMappingException(
								"@ElementCollection combined with @Lob is only supported for Map collection types."
						);
					}
				}
			}
		}
		catch ( EnversMappingException e ) {
			throw new HibernateException(
					String.format(
							Locale.ENGLISH,
							"Invalid mapping in [%s] for property [%s]",
							property.getDeclaringClass().getName(),
							property.getName()
					),
					e
			);
		}
	}

	protected boolean checkAudited(
			XProperty property,
			PropertyAuditingData propertyData, String propertyName,
			Audited allClassAudited, String modifiedFlagSuffix) {
		// Checking if this property is explicitly audited or if all properties are.
		Audited aud = ( property.isAnnotationPresent( Audited.class ) )
				? property.getAnnotation( Audited.class )
				: allClassAudited;
		if ( aud == null
				&& overriddenAuditedProperties.contains( property )
				&& !overriddenNotAuditedProperties.contains( property ) ) {
			// Assigning @Audited defaults. If anyone needs to customize those values in the future,
			// appropriate fields shall be added to @AuditOverride annotation.
			aud = DEFAULT_AUDITED;
		}
		if ( aud != null ) {
			propertyData.setRelationTargetAuditMode( aud.targetAuditMode() );
			propertyData.setUsingModifiedFlag( checkUsingModifiedFlag( aud ) );
			propertyData.setModifiedFlagName( ModifiedColumnNameResolver.getName( propertyName, modifiedFlagSuffix ) );
			if ( !StringTools.isEmpty( aud.modifiedColumnName() ) ) {
				propertyData.setExplicitModifiedFlagName( aud.modifiedColumnName() );
			}
			return true;
		}
		else {
			return false;
		}
	}

	protected boolean checkUsingModifiedFlag(Audited aud) {
		// HHH-10468
		if ( metadataBuildingContext.getConfiguration().hasSettingForUseModifiedFlag() ) {
			// HHH-10468
			// Modify behavior so that if the global setting has been set by user properties, then
			// the audit behavior should be a disjunction between the global setting and the field
			// annotation.  This allows the annotation to take precedence when the global value is
			// false and for the global setting to take precedence when true.
			return metadataBuildingContext.getConfiguration().isModifiedFlagsEnabled() || aud.withModifiedFlag();
		}
		// no global setting enabled, use the annotation's value only.
		return aud.withModifiedFlag();
	}

	private void setPropertyRelationMappedBy(XProperty property, PropertyAuditingData propertyData) {
		final OneToMany oneToMany = property.getAnnotation( OneToMany.class );
		if ( oneToMany != null && StringHelper.isNotEmpty( oneToMany.mappedBy() ) ) {
			propertyData.setRelationMappedBy( oneToMany.mappedBy() );
		}
	}

	private void setPropertyAuditMappedBy(XProperty property, PropertyAuditingData propertyData) {
		final AuditMappedBy auditMappedBy = property.getAnnotation( AuditMappedBy.class );
		if ( auditMappedBy != null ) {
			propertyData.setAuditMappedBy( auditMappedBy.mappedBy() );
			if ( StringHelper.isNotEmpty( auditMappedBy.positionMappedBy() ) ) {
				propertyData.setPositionMappedBy( auditMappedBy.positionMappedBy() );
			}
		}
	}

	private void addPropertyMapKey(XProperty property, PropertyAuditingData propertyData) {
		final MapKey mapKey = property.getAnnotation( MapKey.class );
		if ( mapKey != null ) {
			propertyData.setMapKey( mapKey.name() );
		}
		else {
			final MapKeyEnumerated mapKeyEnumerated = property.getAnnotation( MapKeyEnumerated.class );
			if ( mapKeyEnumerated != null ) {
				propertyData.setMapKeyEnumType( mapKeyEnumerated.value() );
			}
		}
	}

	private void addPropertyJoinTables(XProperty property, PropertyAuditingData propertyData) {
		// The AuditJoinTable annotation source will follow the following priority rules
		//		1. Use the override if one is specified
		//		2. Use the site annotation if one is specified
		//		3. Use the default if neither are specified
		//
		// The prime directive for (1) is so that when users in a subclass use @AuditOverride(s)
		// the join-table specified there should have a higher priority in the event the
		// super-class defines an equivalent @AuditJoinTable at the site/property level.

		final AuditJoinTable overrideJoinTable = overriddenAuditedPropertiesJoinTables.get( property );
		if ( overrideJoinTable != null ) {
			propertyData.setJoinTable( new AuditJoinTableData( overrideJoinTable ) );
		}
		else {
			final AuditJoinTable propertyJoinTable = property.getAnnotation( AuditJoinTable.class );
			if ( propertyJoinTable != null ) {
				propertyData.setJoinTable( new AuditJoinTableData( propertyJoinTable ) );
			}
			else {
				propertyData.setJoinTable( DEFAULT_AUDIT_JOIN_TABLE );
			}
		}
	}

	/**
	 * Add the {@link AuditOverride} annotations.
	 *
	 * @param property the property being processed
	 * @param propertyData the Envers auditing data for this property
	 */
	private void addPropertyAuditingOverrides(XProperty property, PropertyAuditingData propertyData) {
		final AuditOverride annotationOverride = property.getAnnotation( AuditOverride.class );
		if ( annotationOverride != null ) {
			propertyData.addAuditingOverride( annotationOverride );
		}
		final AuditOverrides annotationOverrides = property.getAnnotation( AuditOverrides.class );
		if ( annotationOverrides != null ) {
			propertyData.addAuditingOverrides( annotationOverrides );
		}
	}

	/**
	 * Process the {@link AuditOverride} annotations for this property.
	 *
	 * @param property the property for which the {@link AuditOverride}
	 * annotations are being processed
	 * @param propertyData the Envers auditing data for this property
	 *
	 * @return {@code false} if isAudited() of the override annotation was set to
	 */
	private boolean processPropertyAuditingOverrides(XProperty property, PropertyAuditingData propertyData) {
		// Only components register audit overrides, classes will have no entries.
		for ( AuditOverrideData override : auditedPropertiesHolder.getAuditingOverrides() ) {
			if ( property.getName().equals( override.getName() ) ) {
				// the override applies to this property
				if ( !override.isAudited() ) {
					return false;
				}
				else {
					if ( override.getAuditJoinTableData() != null ) {
						propertyData.setJoinTable( override.getAuditJoinTableData() );
					}
				}
			}
		}
		return true;
	}

	protected boolean isOverriddenNotAudited(XProperty property) {
		return overriddenNotAuditedProperties.contains( property );
	}

	protected boolean isOverriddenNotAudited(XClass clazz) {
		return overriddenNotAuditedClasses.contains( clazz );
	}

	protected boolean isOverriddenAudited(XProperty property) {
		return overriddenAuditedProperties.contains( property );
	}

	protected boolean isOverriddenAudited(XClass clazz) {
		return overriddenAuditedClasses.contains( clazz );
	}

	private static final Audited DEFAULT_AUDITED = new Audited() {
		@Override
		public RelationTargetAuditMode targetAuditMode() {
			return RelationTargetAuditMode.AUDITED;
		}

		@Override
		public Class[] auditParents() {
			return new Class[0];
		}

		@Override
		public boolean withModifiedFlag() {
			return false;
		}

		@Override
		public String modifiedColumnName() {
			return "";
		}

		@Override
		public Class<? extends Annotation> annotationType() {
			return this.getClass();
		}
	};
}
