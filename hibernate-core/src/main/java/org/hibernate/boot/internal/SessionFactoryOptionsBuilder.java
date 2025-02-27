/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.boot.internal;

import java.lang.reflect.Constructor;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import org.hibernate.ConnectionAcquisitionMode;
import org.hibernate.ConnectionReleaseMode;
import org.hibernate.CustomEntityDirtinessStrategy;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityNameResolver;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionFactoryObserver;
import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.boot.SchemaAutoTooling;
import org.hibernate.boot.TempTableDdlTransactionHandling;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.classloading.spi.ClassLoaderService;
import org.hibernate.boot.registry.selector.spi.StrategySelectionException;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.internal.NoCachingRegionFactory;
import org.hibernate.cache.internal.StandardTimestampsCacheFactory;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsCacheFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.BaselineSessionEventsListenerBuilder;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.config.internal.ConfigurationServiceImpl;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.env.spi.ExtractedDatabaseMetaData;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.id.uuid.LocalObjectUuidHelper;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.NullnessHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.jpa.spi.JpaCompliance;
import org.hibernate.jpa.spi.MutableJpaCompliance;
import org.hibernate.loader.BatchFetchStyle;
import org.hibernate.metamodel.RepresentationMode;
import org.hibernate.proxy.EntityNotFoundDelegate;
import org.hibernate.query.ImmutableEntityUpdateQueryHandlingMode;
import org.hibernate.query.NullPrecedence;
import org.hibernate.query.criteria.ValueHandlingMode;
import org.hibernate.query.hql.HqlTranslator;
import org.hibernate.query.sqm.function.SqmFunctionDescriptor;
import org.hibernate.query.sqm.function.SqmFunctionRegistry;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.query.sqm.sql.SqmTranslatorFactory;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.hibernate.stat.Statistics;
import org.hibernate.tuple.entity.EntityTuplizer;
import org.hibernate.tuple.entity.EntityTuplizerFactory;
import org.hibernate.type.FormatMapper;
import org.hibernate.type.JacksonJsonFormatMapper;
import org.hibernate.type.JsonBJsonFormatMapper;

import static org.hibernate.cfg.AvailableSettings.ALLOW_JTA_TRANSACTION_ACCESS;
import static org.hibernate.cfg.AvailableSettings.ALLOW_REFRESH_DETACHED_ENTITY;
import static org.hibernate.cfg.AvailableSettings.ALLOW_UPDATE_OUTSIDE_TRANSACTION;
import static org.hibernate.cfg.AvailableSettings.AUTO_CLOSE_SESSION;
import static org.hibernate.cfg.AvailableSettings.AUTO_EVICT_COLLECTION_CACHE;
import static org.hibernate.cfg.AvailableSettings.AUTO_SESSION_EVENTS_LISTENER;
import static org.hibernate.cfg.AvailableSettings.BATCH_FETCH_STYLE;
import static org.hibernate.cfg.AvailableSettings.BATCH_VERSIONED_DATA;
import static org.hibernate.cfg.AvailableSettings.CACHE_REGION_PREFIX;
import static org.hibernate.cfg.AvailableSettings.CALLABLE_NAMED_PARAMS_ENABLED;
import static org.hibernate.cfg.AvailableSettings.CHECK_NULLABILITY;
import static org.hibernate.cfg.AvailableSettings.CONNECTION_HANDLING;
import static org.hibernate.cfg.AvailableSettings.CONVENTIONAL_JAVA_CONSTANTS;
import static org.hibernate.cfg.AvailableSettings.CRITERIA_VALUE_HANDLING_MODE;
import static org.hibernate.cfg.AvailableSettings.CUSTOM_ENTITY_DIRTINESS_STRATEGY;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_BATCH_FETCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_CATALOG;
import static org.hibernate.cfg.AvailableSettings.DEFAULT_SCHEMA;
import static org.hibernate.cfg.AvailableSettings.DELAY_ENTITY_LOADER_CREATIONS;
import static org.hibernate.cfg.AvailableSettings.DISCARD_PC_ON_CLOSE;
import static org.hibernate.cfg.AvailableSettings.ENABLE_LAZY_LOAD_NO_TRANS;
import static org.hibernate.cfg.AvailableSettings.FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH;
import static org.hibernate.cfg.AvailableSettings.FLUSH_BEFORE_COMPLETION;
import static org.hibernate.cfg.AvailableSettings.GENERATE_STATISTICS;
import static org.hibernate.cfg.AvailableSettings.IMMUTABLE_ENTITY_UPDATE_QUERY_HANDLING_MODE;
import static org.hibernate.cfg.AvailableSettings.INTERCEPTOR;
import static org.hibernate.cfg.AvailableSettings.IN_CLAUSE_PARAMETER_PADDING;
import static org.hibernate.cfg.AvailableSettings.JDBC_TIME_ZONE;
import static org.hibernate.cfg.AvailableSettings.JPA_CALLBACKS_ENABLED;
import static org.hibernate.cfg.AvailableSettings.JTA_TRACK_BY_THREAD;
import static org.hibernate.cfg.AvailableSettings.LOG_SESSION_METRICS;
import static org.hibernate.cfg.AvailableSettings.MAX_FETCH_DEPTH;
import static org.hibernate.cfg.AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER;
import static org.hibernate.cfg.AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER;
import static org.hibernate.cfg.AvailableSettings.NATIVE_EXCEPTION_HANDLING_51_COMPLIANCE;
import static org.hibernate.cfg.AvailableSettings.OMIT_JOIN_OF_SUPERCLASS_TABLES;
import static org.hibernate.cfg.AvailableSettings.ORDER_INSERTS;
import static org.hibernate.cfg.AvailableSettings.ORDER_UPDATES;
import static org.hibernate.cfg.AvailableSettings.PREFER_USER_TRANSACTION;
import static org.hibernate.cfg.AvailableSettings.QUERY_CACHE_FACTORY;
import static org.hibernate.cfg.AvailableSettings.QUERY_STARTUP_CHECKING;
import static org.hibernate.cfg.AvailableSettings.QUERY_STATISTICS_MAX_SIZE;
import static org.hibernate.cfg.AvailableSettings.QUERY_SUBSTITUTIONS;
import static org.hibernate.cfg.AvailableSettings.SESSION_FACTORY_NAME;
import static org.hibernate.cfg.AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI;
import static org.hibernate.cfg.AvailableSettings.SESSION_SCOPED_INTERCEPTOR;
import static org.hibernate.cfg.AvailableSettings.STATEMENT_BATCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.STATEMENT_FETCH_SIZE;
import static org.hibernate.cfg.AvailableSettings.STATEMENT_INSPECTOR;
import static org.hibernate.cfg.AvailableSettings.USE_DIRECT_REFERENCE_CACHE_ENTRIES;
import static org.hibernate.cfg.AvailableSettings.USE_GET_GENERATED_KEYS;
import static org.hibernate.cfg.AvailableSettings.USE_IDENTIFIER_ROLLBACK;
import static org.hibernate.cfg.AvailableSettings.USE_MINIMAL_PUTS;
import static org.hibernate.cfg.AvailableSettings.USE_QUERY_CACHE;
import static org.hibernate.cfg.AvailableSettings.USE_SCROLLABLE_RESULTSET;
import static org.hibernate.cfg.AvailableSettings.USE_SECOND_LEVEL_CACHE;
import static org.hibernate.cfg.AvailableSettings.USE_SQL_COMMENTS;
import static org.hibernate.cfg.AvailableSettings.USE_STRUCTURED_CACHE;
import static org.hibernate.cfg.AvailableSettings.VALIDATE_QUERY_PARAMETERS;
import static org.hibernate.cfg.AvailableSettings.WRAP_RESULT_SETS;
import static org.hibernate.engine.config.spi.StandardConverters.BOOLEAN;
import static org.hibernate.internal.CoreLogging.messageLogger;
import static org.hibernate.internal.log.DeprecationLogger.DEPRECATION_LOGGER;

/**
 * In-flight state of {@link SessionFactoryOptions}
 * during {@link org.hibernate.boot.SessionFactoryBuilder} processing.
 *
 * The intention is that SessionFactoryBuilder internally creates and populates
 * this builder, which is then used to construct the SessionFactoryOptions
 * as part of building the SessionFactory ({@link org.hibernate.boot.SessionFactoryBuilder#build})
 *
 * @author Steve Ebersole
 */
@SuppressWarnings("WeakerAccess")
public class SessionFactoryOptionsBuilder implements SessionFactoryOptions {
	private static final CoreMessageLogger log = messageLogger( SessionFactoryOptionsBuilder.class );

	private final String uuid = LocalObjectUuidHelper.generateLocalObjectUuid();
	private final StandardServiceRegistry serviceRegistry;

	// integration
	private Object beanManagerReference;
	private Object validatorFactoryReference;
	private FormatMapper jsonFormatMapper;

	// SessionFactory behavior
	private boolean jpaBootstrap;
	private String sessionFactoryName;
	private boolean sessionFactoryNameAlsoJndiName;

	// Session behavior
	private boolean flushBeforeCompletionEnabled;
	private boolean autoCloseSessionEnabled;
	private boolean jtaTransactionAccessEnabled;
	private boolean allowOutOfTransactionUpdateOperations;
	private boolean releaseResourcesOnCloseEnabled;
	private boolean allowRefreshDetachedEntity;

	// (JTA) transaction handling
	private boolean jtaTrackByThread;
	private boolean preferUserTransaction;

	// Statistics/Interceptor/observers
	private boolean statisticsEnabled;
	private Interceptor interceptor;
	private Class<? extends Interceptor> statelessInterceptorClass;
	private Supplier<? extends Interceptor> statelessInterceptorSupplier;
	private StatementInspector statementInspector;
	private List<SessionFactoryObserver> sessionFactoryObserverList = new ArrayList<>();
	private BaselineSessionEventsListenerBuilder baselineSessionEventsListenerBuilder;	// not exposed on builder atm

	// persistence behavior
	private CustomEntityDirtinessStrategy customEntityDirtinessStrategy;
	private List<EntityNameResolver> entityNameResolvers = new ArrayList<>();
	private EntityNotFoundDelegate entityNotFoundDelegate;
	private boolean identifierRollbackEnabled;
	private EntityTuplizerFactory entityTuplizerFactory = new EntityTuplizerFactory();
	private boolean checkNullability;
	private boolean initializeLazyStateOutsideTransactions;
	private TempTableDdlTransactionHandling tempTableDdlTransactionHandling;
	private BatchFetchStyle batchFetchStyle;
	private boolean delayBatchFetchLoaderCreations;
	private int defaultBatchFetchSize;
	private Integer maximumFetchDepth;
	private NullPrecedence defaultNullPrecedence;
	private boolean orderUpdatesEnabled;
	private boolean orderInsertsEnabled;
	private boolean postInsertIdentifierDelayed;
	private boolean collectionsInDefaultFetchGroupEnabled;

	// JPA callbacks
	private boolean callbacksEnabled;

	// multi-tenancy
	private boolean multiTenancyEnabled;
	private CurrentTenantIdentifierResolver currentTenantIdentifierResolver;

	// Queries
	private HqlTranslator hqlTranslator;
	private SqmMultiTableMutationStrategy sqmMultiTableMutationStrategy;
	private SqmMultiTableInsertStrategy sqmMultiTableInsertStrategy;
	private SqmFunctionRegistry sqmFunctionRegistry;
	private SqmTranslatorFactory sqmTranslatorFactory;
	private Boolean useOfJdbcNamedParametersEnabled;
	private Map querySubstitutions;
	private boolean namedQueryStartupCheckingEnabled;
	private boolean conventionalJavaConstants;
	private final boolean omitJoinOfSuperclassTablesEnabled;
	private final int preferredSqlTypeCodeForBoolean;
	private final TimeZoneStorageStrategy defaultTimeZoneStorageStrategy;

	// Caching
	private boolean secondLevelCacheEnabled;
	private boolean queryCacheEnabled;
	private TimestampsCacheFactory timestampsCacheFactory;
	private String cacheRegionPrefix;
	private boolean minimalPutsEnabled;
	private boolean structuredCacheEntriesEnabled;
	private boolean directReferenceCacheEntriesEnabled;
	private boolean autoEvictCollectionCache;

	// Schema tooling
	private SchemaAutoTooling schemaAutoTooling;

	// JDBC Handling
	private boolean getGeneratedKeysEnabled;
	private int jdbcBatchSize;
	private boolean jdbcBatchVersionedData;
	private Integer jdbcFetchSize;
	private boolean scrollableResultSetsEnabled;
	private boolean commentsEnabled;
	private PhysicalConnectionHandlingMode connectionHandlingMode;
	private boolean connectionProviderDisablesAutoCommit;
	private boolean wrapResultSetsEnabled;
	private TimeZone jdbcTimeZone;
	private boolean queryParametersValidationEnabled;
	private ValueHandlingMode criteriaValueHandlingMode;
	private ImmutableEntityUpdateQueryHandlingMode immutableEntityUpdateQueryHandlingMode;
	// These two settings cannot be modified from the builder,
	// in order to maintain consistency.
	// Indeed, other components (the schema tools) also make use of these settings,
	// and THOSE do not have access to session factory options.
	private final String defaultCatalog;
	private final String defaultSchema;

	private Map<String, SqmFunctionDescriptor> sqlFunctions;

	private JpaCompliance jpaCompliance;

	private boolean failOnPaginationOverCollectionFetchEnabled;
	private boolean inClauseParameterPaddingEnabled;

	private boolean nativeExceptionHandling51Compliance;
	private int queryStatisticsMaxSize;


	@SuppressWarnings({"WeakerAccess", "deprecation"})
	public SessionFactoryOptionsBuilder(StandardServiceRegistry serviceRegistry, BootstrapContext context) {
		this.serviceRegistry = serviceRegistry;
		this.jpaBootstrap = context.isJpaBootstrap();

		final StrategySelector strategySelector = serviceRegistry.getService( StrategySelector.class );
		ConfigurationService cfgService = serviceRegistry.getService( ConfigurationService.class );
		final JdbcServices jdbcServices = serviceRegistry.getService( JdbcServices.class );

		final Map configurationSettings = new HashMap();
		//noinspection unchecked
		configurationSettings.putAll( jdbcServices.getJdbcEnvironment().getDialect().getDefaultProperties() );
		//noinspection unchecked
		configurationSettings.putAll( cfgService.getSettings() );
		if ( cfgService == null ) {
			cfgService = new ConfigurationServiceImpl( configurationSettings );
			( (ConfigurationServiceImpl) cfgService ).injectServices( (ServiceRegistryImplementor) serviceRegistry );
		}

		this.beanManagerReference = NullnessHelper.coalesceSuppliedValues(
				() -> configurationSettings.get( AvailableSettings.JAKARTA_CDI_BEAN_MANAGER ),
				() -> {
					final Object value = configurationSettings.get( AvailableSettings.CDI_BEAN_MANAGER );
					if ( value != null ) {
						DEPRECATION_LOGGER.deprecatedSetting(
								AvailableSettings.CDI_BEAN_MANAGER,
								AvailableSettings.JAKARTA_CDI_BEAN_MANAGER
						);
					}
					return value;
				}
		);

		this.validatorFactoryReference = configurationSettings.getOrDefault(
				AvailableSettings.JPA_VALIDATION_FACTORY,
				configurationSettings.get( AvailableSettings.JAKARTA_VALIDATION_FACTORY )
		);
		this.jsonFormatMapper = determineJsonFormatMapper(
				configurationSettings.get( AvailableSettings.JSON_FORMAT_MAPPER ),
				strategySelector
		);

		this.sessionFactoryName = (String) configurationSettings.get( SESSION_FACTORY_NAME );
		this.sessionFactoryNameAlsoJndiName = cfgService.getSetting(
				SESSION_FACTORY_NAME_IS_JNDI,
				BOOLEAN,
				true
		);
		this.jtaTransactionAccessEnabled = cfgService.getSetting(
				ALLOW_JTA_TRANSACTION_ACCESS,
				BOOLEAN,
				true
		);

		this.allowRefreshDetachedEntity = cfgService.getSetting(
				ALLOW_REFRESH_DETACHED_ENTITY,
				BOOLEAN,
				true
		);

		this.flushBeforeCompletionEnabled = cfgService.getSetting( FLUSH_BEFORE_COMPLETION, BOOLEAN, true );
		this.autoCloseSessionEnabled = cfgService.getSetting( AUTO_CLOSE_SESSION, BOOLEAN, false );

		this.statisticsEnabled = cfgService.getSetting( GENERATE_STATISTICS, BOOLEAN, false );
		this.interceptor = determineInterceptor( configurationSettings, strategySelector );
		this.statelessInterceptorSupplier = determineStatelessInterceptor( configurationSettings, strategySelector );
		this.statementInspector = strategySelector.resolveStrategy(
				StatementInspector.class,
				configurationSettings.get( STATEMENT_INSPECTOR )
		);

		// todo : expose this from builder?
		final String autoSessionEventsListenerName = (String) configurationSettings.get(
				AUTO_SESSION_EVENTS_LISTENER
		);
		final Class<? extends SessionEventListener> autoSessionEventsListener = autoSessionEventsListenerName == null
				? null
				: strategySelector.selectStrategyImplementor( SessionEventListener.class, autoSessionEventsListenerName );

		final boolean logSessionMetrics = cfgService.getSetting( LOG_SESSION_METRICS, BOOLEAN, statisticsEnabled );
		this.baselineSessionEventsListenerBuilder = new BaselineSessionEventsListenerBuilder( logSessionMetrics, autoSessionEventsListener );

		this.customEntityDirtinessStrategy = strategySelector.resolveDefaultableStrategy(
				CustomEntityDirtinessStrategy.class,
				configurationSettings.get( CUSTOM_ENTITY_DIRTINESS_STRATEGY ),
				DefaultCustomEntityDirtinessStrategy.INSTANCE
		);

		this.entityNotFoundDelegate = StandardEntityNotFoundDelegate.INSTANCE;
		this.identifierRollbackEnabled = cfgService.getSetting( USE_IDENTIFIER_ROLLBACK, BOOLEAN, false );
		this.checkNullability = cfgService.getSetting( CHECK_NULLABILITY, BOOLEAN, true );
		this.initializeLazyStateOutsideTransactions = cfgService.getSetting( ENABLE_LAZY_LOAD_NO_TRANS, BOOLEAN, false );

		this.multiTenancyEnabled = serviceRegistry.getService(MultiTenantConnectionProvider.class)!=null;
		this.currentTenantIdentifierResolver = strategySelector.resolveStrategy(
				CurrentTenantIdentifierResolver.class,
				configurationSettings.get( MULTI_TENANT_IDENTIFIER_RESOLVER )
		);

		this.batchFetchStyle = BatchFetchStyle.interpret( configurationSettings.get( BATCH_FETCH_STYLE ) );
		this.delayBatchFetchLoaderCreations = cfgService.getSetting( DELAY_ENTITY_LOADER_CREATIONS, BOOLEAN, true );
		this.defaultBatchFetchSize = ConfigurationHelper.getInt( DEFAULT_BATCH_FETCH_SIZE, configurationSettings, -1 );
		this.maximumFetchDepth = ConfigurationHelper.getInteger( MAX_FETCH_DEPTH, configurationSettings );
		final String defaultNullPrecedence = ConfigurationHelper.getString(
				AvailableSettings.DEFAULT_NULL_ORDERING, configurationSettings, "none", "first", "last"
		);
		this.defaultNullPrecedence = NullPrecedence.parse( defaultNullPrecedence );
		this.orderUpdatesEnabled = ConfigurationHelper.getBoolean( ORDER_UPDATES, configurationSettings );
		this.orderInsertsEnabled = ConfigurationHelper.getBoolean( ORDER_INSERTS, configurationSettings );

		this.callbacksEnabled = ConfigurationHelper.getBoolean( JPA_CALLBACKS_ENABLED, configurationSettings, true );

		this.jtaTrackByThread = cfgService.getSetting( JTA_TRACK_BY_THREAD, BOOLEAN, true );

		final String hqlTranslatorImplFqn = ConfigurationHelper.extractValue(
				AvailableSettings.SEMANTIC_QUERY_PRODUCER,
				configurationSettings,
				() -> ConfigurationHelper.extractPropertyValue(
						AvailableSettings.QUERY_TRANSLATOR,
						configurationSettings
				)
		);
		this.hqlTranslator = resolveHqlTranslator(
				hqlTranslatorImplFqn,
				serviceRegistry,
				strategySelector
		);

		final String sqmTranslatorFactoryImplFqn = ConfigurationHelper.extractPropertyValue(
				AvailableSettings.SEMANTIC_QUERY_TRANSLATOR,
				configurationSettings
		);
		this.sqmTranslatorFactory = resolveSqmTranslator(
				sqmTranslatorFactoryImplFqn,
				serviceRegistry,
				strategySelector
		);


		final String sqmMutationStrategyImplName = ConfigurationHelper.extractValue(
				AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY,
				configurationSettings,
				() -> ConfigurationHelper.extractValue(
						AvailableSettings.ID_TABLE_STRATEGY,
						configurationSettings,
						() -> ConfigurationHelper.extractPropertyValue(
								AvailableSettings.HQL_BULK_ID_STRATEGY,
								configurationSettings
						)
				)
		);

		this.sqmMultiTableMutationStrategy = resolveSqmMutationStrategy(
				sqmMutationStrategyImplName,
				serviceRegistry,
				strategySelector
		);

		final String sqmInsertStrategyImplName = ConfigurationHelper.extractValue(
				AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY,
				configurationSettings,
				() -> null
		);

		this.sqmMultiTableInsertStrategy = resolveSqmInsertStrategy(
				sqmInsertStrategyImplName,
				serviceRegistry,
				strategySelector
		);

		this.useOfJdbcNamedParametersEnabled = cfgService.getSetting( CALLABLE_NAMED_PARAMS_ENABLED, BOOLEAN, true );

		this.querySubstitutions = ConfigurationHelper.toMap( QUERY_SUBSTITUTIONS, " ,=;:\n\t\r\f", configurationSettings );
		this.namedQueryStartupCheckingEnabled = cfgService.getSetting( QUERY_STARTUP_CHECKING, BOOLEAN, true );
		this.conventionalJavaConstants = cfgService.getSetting(
				CONVENTIONAL_JAVA_CONSTANTS, BOOLEAN, true );
		this.omitJoinOfSuperclassTablesEnabled = cfgService.getSetting( OMIT_JOIN_OF_SUPERCLASS_TABLES, BOOLEAN, true );
		this.preferredSqlTypeCodeForBoolean = ConfigurationHelper.getPreferredSqlTypeCodeForBoolean( serviceRegistry );
		this.defaultTimeZoneStorageStrategy = context.getMetadataBuildingOptions().getDefaultTimeZoneStorage();

		final RegionFactory regionFactory = serviceRegistry.getService( RegionFactory.class );
		if ( !NoCachingRegionFactory.class.isInstance( regionFactory ) ) {
			this.secondLevelCacheEnabled = cfgService.getSetting( USE_SECOND_LEVEL_CACHE, BOOLEAN, true );
			this.queryCacheEnabled = cfgService.getSetting( USE_QUERY_CACHE, BOOLEAN, false );
			this.timestampsCacheFactory = strategySelector.resolveDefaultableStrategy(
					TimestampsCacheFactory.class,
					configurationSettings.get( QUERY_CACHE_FACTORY ),
					StandardTimestampsCacheFactory.INSTANCE
			);
			this.cacheRegionPrefix = ConfigurationHelper.extractPropertyValue(
					CACHE_REGION_PREFIX,
					configurationSettings
			);
			this.minimalPutsEnabled = cfgService.getSetting(
					USE_MINIMAL_PUTS,
					BOOLEAN,
					regionFactory.isMinimalPutsEnabledByDefault()
			);
			this.structuredCacheEntriesEnabled = cfgService.getSetting( USE_STRUCTURED_CACHE, BOOLEAN, false );
			this.directReferenceCacheEntriesEnabled = cfgService.getSetting(
					USE_DIRECT_REFERENCE_CACHE_ENTRIES,
					BOOLEAN,
					false
			);
			this.autoEvictCollectionCache = cfgService.getSetting( AUTO_EVICT_COLLECTION_CACHE, BOOLEAN, false );
		}
		else {
			this.secondLevelCacheEnabled = false;
			this.queryCacheEnabled = false;
			this.timestampsCacheFactory = null;
			this.cacheRegionPrefix = null;
			this.minimalPutsEnabled = false;
			this.structuredCacheEntriesEnabled = false;
			this.directReferenceCacheEntriesEnabled = false;
			this.autoEvictCollectionCache = false;
		}

		try {
			this.schemaAutoTooling = SchemaAutoTooling.interpret( (String) configurationSettings.get( AvailableSettings.HBM2DDL_AUTO ) );
		}
		catch (Exception e) {
			log.warn( e.getMessage() + "  Ignoring" );
		}


		final ExtractedDatabaseMetaData meta = jdbcServices.getExtractedMetaDataSupport();

		this.tempTableDdlTransactionHandling = TempTableDdlTransactionHandling.NONE;
		if ( meta.doesDataDefinitionCauseTransactionCommit() ) {
			if ( meta.supportsDataDefinitionInTransaction() ) {
				this.tempTableDdlTransactionHandling = TempTableDdlTransactionHandling.ISOLATE_AND_TRANSACT;
			}
			else {
				this.tempTableDdlTransactionHandling = TempTableDdlTransactionHandling.ISOLATE;
			}
		}

		this.jdbcBatchSize = ConfigurationHelper.getInt( STATEMENT_BATCH_SIZE, configurationSettings, 1 );
		if ( !meta.supportsBatchUpdates() ) {
			this.jdbcBatchSize = 0;
		}

		this.jdbcBatchVersionedData = ConfigurationHelper.getBoolean( BATCH_VERSIONED_DATA, configurationSettings, true );
		this.scrollableResultSetsEnabled = ConfigurationHelper.getBoolean(
				USE_SCROLLABLE_RESULTSET,
				configurationSettings,
				meta.supportsScrollableResults()
		);
		this.wrapResultSetsEnabled = ConfigurationHelper.getBoolean(
				WRAP_RESULT_SETS,
				configurationSettings,
				false
		);
		this.getGeneratedKeysEnabled = ConfigurationHelper.getBoolean(
				USE_GET_GENERATED_KEYS,
				configurationSettings,
				meta.supportsGetGeneratedKeys()
		);
		this.jdbcFetchSize = ConfigurationHelper.getInteger( STATEMENT_FETCH_SIZE, configurationSettings );

		this.connectionHandlingMode = interpretConnectionHandlingMode( configurationSettings, serviceRegistry );
		this.connectionProviderDisablesAutoCommit = ConfigurationHelper.getBoolean(
				AvailableSettings.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT,
				configurationSettings,
				false
		);

		this.commentsEnabled = ConfigurationHelper.getBoolean( USE_SQL_COMMENTS, configurationSettings );

		this.preferUserTransaction = ConfigurationHelper.getBoolean( PREFER_USER_TRANSACTION, configurationSettings, false  );

		this.allowOutOfTransactionUpdateOperations = ConfigurationHelper.getBoolean(
				ALLOW_UPDATE_OUTSIDE_TRANSACTION,
				configurationSettings,
				false
		);

		this.releaseResourcesOnCloseEnabled = ConfigurationHelper.getBoolean(
				DISCARD_PC_ON_CLOSE,
				configurationSettings,
				false
		);

		Object jdbcTimeZoneValue = configurationSettings.get(
				JDBC_TIME_ZONE
		);

		if ( jdbcTimeZoneValue instanceof TimeZone ) {
			this.jdbcTimeZone = (TimeZone) jdbcTimeZoneValue;
		}
		else if ( jdbcTimeZoneValue instanceof ZoneId ) {
			this.jdbcTimeZone = TimeZone.getTimeZone( (ZoneId) jdbcTimeZoneValue );
		}
		else if ( jdbcTimeZoneValue instanceof String ) {
			this.jdbcTimeZone = TimeZone.getTimeZone( ZoneId.of((String) jdbcTimeZoneValue) );
		}
		else if ( jdbcTimeZoneValue != null ) {
			throw new IllegalArgumentException( "Configuration property " + JDBC_TIME_ZONE + " value [" + jdbcTimeZoneValue + "] is not supported!" );
		}

		this.queryParametersValidationEnabled = ConfigurationHelper.getBoolean(
				VALIDATE_QUERY_PARAMETERS,
				configurationSettings,
				true
		);

		this.criteriaValueHandlingMode = ValueHandlingMode.interpret(
				configurationSettings.get( CRITERIA_VALUE_HANDLING_MODE )
		);

		// added the boolean parameter in case we want to define some form of "all" as discussed
		this.jpaCompliance = context.getJpaCompliance();

		this.failOnPaginationOverCollectionFetchEnabled = ConfigurationHelper.getBoolean(
				FAIL_ON_PAGINATION_OVER_COLLECTION_FETCH,
				configurationSettings,
				false
		);

		this.immutableEntityUpdateQueryHandlingMode = ImmutableEntityUpdateQueryHandlingMode.interpret(
				configurationSettings.get( IMMUTABLE_ENTITY_UPDATE_QUERY_HANDLING_MODE )
		);

		this.defaultCatalog = ConfigurationHelper.getString( DEFAULT_CATALOG, configurationSettings );
		this.defaultSchema = ConfigurationHelper.getString( DEFAULT_SCHEMA, configurationSettings );

		this.inClauseParameterPaddingEnabled =  ConfigurationHelper.getBoolean(
				IN_CLAUSE_PARAMETER_PADDING,
				configurationSettings,
				false
		);

		this.nativeExceptionHandling51Compliance = ConfigurationHelper.getBoolean(
				NATIVE_EXCEPTION_HANDLING_51_COMPLIANCE,
				configurationSettings,
				false
		);

		this.queryStatisticsMaxSize = ConfigurationHelper.getInt(
				QUERY_STATISTICS_MAX_SIZE,
				configurationSettings,
				Statistics.DEFAULT_QUERY_STATISTICS_MAX_SIZE
		);

		if ( context.isJpaBootstrap() && nativeExceptionHandling51Compliance ) {
			log.nativeExceptionHandling51ComplianceJpaBootstrapping();
			this.nativeExceptionHandling51Compliance = false;
		}
	}

	private SqmMultiTableMutationStrategy resolveSqmMutationStrategy(
			String strategyName,
			StandardServiceRegistry serviceRegistry,
			StrategySelector strategySelector) {
		if ( strategyName == null ) {
			return null;
		}

		return strategySelector.resolveStrategy(
				SqmMultiTableMutationStrategy.class,
				strategyName,
				(SqmMultiTableMutationStrategy) null,
				strategyClass -> {
					Constructor<SqmMultiTableMutationStrategy> dialectConstructor = null;
					Constructor<SqmMultiTableMutationStrategy> emptyConstructor = null;
					// todo (6.0) : formalize the allowed constructor parameterizations
					for ( Constructor<?> declaredConstructor : strategyClass.getDeclaredConstructors() ) {
						final Class<?>[] parameterTypes = declaredConstructor.getParameterTypes();
						if ( parameterTypes.length == 1 && parameterTypes[0] == Dialect.class ) {
							dialectConstructor = (Constructor<SqmMultiTableMutationStrategy>) declaredConstructor;
							break;
						}
						else if ( parameterTypes.length == 0 ) {
							emptyConstructor = (Constructor<SqmMultiTableMutationStrategy>) declaredConstructor;
						}
					}

					try {
						if ( dialectConstructor != null ) {
							return dialectConstructor.newInstance(
									serviceRegistry.getService( JdbcServices.class ).getDialect()
							);
						}
						else if ( emptyConstructor != null ) {
							return emptyConstructor.newInstance();
						}
					}
					catch (Exception e) {
						throw new StrategySelectionException(
								String.format( "Could not instantiate named strategy class [%s]", strategyClass.getName() ),
								e
						);
					}
					throw new IllegalArgumentException( "Cannot instantiate the class [" + strategyClass.getName() + "] because it does not have a constructor that accepts a dialect or an empty constructor!" );
				}
		);
	}

	private SqmMultiTableInsertStrategy resolveSqmInsertStrategy(
			String strategyName,
			StandardServiceRegistry serviceRegistry,
			StrategySelector strategySelector) {
		if ( strategyName == null ) {
			return null;
		}

		return strategySelector.resolveStrategy(
				SqmMultiTableInsertStrategy.class,
				strategyName,
				(SqmMultiTableInsertStrategy) null,
				strategyClass -> {
					Constructor<SqmMultiTableInsertStrategy> dialectConstructor = null;
					Constructor<SqmMultiTableInsertStrategy> emptyConstructor = null;
					// todo (6.0) : formalize the allowed constructor parameterizations
					for ( Constructor<?> declaredConstructor : strategyClass.getDeclaredConstructors() ) {
						final Class<?>[] parameterTypes = declaredConstructor.getParameterTypes();
						if ( parameterTypes.length == 1 && parameterTypes[0] == Dialect.class ) {
							dialectConstructor = (Constructor<SqmMultiTableInsertStrategy>) declaredConstructor;
							break;
						}
						else if ( parameterTypes.length == 0 ) {
							emptyConstructor = (Constructor<SqmMultiTableInsertStrategy>) declaredConstructor;
						}
					}

					try {
						if ( dialectConstructor != null ) {
							return dialectConstructor.newInstance(
									serviceRegistry.getService( JdbcServices.class ).getDialect()
							);
						}
						else if ( emptyConstructor != null ) {
							return emptyConstructor.newInstance();
						}
					}
					catch (Exception e) {
						throw new StrategySelectionException(
								String.format( "Could not instantiate named strategy class [%s]", strategyClass.getName() ),
								e
						);
					}
					throw new IllegalArgumentException( "Cannot instantiate the class [" + strategyClass.getName() + "] because it does not have a constructor that accepts a dialect or an empty constructor!" );
				}
		);
	}

	private HqlTranslator resolveHqlTranslator(
			String producerName,
			StandardServiceRegistry serviceRegistry,
			StrategySelector strategySelector) {
		if ( StringHelper.isEmpty( producerName ) ) {
			return null;
		}

		//noinspection Convert2Lambda
		return strategySelector.resolveDefaultableStrategy(
				HqlTranslator.class,
				producerName,
				new Callable<HqlTranslator>() {
					@Override
					public HqlTranslator call() throws Exception {
						final ClassLoaderService classLoaderService = serviceRegistry.getService( ClassLoaderService.class );
						return (HqlTranslator) classLoaderService.classForName( producerName ).newInstance();
					}
				}
		);
	}

	private SqmTranslatorFactory resolveSqmTranslator(
			String translatorImplFqn,
			StandardServiceRegistry serviceRegistry,
			StrategySelector strategySelector) {
		if ( StringHelper.isEmpty( translatorImplFqn ) ) {
			return null;
		}

		return strategySelector.resolveStrategy(
				SqmTranslatorFactory.class,
				translatorImplFqn
		);
	}

	@SuppressWarnings("deprecation")
	private static Interceptor determineInterceptor(Map configurationSettings, StrategySelector strategySelector) {
		Object setting = configurationSettings.get( INTERCEPTOR );
		if ( setting == null ) {
			// try the legacy (deprecated) JPA name
			setting = configurationSettings.get( org.hibernate.cfg.AvailableSettings.INTERCEPTOR );
			if ( setting != null ) {
				DEPRECATION_LOGGER.deprecatedSetting(
						org.hibernate.cfg.AvailableSettings.INTERCEPTOR,
						INTERCEPTOR
				);
			}
		}

		return strategySelector.resolveStrategy(
				Interceptor.class,
				setting
		);
	}

	@SuppressWarnings({"unchecked" })
	private static Supplier<? extends Interceptor> determineStatelessInterceptor(
			Map configurationSettings,
			StrategySelector strategySelector) {
		Object setting = configurationSettings.get( SESSION_SCOPED_INTERCEPTOR );

		if ( setting == null ) {
			return null;
		}
		else if ( setting instanceof Supplier ) {
			return (Supplier<? extends Interceptor>) setting;
		}
		else if ( setting instanceof Class ) {
			Class<? extends Interceptor> clazz = (Class<? extends Interceptor>) setting;
			return interceptorSupplier( clazz );
		}
		else {
			return interceptorSupplier(
					strategySelector.selectStrategyImplementor(
							Interceptor.class,
							setting.toString()
					)
			);
		}
	}


	private static Supplier<? extends Interceptor> interceptorSupplier(Class<? extends Interceptor> clazz) {
		return () -> {
			try {
				return clazz.newInstance();
			}
			catch (InstantiationException | IllegalAccessException e) {
				throw new HibernateException( "Could not supply session-scoped SessionFactory Interceptor", e );
			}
		};
	}

	private PhysicalConnectionHandlingMode interpretConnectionHandlingMode(
			Map configurationSettings,
			StandardServiceRegistry serviceRegistry) {
		final PhysicalConnectionHandlingMode specifiedHandlingMode = PhysicalConnectionHandlingMode.interpret(
				configurationSettings.get( CONNECTION_HANDLING )
		);

		if ( specifiedHandlingMode != null ) {
			return specifiedHandlingMode;
		}

		final TransactionCoordinatorBuilder transactionCoordinatorBuilder = serviceRegistry.getService( TransactionCoordinatorBuilder.class );
		return transactionCoordinatorBuilder.getDefaultConnectionHandlingMode();
	}

	private static FormatMapper determineJsonFormatMapper(Object setting, StrategySelector strategySelector) {
		return strategySelector.resolveDefaultableStrategy(
				FormatMapper.class,
				setting,
				(Callable<FormatMapper>) () -> {
					try {
						// Force initialization of the instance
						JacksonJsonFormatMapper.INSTANCE.hashCode();
						return JacksonJsonFormatMapper.INSTANCE;
					}
					catch (NoClassDefFoundError ex) {
						// Ignore
					}
					try {
						// Force initialization of the instance
						JsonBJsonFormatMapper.INSTANCE.hashCode();
						return JsonBJsonFormatMapper.INSTANCE;
					}
					catch (NoClassDefFoundError ex) {
						// Ignore
					}
					return null;
				}
		);
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SessionFactoryOptionsState

	@Override
	public String getUuid() {
		return this.uuid;
	}

	@Override
	public StandardServiceRegistry getServiceRegistry() {
		return serviceRegistry;
	}

	@Override
	public boolean isJpaBootstrap() {
		return jpaBootstrap;
	}

	@Override
	public boolean isJtaTransactionAccessEnabled() {
		return jtaTransactionAccessEnabled;
	}

	@Override
	public boolean isAllowRefreshDetachedEntity() {
		return allowRefreshDetachedEntity;
	}

	@Override
	public boolean isAllowOutOfTransactionUpdateOperations() {
		return allowOutOfTransactionUpdateOperations;
	}


	@Override
	public boolean isReleaseResourcesOnCloseEnabled() {
		return releaseResourcesOnCloseEnabled;
	}

	@Override
	public Object getBeanManagerReference() {
		return beanManagerReference;
	}

	@Override
	public Object getValidatorFactoryReference() {
		return validatorFactoryReference;
	}

	@Override
	public String getSessionFactoryName() {
		return sessionFactoryName;
	}

	@Override
	public boolean isSessionFactoryNameAlsoJndiName() {
		return sessionFactoryNameAlsoJndiName;
	}

	@Override
	public boolean isFlushBeforeCompletionEnabled() {
		return flushBeforeCompletionEnabled;
	}

	@Override
	public boolean isAutoCloseSessionEnabled() {
		return autoCloseSessionEnabled;
	}

	@Override
	public boolean isStatisticsEnabled() {
		return statisticsEnabled;
	}

	@Override
	public Interceptor getInterceptor() {
		return interceptor == null ? EmptyInterceptor.INSTANCE : interceptor;
	}

	@Override
	public Class<? extends Interceptor> getStatelessInterceptorImplementor() {
		return statelessInterceptorClass;
	}

	@Override
	public Supplier<? extends Interceptor> getStatelessInterceptorImplementorSupplier() {
		return statelessInterceptorSupplier;
	}

	@Override
	public HqlTranslator getCustomHqlTranslator() {
		return hqlTranslator;
	}

	@Override
	public SqmTranslatorFactory getCustomSqmTranslatorFactory() {
		return sqmTranslatorFactory;
	}

	@Override
	public SqmMultiTableMutationStrategy getCustomSqmMultiTableMutationStrategy() {
		return sqmMultiTableMutationStrategy;
	}

	@Override
	public SqmMultiTableInsertStrategy getCustomSqmMultiTableInsertStrategy() {
		return sqmMultiTableInsertStrategy;
	}

	@Override
	public boolean isUseOfJdbcNamedParametersEnabled() {
		return this.useOfJdbcNamedParametersEnabled;
	}

	@Override
	public SqmFunctionRegistry getCustomSqmFunctionRegistry() {
		return this.sqmFunctionRegistry;
	}

	@Override
	public StatementInspector getStatementInspector() {
		return statementInspector;
	}

	@Override
	public SessionFactoryObserver[] getSessionFactoryObservers() {
		return sessionFactoryObserverList.toArray( new SessionFactoryObserver[ sessionFactoryObserverList.size() ] );
	}

	@Override
	public BaselineSessionEventsListenerBuilder getBaselineSessionEventsListenerBuilder() {
		return baselineSessionEventsListenerBuilder;
	}

	@Override
	public boolean isIdentifierRollbackEnabled() {
		return identifierRollbackEnabled;
	}

	@Override
	public EntityTuplizerFactory getEntityTuplizerFactory() {
		return entityTuplizerFactory;
	}

	@Override
	public boolean isCheckNullability() {
		return checkNullability;
	}

	@Override
	public boolean isInitializeLazyStateOutsideTransactionsEnabled() {
		return initializeLazyStateOutsideTransactions;
	}

	@Override
	public TempTableDdlTransactionHandling getTempTableDdlTransactionHandling() {
		return tempTableDdlTransactionHandling;
	}

	@Override
	public BatchFetchStyle getBatchFetchStyle() {
		return batchFetchStyle;
	}

	@Override
	public boolean isDelayBatchFetchLoaderCreationsEnabled() {
		return delayBatchFetchLoaderCreations;
	}

	@Override
	public int getDefaultBatchFetchSize() {
		return defaultBatchFetchSize;
	}

	@Override
	public Integer getMaximumFetchDepth() {
		return maximumFetchDepth;
	}

	@Override
	public NullPrecedence getDefaultNullPrecedence() {
		return defaultNullPrecedence;
	}

	@Override
	public boolean isOrderUpdatesEnabled() {
		return orderUpdatesEnabled;
	}

	@Override
	public boolean isOrderInsertsEnabled() {
		return orderInsertsEnabled;
	}

	@Override
	public boolean isMultiTenancyEnabled() {
		return multiTenancyEnabled;
	}

	@Override
	public CurrentTenantIdentifierResolver getCurrentTenantIdentifierResolver() {
		return currentTenantIdentifierResolver;
	}

	@Override
	public boolean isJtaTrackByThread() {
		return jtaTrackByThread;
	}

	@Override
	public Map getQuerySubstitutions() {
		return querySubstitutions;
	}

	@Override
	public boolean isNamedQueryStartupCheckingEnabled() {
		return namedQueryStartupCheckingEnabled;
	}

	@Override
	public boolean isConventionalJavaConstants() {
		return conventionalJavaConstants;
	}

	@Override
	public boolean isSecondLevelCacheEnabled() {
		return secondLevelCacheEnabled;
	}

	@Override
	public boolean isQueryCacheEnabled() {
		return queryCacheEnabled;
	}

	@Override
	public TimestampsCacheFactory getTimestampsCacheFactory() {
		return timestampsCacheFactory;
	}

	@Override
	public String getCacheRegionPrefix() {
		return cacheRegionPrefix;
	}

	@Override
	public boolean isMinimalPutsEnabled() {
		return minimalPutsEnabled;
	}

	@Override
	public boolean isStructuredCacheEntriesEnabled() {
		return structuredCacheEntriesEnabled;
	}

	@Override
	public boolean isDirectReferenceCacheEntriesEnabled() {
		return directReferenceCacheEntriesEnabled;
	}

	@Override
	public boolean isAutoEvictCollectionCache() {
		return autoEvictCollectionCache;
	}

	@Override
	public SchemaAutoTooling getSchemaAutoTooling() {
		return schemaAutoTooling;
	}

	@Override
	public int getJdbcBatchSize() {
		return jdbcBatchSize;
	}

	@Override
	public boolean isJdbcBatchVersionedData() {
		return jdbcBatchVersionedData;
	}

	@Override
	public boolean isScrollableResultSetsEnabled() {
		return scrollableResultSetsEnabled;
	}

	@Override
	public boolean isWrapResultSetsEnabled() {
		return wrapResultSetsEnabled;
	}

	@Override
	public boolean isGetGeneratedKeysEnabled() {
		return getGeneratedKeysEnabled;
	}

	@Override
	public Integer getJdbcFetchSize() {
		return jdbcFetchSize;
	}

	@Override
	public PhysicalConnectionHandlingMode getPhysicalConnectionHandlingMode() {
		return connectionHandlingMode;
	}

	@Override
	public void setCheckNullability(boolean enabled) {
		this.checkNullability = enabled;
	}

	@Override
	public ConnectionReleaseMode getConnectionReleaseMode() {
		return getPhysicalConnectionHandlingMode().getReleaseMode();
	}

	@Override
	public boolean doesConnectionProviderDisableAutoCommit() {
		return connectionProviderDisablesAutoCommit;
	}

	@Override
	public boolean isCommentsEnabled() {
		return commentsEnabled;
	}

	@Override
	public CustomEntityDirtinessStrategy getCustomEntityDirtinessStrategy() {
		return customEntityDirtinessStrategy;
	}

	@Override
	public EntityNameResolver[] getEntityNameResolvers() {
		return entityNameResolvers.toArray( new EntityNameResolver[ entityNameResolvers.size() ] );
	}

	@Override
	public EntityNotFoundDelegate getEntityNotFoundDelegate() {
		return entityNotFoundDelegate;
	}

	@Override
	public Map<String, SqmFunctionDescriptor> getCustomSqlFunctionMap() {
		return sqlFunctions == null ? Collections.emptyMap() : sqlFunctions;
	}

	@Override
	public boolean isPreferUserTransaction() {
		return this.preferUserTransaction;
	}

	@Override
	public TimeZone getJdbcTimeZone() {
		return this.jdbcTimeZone;
	}

	@Override
	public boolean isQueryParametersValidationEnabled() {
		return this.queryParametersValidationEnabled;
	}

	@Override
	public ValueHandlingMode getCriteriaValueHandlingMode() {
		return criteriaValueHandlingMode;
	}

	@Override
	public ImmutableEntityUpdateQueryHandlingMode getImmutableEntityUpdateQueryHandlingMode() {
		return immutableEntityUpdateQueryHandlingMode;
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
	public boolean isFailOnPaginationOverCollectionFetchEnabled() {
		return this.failOnPaginationOverCollectionFetchEnabled;
	}

	@Override
	public boolean inClauseParameterPaddingEnabled() {
		return this.inClauseParameterPaddingEnabled;
	}

	@Override
	public JpaCompliance getJpaCompliance() {
		return jpaCompliance;
	}

	@Override
	public boolean nativeExceptionHandling51Compliance() {
		return nativeExceptionHandling51Compliance;
	}

	@Override
	public int getQueryStatisticsMaxSize() {
		return queryStatisticsMaxSize;
	}

	@Override
	public boolean areJPACallbacksEnabled() {
		return callbacksEnabled;
	}

	@Override
	public boolean isCollectionsInDefaultFetchGroupEnabled() {
		return collectionsInDefaultFetchGroupEnabled;
	}

	@Override
	public boolean isOmitJoinOfSuperclassTablesEnabled() {
		return omitJoinOfSuperclassTablesEnabled;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return preferredSqlTypeCodeForBoolean;
	}

	@Override
	public TimeZoneStorageStrategy getDefaultTimeZoneStorageStrategy() {
		return defaultTimeZoneStorageStrategy;
	}

	@Override
	public FormatMapper getJsonFormatMapper() {
		return jsonFormatMapper;
	}
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// In-flight mutation access

	public void applyBeanManager(Object beanManager) {
		this.beanManagerReference = beanManager;
	}

	public void applyValidatorFactory(Object validatorFactory) {
		this.validatorFactoryReference = validatorFactory;
	}

	public void applySessionFactoryName(String sessionFactoryName) {
		this.sessionFactoryName = sessionFactoryName;
	}

	public void enableSessionFactoryNameAsJndiName(boolean isJndiName) {
		this.sessionFactoryNameAlsoJndiName = isJndiName;
	}

	public void enableSessionAutoClosing(boolean autoClosingEnabled) {
		this.autoCloseSessionEnabled = autoClosingEnabled;
	}

	public void enableSessionAutoFlushing(boolean flushBeforeCompletionEnabled) {
		this.flushBeforeCompletionEnabled = flushBeforeCompletionEnabled;
	}

	public void enableJtaTrackingByThread(boolean enabled) {
		this.jtaTrackByThread = enabled;
	}

	public void enablePreferUserTransaction(boolean preferUserTransaction) {
		this.preferUserTransaction = preferUserTransaction;
	}

	public void enableStatisticsSupport(boolean enabled) {
		this.statisticsEnabled = enabled;
	}

	public void addSessionFactoryObservers(SessionFactoryObserver... observers) {
		Collections.addAll( this.sessionFactoryObserverList, observers );
	}

	public void applyInterceptor(Interceptor interceptor) {
		this.interceptor = interceptor;
	}

	public void applyStatelessInterceptor(Class<? extends Interceptor> statelessInterceptorClass) {
		this.statelessInterceptorClass = statelessInterceptorClass;
	}

	public void applyStatelessInterceptorSupplier(Supplier<? extends Interceptor> statelessInterceptorSupplier) {
		this.statelessInterceptorSupplier = statelessInterceptorSupplier;
	}

	public void applySqmFunctionRegistry(SqmFunctionRegistry sqmFunctionRegistry) {
		this.sqmFunctionRegistry = sqmFunctionRegistry;
	}

	public void applyStatementInspector(StatementInspector statementInspector) {
		this.statementInspector = statementInspector;
	}

	public void applyCustomEntityDirtinessStrategy(CustomEntityDirtinessStrategy strategy) {
		this.customEntityDirtinessStrategy = strategy;
	}

	public void addEntityNameResolvers(EntityNameResolver... entityNameResolvers) {
		Collections.addAll( this.entityNameResolvers, entityNameResolvers );
	}

	public void applyEntityNotFoundDelegate(EntityNotFoundDelegate entityNotFoundDelegate) {
		this.entityNotFoundDelegate = entityNotFoundDelegate;
	}

	public void enableIdentifierRollbackSupport(boolean enabled) {
		this.identifierRollbackEnabled = enabled;
	}

	public void enableNullabilityChecking(boolean enabled) {
		this.checkNullability = enabled;
	}

	public void allowLazyInitializationOutsideTransaction(boolean enabled) {
		this.initializeLazyStateOutsideTransactions = enabled;
	}

	public void applyEntityTuplizerFactory(EntityTuplizerFactory entityTuplizerFactory) {
		this.entityTuplizerFactory = entityTuplizerFactory;
	}

	public void applyEntityTuplizer(RepresentationMode entityMode, Class<? extends EntityTuplizer> tuplizerClass) {
		this.entityTuplizerFactory.registerDefaultTuplizerClass( entityMode, tuplizerClass );
	}

	public void applyTempTableDdlTransactionHandling(TempTableDdlTransactionHandling handling) {
		this.tempTableDdlTransactionHandling = handling;
	}

	/**
	 * @deprecated (since 6.0) : No longer used internally
	 */
	@Deprecated
	public void applyBatchFetchStyle(BatchFetchStyle style) {
		this.batchFetchStyle = style;
	}

	public void applyDelayedEntityLoaderCreations(boolean delay) {
		this.delayBatchFetchLoaderCreations = delay;
	}

	public void applyDefaultBatchFetchSize(int size) {
		this.defaultBatchFetchSize = size;
	}

	public void applyMaximumFetchDepth(int depth) {
		this.maximumFetchDepth = depth;
	}

	public void applyDefaultNullPrecedence(NullPrecedence nullPrecedence) {
		this.defaultNullPrecedence = nullPrecedence;
	}

	public void enableOrderingOfInserts(boolean enabled) {
		this.orderInsertsEnabled = enabled;
	}

	public void enableOrderingOfUpdates(boolean enabled) {
		this.orderUpdatesEnabled = enabled;
	}

	public void enableDelayedIdentityInserts(boolean enabled) {
		this.postInsertIdentifierDelayed = enabled;
	}

	public void applyMultiTenancy(boolean enabled) {
		this.multiTenancyEnabled = enabled;
	}

	public void applyCurrentTenantIdentifierResolver(CurrentTenantIdentifierResolver resolver) {
		this.currentTenantIdentifierResolver = resolver;
	}

	@SuppressWarnings("unchecked")
	public void applyQuerySubstitutions(Map substitutions) {
		this.querySubstitutions.putAll( substitutions );
	}

	public void enableNamedQueryCheckingOnStartup(boolean enabled) {
		this.namedQueryStartupCheckingEnabled = enabled;
	}

	public void enableSecondLevelCacheSupport(boolean enabled) {
		this.secondLevelCacheEnabled =  enabled;
	}

	public void enableQueryCacheSupport(boolean enabled) {
		this.queryCacheEnabled = enabled;
	}

	public void applyTimestampsCacheFactory(TimestampsCacheFactory factory) {
		this.timestampsCacheFactory = factory;
	}

	public void applyCacheRegionPrefix(String prefix) {
		this.cacheRegionPrefix = prefix;
	}

	public void enableMinimalPuts(boolean enabled) {
		this.minimalPutsEnabled = enabled;
	}

	public void enabledStructuredCacheEntries(boolean enabled) {
		this.structuredCacheEntriesEnabled = enabled;
	}

	public void allowDirectReferenceCacheEntries(boolean enabled) {
		this.directReferenceCacheEntriesEnabled = enabled;
	}

	public void enableAutoEvictCollectionCaches(boolean enabled) {
		this.autoEvictCollectionCache = enabled;
	}

	public void applyJdbcBatchSize(int size) {
		this.jdbcBatchSize = size;
	}

	public void enableJdbcBatchingForVersionedEntities(boolean enabled) {
		this.jdbcBatchVersionedData = enabled;
	}

	public void enableScrollableResultSupport(boolean enabled) {
		this.scrollableResultSetsEnabled = enabled;
	}

	@Deprecated
	public void enableResultSetWrappingSupport(boolean enabled) {
		this.wrapResultSetsEnabled = enabled;
	}

	public void enableGeneratedKeysSupport(boolean enabled) {
		this.getGeneratedKeysEnabled = enabled;
	}

	public void applyJdbcFetchSize(int size) {
		this.jdbcFetchSize = size;
	}

	public void applyConnectionHandlingMode(PhysicalConnectionHandlingMode mode) {
		this.connectionHandlingMode = mode;
	}

	public void applyConnectionReleaseMode(ConnectionReleaseMode connectionReleaseMode) {
		if ( this.connectionHandlingMode == null ) {
			this.connectionHandlingMode = PhysicalConnectionHandlingMode.interpret(
					ConnectionAcquisitionMode.AS_NEEDED,
					connectionReleaseMode
			);
		}
		else {
			this.connectionHandlingMode = PhysicalConnectionHandlingMode.interpret(
					this.connectionHandlingMode.getAcquisitionMode(),
					connectionReleaseMode
			);
		}
	}

	public void applyConnectionProviderDisablesAutoCommit(boolean providerDisablesAutoCommit) {
		this.connectionProviderDisablesAutoCommit = providerDisablesAutoCommit;
	}

	public void enableCommentsSupport(boolean enabled) {
		this.commentsEnabled = enabled;
	}

	public void applySqlFunction(String registrationName, SqmFunctionDescriptor sqlFunction) {
		if ( this.sqlFunctions == null ) {
			this.sqlFunctions = new HashMap<>();
		}
		this.sqlFunctions.put( registrationName, sqlFunction );
	}

	public void allowOutOfTransactionUpdateOperations(boolean allow) {
		this.allowOutOfTransactionUpdateOperations = allow;
	}

	public void enableReleaseResourcesOnClose(boolean enable) {
		this.releaseResourcesOnCloseEnabled = enable;
	}

	public void enableStrictJpaQueryLanguageCompliance(boolean enabled) {
		enableJpaQueryCompliance( enabled );
	}

	public void enableJpaQueryCompliance(boolean enabled) {
		mutableJpaCompliance().setQueryCompliance( enabled );
	}

	private MutableJpaCompliance mutableJpaCompliance() {
		if ( ! MutableJpaCompliance.class.isInstance( this.jpaCompliance ) ) {
			throw new IllegalStateException( "JpaCompliance is no longer mutable" );
		}

		return (MutableJpaCompliance) this.jpaCompliance;
	}

	public void enableJpaTransactionCompliance(boolean enabled) {
		mutableJpaCompliance().setTransactionCompliance( enabled );
	}

	public void enableJpaListCompliance(boolean enabled) {
		mutableJpaCompliance().setListCompliance( enabled );
	}

	public void enableJpaClosedCompliance(boolean enabled) {
		mutableJpaCompliance().setClosedCompliance( enabled );
	}

	public void enableJpaProxyCompliance(boolean enabled) {
		mutableJpaCompliance().setProxyCompliance( enabled );
	}

	public void enableJpaCachingCompliance(boolean enabled) {
		mutableJpaCompliance().setCachingCompliance( enabled );
	}

	public void enableJpaOrderByMappingCompliance(boolean enabled) {
		mutableJpaCompliance().setOrderByMappingCompliance( enabled );
	}

	public void enableGeneratorNameScopeCompliance(boolean enabled) {
		mutableJpaCompliance().setGeneratorNameScopeCompliance( enabled );
	}


	public void enableCollectionInDefaultFetchGroup(boolean enabled) {
		this.collectionsInDefaultFetchGroupEnabled = enabled;
	}

	public void disableRefreshDetachedEntity() {
		this.allowRefreshDetachedEntity = false;
	}

	public void disableJtaTransactionAccess() {
		this.jtaTransactionAccessEnabled = false;
	}

	public SessionFactoryOptions buildOptions() {
		if ( MutableJpaCompliance.class.isInstance( this.jpaCompliance ) ) {
			this.jpaCompliance = mutableJpaCompliance().immutableCopy();
		}

		return this;
	}
}
