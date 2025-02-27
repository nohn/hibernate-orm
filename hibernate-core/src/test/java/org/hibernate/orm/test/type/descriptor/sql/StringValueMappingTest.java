/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.type.descriptor.sql;

import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TimeZone;

import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.NonContextualLobCreator;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.StringJavaTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.ClobJdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import org.hibernate.testing.orm.junit.BaseUnitTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Steve Ebersole
 */
@BaseUnitTest
public class StringValueMappingTest {
	private final StringJavaTypeDescriptor stringJavaDescriptor = new StringJavaTypeDescriptor();

	private final VarcharJdbcType varcharSqlDescriptor = new VarcharJdbcType();
	private final ClobJdbcType clobSqlDescriptor = ClobJdbcType.DEFAULT;

	private final WrapperOptions wrapperOptions = new WrapperOptions() {
		@Override
		public SharedSessionContractImplementor getSession() {
			return null;
		}

		@Override
		public SessionFactoryImplementor getSessionFactory() {
			return null;
		}

		public boolean useStreamForLobBinding() {
			return false;
		}

		@Override
		public int getPreferredSqlTypeCodeForBoolean() {
			return 0;
		}

		public LobCreator getLobCreator() {
			return NonContextualLobCreator.INSTANCE;
		}

		public JdbcType remapSqlTypeDescriptor(JdbcType sqlTypeDescriptor) {
			return sqlTypeDescriptor;
		}

		@Override
		public TimeZone getJdbcTimeZone() {
			return null;
		}
	};

	public static final int COLUMN_POSITION = 0;
	public static final int BIND_POSITION = -1;

	@Test
	public void testNormalVarcharHandling() throws SQLException {
		final ValueExtractor<String> extractor = varcharSqlDescriptor.getExtractor( stringJavaDescriptor );
		final ValueBinder<String> binder = varcharSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = "string value";

		ResultSet resultSet = ResultSetProxy.generateProxy( fixture );
		final String value = extractor.extract( resultSet, COLUMN_POSITION, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( fixture );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	@Test
	public void testNullVarcharHandling() throws SQLException {
		final ValueExtractor<String> extractor = varcharSqlDescriptor.getExtractor( stringJavaDescriptor );
		final ValueBinder<String> binder = varcharSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = null;

		ResultSet resultSet = ResultSetProxy.generateProxy( fixture );
		final String value = extractor.extract( resultSet, COLUMN_POSITION, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( fixture );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	@Test
	public void testNormalClobHandling() throws SQLException {
		final ValueExtractor<String> extractor = clobSqlDescriptor.getExtractor( stringJavaDescriptor );
		final ValueBinder<String> binder = clobSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = "clob string";
		final Clob clob = new StringClobImpl( fixture );

		ResultSet resultSet = ResultSetProxy.generateProxy( clob );
		final String value = extractor.extract( resultSet, COLUMN_POSITION, wrapperOptions );
		assertEquals( fixture, value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( clob );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}

	@Test
	public void testNullClobHandling() throws SQLException {
		final ValueExtractor<String> extractor = clobSqlDescriptor.getExtractor( stringJavaDescriptor );
		final ValueBinder<String> binder = clobSqlDescriptor.getBinder( stringJavaDescriptor );

		final String fixture = null;
		final Clob clob = null;

		ResultSet resultSet = ResultSetProxy.generateProxy( clob );
		final String value = extractor.extract( resultSet, COLUMN_POSITION, wrapperOptions );
		assertNull( value );

		PreparedStatement ps = PreparedStatementProxy.generateProxy( clob );
		binder.bind( ps, fixture, BIND_POSITION, wrapperOptions );
	}
}
