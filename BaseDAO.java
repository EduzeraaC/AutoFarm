package gameserver.custom.dao;

import commons.logging.CLogger;
import commons.pool.ConnectionPool;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class BaseDAO {

    private static final CLogger LOGGER = new CLogger(BaseDAO.class.getName());

    @FunctionalInterface
    protected interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }

    @FunctionalInterface
    protected interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    @FunctionalInterface
    protected interface SQLResultFunction<R> {
        R apply(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    protected interface SQLConsumerWithSQL<T> {
        void accept(T t) throws SQLException;
        String sql = null;
    }

    protected void execute(String sql, SQLConsumer<PreparedStatement> consumer) {
        try (Connection con = ConnectionPool.getConnection();
             final PreparedStatement ps = con.prepareStatement(sql)) {
            consumer.accept(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Database execute error for SQL:" + sql, e);
        }
    }

    protected  <T> T query(String sql, SQLFunction<PreparedStatement, ResultSet> executor, SQLResultFunction<T> mapper) {
        try (Connection con = ConnectionPool.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = executor.apply(ps)) {

            return mapper.apply(rs);

        } catch (SQLException e) {
            LOGGER.info("Database query error for SQL: " + sql, e);
            return null;
        }
    }

    protected void executeBatch(String sql, SQLConsumer<PreparedStatement> consumer) {
        try (Connection con = ConnectionPool.getConnection();
             final PreparedStatement ps = con.prepareStatement(sql)) {

            final boolean previousAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            try {
                consumer.accept(ps);
                ps.executeBatch();
                con.commit();

            } catch (SQLException e) {
                con.rollback();
                throw new DataAccessException("Database execute error for SQL:" + sql, e);

            } finally {
                con.setAutoCommit(previousAutoCommit);
            }

        } catch (SQLException e) {
            throw new DataAccessException("Failed to prepare batch: " + sql, e);
        }
    }

    protected void executeAllInTransaction(SQLConsumerPrepared... consumers) {
        try (Connection con = ConnectionPool.getConnection()) {
            final boolean previousAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);

            try {
                for (SQLConsumerPrepared consumerPrepared : consumers) {
                    try (PreparedStatement ps = con.prepareStatement(consumerPrepared.sql)) {
                        consumerPrepared.consumer.accept(ps);

                        if (consumerPrepared.batch) {
                            ps.executeBatch();
                        } else {
                            ps.executeUpdate();
                        }
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw new DataAccessException("Transaction failed", e);
            } finally {
                con.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get connection", e);
        }
    }

    protected static class DataAccessException extends RuntimeException {
        public DataAccessException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    protected record ConnectionPreparedStatementPair(Connection con, PreparedStatement ps) {
    }

    protected record SQLConsumerPrepared(SQLConsumer<PreparedStatement> consumer, String sql, boolean batch) implements SQLConsumerWithSQL<ConnectionPreparedStatementPair> {

        @Override
            public void accept(ConnectionPreparedStatementPair pair) throws SQLException {
                consumer.accept(pair.ps);
            }
        }
}
