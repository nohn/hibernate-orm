/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.metamodel.mapping.internal;

import java.util.List;
import java.util.function.Consumer;

import org.hibernate.NotYetImplementedFor6Exception;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.IndexedConsumer;
import org.hibernate.mapping.Table;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.hibernate.metamodel.mapping.EmbeddableMappingType;
import org.hibernate.metamodel.mapping.EmbeddableValuedModelPart;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingModelCreationLogger;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.NonAggregatedIdentifierMapping;
import org.hibernate.metamodel.mapping.NonTransientException;
import org.hibernate.metamodel.mapping.PluralAttributeMapping;
import org.hibernate.metamodel.mapping.SelectableConsumer;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.metamodel.mapping.SelectableMappings;
import org.hibernate.metamodel.mapping.SingularAttributeMapping;
import org.hibernate.metamodel.model.domain.NavigableRole;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.NavigablePath;
import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableGroupProducer;
import org.hibernate.sql.results.graph.DomainResult;
import org.hibernate.sql.results.graph.DomainResultCreationState;
import org.hibernate.type.AnyType;
import org.hibernate.type.CollectionType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.spi.CompositeTypeImplementor;

import static org.hibernate.internal.util.collections.CollectionHelper.arrayList;
import static org.hibernate.metamodel.mapping.NonAggregatedIdentifierMapping.IdentifierValueMapper;

/**
 * Embeddable describing the virtual-id aspect of a non-aggregated composite id
 */
public class VirtualIdEmbeddable extends AbstractEmbeddableMapping implements IdentifierValueMapper {
	private final NavigableRole navigableRole;
	private final NonAggregatedIdentifierMapping idMapping;
	private final VirtualIdRepresentationStrategy representationStrategy;

	private final List<SingularAttributeMapping> attributeMappings;
	private SelectableMappings selectableMappings;

	public VirtualIdEmbeddable(
			Component virtualIdSource,
			NonAggregatedIdentifierMapping idMapping,
			EntityPersister identifiedEntityMapping,
			String rootTableExpression,
			String[] rootTableKeyColumnNames,
			MappingModelCreationProcess creationProcess) {
		super( creationProcess );

		this.navigableRole = idMapping.getNavigableRole();
		this.idMapping = idMapping;
		this.representationStrategy = new VirtualIdRepresentationStrategy( this, identifiedEntityMapping );

		final CompositeType compositeType = (CompositeType) virtualIdSource.getType();
		this.attributeMappings = arrayList( (compositeType).getPropertyNames().length );

		// todo (6.0) : can/should this be a separate VirtualIdEmbedded?
		( (CompositeTypeImplementor) compositeType ).injectMappingModelPart( idMapping, creationProcess );

		creationProcess.registerInitializationCallback(
				"VirtualIdEmbeddable(" + navigableRole.getFullPath() + ")#finishInitialization",
				() -> {
					try {
						final boolean finished = finishInitialization(
								virtualIdSource,
								compositeType,
								rootTableExpression,
								rootTableKeyColumnNames,
								creationProcess
						);

						if ( finished ) {
							return finished;
						}
						else {
							MappingModelCreationLogger.LOGGER.debugf(
									"VirtualIdEmbeddable(%s) finalization was not able to complete successfully",
									navigableRole.getFullPath()
							);
							return false;
						}
					}
					catch (Exception e) {
						if ( e instanceof NonTransientException ) {
							throw e;
						}

						MappingModelCreationLogger.LOGGER.debugf(
								e,
								"(DEBUG) Error finalizing VirtualIdEmbeddable(%s)",
								navigableRole.getFullPath()
						);
						return false;
					}
				}
		);
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// IdentifierValueMapper

	@Override
	public EmbeddableValuedModelPart getEmbeddedPart() {
		return idMapping;
	}

	@Override
	public Object getIdentifier(Object entity, SharedSessionContractImplementor session) {
		return representationStrategy.getInstantiator().instantiate(
				() -> getValues( entity ),
				session.getSessionFactory()
		);
	}

	@Override
	public void setIdentifier(Object entity, Object id, SharedSessionContractImplementor session) {
		if ( entity != id ) {
			setValues( entity, getValues( id ) );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// EmbeddableMappingType

	@Override
	public NavigableRole getNavigableRole() {
		return navigableRole;
	}

	@Override
	public String getPartName() {
		return idMapping.getPartName();
	}

	@Override
	public EmbeddableValuedModelPart getEmbeddedValueMapping() {
		return getEmbeddedPart();
	}

	@Override
	public VirtualIdRepresentationStrategy getRepresentationStrategy() {
		return representationStrategy;
	}

	@Override
	public AttributeMapping findAttributeMapping(String name) {
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final AttributeMapping attr = attributeMappings.get( i );
			if ( name.equals( attr.getAttributeName() ) ) {
				return attr;
			}
		}
		return null;
	}

	@Override
	public SelectableMapping getSelectable(int columnIndex) {
		return selectableMappings.getSelectable( columnIndex );
	}

	@Override
	public int forEachSelectable(SelectableConsumer consumer) {
		return selectableMappings.forEachSelectable( 0, consumer );
	}

	@Override
	public int forEachSelectable(int offset, SelectableConsumer consumer) {
		return selectableMappings.forEachSelectable( offset, consumer );
	}

	@Override
	public int getJdbcTypeCount() {
		return selectableMappings.getJdbcTypeCount();
	}

	@Override
	public List<JdbcMapping> getJdbcMappings() {
		return selectableMappings.getJdbcMappings();
	}

	@Override
	public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
		return selectableMappings.forEachSelectable(
				offset,
				(index, selectable) -> action.accept( index, selectable.getJdbcMapping() )
		);
	}

	@Override
	public boolean isCreateEmptyCompositesEnabled() {
		// generally we do not want empty composites for identifiers
		return false;
	}

	@Override
	public int getNumberOfAttributeMappings() {
		return attributeMappings.size();
	}

	@Override
	public AttributeMapping getAttributeMapping(int position) {
		return attributeMappings.get( position );
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public List<AttributeMapping> getAttributeMappings() {
		return (List) attributeMappings;
	}

	@Override
	public void visitAttributeMappings(Consumer<? super AttributeMapping> action) {
		forEachAttribute( (index, attribute) -> action.accept( attribute ) );
	}

	@Override
	public void forEachAttributeMapping(IndexedConsumer<AttributeMapping> consumer) {
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			consumer.accept( i, attributeMappings.get( i ) );
		}
	}

	@Override
	public int getNumberOfFetchables() {
		return getNumberOfAttributeMappings();
	}

	@Override
	public EntityMappingType findContainingEntityMapping() {
		return idMapping.findContainingEntityMapping();
	}

	@Override
	public void visitSubParts(Consumer<ModelPart> consumer, EntityMappingType treatTargetType) {
		attributeMappings.forEach( consumer );
	}

	@Override
	public ModelPart findSubPart(String name, EntityMappingType treatTargetType) {
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final SingularAttributeMapping attribute = attributeMappings.get( i );
			if ( attribute.getAttributeName().equals( name ) ) {
				return attribute;
			}
		}
		return null;
	}

	@Override
	public <T> DomainResult<T> createDomainResult(NavigablePath navigablePath, TableGroup tableGroup, String resultVariable, DomainResultCreationState creationState) {
		throw new NotYetImplementedFor6Exception( getClass() );
	}

	@Override
	public int forEachJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		int span = 0;

		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final AttributeMapping attributeMapping = attributeMappings.get( i );
			if ( attributeMapping instanceof PluralAttributeMapping ) {
				continue;
			}
			final Object o = attributeMapping.getPropertyAccess().getGetter().get( value );
			span += attributeMapping.forEachJdbcValue( o, clause, span + offset, valuesConsumer, session );
		}
		return span;
	}

	@Override
	public void breakDownJdbcValues(Object domainValue, JdbcValueConsumer valueConsumer, SharedSessionContractImplementor session) {
		attributeMappings.forEach( (attribute) -> {
			final Object attributeValue = attribute.getValue( domainValue );
			attribute.breakDownJdbcValues( attributeValue, valueConsumer, session );
		} );
	}

	@Override
	public Object disassemble(Object value, SharedSessionContractImplementor session) {
		final Object[] result = new Object[ attributeMappings.size() ];
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final AttributeMapping attributeMapping = attributeMappings.get( i );
			Object o = attributeMapping.getPropertyAccess().getGetter().get( value );
			result[i] = attributeMapping.disassemble( o, session );
		}

		return result;
	}

	@Override
	public int forEachDisassembledJdbcValue(
			Object value,
			Clause clause,
			int offset,
			JdbcValuesConsumer valuesConsumer,
			SharedSessionContractImplementor session) {
		final Object[] values = (Object[]) value;
		int span = 0;
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final AttributeMapping mapping = attributeMappings.get( i );
			span += mapping.forEachDisassembledJdbcValue( values[i], clause, span + offset, valuesConsumer, session );
		}
		return span;
	}

	@Override
	public EmbeddableMappingType createInverseMappingType(
			EmbeddedAttributeMapping valueMapping,
			TableGroupProducer declaringTableGroupProducer,
			SelectableMappings selectableMappings,
			MappingModelCreationProcess creationProcess) {
		return new EmbeddableMappingTypeImpl(
				valueMapping,
				declaringTableGroupProducer,
				selectableMappings,
				this,
				creationProcess
		);
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// init

	private boolean finishInitialization(
			Component bootDescriptor,
			CompositeType compositeType,
			String rootTableExpression,
			String[] rootTableKeyColumnNames,
			MappingModelCreationProcess creationProcess) {

		// Reset the attribute mappings that were added in previous attempts
		this.attributeMappings.clear();

		return finishInitialization(
				navigableRole,
				bootDescriptor,
				compositeType,
				rootTableExpression,
				rootTableKeyColumnNames,
				this,
				representationStrategy,
				(attributeName, attributeType) -> {
					if ( attributeType instanceof CollectionType ) {
						throw new IllegalAttributeType( "A \"virtual id\" cannot define collection attributes : " + attributeName );
					}
					if ( attributeType instanceof AnyType ) {
						throw new IllegalAttributeType( "A \"virtual id\" cannot define <any/> attributes : " + attributeName );
					}
				},
				(column, jdbcEnvironment) -> getTableIdentifierExpression( column.getValue().getTable(), creationProcess ),
				this::addAttribute,
				() -> {
					// We need the attribute mapping types to finish initialization first before we can build the column mappings
					creationProcess.registerInitializationCallback(
							"VirtualIdEmbeddable(" + navigableRole + ")#initColumnMappings",
							this::initColumnMappings
					);
				},
				creationProcess
		);
	}

	private static String getTableIdentifierExpression(Table table, MappingModelCreationProcess creationProcess) {
		final SqlStringGenerationContext sqlStringGenerationContext = creationProcess.getCreationContext()
				.getSessionFactory()
				.getSqlStringGenerationContext();
		return sqlStringGenerationContext.format( table.getQualifiedTableName() );
	}

	private boolean initColumnMappings() {
		this.selectableMappings = SelectableMappingsImpl.from( this );
		return true;
	}

	private void addAttribute(AttributeMapping attributeMapping) {
		addAttribute( (SingularAttributeMapping) attributeMapping );
	}

	private void addAttribute(SingularAttributeMapping attributeMapping) {
		// check if we've already seen this attribute...
		for ( int i = 0; i < attributeMappings.size(); i++ ) {
			final AttributeMapping previous = attributeMappings.get( i );
			if ( attributeMapping.getAttributeName().equals( previous.getAttributeName() ) ) {
				attributeMappings.set( i, attributeMapping );
				return;
			}
		}

		attributeMappings.add( attributeMapping );
	}
}
