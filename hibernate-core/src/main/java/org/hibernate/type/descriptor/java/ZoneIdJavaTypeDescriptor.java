/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.type.descriptor.java;

import java.sql.Types;
import java.time.ZoneId;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.JdbcTypeDescriptorIndicators;

/**
 * Describes the {@link ZoneId} Java type
 */
public class ZoneIdJavaTypeDescriptor extends AbstractClassJavaTypeDescriptor<ZoneId> {
	/**
	 * Singleton access
	 */
	public static final ZoneIdJavaTypeDescriptor INSTANCE = new ZoneIdJavaTypeDescriptor();

	public ZoneIdJavaTypeDescriptor() {
		super( ZoneId.class );
	}

	@Override
	public JdbcType getRecommendedJdbcType(JdbcTypeDescriptorIndicators indicators) {
		return indicators.getTypeConfiguration().getJdbcTypeDescriptorRegistry().getDescriptor( Types.VARCHAR );
	}

	@Override
	public String toString(ZoneId value) {
		return value == null ? null : value.getId();
	}

	@Override
	public ZoneId fromString(CharSequence string) {
		return string == null ? null : ZoneId.of( string.toString() );
	}

	@SuppressWarnings("unchecked")
	@Override
	public <X> X unwrap(ZoneId value, Class<X> type, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if ( String.class.isAssignableFrom( type ) ) {
			return (X) toString( value );
		}

		throw unknownUnwrap( type );
	}

	@Override
	public <X> ZoneId wrap(X value, WrapperOptions options) {
		if ( value == null ) {
			return null;
		}

		if ( value instanceof String ) {
			return fromString( (String) value );
		}

		throw unknownWrap( value.getClass() );
	}
}
