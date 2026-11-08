package com.kkargas.gpm.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.kkargas.gpm.enums.PolicyState;
import com.kkargas.gpm.model.Policy;
import com.kkargas.gpm.model.PolicyCategory;
import com.kkargas.gpm.service.LocalGroupPolicyService;
import com.kkargas.gpm.service.PolicyService;


@Controller
public class PolicyController {


    private final PolicyService policyService;

    private final LocalGroupPolicyService
            localGroupPolicyService;


    public PolicyController(
            PolicyService policyService,
            LocalGroupPolicyService localGroupPolicyService) {

        this.policyService =
                policyService;

        this.localGroupPolicyService =
                localGroupPolicyService;
    }


    /*
     * =========================================================
     * HOME
     * =========================================================
     */

    @GetMapping("/")
    public String home() {

        return "redirect:/policies";
    }


    /*
     * =========================================================
     * POLICIES PAGE
     * =========================================================
     */

    @GetMapping("/policies")
    public String getPolicyPage(
            Model model) {


        List<PolicyCategory> categories =
                policyService
                        .loadCategories();


        int totalPolicies =
                categories.stream()
                        .mapToInt(
                                category ->
                                        category
                                                .getPolicies()
                                                .size()
                        )
                        .sum();


        model.addAttribute(
                "categories",
                categories
        );


        model.addAttribute(
                "policyCount",
                totalPolicies
        );


        return "Policies";
    }


    /*
     * =========================================================
     * TOGGLE POLICY
     * =========================================================
     */

    @PostMapping("/policies/toggle")
    public String togglePolicy(
            @RequestParam String policyId,
            @RequestParam boolean enabled,
            @RequestParam(required = false) String selectedValue) {


        Policy policy =
                policyService
                        .findPolicyById(policyId);


        if (enabled && "apps-background-deny".equals(policyId)) {
            if (selectedValue == null || !java.util.Set.of("0", "1", "2").contains(selectedValue)) {
                throw new IllegalArgumentException("Invalid background app policy option");
            }
            policy.setEnabledValue(selectedValue);
        }

        PolicyState targetState =
                enabled
                        ? PolicyState.ENABLED
                        : PolicyState.NOT_CONFIGURED;


        boolean success =
                localGroupPolicyService
                        .applyPolicy(
                                policy,
                                targetState
                        );


        System.out.println(
                policy.getName()
                + " -> "
                + targetState
                + " | LGPO success: "
                + success
        );


        return "redirect:/policies";
    }

}