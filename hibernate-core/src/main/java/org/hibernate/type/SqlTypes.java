/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.type;

import java.sql.Types;

/**
 * The class that defines the constants that are used to identify generic
 * SQL types. This is an extension of {@link Types} that provides type codes
 * for types that Hibernate supports in addition to the standard JDBC types.
 *
 * @author Christian Beikov
 */
public class SqlTypes {
	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code BIT}.
	 *
	 * @see Types#BIT
	 */
	public final static int BIT = Types.BIT;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code TINYINT}.
	 *
	 * @see Types#TINYINT
	 */
	public final static int TINYINT = Types.TINYINT;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code SMALLINT}.
	 *
	 * @see Types#SMALLINT
	 */
	public final static int SMALLINT = Types.SMALLINT;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code INTEGER}.
	 *
	 * @see Types#INTEGER
	 */
	public final static int INTEGER = Types.INTEGER;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code BIGINT}.
	 *
	 * @see Types#BIGINT
	 */
	public final static int BIGINT = Types.BIGINT;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code FLOAT}.
	 *
	 * @see Types#FLOAT
	 */
	public final static int FLOAT = Types.FLOAT;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code REAL}.
	 *
	 * @see Types#REAL
	 */
	public final static int REAL = Types.REAL;


	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code DOUBLE}.
	 *
	 * @see Types#DOUBLE
	 */
	public final static int DOUBLE = Types.DOUBLE;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code NUMERIC}.
	 *
	 * @see Types#NUMERIC
	 */
	public final static int NUMERIC = Types.NUMERIC;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code DECIMAL}.
	 *
	 * @see Types#DECIMAL
	 */
	public final static int DECIMAL = Types.DECIMAL;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code CHAR}.
	 *
	 * @see Types#CHAR
	 */
	public final static int CHAR = Types.CHAR;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code VARCHAR}.
	 *
	 * @see Types#VARCHAR
	 */
	public final static int VARCHAR = Types.VARCHAR;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code LONGVARCHAR}.
	 *
	 * @see Types#LONGVARCHAR
	 */
	public final static int LONGVARCHAR = Types.LONGVARCHAR;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code DATE}.
	 *
	 * @see Types#DATE
	 */
	public final static int DATE = Types.DATE;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code TIME}.
	 *
	 * @see Types#TIME
	 */
	public final static int TIME = Types.TIME;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code TIMESTAMP}.
	 *
	 * @see Types#TIMESTAMP
	 */
	public final static int TIMESTAMP = Types.TIMESTAMP;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code BINARY}.
	 *
	 * @see Types#BINARY
	 */
	public final static int BINARY = Types.BINARY;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code VARBINARY}.
	 *
	 * @see Types#VARBINARY
	 */
	public final static int VARBINARY = Types.VARBINARY;

	/**
	 * <P>The constant in the Java programming language, sometimes referred
	 * to as a type code, that identifies the generic SQL type
	 * {@code LONGVARBINARY}.
	 *
	 * @see Types#LONGVARBINARY
	 */
	public final static int LONGVARBINARY = Types.LONGVARBINARY;

	/**
	 * <P>The constant in the Java programming language
	 * that identifies the generic SQL value
	 * {@code NULL}.
	 *
	 * @see Types#NULL
	 */
	public final static int NULL = Types.NULL;

	/**
	 * The constant in the Java programming language that indicates
	 * that the SQL type is database-specific and
	 * gets mapped to a Java object that can be accessed via
	 * the methods {@code getObject} and {@code setObject}.
	 *
	 * @see Types#OTHER
	 */
	public final static int OTHER = Types.OTHER;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code JAVA_OBJECT}.
	 *
	 * @see Types#JAVA_OBJECT
	 */
	public final static int JAVA_OBJECT = Types.JAVA_OBJECT;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code DISTINCT}.
	 *
	 * @see Types#DISTINCT
	 */
	public final static int DISTINCT = Types.DISTINCT;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code STRUCT}.
	 *
	 * @see Types#STRUCT
	 */
	public final static int STRUCT = Types.STRUCT;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code ARRAY}.
	 *
	 * @see Types#ARRAY
	 */
	public final static int ARRAY = Types.ARRAY;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code BLOB}.
	 *
	 * @see Types#ARRAY
	 */
	public final static int BLOB = Types.BLOB;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code CLOB}.
	 *
	 * @see Types#CLOB
	 */
	public final static int CLOB = Types.CLOB;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code REF}.
	 *
	 * @see Types#REF
	 */
	public final static int REF = Types.REF;

	/**
	 * The constant in the Java programming language, somtimes referred to
	 * as a type code, that identifies the generic SQL type {@code DATALINK}.
	 *
	 * @see Types#DATALINK
	 */
	public final static int DATALINK = Types.DATALINK;

	/**
	 * The constant in the Java programming language, somtimes referred to
	 * as a type code, that identifies the generic SQL type {@code BOOLEAN}.
	 *
	 * @see Types#BOOLEAN
	 */
	public final static int BOOLEAN = Types.BOOLEAN;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code ROWID}
	 *
	 * @see Types#ROWID
	 */
	public final static int ROWID = Types.ROWID;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code NCHAR}
	 *
	 * @see Types#NCHAR
	 */
	public static final int NCHAR = Types.NCHAR;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code NVARCHAR}.
	 *
	 * @see Types#NVARCHAR
	 */
	public static final int NVARCHAR = Types.NVARCHAR;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code LONGNVARCHAR}.
	 *
	 * @see Types#LONGNVARCHAR
	 */
	public static final int LONGNVARCHAR = Types.LONGNVARCHAR;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code NCLOB}.
	 *
	 * @see Types#NCLOB
	 */
	public static final int NCLOB = Types.NCLOB;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code XML}.
	 *
	 * @see Types#SQLXML
	 */
	public static final int SQLXML = Types.SQLXML;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type {@code REF CURSOR}.
	 *
	 * @see Types#REF_CURSOR
	 */
	public static final int REF_CURSOR = Types.REF_CURSOR;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code TIME WITH TIMEZONE}.
	 *
	 * @see Types#TIME_WITH_TIMEZONE
	 */
	public static final int TIME_WITH_TIMEZONE = Types.TIME_WITH_TIMEZONE;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code TIMESTAMP WITH TIMEZONE}.
	 *
	 * @see Types#TIMESTAMP_WITH_TIMEZONE
	 */
	public static final int TIMESTAMP_WITH_TIMEZONE = Types.TIMESTAMP_WITH_TIMEZONE;

	// Misc types

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code UUID}.
	 */
	public static final int UUID = 3000;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code JSON}.
	 */
	public static final int JSON = 3001;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code INET} for IPv4 or IPv6 addresses.
	 */
	public static final int INET = 3002;

	// Interval types

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code INTERVAL SECOND} for a temporal amount in terms of seconds and fractional seconds.
	 */
	public static final int INTERVAL_SECOND = 3100;

	// Geometry types

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code GEOMETRY}.
	 */
	public static final int GEOMETRY = 3200;

	/**
	 * The constant in the Java programming language, sometimes referred to
	 * as a type code, that identifies the generic SQL type
	 * {@code POINT}.
	 */
	public static final int POINT = 3201;

	private SqlTypes() {
	}
}
