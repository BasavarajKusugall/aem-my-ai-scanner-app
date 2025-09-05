package com.aem.ai.scanner.dao;


import com.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component(service = MySQLService.class, immediate = true)
public class MySQLService {

    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;


    private DataSource getDataSource() {
        return dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.DB_ALGO_DB);
    }


    public void testQuery() {
        DataSource dataSource = getDataSource();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT bot_chat_id, chat_type, chat_title, bot_name, bot_token, bot_user_id, purpose, is_group_enabled \" +\n" +
                     "                \"FROM telegram_bot_config WHERE is_active = 1");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String name = rs.getString("bot_name");
                System.out.println("User: " + name);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
