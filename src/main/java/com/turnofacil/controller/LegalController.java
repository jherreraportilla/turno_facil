package com.turnofacil.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping("/legal/terms")
    public String termsOfService() {
        return "legal/terms";
    }

    @GetMapping("/legal/privacy")
    public String privacyPolicy() {
        return "legal/privacy";
    }

    @GetMapping("/pricing")
    public String pricing() {
        return "public/pricing";
    }

    @GetMapping("/faq")
    public String faq() {
        return "public/faq";
    }
}
