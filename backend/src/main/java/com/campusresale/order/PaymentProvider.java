package com.campusresale.order;

public interface PaymentProvider {

    String providerCode();

    PaymentProviderCallback simulateSuccessfulPayment(PaymentOrderRecord payment);
}
