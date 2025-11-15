package com.trading;

import com.trading.model.Instrument;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class InstrumentService {

    private final List<String> validIsins = List.of("US0001", "US0002", "GB0001", "JP0001");

    public Optional<Instrument> fetchByIsin(String isin) {

        if (isin == null || !validIsins.contains(isin)) {
            return Optional.empty();
        }

        double yield = Math.round(ThreadLocalRandom.current().nextDouble(0.5, 8.0) * 100.0) / 100.0;
        String tenor = List.of("1Y", "2Y", "5Y", "10Y")
                .get(ThreadLocalRandom.current().nextInt(4));

        String currency = isin.startsWith("US") ? "USD" :
                isin.startsWith("GB") ? "GBP" : "JPY";

        return Optional.of(new Instrument(isin, currency, yield, tenor));
    }
}
