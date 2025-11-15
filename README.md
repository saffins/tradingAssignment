**This project is a small end-to-end mock of a bond trading workflow.
The goal was to simulate how a real electronic trading platform behaves when traders submit orders under moving markets, risk limits, partial fills, and random failures.
Everything is intentionally lightweight — the backend is a simple Java server and the tests are written in Robot Framework, but the flows mimic real fixed-income trading systems.

🔎 What the Project Contains
1. Java Mock Trading Backend

The Java backend simulates several components that normally run in production trading systems:

MarketDataService → generates live ticking prices for instruments

TradeService → manages the entire trade lifecycle

CreditExposureService → blocks trades when exposure limits are breached

FixMockService → creates a simplified FIX execution report

MarketWebSocketServer → pushes market updates in real time

The idea was to have a small but realistic “world” where trades behave unpredictably, like in real markets.

Step 1 — Start the Java Mock Trading Server

Open the project in IntelliJ.
Run the main class:

MainServer.java


If everything is running, the console will print:

Server running → http://localhost:8080
WebSocket running → ws://localhost:8090


You can now hit endpoints using Postman or curl.

Step 2 — Start your Python virtual environment

From your Robot tests folder:

Windows:
.venv\Scripts\activate

macOS/Linux:
source .venv/bin/activate


Check that Robot is installed:

robot --version

Step 3 — Run the Robot Tests

Once the Java server is running and your venv is active:

robot robot-tests/TradeScenarios.robot


Robot will execute all trade scenarios:

valid trade

invalid ISIN

price deviation

retries

multiple traders

partial fills

rapid market updates

cancellation flow

When done, it generates:

log.html

report.html

output.xml**
