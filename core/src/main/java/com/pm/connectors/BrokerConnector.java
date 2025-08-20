package com.pm.connectors;


import com.pm.dto.*;
import java.util.List;

public interface BrokerConnector {
    String brokerCode(); // "ZERODHA", "UPSTOX", etc.

    /** Fetch all accounts that have valid access (token, creds) for this broker. */
    List<BrokerAccountRef> discoverAccounts();

    /** Pull full portfolio snapshot for given broker account (holdings, positions, cash). */
    PortfolioSnapshot fetchPortfolio(BrokerAccountRef account) throws BrokerException;

    /** (Future) place/cancel orders, etc. Expose via same connector. */
    // String placeOrder(...); void cancelOrder(...); etc.
}
