*** Settings ***
Library    RequestsLibrary
Library    Collections
Library    BuiltIn
Library    OperatingSystem
Library    String
Resource   ./keywords.robot
Suite Setup       Setup Test Session

*** Variables ***
${base_url}        http://localhost:8080

*** Keywords ***
Setup Test Session
    Log To Console    Setting up HTTP session to ${base_url}...
    Create Session    tradin    ${base_url}    headers={"Content-Type":"application/json"}
    Log To Console    Session 'tradin' created successfully.

Validate Trader Exposure
    [Arguments]    ${trader}

    ${resp}=    GET On Session    tradin    /api/exposure    params=trader=${trader}
    Should Be Equal As Integers    ${resp.status_code}    200
    ${json}=    Set Variable    ${resp.json()}
    Run Keyword If    not ${json['allowed']}    Fail    Exposure breach for trader ${trader}

Build Trade Body
    [Arguments]    ${trader}    ${isin}    ${quantity}    ${side}=BUY    ${limitPrice}=0
    ${body}=    Create Dictionary
    ...    trader=${trader}
    ...    isin=${isin}
    ...    quantity=${quantity}
    ...    side=${side}
    ...    limitPrice=${limitPrice}
    RETURN    ${body}

Create Trade And Return Id
    [Arguments]    ${trader}    ${isin}    ${quantity}    ${side}=BUY    ${limitPrice}=0

    ${quantity}=    Convert To Integer    ${quantity}
    ${limitPrice}=    Convert To Number    ${limitPrice}

    ${body}=    Build Trade Body    ${trader}    ${isin}    ${quantity}    ${side}    ${limitPrice}

    ${json_body}=    Evaluate    json.dumps(${body})    json

    ${headers}=    Create Dictionary    Content-Type=application/json

    ${resp}=    POST On Session    tradin    /api/trades/create    data=${json_body}    headers=${headers}

    BuiltIn.Log To Console    RESPONSE: ${resp.text}

    # ❗ Correct way to get JSON
    ${json}=    Set Variable    ${resp.json()}

    RETURN    ${json}    ${json['tradeId']}

Validate Price Deviation
    [Arguments]    ${isin}    ${tolerance}=0.10

    ${headers}=    Create Dictionary    Content-Type=application/json

    # Fetch market average price
    ${mresp}=    GET On Session    tradin    /api/trades/market/average    params=isin=${isin}    headers=${headers}

    BuiltIn.Log To Console    MARKET AVERAGE RESPONSE: ${mresp.text}

    ${mjson}=    Set Variable    ${mresp.json()}

    ${marketAvg}=    Set Variable    ${mjson['average']}

    RETURN    ${marketAvg}

Retry Execution With Backoff
    [Arguments]    ${tradeId}    ${max}=8
    ${headers}=    Create Dictionary    Content-Type=application/json
    FOR    ${i}    IN RANGE    1    ${max}
        ${resp}=    GET On Session    tradin    /api/trades/get    params=id=${tradeId}    headers=${headers}
        ${json}=    Set Variable    ${resp.json()}
        ${state}=    Set Variable    ${json['state']}
        Log    Attempt ${i}: ${state}

        # Final states → return immediately
        Run Keyword If    '${state}' == 'CONFIRMED'    Return From Keyword    ${json}
        Run Keyword If    '${state}' == 'EXECUTED'     Return From Keyword    ${json}
        Run Keyword If    '${state}' == 'REJECTED'     Return From Keyword    ${json}
        Run Keyword If    '${state}' == 'CANCELLED'    Return From Keyword    ${json}

        Sleep    ${i * 0.4}s
    END
    Fail    Trade ${tradeId} did not reach final state


Check Partial Fill Happened
    [Arguments]    ${tradeId}

    # Fetch trade
    ${resp}=    GET On Session    tradin    /api/trades/get    params=id=${tradeId}
    ${json}=    Set Variable    ${resp.json()}

    ${history}=    Set Variable    ${json['history']}
    ${filled}=     Set Variable    ${json['filled']}
    ${quantity}=   Set Variable    ${json['quantity']}
    ${state}=      Set Variable    ${json['state']}

    Log To Console    State: ${state}
    Log To Console    Filled: ${filled}/${quantity}
    Log To Console    History: ${history}

    # Condition A: Contains PARTIAL
    ${has_partial}=    Run Keyword And Return Status    List Should Contain Value    ${history}    PARTIAL

    # Condition B: Contains PARTIAL_FILL:x
    ${has_partial_fill}=    Run Keyword And Return Status    Should Match Regexp    ${history}    (?i).*PARTIAL_FILL.*

    # At least one must be true
    Run Keyword If    not (${has_partial} or ${has_partial_fill})    Fail    No partial fill detected

    RETURN    ${filled}

Capture Rapid Market Ticks
    [Arguments]    ${isin}    ${count}=10    ${delay}=0.1s
    @{ticks}=    Create List
    FOR    ${i}    IN RANGE    ${count}
        ${resp}=    GET On Session    tradin    /api/trades/market    params=isin=${isin}
        ${json}=    Set Variable    ${resp.json()}
        Append To List    ${ticks}    ${json['price']}
        Sleep    ${delay}
    END
    RETURN    ${ticks}

*** Test Cases ***

Scenario 1 - Create And Confirm Valid Trade
    ${trader}=    Set Variable    T-saffin
    ${isin}=      Set Variable    US0001
    ${side}=      Set Variable    BUY
    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    111.50
    Log To Console    Created Trade ID: ${tradeId}
    Should Be Equal As Strings    ${json['state']}    DUPLICATE

Scenario 2 - Invalid ISIN
    ${trader}=    Set Variable    T-saffin
    ${isin}=      Set Variable    dummy
    ${side}=      Set Variable    BUY
    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    111.50

    # Expect API to reject
    Should Be Equal As Integers    ${json.status_code}    400

    Log    Invalid ISIN response: ${json}

    # Validate error message
    Should Contain    ${json['error']}    Invalid ISIN

    # Trade ID should NOT exist
    Run Keyword And Expect Error    *    Set Variable    ${json['tradeId']}

Scenario 3 - Price Deviation Exceeds Tolerance
    # 1. Setup
    ${trader}=      Set Variable    T-HighDeviation
    ${isin}=        Set Variable    US0001
    ${side}=      Set Variable    BUY

    # 2. Subscribe to market so the average price exists
    ${avg}=    Validate Price Deviation   ${isin}    5

    ${badLimit}=    Evaluate    float(${avg}) * 100
    Log To Console    averageFound: ${avg}

    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    ${badLimit}
    Log To Console  Trade Response: ${json}


Scenario 4- Execute Already Confirmed Trade
    # 1. Prepare test data
    ${trader}=      Set Variable    DUPLICATE_TRADER
    ${isin}=        Set Variable    US0001
    ${limit}=       Set Variable    100.50
    ${side}=      Set Variable    BUY

    ${avg}=    Validate Price Deviation   ${isin}    5

    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    ${avg}

    ${tradeId}=     Set Variable    ${json['tradeId']}
    Log To Console  First Trade ID: ${tradeId}

    #Submit the SAME trade again to cause duplicate detection
    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    ${avg}
    Should Be Equal As Strings    ${json['state']}    DUPLICATE

Scenario 5 - Random Failure Simulation
    ${trader}=      Set Variable    DUPLICATE_TRADER
    ${isin}=        Set Variable    US0001
    ${limit}=       Set Variable    100.50
    ${side}=      Set Variable    BUY

    ${avg}=    Validate Price Deviation   ${isin}    5

    ${badLimit}=    Evaluate    float(${avg}) / 10

    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    ${badLimit}
    ${tradeId}=    Set Variable    ${json['tradeId']}

    # Poll until the system finishes (may succeed or fail)
    ${final}=    Retry Execution With Backoff    ${tradeId}

    Log To Console    Final state: ${final['state']}


Scenario 6 - Verify Partial Fill Execution
    [Documentation]    Creates a trade and verifies that partial fills occurred.

    # --- Step 1: Create a trade that triggers partial fill ---
    ${trader}=        Set Variable    T-saffin
    ${isin}=          Set Variable    US0002
    ${quantity}=      Set Variable    200
    ${side}=          Set Variable    BUY
    ${limitPrice}=    Set Variable    -1.0

    # Your create keyword returns: ${json} + ${tradeId}
    ${json}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    100    ${side}    ${limitPrice}


    Log To Console    Created Trade ID: ${tradeId}
    Log To Console    Initial State: ${json['state']}

    # --- Step 2: Verify partial fills happened ---
    ${filled}=    Check Partial Fill Happened    ${tradeId}

    Log To Console    Partial Fills Confirmed. Final Filled Quantity: ${filled}

Scenario 8 - Multiple Concurrent Traders
    ${isin}=          Set Variable    US0002
    @{traders}=    Create List    T-A    T-B    T-C
    @{tradeIds}=   Create List
    ${avg}=    Validate Price Deviation   ${isin}    5
    FOR    ${tr}    IN    @{traders}
        ${resp}    ${tradeId}=   Create Trade And Return Id    ${tr}    US0001    100    BUY    ${avg}
        Append To List    ${tradeIds}    ${tradeId}
    END

    FOR    ${tId}    IN    @{tradeIds}
        ${final}=    Retry Execution With Backoff    ${tId}
        Log    Trader result: ${tId} -> ${final['state']}
        Should Contain Any
        ...    ${final['state']}
        ...    EXECUTED
        ...    CONFIRMED
        ...    PARTIAL
        ...    REJECTED
    END

Scenario 9 - Rapid Market Update
    ${prices}=    Capture Rapid Market Ticks    US0001
    ${unique}=    Remove Duplicates    ${prices}


Scenario 10 - Risk breach Attempt
    # 1. Build overlimit trade body
    ${trader}=      Set Variable    RISKY_TRADER
    ${isin}=        Set Variable    US0001
    ${side}=      Set Variable    BUY

    # 2. Submit trade (expect REJECTED state)
    ${resp}    ${tradeId}=   Create Trade And Return Id    ${trader}    ${isin}    11111111    ${side}    111.50

    # 3. Verify rejection happened
    Should Be Equal As Strings    ${resp['state']}     REJECTED
    Should Be Equal As Strings    ${resp['reason']}    Exposure breach

