package com.aem.ai.scanner.dao.impl;

import com.GenericeConstants;
import com.aem.ai.pm.dao.DataSourcePoolProviderService;
import com.aem.ai.scanner.dao.WatchlistDao;
import com.aem.ai.scanner.model.InstrumentSymbol;
import org.apache.commons.lang3.StringUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Component(service = WatchlistDao.class, immediate = true)
@Designate(ocd = JdbcWatchlistDao.Config.class)
public class JdbcWatchlistDao implements WatchlistDao {

    private static final Logger log = LoggerFactory.getLogger(JdbcWatchlistDao.class);
    @Reference
    private DataSourcePoolProviderService dataSourcePoolProviderService;

    private DataSource getDataSource() {
        return dataSourcePoolProviderService.getDataSourceByName(GenericeConstants.DB_ALGO_DB);
    }
    @ObjectClassDefinition(name="BSK MarketData Watchlist DAO")
    public @interface Config {
        @AttributeDefinition(name="Upstox table (nifty50 or nifty500)", description="Table with INSTRUMENT_KEY or SYMBOL")
        String upstox_table() default "nifty500_watchlist";

        @AttributeDefinition(name="Upstox symbol column")
        String upstox_symbol_col() default "INSTRUMENT_KEY";

        @AttributeDefinition(name="Delta table")
        String delta_table() default "delta_watchlist";

        @AttributeDefinition(name="Delta symbol column")
        String delta_symbol_col() default "SYMBOL";
    }

    public Config cfg;

    @Activate @Modified
    protected void activate(Config cfg) { this.cfg = cfg; }

    @Override
    public List<InstrumentSymbol> symbolsForUpstox() {
        return read(cfg.upstox_table(), "UPSTOX");
    }

    @Override
    public List<InstrumentSymbol> symbolsForDelta() {
        return read(cfg.delta_table(), "DELTA");
    }

    @Override
    public String upstoxTable() {
        return cfg.upstox_table();
    }

    @Override
    public String deltaTable() {
        return cfg.delta_table();
    }

    private List<InstrumentSymbol> read(String table, String type) {
        List<InstrumentSymbol> watchlist = new ArrayList<>();
        String sql = "SELECT * FROM " + table;

        DataSource dataSource = getDataSource();
        if (dataSource == null) {
            log.error("DataSource for {} is null, cannot read watchlist", table);
            return watchlist;
        }

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String instrumentKey =  StringUtils.EMPTY;
                String symbol = rs.getString("SYMBOL");
                if (StringUtils.equals(type,"UPSTOX")){
                    instrumentKey = rs.getString("INSTRUMENT_KEY");
                }
                String bestStrategy = rs.getString("BEST_STRATEGY");

                // NSE_EQ constant assumed available from GenericeConstants
                InstrumentSymbol inst = new InstrumentSymbol(symbol, bestStrategy, GenericeConstants.NSE_EQ + instrumentKey);
                watchlist.add(inst);
            }
            log.info("Loaded {} symbols from {}", watchlist.size(), table);

        } catch (Exception e) {
            log.error("Failed reading watchlist from {}: {}", table, e.getMessage(), e);
        }
        return watchlist;
    }

}
