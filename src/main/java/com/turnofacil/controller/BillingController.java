package com.turnofacil.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.turnofacil.model.Subscription;
import com.turnofacil.model.User;
import com.turnofacil.model.enums.Plan;
import com.turnofacil.repository.SubscriptionRepository;
import com.turnofacil.repository.UserRepository;
import com.turnofacil.service.BusinessConfigService;
import com.turnofacil.service.PlanLimitsService;
import com.turnofacil.service.StripeService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final StripeService stripeService;
    private final SubscriptionRepository subscriptionRepo;
    private final UserRepository userRepo;
    private final PlanLimitsService planLimitsService;
    private final BusinessConfigService businessConfigService;

    public BillingController(StripeService stripeService,
                             SubscriptionRepository subscriptionRepo,
                             UserRepository userRepo,
                             PlanLimitsService planLimitsService,
                             BusinessConfigService businessConfigService) {
        this.stripeService = stripeService;
        this.subscriptionRepo = subscriptionRepo;
        this.userRepo = userRepo;
        this.planLimitsService = planLimitsService;
        this.businessConfigService = businessConfigService;
    }

    @GetMapping("/admin/billing")
    public String billingPage(Model model, Authentication auth, HttpServletRequest request) {
        User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
        Subscription sub = subscriptionRepo.findByUserId(user.getId()).orElse(null);

        model.addAttribute("subscription", sub);
        model.addAttribute("effectivePlan", sub != null ? sub.getEffectivePlan() : Plan.FREE);
        model.addAttribute("appointmentsRemaining", planLimitsService.getAppointmentsRemainingThisMonth(user.getId()));
        model.addAttribute("servicesRemaining", planLimitsService.getServicesRemaining(user.getId()));
        model.addAttribute("currentUrl", request.getRequestURI());
        model.addAttribute("businessConfig", businessConfigService.getByUserId(user.getId()));

        return "admin/billing";
    }

    @PostMapping("/admin/billing/checkout")
    public String createCheckout(@RequestParam String plan,
                                 Authentication auth,
                                 RedirectAttributes redirectAttrs) {
        try {
            User user = userRepo.findByEmailIgnoreCase(auth.getName()).orElseThrow();
            Plan targetPlan = Plan.valueOf(plan.toUpperCase());

            if (targetPlan == Plan.FREE) {
                redirectAttrs.addFlashAttribute("error", "No puedes suscribirte al plan gratuito");
                return "redirect:/admin/billing";
            }

            String checkoutUrl = stripeService.createCheckoutSession(user, targetPlan);
            return "redirect:" + checkoutUrl;
        } catch (StripeException e) {
            log.error("Error creating Stripe checkout: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("error", "Error al procesar el pago. Inténtalo de nuevo.");
            return "redirect:/admin/billing";
        }
    }

    @PostMapping("/webhook/stripe")
    @ResponseBody
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event event;
        try {
            event = stripeService.constructEvent(payload, sigHeader);
        } catch (Exception e) {
            log.error("Invalid Stripe webhook signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        log.info("Stripe webhook received: {}", event.getType());

        switch (event.getType()) {
            case "checkout.session.completed" -> stripeService.handleCheckoutCompleted(event);
            case "invoice.paid" -> stripeService.handleInvoicePaid(event);
            case "invoice.payment_failed" -> stripeService.handleInvoicePaymentFailed(event);
            case "customer.subscription.deleted" -> stripeService.handleSubscriptionDeleted(event);
            default -> log.debug("Unhandled event type: {}", event.getType());
        }

        return ResponseEntity.ok("OK");
    }
}
