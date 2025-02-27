/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.dialect;

import java.sql.DatabaseMetaData;
import java.time.Duration;

import org.hibernate.LockOptions;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.dialect.sequence.TiDBSequenceSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorTiDBDatabaseImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;

/**
 * An SQL dialect for TiDB (prior to 5.x).
 *
 * @author Cong Wang
 */
public class TiDBDialect extends MySQLDialect {

	private final int tidbVersion;

	public TiDBDialect() {
		// Let's be conservative and assume people use a 4 byte character set
		this( 500, 4 );
	}

	public TiDBDialect(DialectResolutionInfo info) {
		this(
				info.getDatabaseMajorVersion() * 100 + info.getDatabaseMinorVersion() * 10,
				getCharacterSetBytesPerCharacter( info.unwrap( DatabaseMetaData.class ) )
		);
		registerKeywords( info );
	}

	public TiDBDialect(int version, int characterSetBytesPerCharacter) {
		// For simplicity’s sake, configure MySQL 5.7 compatibility
		super( DatabaseVersion.make( 5, 7 ), characterSetBytesPerCharacter );
		this.tidbVersion = version;
		registerKeywords();
	}

	public int getTidbVersion() {
		return tidbVersion;
	}

	private void registerKeywords() {
		// TiDB implemented 'Window Functions' of MySQL 8, so the following keywords are reserved.
		registerKeyword("CUME_DIST");
		registerKeyword("DENSE_RANK");
		registerKeyword("EXCEPT");
		registerKeyword("FIRST_VALUE");
		registerKeyword("GROUPS");
		registerKeyword("LAG");
		registerKeyword("LAST_VALUE");
		registerKeyword("LEAD");
		registerKeyword("NTH_VALUE");
		registerKeyword("NTILE");
		registerKeyword("PERCENT_RANK");
		registerKeyword("RANK");
		registerKeyword("ROW_NUMBER");
	}

	@Override
	public boolean supportsCascadeDelete() {
		return false;
	}

	@Override
	public String getQuerySequencesString() {
		return "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = database()";
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return TiDBSequenceSupport.INSTANCE;
	}

	@Override
	public SequenceInformationExtractor getSequenceInformationExtractor() {
		return SequenceInformationExtractorTiDBDatabaseImpl.INSTANCE;
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new TiDBSqlAstTranslator<>( sessionFactory, statement );
			}
		};
	}

	@Override
	public boolean supportsNoWait() {
		return true;
	}

	@Override
	public boolean supportsWait() {
		return true;
	}

	@Override
	public String getReadLockString(int timeout) {
		if ( timeout == LockOptions.NO_WAIT ) {
			return getForUpdateNowaitString();
		}
		return super.getReadLockString( timeout );
	}

	@Override
	public String getReadLockString(String aliases, int timeout) {
		if ( timeout == LockOptions.NO_WAIT ) {
			return getForUpdateNowaitString( aliases );
		}
		return super.getReadLockString( aliases, timeout );
	}

	@Override
	public String getWriteLockString(int timeout) {
		if ( timeout == LockOptions.NO_WAIT ) {
			return getForUpdateNowaitString();
		}

		if ( timeout > 0 ) {
			return getForUpdateString() + " wait " + getLockWaitTimeoutInSeconds( timeout );
		}

		return getForUpdateString();
	}

	@Override
	public String getForUpdateNowaitString() {
		return getForUpdateString() + " nowait";
	}

	@Override
	public String getForUpdateNowaitString(String aliases) {
		return getForUpdateString( aliases ) + " nowait";
	}

	private static long getLockWaitTimeoutInSeconds(int timeoutInMilliseconds) {
		Duration duration = Duration.ofMillis( timeoutInMilliseconds );
		return duration.getSeconds();
	}

}
