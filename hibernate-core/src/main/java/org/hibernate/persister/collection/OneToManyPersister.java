/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.persister.collection;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Set;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.batch.internal.BasicBatchKey;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.FilterAliasGenerator;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.jdbc.Expectation;
import org.hibernate.jdbc.Expectations;
import org.hibernate.mapping.Collection;
import org.hibernate.persister.entity.Joinable;
import org.hibernate.persister.entity.OuterJoinLoadable;
import org.hibernate.persister.spi.PersisterCreationContext;
import org.hibernate.pretty.MessageHelper;
import org.hibernate.sql.Update;
import org.hibernate.sql.ast.tree.from.TableGroup;

/**
 * Collection persister for one-to-many associations.
 *
 * @author Gavin King
 * @author Brett Meyer
 */
public class OneToManyPersister extends AbstractCollectionPersister {

	private final boolean cascadeDeleteEnabled;
	private final boolean keyIsNullable;
	private final boolean keyIsUpdateable;

	@Override
	protected boolean isRowDeleteEnabled() {
		return keyIsUpdateable && keyIsNullable;
	}

	@Override
	protected boolean isRowInsertEnabled() {
		return keyIsUpdateable;
	}

	public boolean isCascadeDeleteEnabled() {
		return cascadeDeleteEnabled;
	}

	public OneToManyPersister(
			Collection collectionBinding,
			CollectionDataAccess cacheAccessStrategy,
			PersisterCreationContext creationContext) throws MappingException, CacheException {
		super( collectionBinding, cacheAccessStrategy, creationContext );
		cascadeDeleteEnabled = collectionBinding.getKey().isCascadeDeleteEnabled()
				&& creationContext.getSessionFactory().getDialect().supportsCascadeDelete();
		keyIsNullable = collectionBinding.getKey().isNullable();
		keyIsUpdateable = collectionBinding.getKey().isUpdateable();
	}

	/**
	 * Generate the SQL UPDATE that updates all the foreign keys to null
	 */
	@Override
	protected String generateDeleteString() {
		final Update update = createUpdate().setTableName( qualifiedTableName )
				.addColumns( keyColumnNames, "null" );

		if ( hasIndex && !indexContainsFormula ) {
			for ( int i = 0 ; i < indexColumnNames.length ; i++ ) {
				if ( indexColumnIsSettable[i] ) {
					update.addColumn( indexColumnNames[i], "null" );
				}
			}
		}

		update.addPrimaryKeyColumns( keyColumnNames );

		if ( hasWhere ) {
			update.setWhere( sqlWhereString );
		}

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			update.setComment( "delete one-to-many " + getRole() );
		}

		return update.toStatementString();
	}

	/**
	 * Generate the SQL UPDATE that updates a foreign key to a value
	 */
	@Override
	protected String generateInsertRowString() {
		final Update update = createUpdate().setTableName( qualifiedTableName )
				.addColumns( keyColumnNames );

		if ( hasIndex && !indexContainsFormula ) {
			for ( int i = 0 ; i < indexColumnNames.length ; i++ ) {
				if ( indexColumnIsSettable[i] ) {
					update.addColumn( indexColumnNames[i] );
				}
			}
		}

		//identifier collections not supported for 1-to-many

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			update.setComment( "create one-to-many row " + getRole() );
		}

		return update.addPrimaryKeyColumns( elementColumnNames, elementColumnWriters )
				.toStatementString();
	}

	/**
	 * Generate the SQL UPDATE that inserts a collection index
	 */
	@Override
	protected String generateUpdateRowString() {
		final Update update = createUpdate().setTableName( qualifiedTableName );

		if ( hasIndex && !indexContainsFormula ) {
			for ( int i = 0 ; i < indexColumnNames.length ; i++ ) {
				if ( indexColumnIsSettable[i] ) {
					update.addColumn( indexColumnNames[i] );
				}
			}
		}

		update.addPrimaryKeyColumns( elementColumnNames, elementColumnIsSettable, elementColumnWriters );

		if ( hasIdentifier ) {
			update.addPrimaryKeyColumns( new String[] {identifierColumnName} );
		}

		return update.toStatementString();
	}

	/**
	 * Generate the SQL UPDATE that updates a particular row's foreign
	 * key to null
	 */
	@Override
	protected String generateDeleteRowString() {
		final Update update = createUpdate().setTableName( qualifiedTableName )
				.addColumns( keyColumnNames, "null" );

		if ( hasIndex && !indexContainsFormula ) {
			for ( int i = 0 ; i < indexColumnNames.length ; i++ ) {
				if ( indexColumnIsSettable[i] ) {
					update.addColumn( indexColumnNames[i], "null" );
				}
			}
		}

		if ( getFactory().getSessionFactoryOptions().isCommentsEnabled() ) {
			update.setComment( "delete one-to-many row " + getRole() );
		}

		//use a combination of foreign key columns and pk columns, since
		//the ordering of removal and addition is not guaranteed when
		//a child moves from one parent to another
		String[] rowSelectColumnNames = ArrayHelper.join( keyColumnNames, elementColumnNames );
		return update.addPrimaryKeyColumns( rowSelectColumnNames )
				.toStatementString();
	}

	@Override
	public void recreate(PersistentCollection collection, Object id, SharedSessionContractImplementor session)
			throws HibernateException {
		super.recreate( collection, id, session );
		writeIndex( collection, collection.entries( this ), id, true, session );
	}

	@Override
	public void insertRows(PersistentCollection collection, Object id, SharedSessionContractImplementor session)
			throws HibernateException {
		super.insertRows( collection, id, session );
		writeIndex( collection, collection.entries( this ), id, true, session );
	}

	@Override
	protected void doProcessQueuedOps(PersistentCollection collection, Object id, SharedSessionContractImplementor session)
			throws HibernateException {
		writeIndex( collection, collection.queuedAdditionIterator(), id, false, session );
	}

	private void writeIndex(
			PersistentCollection collection,
			Iterator entries,
			Object id,
			boolean resetIndex,
			SharedSessionContractImplementor session) {
		// If one-to-many and inverse, still need to create the index.  See HHH-5732.
		if ( isInverse && hasIndex && !indexContainsFormula && ArrayHelper.countTrue( indexColumnIsSettable ) > 0 ) {
			try {
				if ( entries.hasNext() ) {
					int nextIndex = resetIndex ? 0 : getSize( id, session );
					Expectation expectation = Expectations.appropriateExpectation( getUpdateCheckStyle() );
					while ( entries.hasNext() ) {

						final Object entry = entries.next();
						if ( entry != null && collection.entryExists( entry, nextIndex ) ) {
							int offset = 1;
							PreparedStatement st = null;
							boolean callable = isUpdateCallable();
							boolean useBatch = expectation.canBeBatched();
							String sql = getSQLUpdateRowString();

							if ( useBatch ) {
								if ( recreateBatchKey == null ) {
									recreateBatchKey = new BasicBatchKey(
											getRole() + "#RECREATE",
											expectation
									);
								}
								st = session
										.getJdbcCoordinator()
										.getBatch( recreateBatchKey )
										.getBatchStatement( sql, callable );
							}
							else {
								st = session
										.getJdbcCoordinator()
										.getStatementPreparer()
										.prepareStatement( sql, callable );
							}

							try {
								offset += expectation.prepare( st );
								if ( hasIdentifier ) {
									offset = writeIdentifier(
											st,
											collection.getIdentifier( entry, nextIndex ),
											offset,
											session
									);
								}
								offset = writeIndex(
										st,
										collection.getIndex( entry, nextIndex, this ),
										offset,
										session
								);
								offset = writeElement( st, collection.getElement( entry ), offset, session );

								if ( useBatch ) {
									session.getJdbcCoordinator()
											.getBatch( recreateBatchKey )
											.addToBatch();
								}
								else {
									expectation.verifyOutcome(
											session.getJdbcCoordinator()
													.getResultSetReturn()
													.executeUpdate( st ), st, -1, sql
									);
								}
							}
							catch (SQLException sqle) {
								if ( useBatch ) {
									session.getJdbcCoordinator().abortBatch();
								}
								throw sqle;
							}
							finally {
								if ( !useBatch ) {
									session.getJdbcCoordinator().getResourceRegistry().release( st );
									session.getJdbcCoordinator().afterStatementExecution();
								}
							}

						}
						nextIndex++;
					}
				}
			}
			catch (SQLException sqle) {
				throw sqlExceptionHelper.convert(
						sqle,
						"could not update collection: " +
								MessageHelper.collectionInfoString( this, collection, id, session ),
						getSQLUpdateRowString()
				);
			}
		}
	}

	public boolean consumesEntityAlias() {
		return true;
	}

	public boolean consumesCollectionAlias() {
		return true;
	}

	public boolean isOneToMany() {
		return true;
	}

	@Override
	public boolean isManyToMany() {
		return false;
	}

	private BasicBatchKey deleteRowBatchKey;
	private BasicBatchKey insertRowBatchKey;

	@Override
	protected int doUpdateRows(Object id, PersistentCollection collection, SharedSessionContractImplementor session) {

		// we finish all the "removes" first to take care of possible unique
		// constraints and so that we can take better advantage of batching

		try {
			int count = 0;
			if ( isRowDeleteEnabled() ) {
				final Expectation deleteExpectation = Expectations.appropriateExpectation( getDeleteCheckStyle() );
				final boolean useBatch = deleteExpectation.canBeBatched();
				if ( useBatch && deleteRowBatchKey == null ) {
					deleteRowBatchKey = new BasicBatchKey(
							getRole() + "#DELETEROW",
							deleteExpectation
					);
				}
				final String sql = getSQLDeleteRowString();

				PreparedStatement st = null;
				// update removed rows fks to null
				try {
					int i = 0;
					Iterator entries = collection.entries( this );
					int offset = 1;
					while ( entries.hasNext() ) {
						Object entry = entries.next();
						if ( collection.needsUpdating(
								entry,
								i,
								elementType
						) ) {  // will still be issued when it used to be null
							if ( useBatch ) {
								st = session
										.getJdbcCoordinator()
										.getBatch( deleteRowBatchKey )
										.getBatchStatement( sql, isDeleteCallable() );
							}
							else {
								st = session
										.getJdbcCoordinator()
										.getStatementPreparer()
										.prepareStatement( sql, isDeleteCallable() );
							}
							int loc = writeKey( st, id, offset, session );
							writeElementToWhere( st, collection.getSnapshotElement( entry, i ), loc, session );
							if ( useBatch ) {
								session
										.getJdbcCoordinator()
										.getBatch( deleteRowBatchKey )
										.addToBatch();
							}
							else {
								deleteExpectation.verifyOutcome(
										session.getJdbcCoordinator()
												.getResultSetReturn()
												.executeUpdate( st ), st, -1, sql
								);
							}
							count++;
						}
						i++;
					}
				}
				catch (SQLException e) {
					if ( useBatch ) {
						session.getJdbcCoordinator().abortBatch();
					}
					throw e;
				}
				finally {
					if ( !useBatch ) {
						session.getJdbcCoordinator().getResourceRegistry().release( st );
						session.getJdbcCoordinator().afterStatementExecution();
					}
				}
			}

			if ( isRowInsertEnabled() ) {
				final Expectation insertExpectation = Expectations.appropriateExpectation( getInsertCheckStyle() );
				boolean useBatch = insertExpectation.canBeBatched();
				boolean callable = isInsertCallable();
				if ( useBatch && insertRowBatchKey == null ) {
					insertRowBatchKey = new BasicBatchKey(
							getRole() + "#INSERTROW",
							insertExpectation
					);
				}
				final String sql = getSQLInsertRowString();

				PreparedStatement st = null;
				// now update all changed or added rows fks
				try {
					int i = 0;
					Iterator entries = collection.entries( this );
					while ( entries.hasNext() ) {
						Object entry = entries.next();
						int offset = 1;
						if ( collection.needsUpdating( entry, i, elementType ) ) {
							if ( useBatch ) {
								st = session
										.getJdbcCoordinator()
										.getBatch( insertRowBatchKey )
										.getBatchStatement( sql, callable );
							}
							else {
								st = session
										.getJdbcCoordinator()
										.getStatementPreparer()
										.prepareStatement( sql, callable );
							}

							offset += insertExpectation.prepare( st );

							int loc = writeKey( st, id, offset, session );
							if ( hasIndex && !indexContainsFormula ) {
								loc = writeIndexToWhere( st, collection.getIndex( entry, i, this ), loc, session );
							}

							writeElementToWhere( st, collection.getElement( entry ), loc, session );

							if ( useBatch ) {
								session.getJdbcCoordinator().getBatch( insertRowBatchKey ).addToBatch();
							}
							else {
								insertExpectation.verifyOutcome(
										session.getJdbcCoordinator()
												.getResultSetReturn()
												.executeUpdate( st ), st, -1, sql
								);
							}
							count++;
						}
						i++;
					}
				}
				catch (SQLException sqle) {
					if ( useBatch ) {
						session.getJdbcCoordinator().abortBatch();
					}
					throw sqle;
				}
				finally {
					if ( !useBatch ) {
						session.getJdbcCoordinator().getResourceRegistry().release( st );
						session.getJdbcCoordinator().afterStatementExecution();
					}
				}
			}

			return count;
		}
		catch (SQLException sqle) {
			throw getFactory().getSQLExceptionHelper().convert(
					sqle,
					"could not update collection rows: " +
							MessageHelper.collectionInfoString( this, collection, id, session ),
					getSQLInsertRowString()
			);
		}
	}

	@Override
	public String getTableName() {
		return ( (Joinable) getElementPersister() ).getTableName();
	}

	@Override
	public String filterFragment(String alias) throws MappingException {
		String result = super.filterFragment( alias );
		if ( getElementPersister() instanceof Joinable ) {
			result += ( (Joinable) getElementPersister() ).oneToManyFilterFragment( alias );
		}
		return result;

	}

	@Override
	protected String filterFragment(String alias, Set<String> treatAsDeclarations) throws MappingException {
		String result = super.filterFragment( alias );
		if ( getElementPersister() instanceof Joinable ) {
			result += ( (Joinable) getElementPersister() ).oneToManyFilterFragment( alias, treatAsDeclarations );
		}
		return result;
	}

	@Override
	public FilterAliasGenerator getFilterAliasGenerator(String rootAlias) {
		return getElementPersister().getFilterAliasGenerator( rootAlias );
	}

	@Override
	public FilterAliasGenerator getFilterAliasGenerator(TableGroup rootTableGroup) {
		return getElementPersister().getFilterAliasGenerator( rootTableGroup );
	}
}
