package tn.esprit.synergygig.services;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;

public class PaymentService {

    static {
        Stripe.apiKey = System.getenv("");
    }

    // ================= CREATE PAYMENT INTENT =================
    public PaymentIntent createPaymentIntent(double amount) throws Exception {

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount((long) (amount * 100)) // centimes
                        .setCurrency("usd")
                        .setCaptureMethod(
                                PaymentIntentCreateParams.CaptureMethod.MANUAL
                        )
                        .build();

        return PaymentIntent.create(params);
    }

    // ================= CAPTURE PAYMENT =================
    public void capturePayment(String paymentIntentId) throws Exception {

        PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
        intent.capture();
    }

    // ================= REFUND PAYMENT =================
    public void refundPayment(String paymentIntentId) throws Exception {

        RefundCreateParams params =
                RefundCreateParams.builder()
                        .setPaymentIntent(paymentIntentId)
                        .build();

        Refund.create(params);
    }
    // ================= GENERATE PAYMENT LINK (TEST VERSION) =================
    public String generatePaymentLink(String paymentIntentId) {
        return "https://dashboard.stripe.com/test/payments/" + paymentIntentId;
    }

}