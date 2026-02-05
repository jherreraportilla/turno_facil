package com.turnofacil.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.turnofacil.model.Subscription;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.Plan;
import com.turnofacil.repository.SubscriptionRepository;
import com.turnofacil.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class StripeService {

    private static final Logger log = LoggerFactory.getLogger(StripeService.class);

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${stripe.prices.pro:}")
    private String proPriceId;

    @Value("${stripe.prices.business:}")
    private String businessPriceId;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private final SubscriptionRepository subscriptionRepo;
    private final UserRepository userRepo;

    public StripeService(SubscriptionRepository subscriptionRepo, UserRepository userRepo) {
        this.subscriptionRepo = subscriptionRepo;
        this.userRepo = userRepo;
    }

    public String createCheckoutSession(User user, Plan plan) throws StripeException {
        Subscription sub = subscriptionRepo.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("No subscription found"));

        // Crear o reusar Stripe Customer
        String customerId = sub.getStripeCustomerId();
        if (customerId == null || customerId.isBlank()) {
            Customer customer = Customer.create(
                    CustomerCreateParams.builder()
                            .setEmail(user.getEmail())
                            .setName(user.getName())
                            .putMetadata("userId", user.getId().toString())
                            .build()
            );
            customerId = customer.getId();
            sub.linkStripeCustomer(customerId);
            subscriptionRepo.save(sub);
        }

        String priceId = plan == Plan.BUSINESS ? businessPriceId : proPriceId;

        Session session = Session.create(
                SessionCreateParams.builder()
                        .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                        .setCustomer(customerId)
                        .addLineItem(
                                SessionCreateParams.LineItem.builder()
                                        .setPrice(priceId)
                                        .setQuantity(1L)
                                        .build()
                        )
                        .setSuccessUrl(baseUrl + "/admin/billing?success=true")
                        .setCancelUrl(baseUrl + "/admin/billing?cancelled=true")
                        .putMetadata("userId", user.getId().toString())
                        .build()
        );

        return session.getUrl();
    }

    public Event constructEvent(String payload, String sigHeader) throws StripeException {
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }

    @Transactional
    public void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
        if (session == null) return;

        String userId = session.getMetadata().get("userId");
        if (userId == null) return;

        subscriptionRepo.findByUserId(Long.parseLong(userId)).ifPresent(sub -> {
            String stripeSubId = session.getSubscription();

            try {
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(stripeSubId);
                Plan plan = determinePlanFromStripePrice(stripeSub);
                LocalDate periodStart = toLocalDate(stripeSub.getCurrentPeriodStart());
                LocalDate periodEnd = toLocalDate(stripeSub.getCurrentPeriodEnd());

                sub.activate(plan, stripeSubId, periodStart, periodEnd);
            } catch (StripeException e) {
                log.error("Error retrieving Stripe subscription: {}", e.getMessage());
                // Fallback: activar con PRO
                sub.activate(Plan.PRO, stripeSubId, LocalDate.now(), LocalDate.now().plusMonths(1));
            }

            subscriptionRepo.save(sub);
            log.info("Subscription activated: userId={}, plan={}", userId, sub.getPlan());
        });
    }

    @Transactional
    public void handleInvoicePaid(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null || invoice.getSubscription() == null) return;

        subscriptionRepo.findByStripeSubscriptionId(invoice.getSubscription()).ifPresent(sub -> {
            try {
                com.stripe.model.Subscription stripeSub = com.stripe.model.Subscription.retrieve(invoice.getSubscription());
                LocalDate periodStart = toLocalDate(stripeSub.getCurrentPeriodStart());
                LocalDate periodEnd = toLocalDate(stripeSub.getCurrentPeriodEnd());
                sub.renewPeriod(periodStart, periodEnd);
            } catch (StripeException e) {
                log.error("Error updating period from invoice: {}", e.getMessage());
            }
            subscriptionRepo.save(sub);
            log.info("Subscription renewed: userId={}", sub.getUser().getId());
        });
    }

    @Transactional
    public void handleInvoicePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer().getObject().orElse(null);
        if (invoice == null || invoice.getSubscription() == null) return;

        subscriptionRepo.findByStripeSubscriptionId(invoice.getSubscription()).ifPresent(sub -> {
            sub.markPastDue();
            subscriptionRepo.save(sub);
            log.warn("Payment failed: userId={}", sub.getUser().getId());
        });
    }

    @Transactional
    public void handleSubscriptionDeleted(Event event) {
        com.stripe.model.Subscription stripeSub =
                (com.stripe.model.Subscription) event.getDataObjectDeserializer().getObject().orElse(null);
        if (stripeSub == null) return;

        subscriptionRepo.findByStripeSubscriptionId(stripeSub.getId()).ifPresent(sub -> {
            sub.cancel();
            subscriptionRepo.save(sub);
            log.info("Subscription cancelled: userId={}", sub.getUser().getId());
        });
    }

    // === Métodos auxiliares ===

    /**
     * Determina el plan de dominio basado en el priceId de Stripe.
     */
    private Plan determinePlanFromStripePrice(com.stripe.model.Subscription stripeSub) {
        String priceId = stripeSub.getItems().getData().get(0).getPrice().getId();
        if (priceId.equals(businessPriceId)) {
            return Plan.BUSINESS;
        }
        return Plan.PRO;
    }

    private LocalDate toLocalDate(Long epoch) {
        if (epoch == null) return null;
        return Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
