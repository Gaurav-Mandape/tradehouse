# Tradehouse

Tradehouse is a dependency-free virtual stock trading platform for the assignment MVP. It uses a single predefined user with `$100,000` in virtual cash, deterministic CSV test data for 10 stocks across 10 trading days, and 30-minute observations from 09:30 to 16:00.

## Run

Requires a JDK 11 or newer. From the project folder:

```powershell
New-Item -ItemType Directory -Force -Path out
javac -d out src\Main.java
java -cp out Main
```

Open http://localhost:8080.

The first run creates `data/market-data.csv` automatically. It is intentionally generated from a fixed seed so the demo data stays repeatable. The server keeps portfolio state in memory and resets when restarted. There are no real-money transactions, accounts, authentication, or external market APIs.

## Included MVP workflows

- Browse 10 available stocks and current simulated prices
- Select a historical date and 30-minute market time to view the matching CSV quote
- Buy and sell whole shares with validation for cash and available holdings
- Review cash, invested value, current portfolio value, and unrealized profit/loss
- Review virtual transaction history

## Suggested demo sequence

1. Open the dashboard and show the 10-stock market watch.
2. Select `AAPL`, change the time from `09:30` to `16:00`, and show the quote changing from the CSV data.
3. Buy 10 shares from the order ticket.
4. Show cash, portfolio value, the AAPL position, and the transaction history updating.
5. Switch to Sell, sell 3 shares, and show the updated position and activity.
6. Point out the simulated market and paper-only labels.

## Project structure

- `src/Main.java` - HTTP server, CSV seed/load logic, market selection, portfolio, and trade API
- `web/index.html` - dashboard markup
- `web/styles.css` - responsive visual design
- `web/app.js` - browser state and API interactions
- `data/market-data.csv` - generated on first run
