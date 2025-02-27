/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.hibernate.LockOptions;
import org.hibernate.QueryTimeoutException;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.cfg.Environment;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.function.NvlCoalesceEmulation;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.Oracle12cIdentityColumnSupport;
import org.hibernate.dialect.pagination.LegacyOracleLimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.Oracle12LimitHandler;
import org.hibernate.dialect.sequence.OracleSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.config.spi.StandardConverters;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.hibernate.exception.LockTimeoutException;
import org.hibernate.exception.spi.SQLExceptionConversionDelegate;
import org.hibernate.exception.spi.TemplatedViolatedConstraintNameExtractor;
import org.hibernate.exception.spi.ViolatedConstraintNameExtractor;
import org.hibernate.internal.util.JdbcExceptionHelper;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.procedure.internal.StandardCallableStatementSupport;
import org.hibernate.procedure.spi.CallableStatementSupport;
import org.hibernate.query.CastType;
import org.hibernate.query.FetchClauseType;
import org.hibernate.query.IntervalType;
import org.hibernate.query.SemanticException;
import org.hibernate.query.TemporalUnit;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.query.sqm.mutation.internal.temptable.GlobalTemporaryTableInsertStrategy;
import org.hibernate.query.sqm.mutation.internal.temptable.GlobalTemporaryTableMutationStrategy;
import org.hibernate.dialect.temptable.TemporaryTable;
import org.hibernate.dialect.temptable.TemporaryTableKind;
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
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorOracleDatabaseImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.hibernate.type.JavaObjectType;
import org.hibernate.type.NullType;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.StandardBasicTypes;
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.BlobJdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.NullJdbcType;
import org.hibernate.type.descriptor.jdbc.ObjectNullAsNullTypeJdbcType;
import org.hibernate.type.descriptor.jdbc.spi.JdbcTypeRegistry;

import jakarta.persistence.TemporalType;

import static org.hibernate.exception.spi.TemplatedViolatedConstraintNameExtractor.extractUsingTemplate;
import static org.hibernate.query.TemporalUnit.DAY;
import static org.hibernate.query.TemporalUnit.HOUR;
import static org.hibernate.query.TemporalUnit.MINUTE;
import static org.hibernate.query.TemporalUnit.MONTH;
import static org.hibernate.query.TemporalUnit.SECOND;
import static org.hibernate.query.TemporalUnit.YEAR;

/**
 * A dialect for Oracle 8i and above.
 *
 * @author Steve Ebersole
 * @author Gavin King
 */
public class OracleDialect extends Dialect {

	private static final Pattern DISTINCT_KEYWORD_PATTERN = Pattern.compile( "\\bdistinct\\b" );
	private static final Pattern GROUP_BY_KEYWORD_PATTERN = Pattern.compile( "\\bgroup\\sby\\b" );
	private static final Pattern ORDER_BY_KEYWORD_PATTERN = Pattern.compile( "\\border\\sby\\b" );
	private static final Pattern UNION_KEYWORD_PATTERN = Pattern.compile( "\\bunion\\b" );
	private static final Pattern SQL_STATEMENT_TYPE_PATTERN = Pattern.compile("^(?:/\\*.*?\\*/)?\\s*(select|insert|update|delete)\\s+.*?", Pattern.CASE_INSENSITIVE);
	private static final int PARAM_LIST_SIZE_LIMIT = 1000;

	public static final String PREFER_LONG_RAW = "hibernate.dialect.oracle.prefer_long_raw";

	private final LimitHandler limitHandler;
	private final DatabaseVersion version;

	public OracleDialect(DialectResolutionInfo info) {
		this( info.makeCopy() );
		registerKeywords( info );
	}

	public OracleDialect() {
		this( DatabaseVersion.make( 8, 0 ) );
	}

	public OracleDialect(DatabaseVersion version) {
		super();
		this.version = version;

		registerCharacterTypeMappings();
		registerNumericTypeMappings();
		registerDateTimeTypeMappings();
		registerBinaryTypeMappings();
		registerExtendedTypeMappings();
		registerReverseHibernateTypeMappings();
		registerDefaultProperties();

		limitHandler = supportsFetchClause( FetchClauseType.ROWS_ONLY )
				? Oracle12LimitHandler.INSTANCE
				: new LegacyOracleLimitHandler( getVersion() );
	}

	@Override
	public DatabaseVersion getVersion() {
		return version;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return Types.BIT;
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );

		CommonFunctionFactory.cosh( queryEngine );
		CommonFunctionFactory.sinh( queryEngine );
		CommonFunctionFactory.tanh( queryEngine );
		CommonFunctionFactory.trunc( queryEngine );
		CommonFunctionFactory.log( queryEngine );
		CommonFunctionFactory.soundex( queryEngine );
		CommonFunctionFactory.trim2( queryEngine );
		CommonFunctionFactory.initcap( queryEngine );
		CommonFunctionFactory.instr( queryEngine );
		CommonFunctionFactory.substr( queryEngine );
		CommonFunctionFactory.substring_substr( queryEngine );
		CommonFunctionFactory.leftRight_substr( queryEngine );
		CommonFunctionFactory.translate( queryEngine );
		CommonFunctionFactory.bitand( queryEngine );
		CommonFunctionFactory.lastDay( queryEngine );
		CommonFunctionFactory.toCharNumberDateTimestamp( queryEngine );
		CommonFunctionFactory.ceiling_ceil( queryEngine );
		CommonFunctionFactory.concat_pipeOperator( queryEngine );
		CommonFunctionFactory.rownumRowid( queryEngine );
		CommonFunctionFactory.sysdate( queryEngine );
		CommonFunctionFactory.systimestamp( queryEngine );
		CommonFunctionFactory.characterLength_length( queryEngine, SqlAstNodeRenderingMode.DEFAULT );
		CommonFunctionFactory.addMonths( queryEngine );
		CommonFunctionFactory.monthsBetween( queryEngine );
		CommonFunctionFactory.everyAny_sumCaseCase( queryEngine );

		CommonFunctionFactory.median( queryEngine );
		CommonFunctionFactory.stddev( queryEngine );
		CommonFunctionFactory.stddevPopSamp( queryEngine );
		CommonFunctionFactory.variance( queryEngine );
		CommonFunctionFactory.varPopSamp( queryEngine );
		CommonFunctionFactory.covarPopSamp( queryEngine );
		CommonFunctionFactory.corr( queryEngine );
		CommonFunctionFactory.regrLinearRegressionAggregates( queryEngine );
		CommonFunctionFactory.bitLength_pattern( queryEngine, "vsize(?1)*8" );

		if ( getVersion().isBefore( 9 ) ) {
			queryEngine.getSqmFunctionRegistry().register( "coalesce", new NvlCoalesceEmulation() );
		}
		else {
			//Oracle has had coalesce() since 9.0.1
			CommonFunctionFactory.coalesce( queryEngine );
		}

		queryEngine.getSqmFunctionRegistry().registerBinaryTernaryPattern(
				"locate",
				queryEngine.getTypeConfiguration().getBasicTypeRegistry().resolve( StandardBasicTypes.INTEGER ),
				"instr(?2,?1)",
				"instr(?2,?1,?3)"
		).setArgumentListSignature("(pattern, string[, start])");
	}

	@Override
	public int getMaxVarcharLength() {
		//with MAX_STRING_SIZE=EXTENDED, changes to 32_767
		//TODO: provide a way to change this without a custom Dialect
		return 4000;
	}

	@Override
	public int getMaxVarbinaryLength() {
		//with MAX_STRING_SIZE=EXTENDED, changes to 32_767
		return 2000;
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new OracleSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public String currentDate() {
		return getVersion().isBefore( 9 ) ? currentTimestamp() : "current_date";
	}

	@Override
	public String currentTime() {
		return currentTimestamp();
	}

	@Override
	public String currentTimestamp() {
		return getVersion().isBefore( 9 ) ? "sysdate" : currentTimestampWithTimeZone();
	}

	@Override
	public String currentLocalTime() {
		return currentLocalTimestamp();
	}

	@Override
	public String currentLocalTimestamp() {
		return getVersion().isBefore( 9 ) ? currentTimestamp() : "localtimestamp";
	}

	@Override
	public String currentTimestampWithTimeZone() {
		return getVersion().isBefore( 9 ) ? currentTimestamp() : "current_timestamp";
	}


	/**
	 * Oracle doesn't have any sort of {@link Types#BOOLEAN}
	 * type or {@link Types#TIME} type, and its default behavior
	 * for casting dates and timestamps to and from strings is just awful.
	 */
	@Override
	public String castPattern(CastType from, CastType to) {
		String result;
		switch ( to ) {
			case INTEGER:
			case LONG:
				result = BooleanDecoder.toInteger( from );
				if ( result != null ) {
					return result;
				}
				break;
			case INTEGER_BOOLEAN:
				result = BooleanDecoder.toIntegerBoolean( from );
				if ( result != null ) {
					return result;
				}
				break;
			case YN_BOOLEAN:
				result = BooleanDecoder.toYesNoBoolean( from );
				if ( result != null ) {
					return result;
				}
				break;
			case BOOLEAN:
			case TF_BOOLEAN:
				result = BooleanDecoder.toTrueFalseBoolean( from );
				if ( result != null ) {
					return result;
				}
				break;
			case STRING:
				switch ( from ) {
					case INTEGER_BOOLEAN:
					case TF_BOOLEAN:
					case YN_BOOLEAN:
						return BooleanDecoder.toString( from );
					case DATE:
						return "to_char(?1,'YYYY-MM-DD')";
					case TIME:
						return "to_char(?1,'HH24:MI:SS')";
					case TIMESTAMP:
						return "to_char(?1,'YYYY-MM-DD HH24:MI:SS.FF9')";
					case OFFSET_TIMESTAMP:
						return "to_char(?1,'YYYY-MM-DD HH24:MI:SS.FF9TZH:TZM')";
					case ZONE_TIMESTAMP:
						return "to_char(?1,'YYYY-MM-DD HH24:MI:SS.FF9 TZR')";
				}
				break;
			case DATE:
				if ( from == CastType.STRING ) {
					return "to_date(?1,'YYYY-MM-DD')";
				}
				break;
			case TIME:
				if ( from == CastType.STRING ) {
					return "to_date(?1,'HH24:MI:SS')";
				}
				break;
			case TIMESTAMP:
				if ( from == CastType.STRING ) {
					return "to_timestamp(?1,'YYYY-MM-DD HH24:MI:SS.FF9')";
				}
				break;
			case OFFSET_TIMESTAMP:
				if ( from == CastType.STRING ) {
					return "to_timestamp_tz(?1,'YYYY-MM-DD HH24:MI:SS.FF9TZH:TZM')";
				}
				break;
			case ZONE_TIMESTAMP:
				if ( from == CastType.STRING ) {
					return "to_timestamp_tz(?1,'YYYY-MM-DD HH24:MI:SS.FF9 TZR')";
				}
				break;
		}
		return super.castPattern(from, to);
	}

	/**
	 * We minimize multiplicative factors by using seconds
	 * (with fractional part) as the "native" precision for
	 * duration arithmetic.
	 */
	@Override
	public long getFractionalSecondPrecisionInNanos() {
		return 1_000_000_000; //seconds
	}

	/**
	 * Oracle supports a limited list of temporal fields in the
	 * extract() function, but we can emulate some of them by
	 * using to_char() with a format string instead of extract().
	 *
	 * Thus, the additional supported fields are
	 * {@link TemporalUnit#DAY_OF_YEAR},
	 * {@link TemporalUnit#DAY_OF_MONTH},
	 * {@link TemporalUnit#DAY_OF_YEAR},
	 * and {@link TemporalUnit#WEEK}.
	 */
	@Override
	public String extractPattern(TemporalUnit unit) {
		switch (unit) {
			case DAY_OF_WEEK:
				return "to_number(to_char(?2,'D'))";
			case DAY_OF_MONTH:
				return "to_number(to_char(?2,'DD'))";
			case DAY_OF_YEAR:
				return "to_number(to_char(?2,'DDD'))";
			case WEEK:
				return "to_number(to_char(?2,'IW'))"; //the ISO week number
			case WEEK_OF_YEAR:
				return "to_number(to_char(?2,'WW'))";
			// Oracle doesn't support extracting the quarter
			case QUARTER:
				return "to_number(to_char(?2,'Q'))";
			// Oracle can't extract time parts from a date column, so we need to cast to timestamp
			// This is because Oracle treats date as ANSI SQL date which has no time part
			// Also see https://docs.oracle.com/cd/B28359_01/server.111/b28286/functions052.htm#SQLRF00639
			case HOUR:
				return "to_number(to_char(?2,'HH24'))";
			case MINUTE:
				return "to_number(to_char(?2,'MI'))";
			case SECOND:
				return "to_number(to_char(?2,'SS'))";
			default:
				return super.extractPattern(unit);
		}
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType, IntervalType intervalType) {
		StringBuilder pattern = new StringBuilder();
		pattern.append("(?3+");
		switch ( unit ) {
			case YEAR:
			case QUARTER:
			case MONTH:
				pattern.append("numtoyminterval");
				break;
			case WEEK:
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
			case NANOSECOND:
			case NATIVE:
				pattern.append("numtodsinterval");
				break;
			default:
				throw new SemanticException(unit + " is not a legal field");
		}
		pattern.append("(");
		switch ( unit ) {
			case NANOSECOND:
			case QUARTER:
			case WEEK:
				pattern.append("(");
				break;
		}
		pattern.append("?2");
		switch ( unit ) {
			case QUARTER:
				pattern.append(")*3");
				break;
			case WEEK:
				pattern.append(")*7");
				break;
			case NANOSECOND:
				pattern.append(")/1e9");
				break;
		}
		pattern.append(",'");
		switch ( unit ) {
			case QUARTER:
				pattern.append("month");
				break;
			case WEEK:
				pattern.append("day");
				break;
			case NANOSECOND:
			case NATIVE:
				pattern.append("second");
				break;
			default:
				pattern.append("?1");
		}
		pattern.append("')");
		pattern.append(")");
		return pattern.toString();
	}

	@Override
	public String timestampdiffPattern(
			TemporalUnit unit,
			TemporalType fromTemporalType, TemporalType toTemporalType) {
		StringBuilder pattern = new StringBuilder();
		boolean timestamp = toTemporalType == TemporalType.TIMESTAMP || fromTemporalType == TemporalType.TIMESTAMP;
		switch (unit) {
			case YEAR:
				extractField(pattern, YEAR, unit);
				break;
			case QUARTER:
			case MONTH:
				pattern.append("(");
				extractField(pattern, YEAR, unit);
				pattern.append("+");
				extractField(pattern, MONTH, unit);
				pattern.append(")");
				break;
			case WEEK:
			case DAY:
				extractField(pattern, DAY, unit);
				break;
			case HOUR:
				pattern.append("(");
				extractField(pattern, DAY, unit);
				if (timestamp) {
					pattern.append("+");
					extractField(pattern, HOUR, unit);
				}
				pattern.append(")");
				break;
			case MINUTE:
				pattern.append("(");
				extractField(pattern, DAY, unit);
				if (timestamp) {
					pattern.append("+");
					extractField(pattern, HOUR, unit);
					pattern.append("+");
					extractField(pattern, MINUTE, unit);
				}
				pattern.append(")");
				break;
			case NATIVE:
			case NANOSECOND:
			case SECOND:
				pattern.append("(");
				extractField(pattern, DAY, unit);
				if (timestamp) {
					pattern.append("+");
					extractField(pattern, HOUR, unit);
					pattern.append("+");
					extractField(pattern, MINUTE, unit);
					pattern.append("+");
					extractField(pattern, SECOND, unit);
				}
				pattern.append(")");
				break;
			default:
				throw new SemanticException("unrecognized field: " + unit);
		}
		return pattern.toString();
	}

	private void extractField(
			StringBuilder pattern,
			TemporalUnit unit, TemporalUnit toUnit) {
		pattern.append("extract(");
		pattern.append( translateExtractField(unit) );
		pattern.append(" from (?3-?2) ");
		switch (unit) {
			case YEAR:
			case MONTH:
				pattern.append("year to month");
				break;
			case DAY:
			case HOUR:
			case MINUTE:
			case SECOND:
				pattern.append("day to second");
				break;
			default:
				throw new SemanticException(unit + " is not a legal field");
		}
		pattern.append(")");
		pattern.append( unit.conversionFactor( toUnit, this ) );
	}

	protected void registerCharacterTypeMappings() {
		if ( getVersion().isBefore( 9 ) ) {
			registerColumnType( Types.VARCHAR, getMaxVarcharLength(), "varchar2($l)" );
			registerColumnType( Types.VARCHAR, "clob" );
		}
		else {
			registerColumnType( Types.CHAR, "char($l char)" );
			registerColumnType( Types.VARCHAR, getMaxVarcharLength(), "varchar2($l char)" );
			registerColumnType( Types.VARCHAR, "clob" );
			registerColumnType( Types.NVARCHAR, getMaxNVarcharLength(), "nvarchar2($l)" );
			registerColumnType( Types.NVARCHAR, "nclob" );
		}
		//note: the 'long' type is deprecated
	}

	protected void registerNumericTypeMappings() {
		registerColumnType( Types.BOOLEAN, "number(1,0)" );

		registerColumnType( Types.BIGINT, "number(19,0)" );
		registerColumnType( Types.SMALLINT, "number(5,0)" );
		registerColumnType( Types.TINYINT, "number(3,0)" );
		registerColumnType( Types.INTEGER, "number(10,0)" );

		// Oracle has DOUBLE semantics for the REAL type, so we map it to float(24)
		registerColumnType( Types.REAL, "float(24)" );

		// Note that 38 is the maximum precision Oracle supports
		registerColumnType( Types.NUMERIC, "number($p,$s)" );
		registerColumnType( Types.DECIMAL, "number($p,$s)" );
	}

	protected void registerDateTimeTypeMappings() {
		if ( getVersion().isBefore( 9 ) ) {
			registerColumnType( Types.DATE, "date" );
			registerColumnType( Types.TIME, "date" );
			registerColumnType( Types.TIMESTAMP, "date" );
			registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "date" );
		}
		else {
			//the only difference between date and timestamp
			//on Oracle is that date has no fractional seconds
			registerColumnType( Types.DATE, "date" );
			registerColumnType( Types.TIME, "date" );
			registerColumnType( Types.TIMESTAMP, "timestamp($p)" );
			registerColumnType( Types.TIMESTAMP_WITH_TIMEZONE, "timestamp($p) with time zone" );
		}
	}

	@Override
	public TimeZoneSupport getTimeZoneSupport() {
		return getVersion().isSameOrAfter( 9 ) ? TimeZoneSupport.NATIVE : TimeZoneSupport.NONE;
	}

	protected void registerBinaryTypeMappings() {
		registerColumnType( Types.BINARY, getMaxVarbinaryLength(), "raw($l)" );
		registerColumnType( Types.BINARY, "blob" );

		registerColumnType( Types.VARBINARY, getMaxVarbinaryLength(), "raw($l)" );
		registerColumnType( Types.VARBINARY, "blob" );
	}

	protected void registerExtendedTypeMappings() {
		if ( getVersion().isSameOrAfter( 10 ) ) {
			registerColumnType( SqlTypes.GEOMETRY, "MDSYS.SDO_GEOMETRY" );
		}
	}

	protected void registerReverseHibernateTypeMappings() {
	}

	protected void registerDefaultProperties() {
		getDefaultProperties().setProperty( Environment.USE_STREAMS_FOR_BINARY, "true" );
		getDefaultProperties().setProperty( Environment.STATEMENT_BATCH_SIZE, DEFAULT_BATCH_SIZE );

		if ( getVersion().isBefore( 12 ) ) {
			// Oracle driver reports to support getGeneratedKeys(), but they only
			// support the version taking an array of the names of the columns to
			// be returned (via its RETURNING clause).  No other driver seems to
			// support this overloaded version.
			getDefaultProperties().setProperty( Environment.USE_GET_GENERATED_KEYS, "false" );
			getDefaultProperties().setProperty( Environment.BATCH_VERSIONED_DATA, "false" );
		}
		else {
			getDefaultProperties().setProperty( Environment.USE_GET_GENERATED_KEYS, "true" );
			getDefaultProperties().setProperty( Environment.BATCH_VERSIONED_DATA, "true" );
		}
	}

	@Override
	public JdbcType resolveSqlTypeDescriptor(
			String columnTypeName,
			int jdbcTypeCode,
			int precision,
			int scale,
			JdbcTypeRegistry jdbcTypeRegistry) {
		// This is the reverse of what registerNumericTypeMappings registers
		switch ( jdbcTypeCode ) {
			case Types.NUMERIC:
				// For some reason, the Oracle JDBC driver reports floats as numerics with scale -127
				if ( scale == -127 ) {
					if ( precision <= getFloatPrecision() ) {
						return jdbcTypeRegistry.getDescriptor( Types.FLOAT );
					}
					return jdbcTypeRegistry.getDescriptor( Types.DOUBLE );
				}
			case Types.DECIMAL:
				if ( scale == 0 ) {
					switch ( precision ) {
						case 1:
							return jdbcTypeRegistry.getDescriptor( Types.BOOLEAN );
						case 3:
							return jdbcTypeRegistry.getDescriptor( Types.TINYINT );
						case 5:
							return jdbcTypeRegistry.getDescriptor( Types.SMALLINT );
						case 10:
							return jdbcTypeRegistry.getDescriptor( Types.INTEGER );
						case 19:
							return jdbcTypeRegistry.getDescriptor( Types.BIGINT );
					}
				}
		}
		return super.resolveSqlTypeDescriptor(
				columnTypeName,
				jdbcTypeCode,
				precision,
				scale,
				jdbcTypeRegistry
		);
	}

	/**
	 * Oracle has neither {@code BIT} nor {@code BOOLEAN}.
	 *
	 * @return false
	 */
	@Override
	public boolean supportsBitType() {
		return false;
	}

	@Override
	public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
		super.contributeTypes( typeContributions, serviceRegistry );

		if ( getVersion().isSameOrAfter( 12 ) ) {
			// account for Oracle's deprecated support for LONGVARBINARY
			// prefer BLOB, unless the user explicitly opts out
			boolean preferLong = serviceRegistry.getService( ConfigurationService.class ).getSetting(
					PREFER_LONG_RAW,
					StandardConverters.BOOLEAN,
					false
			);

			BlobJdbcType descriptor = preferLong ?
					BlobJdbcType.PRIMITIVE_ARRAY_BINDING :
					BlobJdbcType.DEFAULT;

			typeContributions.contributeJdbcTypeDescriptor( descriptor );
		}

		// Oracle requires a custom binder for binding untyped nulls with the NULL type
		typeContributions.contributeJdbcTypeDescriptor( NullJdbcType.INSTANCE );
		typeContributions.contributeJdbcTypeDescriptor( ObjectNullAsNullTypeJdbcType.INSTANCE );

		// Until we remove StandardBasicTypes, we have to keep this
		typeContributions.contributeType(
				new NullType(
						NullJdbcType.INSTANCE,
						typeContributions.getTypeConfiguration()
								.getJavaTypeDescriptorRegistry()
								.getDescriptor( Object.class )
				)
		);
		typeContributions.contributeType(
				new JavaObjectType(
						ObjectNullAsNullTypeJdbcType.INSTANCE,
						typeContributions.getTypeConfiguration()
								.getJavaTypeDescriptorRegistry()
								.getDescriptor( Object.class )
						)
		);
	}

	@Override
	public String getNativeIdentifierGeneratorStrategy() {
		return "sequence";
	}

	// features which change between 8i, 9i, and 10g ~~~~~~~~~~~~~~~~~~~~~~~~~~

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return getVersion().isBefore( 12 )
				? super.getIdentityColumnSupport()
				: new Oracle12cIdentityColumnSupport();
	}

	@Override
	public LimitHandler getLimitHandler() {
		return limitHandler;
	}

	@Override
	public String getSelectClauseNullString(int sqlType) {
		if ( getVersion().isSameOrAfter( 9 ) ) {
			return super.getSelectClauseNullString(sqlType);
		}
		else {
			switch(sqlType) {
				case Types.VARCHAR:
				case Types.CHAR:
					return "to_char(null)";
				case Types.DATE:
				case Types.TIME:
				case Types.TIMESTAMP:
				case Types.TIMESTAMP_WITH_TIMEZONE:
					return "to_date(null)";
				default:
					return "to_number(null)";
			}
		}
	}

	@Override
	public String getCurrentTimestampSelectString() {
		return getVersion().isBefore( 9 )
				? "select sysdate from dual"
				: "select systimestamp from dual";
	}


	// features which remain constant across 8i, 9i, and 10g ~~~~~~~~~~~~~~~~~~

	@Override
	public String getAddColumnString() {
		return "add";
	}

	@Override
	public String getCascadeConstraintsString() {
		return " cascade constraints";
	}

	@Override
	public boolean dropConstraints() {
		return false;
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return OracleSequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		return "select * from all_sequences";
	}

	public SequenceInformationExtractor getSequenceInformationExtractor() {
		return SequenceInformationExtractorOracleDatabaseImpl.INSTANCE;
	}

	@Override
	public String getSelectGUIDString() {
		return "select rawtohex(sys_guid()) from dual";
	}

	@Override
	public ViolatedConstraintNameExtractor getViolatedConstraintNameExtractor() {
		return EXTRACTOR;
	}

	private static final ViolatedConstraintNameExtractor EXTRACTOR =
			new TemplatedViolatedConstraintNameExtractor( sqle -> {
				switch ( JdbcExceptionHelper.extractErrorCode( sqle ) ) {
					case 1:
					case 2291:
					case 2292:
						return extractUsingTemplate( "(", ")", sqle.getMessage() );
					case 1400:
						// simple nullability constraint
						return null;
					default:
						return null;
				}
			} );

	@Override
	public SQLExceptionConversionDelegate buildSQLExceptionConversionDelegate() {
		return (sqlException, message, sql) -> {
			// interpreting Oracle exceptions is much much more precise based on their specific vendor codes.
			switch ( JdbcExceptionHelper.extractErrorCode( sqlException ) ) {

				// lock timeouts ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

				case 30006:
					// ORA-30006: resource busy; acquire with WAIT timeout expired
					throw new LockTimeoutException(message, sqlException, sql);
				case 54:
					// ORA-00054: resource busy and acquire with NOWAIT specified or timeout expired
					throw new LockTimeoutException(message, sqlException, sql);
				case 4021:
					// ORA-04021 timeout occurred while waiting to lock object
					throw new LockTimeoutException(message, sqlException, sql);

				// deadlocks ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

				case 60:
					// ORA-00060: deadlock detected while waiting for resource
					return new LockAcquisitionException( message, sqlException, sql );
				case 4020:
					// ORA-04020 deadlock detected while trying to lock object
					return new LockAcquisitionException( message, sqlException, sql );

				// query cancelled ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

				case 1013:
					// ORA-01013: user requested cancel of current operation
					throw new QueryTimeoutException(  message, sqlException, sql );

				// data integrity violation ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

				case 1407:
					// ORA-01407: cannot update column to NULL
					final String constraintName = getViolatedConstraintNameExtractor().extractConstraintName( sqlException );
					return new ConstraintViolationException( message, sqlException, sql, constraintName );

				default:
					return null;
			}
		};
	}

	@Override
	public int registerResultSetOutParameter(CallableStatement statement, int col) throws SQLException {
		//	register the type of the out param - an Oracle specific type
		statement.registerOutParameter( col, OracleTypesHelper.INSTANCE.getOracleCursorTypeSqlType() );
		col++;
		return col;
	}

	@Override
	public ResultSet getResultSet(CallableStatement ps) throws SQLException {
		ps.execute();
		return (ResultSet) ps.getObject( 1 );
	}

	@Override
	public boolean supportsCommentOn() {
		return true;
	}

	@Override
	public boolean supportsCurrentTimestampSelection() {
		return true;
	}

	@Override
	public boolean isCurrentTimestampSelectStringCallable() {
		return false;
	}

	@Override
	public boolean supportsExistsInSelect() {
		return false;
	}

	@Override
	public int getInExpressionCountLimit() {
		return PARAM_LIST_SIZE_LIMIT;
	}

	@Override
	public boolean forceLobAsLastValue() {
		return true;
	}

	@Override
	public boolean isEmptyStringTreatedAsNull() {
		return true;
	}

	@Override
	public SqmMultiTableMutationStrategy getFallbackSqmMutationStrategy(
			EntityMappingType rootEntityDescriptor,
			RuntimeModelCreationContext runtimeModelCreationContext) {
		return new GlobalTemporaryTableMutationStrategy(
				TemporaryTable.createIdTable(
						rootEntityDescriptor,
						basename -> TemporaryTable.ID_TABLE_PREFIX + basename,
						this,
						runtimeModelCreationContext
				),
				runtimeModelCreationContext.getSessionFactory()
		);
	}

	@Override
	public SqmMultiTableInsertStrategy getFallbackSqmInsertStrategy(
			EntityMappingType rootEntityDescriptor,
			RuntimeModelCreationContext runtimeModelCreationContext) {
		return new GlobalTemporaryTableInsertStrategy(
				TemporaryTable.createEntityTable(
						rootEntityDescriptor,
						name -> TemporaryTable.ENTITY_TABLE_PREFIX + name,
						this,
						runtimeModelCreationContext
				),
				runtimeModelCreationContext.getSessionFactory()
		);
	}

	@Override
	public TemporaryTableKind getSupportedTemporaryTableKind() {
		return TemporaryTableKind.GLOBAL;
	}

	@Override
	public String getTemporaryTableCreateOptions() {
		return "on commit delete rows";
	}

	/**
	 * For Oracle, the FOR UPDATE clause cannot be applied when using ORDER BY, DISTINCT or views.
	 *
	 * @see <a href="https://docs.oracle.com/database/121/SQLRF/statements_10002.htm#SQLRF01702">Oracle FOR UPDATE restrictions</a>
	 */
	@Override
	public boolean useFollowOnLocking(String sql, QueryOptions queryOptions) {
		if ( StringHelper.isEmpty( sql ) || queryOptions == null ) {
			return true;
		}

		sql = sql.toLowerCase( Locale.ROOT );

		return DISTINCT_KEYWORD_PATTERN.matcher( sql ).find()
				|| GROUP_BY_KEYWORD_PATTERN.matcher( sql ).find()
				|| UNION_KEYWORD_PATTERN.matcher( sql ).find()
				|| (
						queryOptions.hasLimit()
								&& (
										ORDER_BY_KEYWORD_PATTERN.matcher( sql ).find()
												|| queryOptions.getLimit().getFirstRow() != null
								)
				);
	}

	@Override
	public String getQueryHintString(String sql, String hints) {
		String statementType = statementType(sql);

		final int pos = sql.indexOf( statementType );
		if ( pos > -1 ) {
			final StringBuilder buffer = new StringBuilder( sql.length() + hints.length() + 8 );
			if ( pos > 0 ) {
				buffer.append( sql, 0, pos );
			}
			buffer
			.append( statementType )
			.append( " /*+ " )
			.append( hints )
			.append( " */" )
			.append( sql.substring( pos + statementType.length() ) );
			sql = buffer.toString();
		}

		return sql;
	}

	@Override
	public int getMaxAliasLength() {
		// Max identifier length is 30, but Hibernate needs to add "uniqueing info" so we account for that
		return 20;
	}

	@Override
	public int getMaxIdentifierLength() {
		return 30;
	}

	@Override
	public CallableStatementSupport getCallableStatementSupport() {
		// Oracle supports returning cursors
		return StandardCallableStatementSupport.REF_CURSOR_INSTANCE;
	}

	@Override
	public boolean canCreateSchema() {
		return false;
	}

	@Override
	public String getCurrentSchemaCommand() {
		return "SELECT SYS_CONTEXT('USERENV','CURRENT_SCHEMA') FROM DUAL";
	}

	@Override
	public boolean supportsPartitionBy() {
		return true;
	}

	private String statementType(String sql) {
		Matcher matcher = SQL_STATEMENT_TYPE_PATTERN.matcher( sql );

		if ( matcher.matches() && matcher.groupCount() == 1 ) {
			return matcher.group(1);
		}

		throw new IllegalArgumentException( "Can't determine SQL statement type for statement: " + sql );
	}

	@Override
	public boolean supportsTupleDistinctCounts() {
		return false;
	}

	@Override
	public boolean supportsOffsetInSubquery() {
		return true;
	}

	@Override
	public boolean supportsFetchClause(FetchClauseType type) {
		// Until 12.2 there was a bug in the Oracle query rewriter causing ORA-00918
		// when the query contains duplicate implicit aliases in the select clause
		return getVersion().isSameOrAfter( 12, 2 );
	}

	@Override
	public boolean supportsWindowFunctions() {
		return true;
	}

	@Override
	public boolean supportsNoWait() {
		return getVersion().isSameOrAfter( 9 );
	}

	@Override
	public boolean supportsSkipLocked() {
		return getVersion().isSameOrAfter( 10 );
	}

	@Override
	public RowLockStrategy getWriteRowLockStrategy() {
		return RowLockStrategy.COLUMN;
	}

	@Override
	public String getForUpdateNowaitString() {
		return " for update nowait";
	}

	@Override
	public String getForUpdateString(String aliases) {
		return " for update of " + aliases;
	}

	@Override
	public String getForUpdateNowaitString(String aliases) {
		return " for update of " + aliases + " nowait";
	}

	@Override
	public String getForUpdateSkipLockedString() {
		return " for update skip locked";
	}

	@Override
	public String getForUpdateSkipLockedString(String aliases) {
		return " for update of " + aliases + " skip locked";
	}

	private String withTimeout(String lockString, int timeout) {
		switch (timeout) {
			case LockOptions.NO_WAIT:
				return supportsNoWait() ? lockString + " nowait" : lockString;
			case LockOptions.SKIP_LOCKED:
				return supportsSkipLocked() ? lockString + " skip locked" : lockString;
			case LockOptions.WAIT_FOREVER:
				return lockString;
			default:
				return supportsWait() ? lockString + " wait " + Math.round(timeout / 1e3f) : lockString;
		}
	}

	@Override
	public String getWriteLockString(int timeout) {
		return withTimeout( getForUpdateString(), timeout );
	}

	@Override
	public String getWriteLockString(String aliases, int timeout) {
		return withTimeout( getForUpdateString(aliases), timeout );
	}

	@Override
	public String getReadLockString(int timeout) {
		return getWriteLockString( timeout );
	}

	@Override
	public String getReadLockString(String aliases, int timeout) {
		return getWriteLockString( aliases, timeout );
	}

	@Override
	public void appendDatetimeFormat(SqlAppender appender, String format) {
		// Unlike other databases, Oracle requires an explicit reset for the fm modifier,
		// otherwise all following pattern variables trim zeros
		appender.appendSql( datetimeFormat( format, true, true ).result() );
	}

	public static Replacer datetimeFormat(String format, boolean useFm, boolean resetFm) {
		String fm = useFm ? "fm" : "";
		String fmReset = resetFm ? fm : "";
		return new Replacer( format, "'", "\"" )
				//era
				.replace("GG", "AD")
				.replace("G", "AD")

				//year
				.replace("yyyy", "YYYY")
				.replace("yyy", fm + "YYYY" + fmReset)
				.replace("yy", "YY")
				.replace("y", fm + "YYYY" + fmReset)

				//month of year
				.replace("MMMM", fm + "Month" + fmReset)
				.replace("MMM", "Mon")
				.replace("MM", "MM")
				.replace("M", fm + "MM" + fmReset)

				//week of year
				.replace("ww", "IW")
				.replace("w", fm + "IW" + fmReset)
				//year for week
				.replace("YYYY", "IYYY")
				.replace("YYY", fm + "IYYY" + fmReset)
				.replace("YY", "IY")
				.replace("Y", fm + "IYYY" + fmReset)

				//week of month
				.replace("W", "W")

				//day of week
				.replace("EEEE", fm + "Day" + fmReset)
				.replace("EEE", "Dy")
				.replace("ee", "D")
				.replace("e", fm + "D" + fmReset)

				//day of month
				.replace("dd", "DD")
				.replace("d", fm + "DD" + fmReset)

				//day of year
				.replace("DDD", "DDD")
				.replace("DD", fm + "DDD" + fmReset)
				.replace("D", fm + "DDD" + fmReset)

				//am pm
				.replace("aa", "AM")
				.replace("a", "AM")

				//hour
				.replace("hh", "HH12")
				.replace("HH", "HH24")
				.replace("h", fm + "HH12" + fmReset)
				.replace("H", fm + "HH24" + fmReset)

				//minute
				.replace("mm", "MI")
				.replace("m", fm + "MI" + fmReset)

				//second
				.replace("ss", "SS")
				.replace("s", fm + "SS" + fmReset)

				//fractional seconds
				.replace("SSSSSS", "FF6")
				.replace("SSSSS", "FF5")
				.replace("SSSS", "FF4")
				.replace("SSS", "FF3")
				.replace("SS", "FF2")
				.replace("S", "FF1")

				//timezones
				.replace("zzz", "TZR")
				.replace("zz", "TZR")
				.replace("z", "TZR")
				.replace("ZZZ", "TZHTZM")
				.replace("ZZ", "TZHTZM")
				.replace("Z", "TZHTZM")
				.replace("xxx", "TZH:TZM")
				.replace("xx", "TZHTZM")
				.replace("x", "TZH"); //note special case
	}

	@Override
	public void appendBinaryLiteral(SqlAppender appender, byte[] bytes) {
		appender.appendSql( "hextoraw('" );
		PrimitiveByteArrayJavaTypeDescriptor.INSTANCE.appendString( appender, bytes );
		appender.appendSql( "')" );
	}

	@Override
	public ResultSet getResultSet(CallableStatement statement, int position) throws SQLException {
		return (ResultSet) statement.getObject( position );
	}

	@Override
	public int registerResultSetOutParameter(CallableStatement statement, String name) throws SQLException {
		statement.registerOutParameter( name, OracleTypesHelper.INSTANCE.getOracleCursorTypeSqlType() );
		return 1;
	}

	@Override
	public ResultSet getResultSet(CallableStatement statement, String name) throws SQLException {
		return (ResultSet) statement.getObject( name );
	}

}
