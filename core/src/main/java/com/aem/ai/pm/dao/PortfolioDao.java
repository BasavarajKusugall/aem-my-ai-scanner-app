package com.aem.ai.pm.dao;


import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.PortfolioSnapshot;
import com.aem.ai.pm.dto.UserBrokerAccount;

import java.sql.Connection;
import java.util.List;

public interface PortfolioDao {
    List<UserBrokerAccount> fetchActiveAccounts();
    void updateUserBrokerAccountJson(Connection c, BrokerAccountRef acc, PortfolioSnapshot snap);
    void upsertAccountSnapshot(Connection c, BrokerAccountRef acc, PortfolioSnapshot snap);
}

