package com.aem.ai.pm.dao;

import java.sql.Connection;
import java.sql.SQLException;

public interface DataSourcePoolProviderService {
    Connection getConnection() throws SQLException;
}
