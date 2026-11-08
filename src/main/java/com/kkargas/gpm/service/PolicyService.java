package com.kkargas.gpm.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kkargas.gpm.enums.PolicyState;
import com.kkargas.gpm.model.Policy;
import com.kkargas.gpm.model.PolicyCategory;

@Service
public class PolicyService {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final LocalGroupPolicyService
            localGroupPolicyService;


    public PolicyService(
            LocalGroupPolicyService
                    localGroupPolicyService) {

        this.localGroupPolicyService =
                localGroupPolicyService;
    }


    private static final List<String> POLICY_FILES = List.of(
            "usb-policies.json",
            "removable-storage-policies.json",
            "app-performance-policies.json",
            "storage-filesystem-policies.json",
            "user-security-policies.json"
    );

    public List<PolicyCategory> loadCategories() {
        try {
            java.util.List<PolicyCategory> categories = new java.util.ArrayList<>();
            for (String file : POLICY_FILES) {
                try (InputStream stream = getClass().getResourceAsStream("/data/" + file)) {
                    if (stream == null) {
                        throw new IllegalStateException("Missing policy definition: " + file);
                    }
                    categories.add(objectMapper.readValue(stream, PolicyCategory.class));
                }
            }
            List<Policy> allPolicies = categories.stream()
                    .flatMap(category -> category.getPolicies().stream())
                    .toList();
            java.util.Map<String, PolicyState> states =
                    localGroupPolicyService.getPolicyStates(allPolicies);
            for (Policy policy : allPolicies) {
                policy.setPolicyState(states.getOrDefault(
                        policy.getId(), PolicyState.NOT_CONFIGURED));
            }
            for (Policy policy : allPolicies) {
                if ("apps-background-deny".equals(policy.getId())) {
                    policy.setConfiguredValue(localGroupPolicyService.getConfiguredValue(policy));
                }
            }
            return categories;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read policy definitions", e);
        }
    }


    public Policy findPolicyById(
            String id) {


        return loadCategories()
                .stream()

                .flatMap(
                        category ->
                                category
                                    .getPolicies()
                                    .stream()
                )

                .filter(
                        policy ->
                                policy
                                    .getId()
                                    .equals(id)
                )

                .findFirst()

                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Unknown policy: "
                                        + id
                                )
                );
    }
}