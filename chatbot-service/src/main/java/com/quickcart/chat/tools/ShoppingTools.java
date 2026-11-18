package com.quickcart.chat.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.quickcart.chat.client.GatewayCallException;
import com.quickcart.chat.client.GatewayClient;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Read-only shopping tools. The user's JWT arrives through ToolContext (key "token"),
 * never through a prompt. Every failure becomes a short "ERROR: ..." string for the model.
 */
@Component
public class ShoppingTools {

    public static final String TOKEN_KEY = "token";

    private static final Logger log = LoggerFactory.getLogger(ShoppingTools.class);
    private static final int MAX_RESULTS = 5;
    private static final int MAX_CART_LINES = 20;

    private final GatewayClient gateway;

    public ShoppingTools(GatewayClient gateway) {
        this.gateway = gateway;
    }

    @Tool(name = "search_products",
            description = "Search the QuickCart catalogue by product name and/or category. "
                    + "Returns up to 5 products with id, name, unit, price in INR and whether it is in stock.")
    public String searchProducts(
            @ToolParam(description = "Words to look for in the product name, for example milk", required = false) String query,
            @ToolParam(description = "Category id taken from list_categories", required = false) Long categoryId,
            @ToolParam(description = "How many products to return, 1 to 5", required = false) Integer maxResults,
            ToolContext toolContext) {
        return run("search_products", toolContext, token -> {
            int size = ToolFormat.clamp(maxResults, MAX_RESULTS, MAX_RESULTS);
            String q = ToolFormat.searchText(query, 100);
            JsonNode body = gateway.get(token, "search_products", b -> {
                Map<String, Object> vars = new HashMap<>();
                b.path("/api/v1/products").queryParam("page", 0).queryParam("size", size).queryParam("sort", "name");
                if (!q.isEmpty()) {
                    b.queryParam("q", "{q}");
                    vars.put("q", q);
                }
                if (categoryId != null && categoryId > 0) {
                    b.queryParam("categoryId", categoryId);
                }
                return b.build(vars);
            });
            JsonNode content = body.path("content");
            if (!content.isArray() || content.isEmpty()) {
                return "No products found.";
            }
            StringBuilder out = new StringBuilder("Found ").append(body.path("totalElements").asLong(content.size()))
                    .append(" product(s), showing ").append(content.size()).append(":\n");
            for (JsonNode p : content) {
                out.append("- id=").append(p.path("id").asLong())
                        .append(" | ").append(ToolFormat.text(p, "name", 80))
                        .append(" | ").append(ToolFormat.text(p, "unit", 30))
                        .append(" | ").append(ToolFormat.money(p.get("price")))
                        .append(" | ").append(ToolFormat.stock(p)).append('\n');
            }
            return out.toString().trim();
        });
    }

    @Tool(name = "get_product",
            description = "Get details of one product by its id: name, unit, price in INR, category, stock status, description.")
    public String getProduct(
            @ToolParam(description = "Product id, a positive number") Long productId,
            ToolContext toolContext) {
        return run("get_product", toolContext, token -> {
            if (productId == null || productId < 1) {
                return "ERROR: productId must be a positive number.";
            }
            JsonNode p = gateway.get(token, "get_product", b -> b.path("/api/v1/products/{id}").build(productId));
            return "id=" + p.path("id").asLong()
                    + " | " + ToolFormat.text(p, "name", 80)
                    + " | " + ToolFormat.text(p, "unit", 30)
                    + " | " + ToolFormat.money(p.get("price"))
                    + " | category: " + ToolFormat.text(p, "categoryName", 60)
                    + " | " + ToolFormat.stock(p)
                    + (p.hasNonNull("description") ? " | " + ToolFormat.text(p, "description", 200) : "");
        });
    }

    @Tool(name = "list_categories",
            description = "List all product categories with their ids. Use it to find a category id for search_products.")
    public String listCategories(ToolContext toolContext) {
        return run("list_categories", toolContext, token -> {
            JsonNode body = gateway.get(token, "list_categories", b -> b.path("/api/v1/categories").build());
            if (!body.isArray() || body.isEmpty()) {
                return "No categories available.";
            }
            StringBuilder out = new StringBuilder("Categories:\n");
            for (JsonNode c : body) {
                out.append("- id=").append(c.path("id").asLong())
                        .append(" | ").append(ToolFormat.text(c, "name", 80)).append('\n');
            }
            return out.toString().trim();
        });
    }

    @Tool(name = "get_my_cart",
            description = "Show the current customer's cart: each line with quantity and price in INR, plus subtotal, "
                    + "delivery fee and total as calculated by the shop.")
    public String getMyCart(ToolContext toolContext) {
        return run("get_my_cart", toolContext, token -> {
            JsonNode cart = gateway.get(token, "get_my_cart", b -> b.path("/api/v1/cart").build());
            JsonNode items = cart.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return "The cart is empty.";
            }
            StringBuilder out = new StringBuilder("Cart:\n");
            int shown = 0;
            for (JsonNode line : items) {
                if (shown++ >= MAX_CART_LINES) {
                    out.append("- ...and ").append(items.size() - MAX_CART_LINES).append(" more line(s)\n");
                    break;
                }
                out.append("- ").append(ToolFormat.text(line, "name", 80));
                if (line.path("available").asBoolean(false)) {
                    out.append(" | ").append(ToolFormat.text(line, "unit", 30))
                            .append(" | quantity ").append(line.path("quantity").asInt())
                            .append(" | each ").append(ToolFormat.money(line.get("unitPrice")))
                            .append(" | line total ").append(ToolFormat.money(line.get("lineTotal")));
                } else {
                    out.append(" | quantity ").append(line.path("quantity").asInt())
                            .append(" | UNAVAILABLE right now (must be removed on the Cart page)");
                }
                out.append('\n');
            }
            out.append("Items: ").append(cart.path("itemCount").asInt())
                    .append(" | Subtotal: ").append(ToolFormat.money(cart.get("subtotal")))
                    .append(" | Delivery fee: ").append(ToolFormat.money(cart.get("deliveryFee")))
                    .append(" | Total: ").append(ToolFormat.money(cart.get("total")));
            if (cart.path("hasUnavailableItems").asBoolean(false)) {
                out.append("\nNote: unavailable lines are not included in the totals.");
            }
            return out.toString();
        });
    }

    @Tool(name = "list_my_orders",
            description = "List the current customer's most recent orders, newest first, with order number, status and total.")
    public String listMyOrders(
            @ToolParam(description = "How many orders to return, 1 to 5", required = false) Integer maxResults,
            ToolContext toolContext) {
        return run("list_my_orders", toolContext, token -> {
            int size = ToolFormat.clamp(maxResults, MAX_RESULTS, MAX_RESULTS);
            JsonNode body = gateway.get(token, "list_my_orders",
                    b -> b.path("/api/v1/orders").queryParam("page", 0).queryParam("size", size).build());
            JsonNode content = body.path("content");
            if (!content.isArray() || content.isEmpty()) {
                return "No orders yet.";
            }
            StringBuilder out = new StringBuilder("Latest ").append(content.size()).append(" of ")
                    .append(body.path("totalElements").asLong(content.size())).append(" order(s):\n");
            for (JsonNode o : content) {
                out.append("- ").append(ToolFormat.text(o, "orderNumber", 30))
                        .append(" (id=").append(o.path("id").asLong()).append(")")
                        .append(" | ").append(ToolFormat.text(o, "status", 30))
                        .append(" | total ").append(ToolFormat.money(o.get("total")))
                        .append(" | ").append(o.path("itemCount").asInt()).append(" item(s)")
                        .append(" | placed ").append(ToolFormat.text(o, "createdAt", 30)).append('\n');
            }
            return out.toString().trim();
        });
    }

    @Tool(name = "get_order",
            description = "Get one of the current customer's orders by id: status, payment, items and totals in INR.")
    public String getOrder(
            @ToolParam(description = "Order id, a positive number (not the order number text)") Long orderId,
            ToolContext toolContext) {
        return run("get_order", toolContext, token -> {
            if (orderId == null || orderId < 1) {
                return "ERROR: orderId must be a positive number.";
            }
            JsonNode o = gateway.get(token, "get_order", b -> b.path("/api/v1/orders/{id}").build(orderId));
            StringBuilder out = new StringBuilder("Order ").append(ToolFormat.text(o, "orderNumber", 30))
                    .append(" | status ").append(ToolFormat.text(o, "status", 30))
                    .append(" | payment ").append(ToolFormat.text(o, "paymentMethod", 30))
                    .append(" ").append(ToolFormat.text(o, "paymentStatus", 30))
                    .append(" | placed ").append(ToolFormat.text(o, "createdAt", 30))
                    .append(" | updated ").append(ToolFormat.text(o, "updatedAt", 30)).append('\n');
            for (JsonNode i : o.path("items")) {
                out.append("- ").append(ToolFormat.text(i, "productName", 80))
                        .append(" | quantity ").append(i.path("quantity").asInt())
                        .append(" | each ").append(ToolFormat.money(i.get("unitPrice")))
                        .append(" | line total ").append(ToolFormat.money(i.get("lineTotal"))).append('\n');
            }
            out.append("Subtotal: ").append(ToolFormat.money(o.get("subtotal")))
                    .append(" | Delivery fee: ").append(ToolFormat.money(o.get("deliveryFee")))
                    .append(" | Total: ").append(ToolFormat.money(o.get("total")));
            return out.toString();
        });
    }

    // ---------------------------------------------------------------- shared wrapper

    /** Reads the token from ToolContext, runs the body, and turns every failure into an error string. */
    private String run(String tool, ToolContext context, Function<String, String> body) {
        Object token = context == null ? null : context.getContext().get(TOKEN_KEY);
        if (!(token instanceof String t) || t.isBlank()) {
            return "ERROR: The customer is not signed in.";
        }
        try {
            return body.apply(t);
        } catch (GatewayCallException e) {
            return "ERROR: " + switch (e.getKind()) {
                case UNAUTHORIZED -> "The session is not valid. Ask the customer to log in again.";
                case NOT_FOUND -> "No such item, or it does not belong to this customer.";
                case UNAVAILABLE -> "The shop data is temporarily unavailable.";
                case REJECTED -> "The shop could not process that request.";
            };
        } catch (RuntimeException e) {
            log.warn("tool={} failed type={}", tool, e.getClass().getSimpleName());   // no message, no args
            return "ERROR: The shop data could not be read.";
        }
    }
}