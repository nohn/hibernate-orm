/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import org.hibernate.LockOptions;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.function.DB2FormatEmulation;
import org.hibernate.dialect.identity.DB2IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.pagination.DB2LimitHandler;
import org.hibernate.dialect.pagination.LegacyDB2LimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.sequence.DB2SequenceSupport;
import org.hibernate.dialect.sequence.LegacyDB2SequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.dialect.unique.DB2UniqueDelegate;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.exception.LockTimeoutException;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.internal.util.JdbcExceptionHelper;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.query.IntervalType;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.mutation.internal.cte.CteInsertStrategy;
import org.hibernate.query.sqm.mutation.internal.cte.CteMutationStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.sql.ast.SqlAstNodeRenderingMode;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.SqlAppender;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorDB2DatabaseImpl;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorNoOpImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.hibernate.type.JavaObjectType;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.*;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import jakarta.persistence.TemporalType;

/**
 * An SQL dialect for DB2.
 *
 * @author Gavin King
 */
public class DB2Dialect extends Dialect {

	private static final int BIND_PARAMETERS_NUMBER_LIMIT = 32_767;

	private static final String FOR_READ_ONLY_SQL = " for read only with rs";
	private static final String FOR_SHARE_SQL = FOR_READ_ONLY_SQL + " use and keep share locks";
	private static final String FOR_UPDATE_SQL = FOR_READ_ONLY_SQL + " use and keep update locks";
	private static final String SKIP_LOCKED_SQL = " skip locked data";
	private static final String FOR_SHARE_SKIP_LOCKED_SQL = FOR_SHARE_SQL + SKIP_LOCKED_SQL;
	private static final String FOR_UPDATE_SKIP_LOCKED_SQL = FOR_UPDATE_SQL + SKIP_LOCKED_SQL;

	private final DatabaseVersion version;

	private final LimitHandler limitHandler;
	private final UniqueDelegate uniqueDelegate;

	public DB2Dialect(DialectResolutionInfo info) {
		this( info.makeCopy() );
		registerKeywords( info );
	}

	public DB2Dialect() {
		this( DatabaseVersion.make( 9, 0 ) );
	}

	public DB2Dialect(DatabaseVersion version) {
		super();
		this.version = version;

		registerColumnType( Types.TINYINT, "smallint" ); //no tinyint

		//HHH-12827: map them both to the same type to avoid problems with schema update
		//Note that 31 is the maximum precision DB2 supports
//		registerColumnType( Types.DECIMAL, "decimal($p,$s)" );
		registerColumnType( Types.NUMERIC, "decimal($p,$s)" );

		if ( getVersion().isBefore( 11 ) ) {
			registerColumnType( Types.BINARY, 254, "char($l) for bit data" ); //should use 'binary' since version 11
			registerColumnType( Types.BINARY, "varchar($l) for bit data" ); //should use 'binary' since version 11

			registerColumnType( Types.VARBINARY, getMaxVarbinaryLength(), "varchar($l) for bit data" ); //should use 'varbinary' since version 11

			//prior to DB2 11, the 'boolean' type existed,
			//but was not allowed as a column type
			registerColumnType( Types.BOOLEAN, "smallint" );
		}
		registerColumnType( Types.VARBINARY, "blob($l)" );

		registerColumnType( Types.BLOB, "blob($l)" );
		registerColumnType( Types.CLOB, "clob($l)" );

		registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp($p)" );
		registerColumnType( Types.TIME_WITH_TIMEZONE, "time" );

		// The long varchar data type was deprecated in DB2 and shouldn't be used anymore
		registerColumnType( Types.VARCHAR, "clob($l)" );
		registerColumnType( Types.NVARCHAR, "nclob($l)" );

		//not keywords, at least not in DB2 11,
		//but perhaps they were in older versions?
		registerKeyword( "current" );
		registerKeyword( "date" );
		registerKeyword( "time" );
		registerKeyword( "timestamp" );
		registerKeyword( "fetch" );
		registerKeyword( "first" );
		registerKeyword( "rows" );
		registerKeyword( "only" );

		getDefaultProperties().setProperty( Environment.STATEMENT_BATCH_SIZE, NO_BATCH );

		uniqueDelegate = createUniqueDelegate();

		limitHandler = getVersion().isBefore( 11, 1 )
				? LegacyDB2LimitHandler.INSTANCE
				: DB2LimitHandler.INSTANCE;
	}

	protected UniqueDelegate createUniqueDelegate() {
		return new DB2UniqueDelegate( this );
	}

	@Override
	public int getMaxVarcharLength() {
		return 32_672;
	}

	@Override
	public DatabaseVersion getVersion() {
		return version;
	}

	@Override
	public int getDefaultDecimalPrecision() {
		//this is the maximum allowed in DB2
		return 31;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );

		// AVG by default uses the input type, so we possibly need to cast the argument type, hence a special function
		CommonFunctionFactory.avg_castingNonDoubleArguments( this, queryEngine, SqlAstNodeRenderingMode.DEFAULT );

		CommonFunctionFactory.cot( queryEngine );
		CommonFunctionFactory.degrees( queryEngine );
		CommonFunctionFactory.log( queryEngine );
		CommonFunctionFactory.log10( queryEngine );
		CommonFunctionFactory.radians( queryEngine );
		CommonFunctionFactory.rand( queryEngine );
		CommonFunctionFactory.soundex( queryEngine );
		CommonFunctionFactory.trim2( queryEngine );
		CommonFunctionFactory.space( queryEngine );
		CommonFunctionFactory.repeat( queryEngine );
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "substr" )
				.setInvariantType(
						queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve( StandardBasicTypes.STRING )
				)
				.setArgumentCountBetween( 2, 4 )
				.setArgumentListSignature( "(string, start[, length[, units]])" )
				.register();
		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "substring" )
				.setInvariantType(
						queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve( StandardBasicTypes.STRING )
				)
				.setArgumentCountBetween( 2, 4 )
				.setArgumentListSignature( "(string{ from|,} start[{ for|,} length[, units]])" )
				.register();
		CommonFunctionFactory.translate( queryEngine );
		CommonFunctionFactory.bitand( queryEngine );
		CommonFunctionFactory.bitor( queryEngine );
		CommonFunctionFactory.bitxor( queryEngine );
		CommonFunctionFactory.bitnot( queryEngine );
		CommonFunctionFactory.yearMonthDay( queryEngine );
		CommonFunctionFactory.hourMinuteSecond( queryEngine );
		CommonFunctionFactory.dayofweekmonthyear( queryEngine );
		CommonFunctionFactory.weekQuarter( queryEngine );
		CommonFunctionFactory.daynameMonthname( queryEngine );
		CommonFunctionFactory.lastDay( queryEngine );
		CommonFunctionFactory.toCharNumberDateTimestamp( queryEngine );
		CommonFunctionFactory.dateTimeTimestamp( queryEngine );
		CommonFunctionFactory.concat_pipeOperator( queryEngine );
		CommonFunctionFactory.octetLength( queryEngine );
		CommonFunctionFactory.ascii( queryEngine );
		CommonFunctionFactory.char_chr( queryEngine );
		CommonFunctionFactory.position( queryEngine );
		CommonFunctionFactory.trunc( queryEngine );
		CommonFunctionFactory.truncate( queryEngine );
		CommonFunctionFactory.insert( queryEngine );
		CommonFunctionFactory.overlayCharacterLength_overlay( queryEngine );
		CommonFunctionFactory.median( queryEngine );
		CommonFunctionFactory.stddev( queryEngine );
		CommonFunctionFactory.stddevPopSamp( queryEngine );
		CommonFunctionFactory.regrLinearRegressionAggregates( queryEngine );
		CommonFunctionFactory.variance( queryEngine );
		CommonFunctionFactory.stdevVarianceSamp( queryEngine );
		CommonFunctionFactory.addYearsMonthsDaysHoursMinutesSeconds( queryEngine );
		CommonFunctionFactory.yearsMonthsDaysHoursMinutesSecondsBetween( queryEngine );
		CommonFunctionFactory.dateTrunc( queryEngine );
		CommonFunctionFactory.bitLength_pattern( queryEngine, "length(?1)*8" );

		queryEngine.getSqmFunctionRegistry().register( "format", new DB2FormatEmulation( queryEngine.getTypeConfiguration() ) );

		queryEngine.getSqmFunctionRegistry().namedDescriptorBuilder( "posstr" )
				.setInvariantType(
						queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve( StandardBasicTypes.INTEGER )
				)
				.setExactArgumentCount( 2 )
				.setArgumentListSignature("(string, pattern)")
				.register();
	}

	@Override
	public String[] getDropSchemaCommand(String schemaName) {
		return new String[] {"drop schema " + schemaName + " restrict"};
	}

	/**
	 * Since we're using {@code seconds_between()} and
	 * {@code add_seconds()}, it makes sense to use
	 * seconds as the "native" precision.
	 */
	@Override
	public long getFractionalSecondPrecisionInNanos() {
		//Note that DB2 actually supports all the way up to
		//thousands-of-nanoseconds precision for timestamps!
		//i.e. timestamp(12)
		return 1_000_000_000; //seconds
	}

	@Override
	public String timestampdiffPattern(TemporalUnit unit, TemporalType fromTemporalType, TemporalType toTemporalType) {
		StringBuilder pattern = new StringBuilder();
		boolean castFrom = fromTemporalType != TemporalType.TIMESTAMP && !unit.isDateUnit();
		boolean castTo = toTemporalType != TemporalType.TIMESTAMP && !unit.isDateUnit();
		switch (unit) {
			case NATIVE:
			case NANOSECOND:
				pattern.append("(seconds_between(");
				break;
			//note: DB2 does have weeks_between()
			case MONTH:
			case QUARTER:
				// the months_between() function results
				// in a non-integral value, so trunc() it
				pattern.append("trunc(months_between(");
				break;
			default:
				pattern.append("?1s_between(");
		}
		if (castTo) {
			pattern.append("cast(?3 as timestamp)");
		}
		else {
			pattern.append("?3");
		}
		pattern.append(",");
		if (castFrom) {
			pattern.append("cast(?2 as timestamp)");
		}
		else {
			pattern.append("?2");
		}
		pattern.append(")");
		switch (unit) {
			case NATIVE:
				pattern.append("+(microsecond(?3)-microsecond(?2))/1e6)");
				break;
			case NANOSECOND:
				pattern.append("*1e9+(microsecond(?3)-microsecond(?2))*1e3)");
				break;
			case MONTH:
				pattern.append(")");
				break;
			case QUARTER:
				pattern.append("/3)");
				break;
		}
		return pattern.toString();
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType, IntervalType intervalType) {
		final StringBuilder pattern = new StringBuilder();
		final boolean castTo;
		if ( unit.isDateUnit() ) {
			castTo = temporalType == TemporalType.TIME;
		}
		else {
			castTo = temporalType == TemporalType.DATE;
		}
		if (castTo) {
			pattern.append("cast(?3 as timestamp)");
		}
		else {
			pattern.append("?3");
		}
		pattern.append("+(");
		// DB2 supports temporal arithmetic. See https://www.ibm.com/support/knowledgecenter/en/SSEPGG_9.7.0/com.ibm.db2.luw.sql.ref.doc/doc/r0023457.html
		switch (unit) {
			case NATIVE:
				// AFAICT the native format is seconds with fractional parts after the decimal point
				pattern.append("?2) seconds");
				break;
			case NANOSECOND:
				pattern.append("(?2)/1e9) seconds");
				break;
			case WEEK:
				pattern.append("(?2)*7) days");
				break;
			case QUARTER:
				pattern.append("(?2)*3) months");
				break;
			default:
				pattern.append("?2) ?1s");
		}
		return pattern.toString();
	}

	@Override
	public String getLowercaseFunction() {
		return getVersion().isBefore( 9, 7 ) ? "lcase" : super.getLowercaseFunction();
	}

	@Override
	public boolean dropConstraints() {
		return false;
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return getVersion().isBefore( 9, 7 )
				? LegacyDB2SequenceSupport.INSTANCE
				: DB2SequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		return "select * from syscat.sequences";
	}

	@Override
	public SequenceInformationExtractor getSequenceInformationExtractor() {
		if ( getQuerySequencesString() == null ) {
			return SequenceInformationExtractorNoOpImpl.INSTANCE;
		}
		else {
			return SequenceInformationExtractorDB2DatabaseImpl.INSTANCE;
		}
	}

	@Override
	public String getForUpdateString() {
		return FOR_UPDATE_SQL;
	}

	@Override
	public boolean supportsSkipLocked() {
		// Introduced in 11.5: https://www.ibm.com/docs/en/db2/11.5?topic=statement-concurrent-access-resolution-clause
		return getVersion().isSameOrAfter( 11, 5 );
	}

	@Override
	public String getForUpdateSkipLockedString() {
		return supportsSkipLocked()
				? FOR_UPDATE_SKIP_LOCKED_SQL
				: FOR_UPDATE_SQL;
	}

	@Override
	public String getForUpdateSkipLockedString(String aliases) {
		return getForUpdateSkipLockedString();
	}

	@Override
	public String getWriteLockString(int timeout) {
		return timeout == LockOptions.SKIP_LOCKED && supportsSkipLocked()
				? FOR_UPDATE_SKIP_LOCKED_SQL
				: FOR_UPDATE_SQL;
	}

	@Override
	public String getReadLockString(int timeout) {
		return timeout == LockOptions.SKIP_LOCKED && supportsSkipLocked()
				? FOR_SHARE_SKIP_LOCKED_SQL
				: FOR_SHARE_SQL;
	}

	@Override
	public boolean supportsOuterJoinForUpdate() {
		return false;
	}

	@Override
	public boolean supportsExistsInSelect() {
		return false;
	}

	@Override
	public boolean supportsLockTimeouts() {
		//as far as I know, DB2 doesn't support this
		return false;
	}

	@Override
	public String getSelectClauseNullString(int sqlType) {
		return selectNullString(sqlType);
	}

	static String selectNullString(int sqlType) {
		String literal;
		switch ( sqlType ) {
			case Types.VARCHAR:
			case Types.CHAR:
				literal = "''";
				break;
			case Types.DATE:
				literal = "'2000-1-1'";
				break;
			case Types.TIME:
				literal = "'00:00:00'";
				break;
			case Types.TIMESTAMP:
			case Types.TIMESTAMP_WITH_TIMEZONE:
				literal = "'2000-1-1 00:00:00'";
				break;
			default:
				literal = "0";
		}
		return "nullif(" + literal + "," + literal + ')';
	}

	@Override
	public int registerResultSetOutParameter(CallableStatement statement, int col) throws SQLException {
		return col;
	}

	@Override
	public ResultSet getResultSet(CallableStatement ps) throws SQLException {
		boolean isResultSet = ps.execute();
		// This assumes you will want to ignore any update counts
		while ( !isResultSet && ps.getUpdateCount() != -1 ) {
			isResultSet = ps.getMoreResults();
		}

		return ps.getResultSet();
	}

	@Override
	public boolean supportsCommentOn() {
		return true;
	}

	@Override
	public SqmMultiTableMutationStrategy getFallbackSqmMutationStrategy(
			EntityMappingType rootEntityDescriptor,
			RuntimeModelCreationContext runtimeModelCreationContext) {
		return new CteMutationStrategy( rootEntityDescriptor, runtimeModelCreationContext );
	}

	@Override
	public SqmMultiTableInsertStrategy getFallbackSqmInsertStrategy(
			EntityMappingType rootEntityDescriptor,
			RuntimeModelCreationContext runtimeModelCreationContext) {
		return new CteInsertStrategy( rootEntityDescriptor, runtimeModelCreationContext );
	}

	@Override
	public boolean supportsCurrentTimestampSelection() {
		return true;
	}

	@Override
	public String getCurrentTimestampSelectString() {
		return "values current timestamp";
	}

	@Override
	public boolean isCurrentTimestampSelectStringCallable() {
		return false;
	}

	@Override
	public boolean supportsParametersInInsertSelect() {
		return true;
	}

	@Override
	public boolean supportsResultSetPositionQueryMethodsOnForwardOnlyCursor() {
		return false;
	}


	// Overridden informational metadata ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public boolean supportsLobValueChangePropagation() {
		return false;
	}

	@Override
	public boolean doesReadCommittedCauseWritersToBlockReaders() {
		return true;
	}

	@Override
	public boolean supportsTupleDistinctCounts() {
		return false;
	}

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		final JdbcTypeRegistry jdbcTypeRegistry = typeContributions.getTypeConfiguration().getJdbcTypeDescriptorRegistry();

		if ( version.isBefore( 11 ) ) {
			jdbcTypeRegistry.addDescriptor( Types.BOOLEAN, SmallIntJdbcType.INSTANCE );
			// Binary literals were only added in 11. See https://www.ibm.com/support/knowledgecenter/SSEPGG_11.1.0/com.ibm.db2.luw.sql.ref.doc/doc/r0000731.html#d79816e393
			jdbcTypeRegistry.addDescriptor( Types.VARBINARY, VarbinaryJdbcType.INSTANCE_WITHOUT_LITERALS );
			if ( version.isBefore( 9, 7 ) ) {
				jdbcTypeRegistry.addDescriptor( Types.NUMERIC, DecimalJdbcType.INSTANCE );
			}
		}
		// See HHH-12753
		// It seems that DB2's JDBC 4.0 support as of 9.5 does not
		// support the N-variant methods like NClob or NString.
		// Therefore here we overwrite the sql type descriptors to
		// use the non-N variants which are supported.
		jdbcTypeRegistry.addDescriptor( Types.NCHAR, CharJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor(
				Types.NCLOB,
				useInputStreamToInsertBlob()
						? ClobJdbcType.STREAM_BINDING
						: ClobJdbcType.CLOB_BINDING
		);
		jdbcTypeRegistry.addDescriptor( Types.NVARCHAR, VarcharJdbcType.INSTANCE );
		jdbcTypeRegistry.addDescriptor( Types.NUMERIC, DecimalJdbcType.INSTANCE );

		// DB2 requires a custom binder for binding untyped nulls that resolves the type through the statement
		typeContributions.contributeJdbcTypeDescriptor( ObjectNullResolvingJdbcType.INSTANCE );

		// Until we remove StandardBasicTypes, we have to keep this
		typeContributions.contributeType(
				new JavaObjectType(
						ObjectNullResolvingJdbcType.INSTANCE,
						typeContributions.getTypeConfiguration()
								.getJavaTypeDescriptorRegistry()
								.getDescriptor( Object.class )
				)
		);
	}

	@Override
	public void appendBinaryLiteral(SqlAppender appender, byte[] bytes) {
		appender.appendSql( "BX'" );
		PrimitiveByteArrayJavaTypeDescriptor.INSTANCE.appendString( appender, bytes );
		appender.appendSql( '\'' );
	}

	@Override
	public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
		return (sqlException, message, sql) -> {
			final String sqlState = JdbcExceptionHelper.extractSqlState( sqlException );
			final int errorCode = JdbcExceptionHelper.extractErrorCode( sqlException );

			if ( -952 == errorCode && "57014".equals( sqlState ) ) {
				throw new LockTimeoutException( message, sqlException, sql );
			}
			return null;
		};
	}

	@Override
	public UniqueDelegate getUniqueDelegate() {
		return uniqueDelegate;
	}

	@Override
	public int getMaxIdentifierLength() {
		return 128;
	}

	@Override
	public LimitHandler getLimitHandler() {
		return limitHandler;
	}

	@Override
	public boolean supportsNullPrecedence() {
		return false;
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new DB2SqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return new DB2IdentityColumnSupport();
	}

	@Override
	public boolean supportsValuesList() {
		return true;
	}

	@Override
	public boolean supportsPartitionBy() {
		return true;
	}

	@Override
	public boolean supportsNonQueryWithCTE() {
		return true;
	}

	@Override
	public boolean supportsOffsetInSubquery() {
		return true;
	}

	@Override
	public boolean supportsWindowFunctions() {
		return true;
	}

	@Override
	public void appendDatetimeFormat(SqlAppender appender, String format) {
		//DB2 does not need nor support FM
		appender.appendSql( OracleDialect.datetimeFormat( format, false, false ).result() );
	}

	@Override
	public String translateExtractField(TemporalUnit unit) {
		switch ( unit ) {
			//WEEK means the ISO week number on DB2
			case DAY_OF_MONTH: return "day";
			case DAY_OF_YEAR: return "doy";
			case DAY_OF_WEEK: return "dow";
			default: return super.translateExtractField( unit );
		}
	}

	@Override
	public void appendBooleanValueString(SqlAppender appender, boolean bool) {
		if ( getVersion().isBefore( 11 ) ) {
			appender.appendSql( bool ? '1' : '0' );
		}
		else {
			appender.appendSql( bool );
		}
	}

	@Override
	public String extractPattern(TemporalUnit unit) {
		if ( unit == TemporalUnit.WEEK ) {
			// Not sure why, but `extract(week from '2019-05-27')` wrongly returns 21 and week_iso behaves correct
			return "week_iso(?2)";
		}
		else {
			return super.extractPattern( unit );
		}
	}

	@Override
	public int getInExpressionCountLimit() {
		return BIND_PARAMETERS_NUMBER_LIMIT;
	}

}
