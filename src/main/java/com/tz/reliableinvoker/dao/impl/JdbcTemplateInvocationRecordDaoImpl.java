package com.tz.reliableinvoker.dao.impl;

import com.tz.reliableinvoker.config.ReliableInvokerProperties;
import com.tz.reliableinvoker.dao.IInvocationRecordDao;
import com.tz.reliableinvoker.model.BackupQueryRequest;
import com.tz.reliableinvoker.model.InvocationRecord;
import com.tz.reliableinvoker.model.RetryQueryRequest;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * JdbcTemplate实现的调用记录数据访问对象
 *
 * <p>基于Spring NamedParameterJdbcTemplate操作数据库，
 * 支持多场景独立表名配置。</p>
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 00:48:02
 * @version 1.0.0-SNAPSHOT
 */
public class JdbcTemplateInvocationRecordDaoImpl implements IInvocationRecordDao {

    /**
     * 命名参数JdbcTemplate，用于执行参数化SQL
     */
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    /**
     * 可靠调用配置属性
     */
    private final ReliableInvokerProperties properties;

    /**
     * 记录行映射器，将ResultSet映射为InvocationRecord对象
     */
    private final RecordRowMapper rowMapper = new RecordRowMapper();

    /**
     * 构造函数，接收JdbcTemplate和配置属性
     *
     * @param jdbcTemplate Spring JdbcTemplate
     * @param properties   可靠调用配置属性
     */
    public JdbcTemplateInvocationRecordDaoImpl(JdbcTemplate jdbcTemplate, ReliableInvokerProperties properties) {
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
        this.properties = properties;
    }

    /**
     * 根据场景名称解析对应的表名
     *
     * <p>优先使用场景级配置的tableName，
     * 若场景未配置或tableName为null，则回退到全局tableName。</p>
     *
     * @param scene 业务场景标识
     * @return 对应的表名
     */
    public String resolveTableName(String scene) {
        ReliableInvokerProperties.SceneProperties sceneProperties = this.properties.getSceneProperties(scene);
        if (sceneProperties != null && sceneProperties.getTableName() != null) {
            return sceneProperties.getTableName();
        }
        return this.properties.getTableName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InvocationRecord save(InvocationRecord record) {
        String tableName = this.resolveTableName(record.getScene());
        String sql = "INSERT INTO " + tableName
                + " (serial_no, scene, params, status,"
                + " retry_count, max_retry_count, retry_delay, execute_time, remark)"
                + " VALUES (:serialNo, :scene, :params, :status,"
                + " :retryCount, :maxRetryCount, :retryDelay, :executeTime, :remark)";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("serialNo", record.getSerialNo())
                .addValue("scene", record.getScene())
                .addValue("params", record.getParams())
                .addValue("status", record.getStatus())
                .addValue("retryCount", record.getRetryCount())
                .addValue("maxRetryCount", record.getMaxRetryCount())
                .addValue("retryDelay", record.getRetryDelay())
                .addValue("executeTime", record.getExecuteTime())
                .addValue("remark", record.getRemark());

        this.namedJdbcTemplate.update(sql, params);
        return this.findBySerialNo(record.getSerialNo(), record.getScene());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(Long id, Integer status, LocalDateTime executeTime, String scene) {
        String tableName = this.resolveTableName(scene);
        String sql = "UPDATE " + tableName
                + " SET status = :status, retry_count = retry_count + 1,"
                + " execute_time = :executeTime, update_time = :updateTime WHERE id = :id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("status", status)
                .addValue("executeTime", executeTime != null ? Timestamp.valueOf(executeTime) : null)
                .addValue("updateTime", Timestamp.valueOf(LocalDateTime.now()));

        this.namedJdbcTemplate.update(sql, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(Long id, String scene) {
        String tableName = this.resolveTableName(scene);
        String sql = "DELETE FROM " + tableName + " WHERE id = :id";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id);

        this.namedJdbcTemplate.update(sql, params);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public InvocationRecord findBySerialNo(String serialNo, String scene) {
        String tableName = this.resolveTableName(scene);
        String sql = "SELECT * FROM " + tableName + " WHERE serial_no = :serialNo";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("serialNo", serialNo);

        List<InvocationRecord> results = this.namedJdbcTemplate.query(sql, params, this.rowMapper);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<InvocationRecord> findForRetry(RetryQueryRequest request) {
        List<Integer> statusList = request.getStatusList();
        if (statusList == null || statusList.isEmpty()) {
            return Collections.emptyList();
        }

        String tableName = this.resolveTableName(request.getScene());
        String sql = "SELECT * FROM " + tableName
                + " WHERE scene = :scene AND status IN (:statuses)"
                + " AND execute_time <= CURRENT_TIMESTAMP"
                + " AND MOD(id, :shardTotal) = :shardIndex"
                + " ORDER BY create_time ASC LIMIT :limit";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("scene", request.getScene())
                .addValue("statuses", statusList)
                .addValue("shardTotal", request.getShardTotal())
                .addValue("shardIndex", request.getShardIndex())
                .addValue("limit", request.getLimit());

        return this.namedJdbcTemplate.query(sql, params, this.rowMapper);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<InvocationRecord> findForBackup(BackupQueryRequest request) {
        List<Integer> statusList = request.getStatusList();
        if (statusList == null || statusList.isEmpty()) {
            return Collections.emptyList();
        }

        String tableName = this.resolveTableName(request.getScene());
        String sql = "SELECT * FROM " + tableName
                + " WHERE status IN (:statuses)"
                + " AND create_time < DATEADD('DAY', -:days, CURRENT_TIMESTAMP)"
                + " AND MOD(id, :shardTotal) = :shardIndex"
                + " ORDER BY create_time ASC LIMIT :limit";

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", statusList)
                .addValue("days", request.getDays())
                .addValue("shardTotal", request.getShardTotal())
                .addValue("shardIndex", request.getShardIndex())
                .addValue("limit", request.getLimit());

        return this.namedJdbcTemplate.query(sql, params, this.rowMapper);
    }

    /**
     * InvocationRecord行映射器
     *
     * <p>将数据库结果集映射为InvocationRecord对象，
     * 处理数据库列名到Java驼峰属性的转换，
     * 以及Timestamp到LocalDateTime的类型转换。</p>
     *
     * @version 1.0.0-SNAPSHOT
     */
    private static class RecordRowMapper implements RowMapper<InvocationRecord> {

        /**
         * 将ResultSet当前行映射为InvocationRecord
         *
         * @param rs     结果集
         * @param rowNum 行号
         * @return 映射后的InvocationRecord对象
         * @throws SQLException SQL异常
         */
        @Override
        public InvocationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            InvocationRecord record = new InvocationRecord();
            record.setId(rs.getLong("id"));
            record.setSerialNo(rs.getString("serial_no"));
            record.setScene(rs.getString("scene"));
            record.setParams(rs.getString("params"));
            record.setStatus(rs.getInt("status"));
            record.setRetryCount(rs.getInt("retry_count"));
            record.setMaxRetryCount(rs.getInt("max_retry_count"));
            record.setRetryDelay(rs.getInt("retry_delay"));

            Timestamp executeTime = rs.getTimestamp("execute_time");
            if (executeTime != null) {
                record.setExecuteTime(executeTime.toLocalDateTime());
            }

            record.setRemark(rs.getString("remark"));

            Timestamp createTime = rs.getTimestamp("create_time");
            if (createTime != null) {
                record.setCreateTime(createTime.toLocalDateTime());
            }

            Timestamp updateTime = rs.getTimestamp("update_time");
            if (updateTime != null) {
                record.setUpdateTime(updateTime.toLocalDateTime());
            }

            return record;
        }
    }
}
