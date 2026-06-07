package com.campusresale.order;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    @Override
    public String providerCode() {
        return "SIMULATED";
    }

    @Override
    public PaymentProviderCallback simulateSuccessfulPayment(PaymentOrderRecord payment) {
        Instant now = Instant.now();
        return new PaymentProviderCallback(
                providerCode(),
                "SIM-CB-" + payment.paymentNo(),
                "SIM-TXN-" + payment.paymentNo(),
                payment.id(),
                payment.amount(),
                "SUCCEEDED",
                now,
                "{\"source\":\"simulate\"}"
        );
    }
}
