/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.sql.Types;
import java.util.Date;
import java.util.Map;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.StaleObjectStateException;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.model.relational.Exportable;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.lock.LockingStrategy;
import org.hibernate.dialect.lock.LockingStrategyException;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.LimitOffsetLimitHandler;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.env.spi.SchemaNameResolver;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.metamodel.mapping.SqlExpressable;
import org.hibernate.persister.entity.Lockable;
import org.hibernate.query.IntervalType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.spi.Exporter;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.StandardBasicTypes;

import jakarta.persistence.TemporalType;

import static org.hibernate.dialect.SimpleDatabaseVersion.ZERO_VERSION;

/**
 * Hibernate Dialect implementation for Cloud Spanner.
 *
 * @author Mike Eltsufin
 * @author Chengyuan Zhao
 * @author Daniel Zou
 * @author Dmitry Solomakha
 */
public class SpannerDialect extends Dialect {

	private final Exporter<Table> spannerTableExporter = new SpannerDialectTableExporter( this );

	private static final LockingStrategy LOCKING_STRATEGY = new DoNothingLockingStrategy();

	private static final EmptyExporter NOOP_EXPORTER = new EmptyExporter();

	private static final UniqueDelegate NOOP_UNIQUE_DELEGATE = new DoNothingUniqueDelegate();

	public SpannerDialect() {
		registerColumnType( Types.BOOLEAN, "bool" );

		registerColumnType( Types.TINYINT, "int64" );
		registerColumnType( Types.SMALLINT, "int64" );
		registerColumnType( Types.INTEGER, "int64" );
		registerColumnType( Types.BIGINT, "int64" );

		registerColumnType( Types.REAL, "float64" );
		registerColumnType( Types.FLOAT, "float64" );
		registerColumnType( Types.DOUBLE, "float64" );
		registerColumnType( Types.DECIMAL, "float64" );
		registerColumnType( Types.NUMERIC, "float64" );

		//timestamp does not accept precision
		registerColumnType( Types.TIMESTAMP, "timestamp" );
		registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp" );
		//there is no time type of any kind
		registerColumnType( Types.TIME, "timestamp" );

		registerColumnType( Types.CHAR, getMaxVarcharLength(), "string($l)" );
		registerColumnType( Types.CHAR, "string(max)" );
		registerColumnType( Types.VARCHAR, getMaxVarcharLength(), "string($l)" );
		registerColumnType( Types.VARCHAR, "string(max)" );

		registerColumnType( Types.NCHAR, getMaxNVarcharLength(), "string($l)" );
		registerColumnType( Types.NCHAR, "string(max)" );
		registerColumnType( Types.NVARCHAR, getMaxNVarcharLength(), "string($l)" );
		registerColumnType( Types.NVARCHAR, "string(max)" );

		registerColumnType( Types.BINARY, getMaxBytesLength(), "bytes($l)" );
		registerColumnType( Types.BINARY, "bytes(max)" );
		registerColumnType( Types.VARBINARY, getMaxBytesLength(), "bytes($l)" );
		registerColumnType( Types.VARBINARY, "bytes(max)" );

		registerColumnType( Types.CLOB, "string(max)" );
		registerColumnType( Types.NCLOB, "string(max)" );
		registerColumnType( Types.BLOB, "bytes(max)" );
	}

	public SpannerDialect(DialectResolutionInfo info) {
		this();
		registerKeywords( info );
	}

	@Override
	public int getMaxVarcharLength() {
		//max is equivalent to 2_621_440
		return 2_621_440;
	}

	public int getMaxBytesLength() {
		//max is equivalent to 10_485_760
		return 10_485_760;
	}

	@Override
	public DatabaseVersion getVersion() {
		return ZERO_VERSION;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );
		final BasicTypeRegistry basicTypeRegistry = queryEngine.getTypeConfiguration().getBasicTypeRegistry();
		final BasicType<byte[]> byteArrayType = basicTypeRegistry.resolve( StandardBasicTypes.BINARY );
		final BasicType<Long> longType = basicTypeRegistry.resolve( StandardBasicTypes.LONG );
		final BasicType<Boolean> booleanType = basicTypeRegistry.resolve( StandardBasicTypes.BOOLEAN );
		final BasicType<String> stringType = basicTypeRegistry.resolve( StandardBasicTypes.STRING );
		final BasicType<Date> dateType = basicTypeRegistry.resolve( StandardBasicTypes.DATE );
		final BasicType<Date> timestampType = basicTypeRegistry.resolve( StandardBasicTypes.TIMESTAMP );

		// Aggregate Functions
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "any_value" )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "array_agg" )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "countif" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "logical_and" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "logical_or" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedAggregateDescriptorBuilder( "string_agg" )
				.setInvariantType( stringType )
				.setArgumentCountBetween( 1, 2 )
				.register();

		// Mathematical Functions
		CommonFunctionFactory.log( queryEngine );
		CommonFunctionFactory.log10( queryEngine );
		CommonFunctionFactory.trunc( queryEngine );
		CommonFunctionFactory.ceiling_ceil( queryEngine );
		CommonFunctionFactory.cosh( queryEngine );
		CommonFunctionFactory.sinh( queryEngine );
		CommonFunctionFactory.tanh( queryEngine );
		CommonFunctionFactory.moreHyperbolic( queryEngine );

		CommonFunctionFactory.bitandorxornot_bitAndOrXorNot( queryEngine );

		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "is_inf" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "is_nan" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "ieee_divide" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "div" )
				.setInvariantType( longType )
				.setExactArgumentCount( 2 )
				.register();

		CommonFunctionFactory.sha1( queryEngine );

		// Hash Functions
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "farm_fingerprint" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "sha256" )
				.setInvariantType( byteArrayType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "sha512" )
				.setInvariantType( byteArrayType )
				.setExactArgumentCount( 1 )
				.register();

		// String Functions
		CommonFunctionFactory.concat_pipeOperator( queryEngine );
		CommonFunctionFactory.trim2( queryEngine );
		CommonFunctionFactory.reverse( queryEngine );
		CommonFunctionFactory.repeat( queryEngine );
		CommonFunctionFactory.substr( queryEngine );
		CommonFunctionFactory.substring_substr( queryEngine );
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "byte_length" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "code_points_to_bytes" )
				.setInvariantType( byteArrayType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "code_points_to_string" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "ends_with" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 2 )
				.register();
//		queryEngine.getSqmFunctionRegistry().namedTemplateBuilder( "format" )
//				.setInvariantType( StandardBasicTypes.STRING )
//				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "from_base64" )
				.setInvariantType( byteArrayType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "from_hex" )
				.setInvariantType( byteArrayType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "regexp_contains" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "regexp_extract" )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "regexp_extract_all" )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "regexp_replace" )
				.setExactArgumentCount( 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "safe_convert_bytes_to_string" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "split" )
				.setArgumentCountBetween( 1, 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "starts_with" )
				.setInvariantType( booleanType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "strpos" )
				.setInvariantType( longType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "to_base64" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "to_code_points" )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "to_hex" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 1 )
				.register();

		// JSON Functions
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "json_query" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "json_value" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 2 )
				.register();

		// Array Functions
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "array" )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "array_concat" )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "array_length" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "array_to_string" )
				.setInvariantType( stringType )
				.setArgumentCountBetween( 2, 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "array_reverse" )
				.setExactArgumentCount( 1 )
				.register();

		// Date functions
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date" )
				.setInvariantType( dateType )
				.setArgumentCountBetween( 1, 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date_add" )
				.setInvariantType( dateType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date_sub" )
				.setInvariantType( dateType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date_diff" )
				.setInvariantType( longType )
				.setExactArgumentCount( 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date_trunc" )
				.setInvariantType( dateType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "date_from_unix_date" )
				.setInvariantType( dateType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "format_date" )
				.setInvariantType( stringType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "parse_date" )
				.setInvariantType( dateType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "unix_date" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();

		// Timestamp functions
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "string" )
				.setInvariantType( stringType )
				.setArgumentCountBetween( 1, 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp" )
				.setInvariantType( timestampType )
				.setArgumentCountBetween( 1, 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_add" )
				.setInvariantType( timestampType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_sub" )
				.setInvariantType( timestampType )
				.setExactArgumentCount( 2 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_diff" )
				.setInvariantType( longType )
				.setExactArgumentCount( 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_trunc" )
				.setInvariantType( timestampType )
				.setArgumentCountBetween( 2, 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "format_timestamp" )
				.setInvariantType( stringType )
				.setArgumentCountBetween( 2, 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "parse_timestamp" )
				.setInvariantType( timestampType )
				.setArgumentCountBetween( 2, 3 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_seconds" )
				.setInvariantType( timestampType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_millis" )
				.setInvariantType( timestampType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "timestamp_micros" )
				.setInvariantType( timestampType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "unix_seconds" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "unix_millis" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "unix_micros" )
				.setInvariantType( longType )
				.setExactArgumentCount( 1 )
				.register();

		queryEngine.getSqmFunctionRegistry().patternDescriptorBuilder("format", "format_timestamp(?2,?1)")
				.setInvariantType( stringType )
				.setExactArgumentCount( 2 )
				.setArgumentListSignature("(datetime as pattern)")
				.register();
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new SpannerSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public Exporter<Table> getTableExporter() {
		return this.spannerTableExporter;
	}

	/* SELECT-related functions */

	@Override
	public boolean supportsCurrentTimestampSelection() {
		return true;
	}

	@Override
	public boolean isCurrentTimestampSelectStringCallable() {
		return false;
	}

	@Override
	public String getCurrentTimestampSelectString() {
		return "select current_timestamp() as now";
	}

	@Override
	public void appendBooleanValueString(SqlAppender appender, boolean bool) {
		appender.appendSql( bool );
	}

	@Override
	public String translateExtractField(TemporalUnit unit) {
		switch (unit) {
			case WEEK:
				return "isoweek";
			case DAY_OF_MONTH:
				return "day";
			case DAY_OF_WEEK:
				return "dayofweek";
			case DAY_OF_YEAR:
				return "dayofyear";
			default:
				return super.translateExtractField(unit);
		}
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType, IntervalType intervalType) {
		if ( temporalType == TemporalType.TIMESTAMP ) {
			switch (unit) {
				case YEAR:
				case QUARTER:
				case MONTH:
					throw new SemanticException("illegal unit for timestamp_add(): " + unit);
				default:
					return "timestamp_add(?3,interval ?2 ?1)";
			}
		}
		else {
			switch (unit) {
				case NANOSECOND:
				case SECOND:
				case MINUTE:
				case HOUR:
				case NATIVE:
					throw new SemanticException("illegal unit for date_add(): " + unit);
				default:
					return "date_add(?3,interval ?2 ?1)";
			}
		}
	}

	@Override
	public String timestampdiffPattern(TemporalUnit unit, TemporalType fromTemporalType, TemporalType toTemporalType) {
		if ( toTemporalType == TemporalType.TIMESTAMP || fromTemporalType == TemporalType.TIMESTAMP ) {
			switch (unit) {
				case YEAR:
				case QUARTER:
				case MONTH:
					throw new SemanticException("illegal unit for timestamp_diff(): " + unit);
				default:
					return "timestamp_diff(?3,?2,?1)";
			}
		}
		else {
			switch (unit) {
				case NANOSECOND:
				case SECOND:
				case MINUTE:
				case HOUR:
				case NATIVE:
					throw new SemanticException("illegal unit for date_diff(): " + unit);
				default:
					return "date_diff(?3,?2,?1)";
			}
		}
	}

	@Override
	public void appendDatetimeFormat(SqlAppender appender, String format) {
		appender.appendSql( datetimeFormat( format ).result() );
	}

	public static Replacer datetimeFormat(String format) {
		return MySQLDialect.datetimeFormat(format)

				//day of week
				.replace("EEEE", "%A")
				.replace("EEE", "%a")

				//minute
				.replace("mm", "%M")
				.replace("m", "%M")

				//month of year
				.replace("MMMM", "%B")
				.replace("MMM", "%b")
				.replace("MM", "%m")
				.replace("M", "%m")

				//week of year
				.replace("ww", "%V")
				.replace("w", "%V")
				//year for week
				.replace("YYYY", "%G")
				.replace("YYY", "%G")
				.replace("YY", "%g")
				.replace("Y", "%g")

				//timezones
				.replace("zzz", "%Z")
				.replace("zz", "%Z")
				.replace("z", "%Z")
				.replace("ZZZ", "%z")
				.replace("ZZ", "%z")
				.replace("Z", "%z")
				.replace("xxx", "%Ez")
				.replace("xx", "%z"); //note special case
	}

	/* DDL-related functions */

	@Override
	public boolean canCreateSchema() {
		return false;
	}

	@Override
	public String[] getCreateSchemaCommand(String schemaName) {
		throw new UnsupportedOperationException(
				"No create schema syntax supported by " + getClass().getName() );
	}

	@Override
	public String[] getDropSchemaCommand(String schemaName) {
		throw new UnsupportedOperationException(
				"No drop schema syntax supported by " + getClass().getName() );
	}

	@Override
	public String getCurrentSchemaCommand() {
		throw new UnsupportedOperationException(
				"No current schema syntax supported by " + getClass().getName() );
	}

	@Override
	public SchemaNameResolver getSchemaNameResolver() {
		throw new UnsupportedOperationException(
				"No schema name resolver supported by " + getClass().getName() );
	}

	@Override
	public boolean dropConstraints() {
		return false;
	}

	@Override
	public boolean qualifyIndexName() {
		return false;
	}

	@Override
	public String getDropForeignKeyString() {
		throw new UnsupportedOperationException(
				"Cannot drop foreign-key constraint because Cloud Spanner does not support foreign keys." );
	}

	@Override
	public String getAddForeignKeyConstraintString(
			String constraintName,
			String[] foreignKey,
			String referencedTable,
			String[] primaryKey,
			boolean referencesPrimaryKey) {
		throw new UnsupportedOperationException(
				"Cannot add foreign-key constraint because Cloud Spanner does not support foreign keys." );
	}

	@Override
	public String getAddForeignKeyConstraintString(
			String constraintName,
			String foreignKeyDefinition) {
		throw new UnsupportedOperationException(
				"Cannot add foreign-key constraint because Cloud Spanner does not support foreign keys." );
	}

	@Override
	public String getAddPrimaryKeyConstraintString(String constraintName) {
		throw new UnsupportedOperationException( "Cannot add primary key constraint in Cloud Spanner." );
	}

	/* Lock acquisition functions */

	@Override
	public boolean supportsLockTimeouts() {
		return false;
	}

	@Override
	public LockingStrategy getLockingStrategy(Lockable lockable, LockMode lockMode) {
		return LOCKING_STRATEGY;
	}

	@Override
	public String getForUpdateString(LockOptions lockOptions) {
		return "";
	}

	@Override
	public String getForUpdateString() {
		return "";
	}

	@Override
	public String getForUpdateString(String aliases) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getForUpdateString(String aliases, LockOptions lockOptions) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getWriteLockString(int timeout) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getWriteLockString(String aliases, int timeout) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getReadLockString(int timeout) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getReadLockString(String aliases, int timeout) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public boolean supportsOuterJoinForUpdate() {
		return false;
	}

	@Override
	public String getForUpdateNowaitString() {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getForUpdateNowaitString(String aliases) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}


	@Override
	public String getForUpdateSkipLockedString() {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	@Override
	public String getForUpdateSkipLockedString(String aliases) {
		throw new UnsupportedOperationException(
				"Cloud Spanner does not support selecting for lock acquisition." );
	}

	/* Unsupported Hibernate Exporters */

	@Override
	public Exporter<Sequence> getSequenceExporter() {
		return NOOP_EXPORTER;
	}

	@Override
	public Exporter<ForeignKey> getForeignKeyExporter() {
		return NOOP_EXPORTER;
	}

	@Override
	public Exporter<Constraint> getUniqueKeyExporter() {
		return NOOP_EXPORTER;
	}

	@Override
	public String applyLocksToSql(
			String sql,
			LockOptions aliasedLockOptions,
			Map<String, String[]> keyColumnNames) {
		return sql;
	}

	@Override
	public UniqueDelegate getUniqueDelegate() {
		return NOOP_UNIQUE_DELEGATE;
	}

	@Override
	public boolean supportsCircularCascadeDeleteConstraints() {
		return false;
	}

	@Override
	public boolean supportsCascadeDelete() {
		return false;
	}

	@Override
	public boolean supportsOffsetInSubquery() {
		return true;
	}

	@Override
	public char openQuote() {
		return '`';
	}

	@Override
	public char closeQuote() {
		return '`';
	}

	@Override
	public LimitHandler getLimitHandler() {
		return LimitOffsetLimitHandler.INSTANCE;
	}

	/* Type conversion and casting */

	@Override
	public String getCastTypeName(SqlExpressable type, Long length, Integer precision, Integer scale) {
		//Spanner doesn't let you specify a length in cast() types
		return super.getRawTypeName( type.getJdbcMapping().getJdbcTypeDescriptor() );
	}

	/**
	 * A no-op {@link Exporter} which is responsible for returning empty Create and Drop SQL strings.
	 *
	 * @author Daniel Zou
	 */
	static class EmptyExporter<T extends Exportable> implements Exporter<T> {

		@Override
		public String[] getSqlCreateStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
			return ArrayHelper.EMPTY_STRING_ARRAY;
		}

		@Override
		public String[] getSqlDropStrings(T exportable, Metadata metadata, SqlStringGenerationContext context) {
			return ArrayHelper.EMPTY_STRING_ARRAY;
		}
	}

	/**
	 * A locking strategy for the Cloud Spanner dialect that does nothing. Cloud Spanner does not
	 * support locking.
	 *
	 * @author Chengyuan Zhao
	 */
	static class DoNothingLockingStrategy implements LockingStrategy {

		@Override
		public void lock(
				Object id, Object version, Object object, int timeout, SharedSessionContractImplementor session)
				throws StaleObjectStateException, LockingStrategyException {
			// Do nothing. Cloud Spanner doesn't have have locking strategies.
		}
	}

	/**
	 * A no-op delegate for generating Unique-Constraints. Cloud Spanner offers unique-restrictions
	 * via interleaved indexes with the "UNIQUE" option. This is not currently supported.
	 *
	 * @author Chengyuan Zhao
	 */
	static class DoNothingUniqueDelegate implements UniqueDelegate {

		@Override
		public String getColumnDefinitionUniquenessFragment(Column column, SqlStringGenerationContext context) {
			return "";
		}

		@Override
		public String getTableCreationUniqueConstraintsFragment(Table table, SqlStringGenerationContext context) {
			return "";
		}

		@Override
		public String getAlterTableToAddUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
			return "";
		}

		@Override
		public String getAlterTableToDropUniqueKeyCommand(UniqueKey uniqueKey, Metadata metadata, SqlStringGenerationContext context) {
			return "";
		}
	}
}

