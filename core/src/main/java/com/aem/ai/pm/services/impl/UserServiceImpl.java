package com.aem.ai.pm.services.impl;

import com.aem.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.pm.dto.AppUser;
import com.aem.ai.pm.services.UserService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Optional;

@Component(service = UserService.class, immediate = true)
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    // ANSI Colors
    private static final String RESET = "\u001B[0m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    @Override
    public AppUser registerOrUpdate(AppUser user) {
        log.info(BLUE + "🟦 Starting registerOrUpdate() for externalRef={} email={}" + RESET,
                user.getExternalRef(), user.getEmail());

        String sql = "INSERT INTO portfolio_mgmt_app_user (external_ref, email, full_name, phone, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, NOW(), NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "email = VALUES(email), " +
                "full_name = VALUES(full_name), " +
                "phone = VALUES(phone), " +
                "status = VALUES(status), " +
                "updated_at = NOW()";



        try (Connection conn = dataSourcePoolProviderService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            log.debug(CYAN + "📥 Preparing SQL Insert/Update for portfolio_mgmt_app_user" + RESET);

            ps.setString(1, user.getExternalRef());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getStatus());

            int rows = ps.executeUpdate();
            log.info(GREEN + "✅ registerOrUpdate executed. Rows affected: {}" + RESET, rows);

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long newId = rs.getLong(1);
                    user.setUserId(newId);
                    log.info(GREEN + "🆔 New UserId generated: {}" + RESET, newId);
                } else {
                    log.debug(YELLOW + "ℹ️ No new UserId generated (record updated)." + RESET);
                }
            }

            log.info(GREEN + "✅ User saved successfully: userId={} externalRef={}" + RESET,
                    user.getUserId(), user.getExternalRef());

            return user;

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in registerOrUpdate: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error saving portfolio_mgmt_app_user", e);
        }
    }


    @Override
    public Optional<AppUser> findById(long userId) {
        log.info(BLUE + "🔍 Looking up AppUser with userId={}" + RESET, userId);

        String sql = "SELECT * FROM portfolio_mgmt_app_user WHERE user_id=?";

        try (Connection conn = dataSourcePoolProviderService.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AppUser u = new AppUser();
                    u.setUserId(rs.getLong("user_id"));
                    u.setExternalRef(rs.getString("external_ref"));
                    u.setEmail(rs.getString("email"));
                    u.setFullName(rs.getString("full_name"));
                    u.setPhone(rs.getString("phone"));
                    u.setStatus(rs.getString("status"));

                    log.info(GREEN + "✅ AppUser found: userId={} externalRef={} email={}" + RESET,
                            u.getUserId(), u.getExternalRef(), u.getEmail());

                    return Optional.of(u);
                } else {
                    log.warn(YELLOW + "⚠️ No AppUser found for userId={}" + RESET, userId);
                }
            }

        } catch (SQLException e) {
            log.error(RED + "❌ SQL Error in findById: {}" + RESET, e.getMessage(), e);
            throw new RuntimeException("Error finding portfolio_mgmt_app_user", e);
        }

        return Optional.empty();
    }

    @Override
    public Optional<AppUser> findByIdOrEmail(String idOrEmail) {


        String sql;
        boolean isNumeric = idOrEmail.matches("\\d+");
        if (isNumeric) {
            sql = "SELECT user_id, email, full_name, phone, status " +
                    "FROM portfolio_mgmt_app_user WHERE user_id = ?";
        } else {
            sql = "SELECT user_id, email, full_name, phone, status " +
                    "FROM portfolio_mgmt_app_user WHERE email = ?";
        }

        try (Connection con = dataSourcePoolProviderService.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            if (isNumeric) {
                ps.setLong(1, Long.parseLong(idOrEmail));
            } else {
                ps.setString(1, idOrEmail);
            }

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AppUser u = new AppUser();
                    u.setUserId(rs.getLong("user_id"));
                    u.setEmail(rs.getString("email"));
                    u.setFullName(rs.getString("full_name"));
                    u.setPhone(rs.getString("phone"));
                    u.setStatus(rs.getString("status"));
                    return Optional.of(u);
                }
            }
        } catch (Exception e) {
            log.error("❌ Error in findByIdOrEmail: {}", e.getMessage(), e);
        }
        return Optional.empty();
    }
}
