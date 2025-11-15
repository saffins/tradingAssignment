*** Settings ***
Library    RequestsLibrary
Library    Collections
Library    BuiltIn
Library    OperatingSystem
Library    String
Library    JSONLibrary
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
    ${resp}=    GET On Session    tradin    url=/api/exposure    params=trader=${trader}
    ${json}=    Set Variable    ${resp.json()}
    IF    not ${json['allowed']}
        Fail    Exposure breach for trader ${trader}
    END
    RETURN    ${json}


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
    ${limitPrice}=  Convert To Number     ${limitPrice}

    ${body}=    Build Trade Body    ${trader}    ${isin}    ${quantity}    ${side}    ${limitPrice}
    ${json_body}=    Evaluate    json.dumps(${body})    json

    # Capture even 400 / 403 responses without failing
    ${resp}=    POST On Session    tradin    url=/api/trades/create    data=${json_body}    expected_status=any
    Log To Console    RESPONSE: ${resp.text}

    ${json}=    Set Variable    ${resp.json()}
    RETURN    ${json}    ${json.get('tradeId')}


Validate Price Deviation
    [Arguments]    ${isin}
    ${resp}=    GET On Session    tradin    url=/api/trades/market/average    params=isin=${isin}
    ${json}=    Set Variable    ${resp.json()}
    RETURN    ${json['average']}


Retry Execution With Backoff
    [Arguments]    ${tradeId}    ${max}=10

    FOR    ${i}    IN RANGE    1    ${max}
        ${resp}=    GET On Session    tradin    url=/api/trades/get    params=id=${tradeId}
        ${json}=    Set Variable    ${resp.json()}
        ${state}=   Set Variable    ${json['state']}

        Log To Console    Attempt ${i}: ${state}

        IF    '${state}' == 'CONFIRMED'
            RETURN    ${json}
        END
        IF    '${state}' == 'EXECUTED'
            RETURN    ${json}
        END
        IF    '${state}' == 'REJECTED'
            RETURN    ${json}
        END
        IF    '${state}' == 'CANCELLED'
            RETURN    ${json}
        END

        Sleep    ${i * 0.4}s
    END

    Fail    Trade ${tradeId} did not reach final state


Check Partial Fill Happened
    [Arguments]    ${tradeId}

    ${resp}=    GET On Session    tradin    url=/api/trades/get    params=id=${tradeId}
    ${json}=    Set Variable    ${resp.json()}

    ${history}=    Set Variable    ${json['history']}
    ${filled}=     Set Variable    ${json['filled']}
    ${quantity}=   Set Variable    ${json['quantity']}

    Log To Console    History: ${history}
    Log To Console    Filled: ${filled}/${quantity}

    ${partial}=    Run Keyword And Return Status    List Should Contain Value    ${history}    PARTIAL

    IF    not ${partial}
        Fail    No partial fill detected
    END

    RETURN    ${filled}


Capture Rapid Market Ticks
    [Arguments]    ${isin}    ${count}=10
    @{ticks}=    Create List

    FOR    ${i}    IN RANGE    ${count}
        ${resp}=    GET On Session    tradin    url=/api/trades/market    params=isin=${isin}
        ${json}=    Set Variable    ${resp.json()}
        Append To List    ${ticks}    ${json['price']}
        Sleep    0.1s
    END

    RETURN    ${ticks}


Capture Trade Metrics
    [Arguments]    ${tradeId}

    ${resp}=    GET On Session    tradin    url=/api/trades/get    params=id=${tradeId}
    ${json}=    Set Variable    ${resp.json()}

    ${start}=    Set Variable    ${json['executionStartTime']}
    ${end}=      Set Variable    ${json['executionEndTime']}
    ${retries}=  Set Variable    ${json['retryCount']}

    ${latency}=    Evaluate    ${end} - ${start}

    Log To Console    METRIC - Latency(ms): ${latency}
    Log To Console    METRIC - Retry Count: ${retries}

    Set Test Variable    ${METRIC_LATENCY}    ${latency}
    Set Test Variable    ${METRIC_RETRIES}    ${retries}

    RETURN    ${json}


*** Test Cases ***

Scenario 1 - Create And Confirm Valid Trade
    ${json}    ${tradeId}=   Create Trade And Return Id    T-saffin    US0001    100    BUY    111.50
    ${final}=    Retry Execution With Backoff    ${tradeId}
    Capture Trade Metrics    ${tradeId}


Scenario 2 - Invalid ISIN
    ${resp}    ${id}=   Create Trade And Return Id    T-saffin    dummy    100    BUY    111.50
    Should Contain    ${resp['error']}    Invalid ISIN


Scenario 3 - Price Deviation Exceeds Tolerance
    ${avg}=    Validate Price Deviation    US0001
    ${badLimit}=    Evaluate    float(${avg}) * 100

    ${json}    ${tradeId}=   Create Trade And Return Id    T-dev    US0001    100    BUY    ${badLimit}

    ${final}=    Retry Execution With Backoff    ${tradeId}
    Should Be Equal As Strings    ${final['state']}    REJECTED


Scenario 4 - Execute Already Confirmed Trade
    ${avg}=    Validate Price Deviation    US0001
    ${json}    ${tid}=   Create Trade And Return Id    T-dupe    US0001    100    BUY    ${avg}
    ${json2}    ${tid2}=   Create Trade And Return Id    T-dupe    US0001    100    BUY    ${avg}
    Should Be Equal As Strings    ${json2['state']}    DUPLICATE


Scenario 5 - Random Failure Simulation
    ${avg}=    Validate Price Deviation    US0001
    ${badLimit}=    Evaluate    float(${avg}) / 10

    ${json}    ${tid}=   Create Trade And Return Id    T-rfail    US0001    100    BUY    ${badLimit}

    ${final}=    Retry Execution With Backoff    ${tid}
    Log To Console    Final: ${final}
    Capture Trade Metrics    ${tid}


Scenario 6 - Verify Partial Fill Execution
    ${json}    ${tid}=   Create Trade And Return Id    T-partial    US0002    100    BUY    -1.0

    ${filled}=    Check Partial Fill Happened    ${tid}
    Log To Console    Partial fill quantity: ${filled}


Scenario 8 - Multiple Concurrent Traders
    @{traders}=    Create List    T-A    T-B    T-C
    @{tradeIds}=   Create List

    ${avg}=    Validate Price Deviation   US0001

    FOR    ${tr}    IN    @{traders}
        ${json}    ${tid}=   Create Trade And Return Id    ${tr}    US0001    100    BUY    ${avg}
        Append To List    ${tradeIds}    ${tid}
    END

    FOR    ${tId}    IN    @{tradeIds}
        ${final}=    Retry Execution With Backoff    ${tId}
        Log To Console    ${tId}: ${final['state']}
    END


Scenario 9 - Rapid Market Update
    ${ticks}=    Capture Rapid Market Ticks    US0001
    Log To Console    Ticks: ${ticks}


Scenario 10 - Risk Breach Attempt
    ${json}    ${tid}=   Create Trade And Return Id    RISKY_TRADER    US0001    9999999    BUY    111.50
    Should Be Equal As Strings    ${json['state']}    REJECTED
    Should Be Equal As Strings    ${json['reason']}    Exposure breach
