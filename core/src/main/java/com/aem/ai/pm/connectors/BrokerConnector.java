package com.aem.ai.pm.connectors;


import com.aem.ai.pm.dto.BrokerAccountRef;
import com.aem.ai.pm.dto.BrokerException;
import com.aem.ai.pm.dto.CashSummary;
import com.aem.ai.pm.dto.PortfolioSnapshot;

import java.util.List;
import java.util.Map;

public interface BrokerConnector {
    String brokerCode(); // "ZERODHA", "UPSTOX", etc.

    /** Fetch all accounts that have valid access (token, creds) for this broker. */
    List<BrokerAccountRef> discoverAccounts();

    /** Pull full portfolio snapshot for given broker account (holdings, positions, cash). */
    PortfolioSnapshot fetchPortfolio(BrokerAccountRef account) throws BrokerException;

    /** (Future) place/cancel orders, etc. Expose via same connector. */
    // String placeOrder(...); void cancelOrder(...); etc.

    CashSummary getFundsForAccount(Map<String, String> headers) throws Exception;
}
