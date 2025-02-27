/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.envers.configuration.internal.metadata;

import java.util.Iterator;
import java.util.Map;

import org.hibernate.envers.boot.model.AttributeContainer;
import org.hibernate.envers.boot.registry.classloading.ClassLoaderAccessHelper;
import org.hibernate.envers.boot.spi.EnversMetadataBuildingContext;
import org.hibernate.envers.configuration.internal.metadata.reader.ComponentAuditingData;
import org.hibernate.envers.configuration.internal.metadata.reader.PropertyAuditingData;
import org.hibernate.envers.internal.entities.mapper.CompositeMapperBuilder;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

/**
 * Generates metadata for components.
 *
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Zuchowski (author at zuchos dot com)
 * @author Chris Cranford
 */
public final class ComponentMetadataGenerator extends AbstractMetadataGenerator {

	private final ValueMetadataGenerator valueGenerator;

	ComponentMetadataGenerator(EnversMetadataBuildingContext metadataBuildingContext, ValueMetadataGenerator valueGenerator) {
		super( metadataBuildingContext );
		this.valueGenerator = valueGenerator;
	}

	@SuppressWarnings({"unchecked"})
	public void addComponent(
			AttributeContainer attributeContainer,
			PropertyAuditingData propertyAuditingData,
			Value value,
			CompositeMapperBuilder mapper,
			String entityName,
			EntityMappingData mappingData,
			boolean firstPass) {
		final Component propComponent = (Component) value;

		final CompositeMapperBuilder componentMapper = mapper.addComponent(
				propertyAuditingData.resolvePropertyData(),
				ClassLoaderAccessHelper.loadClass(
						getMetadataBuildingContext(),
						getClassNameForComponent( propComponent )
				)
		);

		// The property auditing data must be for a component.
		final ComponentAuditingData componentAuditingData = (ComponentAuditingData) propertyAuditingData;

		// Adding all properties of the component
		final Iterator<Property> properties = propComponent.getPropertyIterator();
		while ( properties.hasNext() ) {
			final Property property = properties.next();

			final PropertyAuditingData componentPropertyAuditingData =
					componentAuditingData.getPropertyAuditingData( property.getName() );

			// Checking if that property is audited
			if ( componentPropertyAuditingData != null ) {
				valueGenerator.addValue(
						attributeContainer,
						property.getValue(),
						componentMapper,
						entityName,
						mappingData,
						componentPropertyAuditingData,
						property.isInsertable(),
						firstPass,
						false
				);
			}
		}
	}

	private String getClassNameForComponent(Component component) {
		return component.isDynamic() ? Map.class.getCanonicalName() : component.getComponentClassName();
	}
}
