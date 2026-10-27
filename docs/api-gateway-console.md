# Reproducing the API Gateway configuration by hand (console)

`infra/template.yaml` is the source of truth and `bash scripts/deploy.sh` creates everything. This page
explains the same configuration as console steps, so you can rebuild it manually or explain it in the demo.

All functions: runtime **Java 17**, memory **512 MB**, timeout **15 s**, code = `target/orders-api.jar`.

| Function | Handler | Used by |
|---|---|---|
| `orders-api-create-order` | `com.workshop.orders.lambda.direct.CreateOrderFunction::handleRequest` | A |
| `orders-api-get-order` | `com.workshop.orders.lambda.direct.GetOrderFunction::handleRequest` | A |
| `orders-api-find-orders-by-customer` | `com.workshop.orders.lambda.direct.FindOrdersByCustomerFunction::handleRequest` | A |
| `orders-api-delete-order` | `com.workshop.orders.lambda.direct.DeleteOrderFunction::handleRequest` | A |
| `orders-api-proxy` | `com.workshop.orders.lambda.proxy.OrdersApiHandler::handleRequest` | B |

---

## Architecture A — REST API with Lambda (non-proxy) integration

Create a **REST API** (not HTTP API; HTTP APIs have no mapping templates), Regional endpoint.

### Resources

```
/orders          POST, GET
/orders/{id}     GET, DELETE
```

### `GET /orders/{id}`

1. **Integration request**: type *Lambda Function*, **Lambda proxy integration OFF**, function `orders-api-get-order`.
2. Request body passthrough: **Never**. Mapping template for `application/json`:
   ```velocity
   {"id": "$util.escapeJavaScript($input.params().path.get('id')).replaceAll("\\'", "'")"}
   ```
   `$util.escapeJavaScript` also escapes `'` as `\'`, which is not valid JSON, hence the `replaceAll`.
3. **Method response**: add status codes `200`, `400`, `404`, `500`.
4. **Integration response** (one row per status, matched against the Lambda `errorMessage`):

   | Lambda error regex | Status | Mapping template (`application/json`) |
   |---|---|---|
   | *(default, no regex)* | 200 | `$input.json('$')` |
   | `^INVALID_REQUEST: [\s\S]*` | 400 | see below |
   | `^ORDER_NOT_FOUND: [\s\S]*` | 404 | see below |
   | `^(?!INVALID_REQUEST: \|ORDER_NOT_FOUND: )[\s\S]+` | 500 | `{"error": "INTERNAL_ERROR", "message": "Unexpected error. Reference: $context.requestId"}` |

   404 template (400 is the same with `INVALID_REQUEST`):
   ```velocity
   #set($message = $input.path('$.errorMessage').replaceFirst('^ORDER_NOT_FOUND: ', ''))
   {"error": "ORDER_NOT_FOUND", "message": "$util.escapeJavaScript($message).replaceAll("\\'", "'")"}
   ```

### `GET /orders`

1. **Method request** → URL query string parameters: add `customerId` (not required; Java returns the 400).
2. Integration: Lambda `orders-api-find-orders-by-customer`, non-proxy, passthrough *Never*, template:
   ```velocity
   #set($customerId = $input.params().querystring.get('customerId'))
   #if(!$customerId) #set($customerId = "") #end
   {"customerId": "$util.escapeJavaScript($customerId).replaceAll("\\'", "'")"}
   ```
3. Integration responses: default → **200** `$input.json('$')`; `INVALID_REQUEST` → 400; catch-all → 500 (as above).

### `POST /orders`

1. **Models**: create `CreateOrderRequest` (JSON Schema draft 4):
   ```json
   {"type": "object", "properties": {"customerId": {"type": "string"}, "product": {"type": "string"}, "quantity": {"type": "integer"}}}
   ```
2. **Method request**: Request validator *Validate body*, request body `application/json` → `CreateOrderRequest`.
3. Integration: Lambda `orders-api-create-order`, non-proxy, passthrough *Never*, template `$input.json('$')`.
4. **Method response**: `201` with response header `Location`; `400`; `500`.
5. Integration responses: default → **201** with template
   ```velocity
   #set($context.responseOverride.header.Location = "$context.path/$input.path('$.id')")
   $input.json('$')
   ```
   `INVALID_REQUEST` → 400, catch-all → 500.

### `DELETE /orders/{id}`

1. Integration: Lambda `orders-api-delete-order`, non-proxy, passthrough *Never*, the same request template as
   `GET /orders/{id}` (`{"id": "..."}`).
2. **Method response**: `204` (no model), `400`, `404`, `500`.
3. Integration responses: default → **204** with the template `## no body` (a VTL comment, so the body is empty
   rather than the `null` the function returns); `ORDER_NOT_FOUND` → 404, `INVALID_REQUEST` → 400, catch-all → 500, as for `GET`.
4. Add the Lambda permission for `DELETE /orders/*` (the console offers it when you save the integration).

### Gateway responses (API level)

| Type | Status | Template |
|---|---|---|
| Bad Request Body | 400 | `{"error": "INVALID_REQUEST", "message": "request body must be a JSON object with ..."}` |
| Missing Authentication Token | 404 | `{"error": "ROUTE_NOT_FOUND", "message": "No such resource or method"}` |
| Default 4XX | – | `{"error": "$context.error.responseType", "message": $context.error.messageString}` |
| Default 5XX | – | `{"error": "INTERNAL_ERROR", "message": $context.error.messageString}` |

Deploy the API to stage `prod`. Every change needs a new **Deploy API**.

---

## Architecture B — REST API with Lambda proxy integration

1. Create a second REST API with resources `/orders` and `/orders/{id}`.
2. On each resource create an **ANY** method: integration *Lambda Function*, **Lambda proxy integration ON**,
   function `orders-api-proxy`. No mapping templates, no method/integration responses to configure.
   `DELETE` needs nothing extra here: `ANY` already forwards it to the adapter.
3. Gateway response *Missing Authentication Token* → 404 with the `ROUTE_NOT_FOUND` body (optional).
4. Deploy to stage `prod`.

To capture a real proxy event, set the function's environment variable `LOG_EVENTS=true`, call the API and
copy the logged `event:` line from CloudWatch Logs.
