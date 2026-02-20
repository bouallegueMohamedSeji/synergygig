package tn.esprit.synergygig.test;

import com.stripe.model.PaymentIntent;
import tn.esprit.synergygig.services.PaymentService;

public class StripeTest {

    public static void main(String[] args) {

        try {

            PaymentService service = new PaymentService();

            // 🔵 TEST CREATE PAYMENT
            PaymentIntent intent = service.createPaymentIntent(100);

            System.out.println("Payment Intent ID: " + intent.getId());

            // 🔐 TEST CAPTURE
            service.capturePayment(intent.getId());
            System.out.println("Payment captured successfully.");

            // 🔄 TEST REFUND
            service.refundPayment(intent.getId());
            System.out.println("Payment refunded successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}