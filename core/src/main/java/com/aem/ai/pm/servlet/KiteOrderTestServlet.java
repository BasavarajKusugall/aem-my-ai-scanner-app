package com.aem.ai.pm.servlet;

import com.aem.ai.exception.ApiException;
import com.aem.ai.realtime.brokers.kite.*;
import com.aem.ai.scanner.model.InstrumentSymbol;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/**
 * Test servlet to place orders using KiteOrderService
 */
@Component(
        service = Servlet.class,immediate = true,
        property = {
                "sling.servlet.paths=/bin/kite/placeOrderTest",
                "sling.servlet.methods=GET"
        }
)
public class KiteOrderTestServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(KiteOrderTestServlet.class);

    @Reference
    private KiteOrderManagmentService kiteOrderManagmentService;

    @Override
    protected void doGet(SlingHttpServletRequest req,
                         SlingHttpServletResponse resp) throws ServletException,
            IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();

        try {

            // --- Read parameters from request ---
            String tradingsymbol = req.getParameter("tradingsymbol");
            String exchange = req.getParameter("exchange");
            String transaction_type = req.getParameter("transaction_type"); // BUY/SELL
            String order_type = req.getParameter("order_type"); // MARKET/LIMIT
            String product = req.getParameter("product"); // CNC/MIS
            long quantity = Long.parseLong(req.getParameter("quantity"));
            double price = req.getParameter("price") != null ? Double.parseDouble(req.getParameter("price")) : 0.0;
            String variety = req.getParameter("variety") != null ? req.getParameter("variety") : "amo";

            // --- Build OrderRequest ---
            OrderRequest orderReq = new OrderRequest();
            InstrumentSymbol instrumentSymbol = new InstrumentSymbol(tradingsymbol, tradingsymbol);
            instrumentSymbol.setAllowedMarginFundsPercent(50);
            orderReq.setInstrument(instrumentSymbol);
            orderReq.setTradingsymbol(tradingsymbol);
            orderReq.setExchange(exchange);
            orderReq.setTransaction_type(transaction_type);
            orderReq.setOrder_type(order_type);
            orderReq.setProduct(product);
            orderReq.setQuantity(quantity);
            if (price > 0.0) orderReq.setPrice(price);

            String action = req.getParameter("action");
            if (!StringUtils.equalsIgnoreCase(action, "cancel")){
                // --- Place order ---
                OrderResponse orderResp = kiteOrderManagmentService.placeOrder(variety, orderReq);

                // --- Return JSON response ---
                out.write("{\"status\":\"success\",\"order_id\":\"" + orderResp.order_id + "\"}");
                LOG.info("Order placed successfully: {}", orderResp.order_id);
            }
            // --- List existing orders and modify/cancel them ---
            List<OrderDetail> orderDetailList = kiteOrderManagmentService.listOrders();
            if (!orderDetailList.isEmpty()) {
                for (OrderDetail od : orderDetailList) {
                    LOG.info("Existing Order: ID={} Status={}", od.order_id, od.status);
                    orderReq.setQuantity(10L);
                    orderReq.setPrice(420.0);
                    orderReq.setValidity("DAY");
                    orderReq.setOrder_type("LIMIT");//MARKET/LIMIT
                    OrderResponse amo = kiteOrderManagmentService.modifyOrder("amo", od.order_id, orderReq);
                    LOG.info("Modified Order: ID={} Status={}", amo.order_id);

                    if (StringUtils.equalsIgnoreCase(action, "cancel")){
                        OrderResponse cancelOrder = kiteOrderManagmentService.cancelOrder("amo", od.order_id);
                        LOG.info("Cancelled Order: ID={} Status={}", cancelOrder.order_id);
                        out.write("{\"status\":\"Cancelled\",\"order_id\":\"" + cancelOrder.order_id + "\"}");
                    }
                }

            }

        } catch (ApiException e) {
            LOG.error("Failed to place order: {}", e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.write("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            LOG.error("Unexpected error: {}", e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.write("{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }
}
