**This project is a small end-to-end mock of a bond trading workflow.
The goal was to simulate how a real electronic trading platform behaves when traders submit orders under moving markets, risk limits, partial fills, and random failures.
Everything is intentionally lightweight — the backend is a simple Java server and the tests are written in Robot Framework, but the flows mimic real fixed-income trading systems.**

🔎 What the Project Contains

Java Mock Trading Backend

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

<img width="1360" height="862" alt="image" src="https://github.com/user-attachments/assets/d5aa638e-f5a9-4d23-b355-0032c0e805c9" />


<img width="2276" height="1344" alt="image" src="https://github.com/user-attachments/assets/cba33131-b8a2-48c0-9301-1703a57132e7" />


Step 2 — Start your Python virtual environment

From your Robot tests folder:

Windows:
.venv\Scripts\activate

<img width="1468" height="1378" alt="image" src="https://github.com/user-attachments/assets/05c93ecb-0702-4e2e-89c5-203cda5caf0c" />


Step 3 - view you trade metrics below

<img width="1520" height="1345" alt="image" src="https://github.com/user-attachments/assets/818f1af2-cfed-4a56-8c8b-2b7504a8c6e6" />



