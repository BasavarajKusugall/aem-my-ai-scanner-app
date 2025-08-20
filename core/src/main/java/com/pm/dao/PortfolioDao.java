package com.pm.dao;


import com.pm.dto.*;
import java.sql.Connection;
import java.util.List;

public interface PortfolioDao {
    void upsertAccountSnapshot(Connection c, BrokerAccountRef acc, PortfolioSnapshot snap);
}

