package com.pm.dao;

import javax.sql.DataSource;

public interface DataSourcePoolProviderService {
    DataSource getDataSourceByName(String name);
}
