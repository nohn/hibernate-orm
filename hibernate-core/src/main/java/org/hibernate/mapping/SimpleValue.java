/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.mapping;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.hibernate.FetchMode;
import org.hibernate.MappingException;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.boot.model.convert.internal.ClassBasedConverterDescriptor;
import org.hibernate.boot.model.convert.spi.ConverterDescriptor;
import org.hibernate.boot.model.convert.spi.JpaAttributeConverterCreationContext;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.classloading.spi.ClassLoadingException;
import org.hibernate.boot.spi.InFlightMetadataCollector;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.IdentityGenerator;
import org.hibernate.id.OptimizableGenerator;
import org.hibernate.id.PersistentIdentifierGenerator;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.metamodel.model.convert.spi.JpaAttributeConverter;
import org.hibernate.resource.beans.spi.ManagedBeanRegistry;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.JdbcTypeNameMapper;
import org.hibernate.type.descriptor.converter.AttributeConverterJdbcTypeAdapter;
import org.hibernate.type.descriptor.converter.AttributeConverterTypeAdapter;
import org.hibernate.type.descriptor.java.BasicJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;
import org.hibernate.type.descriptor.jdbc.LobTypeMappings;
import org.hibernate.type.descriptor.jdbc.NationalizedTypeMappings;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.DynamicParameterizedType;

import jakarta.persistence.AttributeConverter;

/**
 * Any value that maps to columns.
 * @author Gavin King
 */
public abstract class SimpleValue implements KeyValue {
	private static final CoreMessageLogger log = CoreLogging.messageLogger( SimpleValue.class );

	public static final String DEFAULT_ID_GEN_STRATEGY = "assigned";

	private MetadataBuildingContext buildingContext;
	private final MetadataImplementor metadata;

	private final List<Selectable> columns = new ArrayList<>();
	private final List<Boolean> insertability = new ArrayList<>();
	private final List<Boolean> updatability = new ArrayList<>();

	private String typeName;
	private Properties typeParameters;
	private boolean isVersion;
	private boolean isNationalized;
	private boolean isLob;

	private Properties identifierGeneratorProperties;
	private String identifierGeneratorStrategy = DEFAULT_ID_GEN_STRATEGY;
	private String nullValue;

	private Table table;
	private String foreignKeyName;
	private String foreignKeyDefinition;
	private boolean alternateUniqueKey;
	private boolean cascadeDeleteEnabled;

	private ConverterDescriptor attributeConverterDescriptor;
	private Type type;

	public SimpleValue(MetadataBuildingContext buildingContext) {
		this.buildingContext = buildingContext;
		this.metadata = buildingContext.getMetadataCollector();
	}

	public SimpleValue(MetadataBuildingContext buildingContext, Table table) {
		this( buildingContext );
		this.table = table;
	}

	public MetadataBuildingContext getBuildingContext() {
		return buildingContext;
	}

	public MetadataImplementor getMetadata() {
		return metadata;
	}

	@Override
	public ServiceRegistry getServiceRegistry() {
		return getMetadata().getMetadataBuildingOptions().getServiceRegistry();
	}

	@Override
	public boolean isCascadeDeleteEnabled() {
		return cascadeDeleteEnabled;
	}

	public void setCascadeDeleteEnabled(boolean cascadeDeleteEnabled) {
		this.cascadeDeleteEnabled = cascadeDeleteEnabled;
	}

	public void addColumn(Column column) {
		addColumn( column, true, true );
	}

	public void addColumn(Column column, boolean isInsertable, boolean isUpdatable) {
		justAddColumn( column, isInsertable, isUpdatable );
		column.setValue( this );
		column.setTypeIndex( columns.size() - 1 );
	}

	public void addFormula(Formula formula) {
		justAddFormula( formula );
	}

	protected void justAddColumn(Column column) {
		justAddColumn( column, true, true );
	}

	protected void justAddColumn(Column column, boolean insertable, boolean updatable) {
		int index = columns.indexOf( column );
		if ( index == -1 ) {
			columns.add(column);
			insertability.add( insertable );
			updatability.add( updatable );
		}
		else {
			if ( insertability.get( index ) != insertable ) {
				throw new IllegalStateException( "Same column is added more than once with different values for isInsertable" );
			}
			if ( updatability.get( index ) != updatable ) {
				throw new IllegalStateException( "Same column is added more than once with different values for isUpdatable" );
			}
		}
	}

	protected void justAddFormula(Formula formula) {
		columns.add( formula );
		insertability.add( false );
		updatability.add( false );
	}

	protected void sortColumns(int[] originalOrder) {
		final Selectable[] originalColumns = columns.toArray(new Selectable[0]);
		final boolean[] originalInsertability = ArrayHelper.toBooleanArray( insertability );
		final boolean[] originalUpdatability = ArrayHelper.toBooleanArray( updatability );
		for ( int i = 0; i < originalOrder.length; i++ ) {
			final int originalIndex = originalOrder[i];
			final Selectable selectable = originalColumns[originalIndex];
			if ( selectable instanceof Column ) {
				( (Column) selectable ).setTypeIndex( i );
			}
			columns.set( i, selectable );
			insertability.set( i, originalInsertability[originalIndex] );
			updatability.set( i, originalUpdatability[originalIndex] );
		}
	}

	@Override
	public boolean hasFormula() {
		Iterator iter = getColumnIterator();
		while ( iter.hasNext() ) {
			Object o = iter.next();
			if (o instanceof Formula) {
				return true;
			}
		}
		return false;
	}

	@Override
	public int getColumnSpan() {
		return columns.size();
	}

	protected Selectable getColumn(int position){
		return columns.get( position );
	}

	@Override
	public Iterator<Selectable> getColumnIterator() {
		return columns.iterator();
	}

	@Override
	public List<Selectable> getSelectables() {
		return columns;
	}

	public List getConstraintColumns() {
		return columns;
	}

	public Iterator<Selectable> getConstraintColumnIterator() {
		return getColumnIterator();
	}

	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		if ( typeName != null && typeName.startsWith( ConverterDescriptor.TYPE_NAME_PREFIX ) ) {
			final String converterClassName = typeName.substring( ConverterDescriptor.TYPE_NAME_PREFIX.length() );
			final ClassLoaderService cls = getMetadata()
					.getMetadataBuildingOptions()
					.getServiceRegistry()
					.getService( ClassLoaderService.class );
			try {
				final Class<? extends AttributeConverter> converterClass = cls.classForName( converterClassName );
				this.attributeConverterDescriptor = new ClassBasedConverterDescriptor(
						converterClass,
						false,
						( (InFlightMetadataCollector) getMetadata() ).getClassmateContext()
				);
				return;
			}
			catch (Exception e) {
				log.logBadHbmAttributeConverterType( typeName, e.getMessage() );
			}
		}

		this.typeName = typeName;
	}

	public void makeVersion() {
		this.isVersion = true;
	}

	public boolean isVersion() {
		return isVersion;
	}

	public void makeNationalized() {
		this.isNationalized = true;
	}

	public boolean isNationalized() {
		return isNationalized;
	}

	public void makeLob() {
		this.isLob = true;
	}

	public boolean isLob() {
		return isLob;
	}

	public void setTable(Table table) {
		this.table = table;
	}

	@Override
	public void createForeignKey() throws MappingException {}

	@Override
	public void createForeignKeyOfEntity(String entityName) {
		if ( !hasFormula() && !"none".equals(getForeignKeyName())) {
			ForeignKey fk = table.createForeignKey( getForeignKeyName(), getConstraintColumns(), entityName, getForeignKeyDefinition() );
			fk.setCascadeDeleteEnabled(cascadeDeleteEnabled);
		}
	}

	private IdentifierGeneratorCreator customIdGeneratorCreator;
	private IdentifierGenerator identifierGenerator;

	/**
	 * Returns the cached identifierGenerator.
	 *
	 * @return IdentifierGenerator null if
	 * {@link #createIdentifierGenerator(IdentifierGeneratorFactory, Dialect, String, String, RootClass)} was never
	 * completed.
	 *
	 * @deprecated (as of 6.0) - not used and no longer supported.
	 */
	@Deprecated
	public IdentifierGenerator getIdentifierGenerator() {
		return identifierGenerator;
	}

	public void setCustomIdGeneratorCreator(IdentifierGeneratorCreator customIdGeneratorCreator) {
		this.customIdGeneratorCreator = customIdGeneratorCreator;
	}

	public IdentifierGeneratorCreator getCustomIdGeneratorCreator() {
		return customIdGeneratorCreator;
	}

	@Override
	public IdentifierGenerator createIdentifierGenerator(
			IdentifierGeneratorFactory identifierGeneratorFactory,
			Dialect dialect,
			RootClass rootClass) throws MappingException {
		return createIdentifierGenerator( identifierGeneratorFactory, dialect, null, null, rootClass );
	}

	@Override
	public IdentifierGenerator createIdentifierGenerator(
			IdentifierGeneratorFactory identifierGeneratorFactory,
			Dialect dialect, 
			String defaultCatalog, 
			String defaultSchema, 
			RootClass rootClass) throws MappingException {
		if ( identifierGenerator != null ) {
			return identifierGenerator;
		}

		if ( customIdGeneratorCreator != null ) {
			final CustomIdGeneratorCreationContext creationContext = new CustomIdGeneratorCreationContext() {
				@Override
				public IdentifierGeneratorFactory getIdentifierGeneratorFactory() {
					return identifierGeneratorFactory;
				}

				@Override
				public Database getDatabase() {
					return buildingContext.getMetadataCollector().getDatabase();
				}

				@Override
				public ServiceRegistry getServiceRegistry() {
					return buildingContext.getBootstrapContext().getServiceRegistry();
				}

				@Override
				public String getDefaultCatalog() {
					return defaultCatalog;
				}

				@Override
				public String getDefaultSchema() {
					return defaultSchema;
				}

				@Override
				public RootClass getRootClass() {
					return rootClass;
				}
			};

			identifierGenerator = customIdGeneratorCreator.createGenerator( creationContext );
			return identifierGenerator;
		}

		final Properties params = new Properties();

		// This is for backwards compatibility only;
		// when this method is called by Hibernate ORM, defaultSchema and defaultCatalog are always
		// null, and defaults are handled later.
		if ( defaultSchema != null ) {
			params.setProperty( PersistentIdentifierGenerator.SCHEMA, defaultSchema);
		}

		if ( defaultCatalog != null ) {
			params.setProperty( PersistentIdentifierGenerator.CATALOG, defaultCatalog );
		}

		// default initial value and allocation size per-JPA defaults
		params.setProperty( OptimizableGenerator.INITIAL_PARAM, String.valueOf( OptimizableGenerator.DEFAULT_INITIAL_VALUE ) );
		params.setProperty( OptimizableGenerator.INCREMENT_PARAM, String.valueOf( OptimizableGenerator.DEFAULT_INCREMENT_SIZE ) );
		//init the table here instead of earlier, so that we can get a quoted table name
		//TODO: would it be better to simply pass the qualified table name, instead of
		//      splitting it up into schema/catalog/table names
		final String tableName = getTable().getQuotedName( dialect );
		params.setProperty( PersistentIdentifierGenerator.TABLE, tableName );

		//pass the column name (a generated id almost always has a single column)
		final String columnName = ( (Column) getColumnIterator().next() ).getQuotedName( dialect );
		params.setProperty( PersistentIdentifierGenerator.PK, columnName );

		//pass the entity-name, if not a collection-id
		if ( rootClass != null ) {
			params.setProperty( IdentifierGenerator.ENTITY_NAME, rootClass.getEntityName() );
			params.setProperty( IdentifierGenerator.JPA_ENTITY_NAME, rootClass.getJpaEntityName() );
			params.setProperty( OptimizableGenerator.IMPLICIT_NAME_BASE, getTable().getName() );

			final StringBuilder tables = new StringBuilder();
			final Iterator<Table> itr = rootClass.getIdentityTables().iterator();
			while ( itr.hasNext() ) {
				final Table table = itr.next();
				tables.append( table.getQuotedName( dialect ) );
				if ( itr.hasNext() ) {
					tables.append( ", " );
				}
			}
			params.setProperty( PersistentIdentifierGenerator.TABLES, tables.toString() );
		}
		else {
			params.setProperty( PersistentIdentifierGenerator.TABLES, tableName );
			params.setProperty( OptimizableGenerator.IMPLICIT_NAME_BASE, tableName );
		}

		if ( identifierGeneratorProperties != null ) {
			params.putAll( identifierGeneratorProperties );
		}

		// TODO : we should pass along all settings once "config lifecycle" is hashed out...
		final ConfigurationService cs = metadata.getMetadataBuildingOptions().getServiceRegistry()
				.getService( ConfigurationService.class );

		params.put(
				IdentifierGenerator.CONTRIBUTOR_NAME,
				buildingContext.getCurrentContributorName()
		);

		if ( cs.getSettings().get( AvailableSettings.PREFERRED_POOLED_OPTIMIZER ) != null ) {
			params.put(
					AvailableSettings.PREFERRED_POOLED_OPTIMIZER,
					cs.getSettings().get( AvailableSettings.PREFERRED_POOLED_OPTIMIZER )
			);
		}

		identifierGenerator = identifierGeneratorFactory.createIdentifierGenerator(
				identifierGeneratorStrategy,
				getType(),
				params
		);

		return identifierGenerator;
	}

	public boolean isUpdateable() {
		//needed to satisfy KeyValue
		return true;
	}
	
	public FetchMode getFetchMode() {
		return FetchMode.SELECT;
	}

	public Table getTable() {
		return table;
	}

	/**
	 * Returns the identifierGeneratorStrategy.
	 * @return String
	 */
	public String getIdentifierGeneratorStrategy() {
		return identifierGeneratorStrategy;
	}

	/**
	 * Sets the identifierGeneratorStrategy.
	 * @param identifierGeneratorStrategy The identifierGeneratorStrategy to set
	 */
	public void setIdentifierGeneratorStrategy(String identifierGeneratorStrategy) {
		this.identifierGeneratorStrategy = identifierGeneratorStrategy;
	}

	public boolean isIdentityColumn(IdentifierGeneratorFactory identifierGeneratorFactory, Dialect dialect) {
		return IdentityGenerator.class.isAssignableFrom(identifierGeneratorFactory.getIdentifierGeneratorClass( identifierGeneratorStrategy ));
	}

	public Properties getIdentifierGeneratorProperties() {
		return identifierGeneratorProperties;
	}

	/**
	 * Sets the identifierGeneratorProperties.
	 * @param identifierGeneratorProperties The identifierGeneratorProperties to set
	 */
	public void setIdentifierGeneratorProperties(Properties identifierGeneratorProperties) {
		this.identifierGeneratorProperties = identifierGeneratorProperties;
	}

	/**
	 * Sets the identifierGeneratorProperties.
	 * @param identifierGeneratorProperties The identifierGeneratorProperties to set
	 */
	public void setIdentifierGeneratorProperties(Map identifierGeneratorProperties) {
		if ( identifierGeneratorProperties != null ) {
			Properties properties = new Properties();
			properties.putAll( identifierGeneratorProperties );
			setIdentifierGeneratorProperties( properties );
		}
	}

	public String getNullValue() {
		return nullValue;
	}

	/**
	 * Sets the nullValue.
	 * @param nullValue The nullValue to set
	 */
	public void setNullValue(String nullValue) {
		this.nullValue = nullValue;
	}

	public String getForeignKeyName() {
		return foreignKeyName;
	}

	public void setForeignKeyName(String foreignKeyName) {
		this.foreignKeyName = foreignKeyName;
	}

	public boolean isConstrained() {
		return !"none".equals( foreignKeyName ) && !hasFormula();
	}

	public String getForeignKeyDefinition() {
		return foreignKeyDefinition;
	}

	public void setForeignKeyDefinition(String foreignKeyDefinition) {
		this.foreignKeyDefinition = foreignKeyDefinition;
	}

	public boolean isAlternateUniqueKey() {
		return alternateUniqueKey;
	}

	public void setAlternateUniqueKey(boolean unique) {
		this.alternateUniqueKey = unique;
	}

	public boolean isNullable() {
		Iterator itr = getColumnIterator();
		while ( itr.hasNext() ) {
			final Object selectable = itr.next();
			if ( selectable instanceof Formula ) {
				// if there are *any* formulas, then the Value overall is
				// considered nullable
				return true;
			}
			else if ( !( (Column) selectable ).isNullable() ) {
				// if there is a single non-nullable column, the Value
				// overall is considered non-nullable.
				return false;
			}
		}
		// nullable by default
		return true;
	}

	public boolean isSimpleValue() {
		return true;
	}

	public boolean isValid(Mapping mapping) throws MappingException {
		return getColumnSpan() == getType().getColumnSpan( mapping );
	}

	protected void setAttributeConverterDescriptor(ConverterDescriptor descriptor) {
		this.attributeConverterDescriptor = descriptor;
	}

	protected ConverterDescriptor getAttributeConverterDescriptor() {
		return attributeConverterDescriptor;
	}

	//	public Type getType() throws MappingException {
//		if ( type != null ) {
//			return type;
//		}
//
//		if ( typeName == null ) {
//			throw new MappingException( "No type name" );
//		}
//
//		if ( typeParameters != null
//				&& Boolean.valueOf( typeParameters.getProperty( DynamicParameterizedType.IS_DYNAMIC ) )
//				&& typeParameters.get( DynamicParameterizedType.PARAMETER_TYPE ) == null ) {
//			createParameterImpl();
//		}
//
//		Type result = getMetadata().getTypeConfiguration().getTypeResolver().heuristicType( typeName, typeParameters );
//
//		if ( isVersion && result instanceof BinaryType ) {
//			// if this is a byte[] version/timestamp, then we need to use RowVersionType
//			// instead of BinaryType (HHH-10413)
//			// todo (6.0) - although for T/SQL databases we should use its
//			log.debug( "version is BinaryType; changing to RowVersionType" );
//			result = RowVersionType.INSTANCE;
//		}
//
//		if ( result == null ) {
//			String msg = "Could not determine type for: " + typeName;
//			if ( table != null ) {
//				msg += ", at table: " + table.getName();
//			}
//			if ( columns != null && columns.size() > 0 ) {
//				msg += ", for columns: " + columns;
//			}
//			throw new MappingException( msg );
//		}
//
//		return result;
//	}

	@Override
	public void setTypeUsingReflection(String className, String propertyName) throws MappingException {
		// NOTE : this is called as the last piece in setting SimpleValue type information, and implementations
		// rely on that fact, using it as a signal that all information it is going to get is defined at this point...

		if ( typeName != null ) {
			// assume either (a) explicit type was specified or (b) determine was already performed
			return;
		}

		if ( type != null ) {
			return;
		}

		if ( attributeConverterDescriptor == null ) {
			// this is here to work like legacy.  This should change when we integrate with metamodel to
			// look for SqlTypeDescriptor and JavaTypeDescriptor individually and create the BasicType (well, really
			// keep a registry of [SqlTypeDescriptor,JavaTypeDescriptor] -> BasicType...)
			if ( className == null ) {
				throw new MappingException( "Attribute types for a dynamic entity must be explicitly specified: " + propertyName );
			}
			typeName = ReflectHelper.reflectedPropertyClass(
					className,
					propertyName,
					getMetadata()
							.getMetadataBuildingOptions()
							.getServiceRegistry()
							.getService( ClassLoaderService.class )
			).getName();
			// todo : to fully support isNationalized here we need to do the process hinted at above
			// 		essentially, much of the logic from #buildAttributeConverterTypeAdapter wrt resolving
			//		a (1) SqlTypeDescriptor, a (2) JavaTypeDescriptor and dynamically building a BasicType
			// 		combining them.
			return;
		}

		// we had an AttributeConverter...
		type = buildAttributeConverterTypeAdapter();
	}

	/**
	 * Build a Hibernate Type that incorporates the JPA AttributeConverter.  AttributeConverter works totally in
	 * memory, meaning it converts between one Java representation (the entity attribute representation) and another
	 * (the value bound into JDBC statements or extracted from results).  However, the Hibernate Type system operates
	 * at the lower level of actually dealing directly with those JDBC objects.  So even though we have an
	 * AttributeConverter, we still need to "fill out" the rest of the BasicType data and bridge calls
	 * to bind/extract through the converter.
	 * <p/>
	 * Essentially the idea here is that an intermediate Java type needs to be used.  Let's use an example as a means
	 * to illustrate...  Consider an {@code AttributeConverter<Integer,String>}.  This tells Hibernate that the domain
	 * model defines this attribute as an Integer value (the 'entityAttributeJavaType'), but that we need to treat the
	 * value as a String (the 'databaseColumnJavaType') when dealing with JDBC (aka, the database type is a
	 * VARCHAR/CHAR):<ul>
	 *     <li>
	 *         When binding values to PreparedStatements we need to convert the Integer value from the entity
	 *         into a String and pass that String to setString.  The conversion is handled by calling
	 *         {@link AttributeConverter#convertToDatabaseColumn(Object)}
	 *     </li>
	 *     <li>
	 *         When extracting values from ResultSets (or CallableStatement parameters) we need to handle the
	 *         value via getString, and convert that returned String to an Integer.  That conversion is handled
	 *         by calling {@link AttributeConverter#convertToEntityAttribute(Object)}
	 *     </li>
	 * </ul>
	 *
	 * @return The built AttributeConverter -> Type adapter
	 *
	 * @todo : ultimately I want to see attributeConverterJavaType and attributeConverterJdbcTypeCode specify-able separately
	 * then we can "play them against each other" in terms of determining proper typing
	 *
	 * @todo : see if we already have previously built a custom on-the-fly BasicType for this AttributeConverter; see note below about caching
	 */
	@SuppressWarnings("unchecked")
	private Type buildAttributeConverterTypeAdapter() {
		// todo : validate the number of columns present here?

		final JpaAttributeConverter jpaAttributeConverter = attributeConverterDescriptor.createJpaAttributeConverter(
				new JpaAttributeConverterCreationContext() {
					@Override
					public ManagedBeanRegistry getManagedBeanRegistry() {
						return getMetadata()
								.getMetadataBuildingOptions()
								.getServiceRegistry()
								.getService( ManagedBeanRegistry.class );
					}

					@Override
					public TypeConfiguration getTypeConfiguration() {
						return getMetadata().getTypeConfiguration();
					}
				}
		);

		final BasicJavaType<?> domainJtd = (BasicJavaType<?>) jpaAttributeConverter.getDomainJavaTypeDescriptor();


		// build the SqlTypeDescriptor adapter ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Going back to the illustration, this should be a SqlTypeDescriptor that handles the Integer <-> String
		//		conversions.  This is the more complicated piece.  First we need to determine the JDBC type code
		//		corresponding to the AttributeConverter's declared "databaseColumnJavaType" (how we read that value out
		// 		of ResultSets).  See JdbcTypeJavaClassMappings for details.  Again, given example, this should return
		// 		VARCHAR/CHAR
		final JdbcType recommendedJdbcType = jpaAttributeConverter.getRelationalJavaTypeDescriptor().getRecommendedJdbcType(
				// todo (6.0) : handle the other JdbcRecommendedSqlTypeMappingContext methods
				new JdbcTypeDescriptorIndicators() {
					@Override
					public TypeConfiguration getTypeConfiguration() {
						return metadata.getTypeConfiguration();
					}

					@Override
					public TimeZoneStorageStrategy getDefaultTimeZoneStorageStrategy() {
						return buildingContext.getBuildingOptions().getDefaultTimeZoneStorage();
					}
				}
		);
		int jdbcTypeCode = recommendedJdbcType.getDefaultSqlTypeCode();
		if ( isLob() ) {
			if ( LobTypeMappings.isMappedToKnownLobCode( jdbcTypeCode ) ) {
				jdbcTypeCode = LobTypeMappings.getLobCodeTypeMapping( jdbcTypeCode );
			}
			else {
				if ( Serializable.class.isAssignableFrom( domainJtd.getJavaTypeClass() ) ) {
					jdbcTypeCode = Types.BLOB;
				}
				else {
					throw new IllegalArgumentException(
							String.format(
									Locale.ROOT,
									"JDBC type-code [%s (%s)] not known to have a corresponding LOB equivalent, and Java type is not Serializable (to use BLOB)",
									jdbcTypeCode,
									JdbcTypeNameMapper.getTypeName( jdbcTypeCode )
							)
					);
				}
			}
		}
		if ( isNationalized() ) {
			jdbcTypeCode = NationalizedTypeMappings.toNationalizedTypeCode( jdbcTypeCode );
		}

		final JdbcType jdbcType = metadata.getTypeConfiguration()
								.getJdbcTypeDescriptorRegistry()
								.getDescriptor( jdbcTypeCode );

		// and finally construct the adapter, which injects the AttributeConverter calls into the binding/extraction
		// 		process...
		final JdbcType jdbcTypeAdapter = new AttributeConverterJdbcTypeAdapter(
				jpaAttributeConverter,
				jdbcType,
				jpaAttributeConverter.getRelationalJavaTypeDescriptor()
		);

		// todo : cache the AttributeConverterTypeAdapter in case that AttributeConverter is applied multiple times.

		final String name = ConverterDescriptor.TYPE_NAME_PREFIX + jpaAttributeConverter.getConverterJavaTypeDescriptor().getJavaType().getTypeName();
		final String description = String.format(
				"BasicType adapter for AttributeConverter<%s,%s>",
				jpaAttributeConverter.getDomainJavaTypeDescriptor().getJavaType().getTypeName(),
				jpaAttributeConverter.getRelationalJavaTypeDescriptor().getJavaType().getTypeName()
		);
		return new AttributeConverterTypeAdapter<>(
				name,
				description,
				jpaAttributeConverter,
				jdbcTypeAdapter,
				jpaAttributeConverter.getRelationalJavaTypeDescriptor(),
				jpaAttributeConverter.getDomainJavaTypeDescriptor(),
				null
		);
	}

	public boolean isTypeSpecified() {
		return typeName != null;
	}

	public void setTypeParameters(Properties parameterMap) {
		this.typeParameters = parameterMap;
	}

	public void setTypeParameters(Map<String, String> parameters) {
		if ( parameters != null ) {
			Properties properties = new Properties();
			properties.putAll( parameters );
			setTypeParameters( properties );
		}
	}

	public Properties getTypeParameters() {
		return typeParameters;
	}

	public void copyTypeFrom( SimpleValue sourceValue ) {
		setTypeName( sourceValue.getTypeName() );
		setTypeParameters( sourceValue.getTypeParameters() );

		type = sourceValue.type;
		attributeConverterDescriptor = sourceValue.attributeConverterDescriptor;
	}

	@Override
	public boolean isSame(Value other) {
		return this == other || other instanceof SimpleValue && isSame( (SimpleValue) other );
	}

	protected static boolean isSame(Value v1, Value v2) {
		return v1 == v2 || v1 != null && v2 != null && v1.isSame( v2 );
	}

	public boolean isSame(SimpleValue other) {
		return Objects.equals( columns, other.columns )
				&& Objects.equals( typeName, other.typeName )
				&& Objects.equals( typeParameters, other.typeParameters )
				&& Objects.equals( table, other.table )
				&& Objects.equals( foreignKeyName, other.foreignKeyName )
				&& Objects.equals( foreignKeyDefinition, other.foreignKeyDefinition );
	}

	@Override
	public String toString() {
		return getClass().getName() + '(' + columns.toString() + ')';
	}

	public Object accept(ValueVisitor visitor) {
		return visitor.accept(this);
	}

	@Override
	public boolean[] getColumnInsertability() {
		return extractBooleansFromList( insertability );
	}

	@Override
	public boolean hasAnyInsertableColumns() {
		//noinspection ForLoopReplaceableByForEach
		for ( int i = 0; i < insertability.size(); i++ ) {
			if ( insertability.get( i ) ) {
				return true;
			}
		}

		return false;
	}

	@Override
	public boolean[] getColumnUpdateability() {
		return extractBooleansFromList( updatability );
	}

	@Override
	public boolean hasAnyUpdatableColumns() {
		for ( int i = 0; i < updatability.size(); i++ ) {
			if ( updatability.get( i ) ) {
				return true;
			}
		}

		return false;
	}

	private static boolean[] extractBooleansFromList(List<Boolean> list) {
		final boolean[] array = new boolean[ list.size() ];
		int i = 0;
		for ( Boolean value : list ) {
			array[ i++ ] = value;
		}
		return array;
	}

	public ConverterDescriptor getJpaAttributeConverterDescriptor() {
		return attributeConverterDescriptor;
	}

	public void setJpaAttributeConverterDescriptor(ConverterDescriptor descriptor) {
		this.attributeConverterDescriptor = descriptor;
	}

	protected void createParameterImpl() {
		try {
			final String[] columnNames = new String[ columns.size() ];
			final Long[] columnLengths = new Long[ columns.size() ];

			for ( int i = 0; i < columns.size(); i++ ) {
				final Selectable selectable = columns.get(i);
				if ( selectable instanceof Column ) {
					final Column column = (Column) selectable;
					columnNames[i] = column.getName();
					columnLengths[i] = column.getLength();
				}
			}

			final XProperty xProperty = (XProperty) typeParameters.get( DynamicParameterizedType.XPROPERTY );
			// todo : not sure this works for handling @MapKeyEnumerated
			final Annotation[] annotations = xProperty == null
					? null
					: xProperty.getAnnotations();

			final ClassLoaderService classLoaderService = getMetadata()
					.getMetadataBuildingOptions()
					.getServiceRegistry()
					.getService( ClassLoaderService.class );
			typeParameters.put(
					DynamicParameterizedType.PARAMETER_TYPE,
					new ParameterTypeImpl(
							classLoaderService.classForTypeName(
									typeParameters.getProperty( DynamicParameterizedType.RETURNED_CLASS )
							),
							annotations,
							table.getCatalog(),
							table.getSchema(),
							table.getName(),
							Boolean.valueOf( typeParameters.getProperty( DynamicParameterizedType.IS_PRIMARY_KEY ) ),
							columnNames,
							columnLengths
					)
			);
		}
		catch ( ClassLoadingException e ) {
			throw new MappingException( "Could not create DynamicParameterizedType for type: " + typeName, e );
		}
	}
	public DynamicParameterizedType.ParameterType makeParameterImpl() {
		try {
			final String[] columnNames = new String[ columns.size() ];
			final Long[] columnLengths = new Long[ columns.size() ];

			for ( int i = 0; i < columns.size(); i++ ) {
				final Selectable selectable = columns.get(i);
				if ( selectable instanceof Column ) {
					final Column column = (Column) selectable;
					columnNames[i] = column.getName();
					columnLengths[i] = column.getLength();
				}
			}

			final XProperty xProperty = (XProperty) typeParameters.get( DynamicParameterizedType.XPROPERTY );
			// todo : not sure this works for handling @MapKeyEnumerated
			final Annotation[] annotations = xProperty == null
					? null
					: xProperty.getAnnotations();

			final ClassLoaderService classLoaderService = getMetadata()
					.getMetadataBuildingOptions()
					.getServiceRegistry()
					.getService( ClassLoaderService.class );

			return new ParameterTypeImpl(
					classLoaderService.classForTypeName( typeParameters.getProperty( DynamicParameterizedType.RETURNED_CLASS ) ),
					annotations,
					table.getCatalog(),
					table.getSchema(),
					table.getName(),
					Boolean.parseBoolean( typeParameters.getProperty( DynamicParameterizedType.IS_PRIMARY_KEY ) ),
					columnNames,
					columnLengths
			);
		}
		catch ( ClassLoadingException e ) {
			throw new MappingException( "Could not create DynamicParameterizedType for type: " + typeName, e );
		}
	}

	private static final class ParameterTypeImpl implements DynamicParameterizedType.ParameterType {

		private final Class returnedClass;
		private final Annotation[] annotationsMethod;
		private final String catalog;
		private final String schema;
		private final String table;
		private final boolean primaryKey;
		private final String[] columns;
		private final Long[] columnLengths;

		private ParameterTypeImpl(
				Class returnedClass,
				Annotation[] annotationsMethod,
				String catalog,
				String schema,
				String table,
				boolean primaryKey,
				String[] columns,
				Long[] columnLengths) {
			this.returnedClass = returnedClass;
			this.annotationsMethod = annotationsMethod;
			this.catalog = catalog;
			this.schema = schema;
			this.table = table;
			this.primaryKey = primaryKey;
			this.columns = columns;
			this.columnLengths = columnLengths;
		}

		@Override
		public Class getReturnedClass() {
			return returnedClass;
		}

		@Override
		public Annotation[] getAnnotationsMethod() {
			return annotationsMethod;
		}

		@Override
		public String getCatalog() {
			return catalog;
		}

		@Override
		public String getSchema() {
			return schema;
		}

		@Override
		public String getTable() {
			return table;
		}

		@Override
		public boolean isPrimaryKey() {
			return primaryKey;
		}

		@Override
		public String[] getColumns() {
			return columns;
		}

		@Override
		public Long[] getColumnLengths() {
			return columnLengths;
		}
	}
}
