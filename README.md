# Mahalaxmi Auto Parts Web

This folder is the web-app conversion of the desktop/counter workflow.

## Shape

- `backend/`: Spring Boot API with JPA, H2 file database, vehicle catalog, inventory, GST billing, bill cancellation, dealer purchases, stock transactions, suppliers, and dashboard stats.
- `frontend/`: React + Vite + Tailwind counter UI for billing, inventory, dealer purchases, suppliers, dashboard, and bill history.

## Run Backend

```bash
cd website/backend
./mvnw spring-boot:run
```

If no Maven wrapper is present, run with any Maven installation:

```bash
mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

## Run Frontend

```bash
cd website/frontend
npm install
npm run dev
```

The UI starts on `http://localhost:5173` and proxies `/api` to Spring Boot.

## Important Product Flow

1. Select car brand.
2. Select model.
3. Select series.
4. See only compatible parts with rack/location and stock.
5. Add parts to bill.
6. Save bill.
7. Backend calculates GST, deducts stock, captures cost/profit, and writes stock transactions.
8. Bill cancellation restores stock.
9. Dealer purchases increase stock, update cost basis, and feed dashboard purchase/profit totals.

## Honest Scope Note

This is a lean web rewrite, not a full enterprise migration. The old app had FastAPI auth, PDF/report endpoints, and desktop packaging. This web version intentionally ports the core money-and-stock workflow first. Add authentication, richer reports, real data import, and production database migration after this core flow is tested with real shop data.
