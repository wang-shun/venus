package com.meidusa.venus.registry.dao.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import com.meidusa.venus.registry.DAOException;
import com.meidusa.venus.registry.dao.VenusApplicationDAO;
import com.meidusa.venus.registry.domain.VenusApplicationDO;

public class VenusApplicationDaoImpl implements VenusApplicationDAO {

	private JdbcTemplate jdbcTemplate;

	public VenusApplicationDaoImpl(JdbcTemplate jdbcTemplate) {
		super();
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public int addApplication(VenusApplicationDO venusApplicationDO) throws DAOException {
		final String sql = "insert into t_venus_application (app_code,provider,consumer,create_name,update_name,create_time, update_time) values ('"
				+ venusApplicationDO.getAppCode() + "'," + venusApplicationDO.isProvider() + ","
				+ venusApplicationDO.isConsumer() + ", '" + venusApplicationDO.getCreateName() + "', '"
				+ venusApplicationDO.getUpdateName() + "', now(), now())";
		KeyHolder keyHolder = new GeneratedKeyHolder();
		int autoIncId = 0;
		jdbcTemplate.update(new PreparedStatementCreator() {
			public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
				PreparedStatement ps = con.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
				return ps;
			}
		}, keyHolder);
		autoIncId = keyHolder.getKey().intValue();
		return autoIncId;

	}

	@Override
	public boolean updateApplication(VenusApplicationDO venusApplicationDO) throws DAOException {
		String sql = "update t_venus_application set provider=?,consumer=?,update_time=now() where id=?";
		int update = 0;
		try {
			update = this.jdbcTemplate.update(sql, venusApplicationDO.isProvider(), venusApplicationDO.isConsumer(),
					venusApplicationDO.getId());
		} catch (Exception e) {
			throw new DAOException("更新venusApplication异常", e);
		}
		return update > 0;
	}

	@Override
	public VenusApplicationDO getApplication(String appCode) throws DAOException {
		String sql = "select id, app_code,provider,consumer,create_name,update_name,create_time, update_time from t_venus_application where app_code = ?";
		try {
			return this.jdbcTemplate.query(sql, new Object[] { appCode }, new ResultSetExtractor<VenusApplicationDO>() {
				@Override
				public VenusApplicationDO extractData(ResultSet rs) throws SQLException, DataAccessException {
					if (rs.next()) {
						return ResultUtils.resultToVenusApplicationDO(rs);
					}
					return null;
				}

			});
		} catch (Exception e) {
			throw new DAOException("根据 appCode 获取venusApplication异常", e);
		}
	}

	@Override
	public VenusApplicationDO getApplication(Integer id) throws DAOException {
		String sql = "select id,app_code,provider,consumer,create_name,update_name,create_time, update_time from t_venus_application where id = ?";
		try {
			return this.jdbcTemplate.query(sql, new Object[] { id }, new ResultSetExtractor<VenusApplicationDO>() {
				@Override
				public VenusApplicationDO extractData(ResultSet rs) throws SQLException, DataAccessException {
					if (rs.next()) {
						return ResultUtils.resultToVenusApplicationDO(rs);
					}
					return null;
				}

			});
		} catch (Exception e) {
			throw new DAOException("获取venusApplication异常", e);
		}
	}

}