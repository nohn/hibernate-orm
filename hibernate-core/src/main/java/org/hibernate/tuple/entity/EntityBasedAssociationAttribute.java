/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.tuple.entity;

import org.hibernate.engine.FetchStyle;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.internal.JoinHelper;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.LoadQueryInfluencers;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.loader.PropertyPath;
import org.hibernate.persister.collection.QueryableCollection;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.persister.entity.OuterJoinLoadable;
import org.hibernate.persister.spi.HydratedCompoundValueHandler;
import org.hibernate.persister.walking.internal.FetchOptionsHelper;
import org.hibernate.persister.walking.internal.StandardAnyTypeDefinition;
import org.hibernate.persister.walking.spi.AnyMappingDefinition;
import org.hibernate.persister.walking.spi.AssociationAttributeDefinition;
import org.hibernate.persister.walking.spi.AssociationKey;
import org.hibernate.persister.walking.spi.CollectionDefinition;
import org.hibernate.persister.walking.spi.EntityDefinition;
import org.hibernate.persister.walking.spi.WalkingException;
import org.hibernate.sql.results.graph.FetchOptions;
import org.hibernate.tuple.BaselineAttributeInformation;
import org.hibernate.type.AnyType;
import org.hibernate.type.AssociationType;
import org.hibernate.type.ForeignKeyDirection;

import static org.hibernate.engine.internal.JoinHelper.getLHSColumnNames;
import static org.hibernate.engine.internal.JoinHelper.getLHSTableName;
import static org.hibernate.engine.internal.JoinHelper.getRHSColumnNames;

/**
* @author Steve Ebersole
*/
public class EntityBasedAssociationAttribute
		extends AbstractEntityBasedAttribute
		implements AssociationAttributeDefinition {


	public EntityBasedAssociationAttribute(
			EntityPersister source,
			SessionFactoryImplementor sessionFactory,
			int attributeNumber,
			String attributeName,
			AssociationType attributeType,
			BaselineAttributeInformation baselineInfo) {
		super( source, sessionFactory, attributeNumber, attributeName, attributeType, baselineInfo );
	}

	@Override
	public AssociationType getType() {
		return (AssociationType) super.getType();
	}
	@Override
	public AssociationKey getAssociationKey() {
		final AssociationType type = getType();

		if ( type.isAnyType() ) {
			return new AssociationKey(
					JoinHelper.getLHSTableName( type, attributeNumber(), (OuterJoinLoadable) getSource() ),
					JoinHelper.getLHSColumnNames(
							type,
							attributeNumber(),
							0,
							(OuterJoinLoadable) getSource(),
							sessionFactory()
					)
			);
		}

		final Joinable joinable = type.getAssociatedJoinable( sessionFactory() );

		if ( type.getForeignKeyDirection() == ForeignKeyDirection.FROM_PARENT ) {
			final String lhsTableName;
			final String[] lhsColumnNames;

			if ( joinable.isCollection() ) {
				final QueryableCollection collectionPersister = (QueryableCollection) joinable;
				lhsTableName = collectionPersister.getTableName();
				lhsColumnNames = collectionPersister.getElementColumnNames();
			}
			else {
				final OuterJoinLoadable entityPersister = (OuterJoinLoadable) source();
				lhsTableName = getLHSTableName( type, attributeNumber(), entityPersister );
				lhsColumnNames = getLHSColumnNames( type, attributeNumber(), entityPersister, sessionFactory() );
			}
			return new AssociationKey( lhsTableName, lhsColumnNames );
		}
		else {
			return new AssociationKey( joinable.getTableName(), getRHSColumnNames( type, sessionFactory() ) );
		}
	}

	@Override
	public AssociationNature getAssociationNature() {
		if ( getType().isAnyType() ) {
			return AssociationNature.ANY;
		}
		else {
			if ( getType().isCollectionType() ) {
				return AssociationNature.COLLECTION;
			}
			else {
				return AssociationNature.ENTITY;
			}
		}
	}

	@Override
	public AnyMappingDefinition toAnyDefinition() {
		return new StandardAnyTypeDefinition(
				(AnyType) getType(),
				getSource().getEntityMetamodel().getProperties()[ attributeNumber() ].isLazy()
		);
	}

	private Joinable joinable;

	protected Joinable getJoinable() {
		if ( getAssociationNature() == AssociationNature.ANY ) {
			throw new WalkingException( "Cannot resolve AnyType to a Joinable" );
		}

		if ( joinable == null ) {
			joinable = getType().getAssociatedJoinable( sessionFactory() );
		}
		return joinable;
	}

	@Override
	public EntityDefinition toEntityDefinition() {
		if ( getAssociationNature() == AssociationNature.ANY ) {
			throw new WalkingException( "Cannot treat any-type attribute as an entity type" );
		}
		if ( getAssociationNature() == AssociationNature.COLLECTION ) {
			throw new IllegalStateException( "Cannot treat collection-valued attribute as entity type" );
		}
		return (EntityPersister) getJoinable();
	}

	@Override
	public CollectionDefinition toCollectionDefinition() {
		if ( getAssociationNature() == AssociationNature.ANY ) {
			throw new WalkingException( "Cannot treat any-type attribute as a collection type" );
		}
		if ( getAssociationNature() == AssociationNature.ENTITY ) {
			throw new IllegalStateException( "Cannot treat entity-valued attribute as collection type" );
		}
		return (QueryableCollection) getJoinable();
	}

	@Override
	public FetchOptions determineFetchPlan(LoadQueryInfluencers loadQueryInfluencers, PropertyPath propertyPath) {
		final EntityPersister owningPersister = getSource().getEntityPersister();

		FetchStyle style = FetchOptionsHelper.determineFetchStyleByProfile(
				loadQueryInfluencers,
				owningPersister,
				propertyPath,
				attributeNumber()
		);
		if ( style == null ) {
			style = FetchOptionsHelper.determineFetchStyleByMetadata(
					( (OuterJoinLoadable) getSource().getEntityPersister() ).getFetchMode( attributeNumber() ),
					getType(),
					sessionFactory()
			);
		}

		final FetchTiming timing = FetchOptionsHelper.determineFetchTiming( style, getType(), sessionFactory() );

		return FetchOptions.valueOf( timing, style );
	}

	@Override
	public CascadeStyle determineCascadeStyle() {
		return getSource().getEntityPersister().getPropertyCascadeStyles()[attributeNumber()];
	}

	private HydratedCompoundValueHandler hydratedCompoundValueHandler;

	@Override
	public HydratedCompoundValueHandler getHydratedCompoundValueExtractor() {
		if ( hydratedCompoundValueHandler == null ) {
			hydratedCompoundValueHandler = new HydratedCompoundValueHandler() {
				@Override
				public Object extract(Object hydratedState) {
					return ( (Object[] ) hydratedState )[ attributeNumber() ];
				}

				@Override
				public void inject(Object hydratedState, Object value) {
					( (Object[] ) hydratedState )[ attributeNumber() ] = value;
				}
			};
		}
		return hydratedCompoundValueHandler;
	}

	@Override
	protected String loggableMetadata() {
		return super.loggableMetadata() + ",association";
	}
}
