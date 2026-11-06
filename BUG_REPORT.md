# BUG-001 — Claim minimum amount differs between API and domain

Status: **Pending Clarification**. QA decision: **Pending Review**.
Recorded: 2026-11-06. Specification reference: CL-03 / amount validation.

## Summary and evidence

Both API request contracts declare a minimum claim amount of **0.01**:

- [BFF contract](demo-app/code/bff-service/src/main/resources/openapi/bff-api.yml), `SubmitClaimRequest.claimAmount`, line 653.
- [Claims contract](demo-app/code/claims-service/src/main/resources/openapi/claims-api.yml), `CreateClaimRequest.claimAmount`, line 693.

The [UI schema](demo-app/code/demo-app-ui/src/features/claims/schemas/claim-schemas.ts)
also enforces 0.01. However, the [Claim constructor](demo-app/code/claims-service/src/main/java/com/example/demo/domain/claim/Claim.java),
lines 90–93, rejects only amounts ≤0 or >1,000,000. Positive sub-cent values pass
that domain guard.

This cross-layer inconsistency is confirmed by source and unit execution. Whether
to strengthen the domain rule or revise the business/API policy requires
clarification. HTTP acceptance of these inputs has **not** been established.

## Expected versus actual

The expected result below is the existing test's API-aligned expectation; its
adoption as the harmonized domain policy remains Pending Clarification.

| Amount | Expected against API minimum | Actual direct domain result |
| --- | --- | --- |
| 0.001 | Reject with InvalidClaimException | Claim created; no exception |
| 0.009 | Reject with InvalidClaimException | Claim created; no exception |
| 0.009999 | Reject with InvalidClaimException | Claim created; no exception |

The existing parameterized method
`ClaimFieldValidationTest.apiMinimumExpectationRejectsPositiveAmountsBelowOneCent`
produces these three failures. Its neighboring characterization test confirms
that each value is retained in the aggregate and ClaimSubmitted event without
domain rounding. Both tests remain active and unchanged.

## Reproduction

Prerequisites: Java 21, Maven and repository dependencies. No Spring context,
database, Docker services or network endpoint is needed by the test itself;
Maven may need dependency downloads on the first run.

1. From the repository root, run the existing validation class:

   ```sh
   mvn -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimFieldValidationTest -Dcheckstyle.skip=true test
   ```

2. Inspect `demo-app/code/claims-service/target/surefire-reports/TEST-com.example.demo.domain.claim.ClaimFieldValidationTest.xml`.
3. Find the three parameterized failures in the method above. Each invocation
   constructs a fresh Claim with valid fixture UUIDs, a valid past incident date,
   valid location/description, and one amount from the table.
4. The assertion expects InvalidClaimException, but construction succeeds.

Observed class result in the clean-export run: **52 passed, 3 failed, 0 errors,
0 skipped**. Maven exits 1. The failure states that InvalidClaimException was
expected but nothing was thrown. No test is disabled or marked as an expected
failure. [Test source](demo-app/code/claims-service/src/test/java/com/example/demo/domain/claim/ClaimFieldValidationTest.java),
lines 145–171; [execution record](demo-app/QA_AI_WORK_JOURNAL.md).

## Business impact and required clarification

The domain enforces a weaker minimum than the published API/UI. An entry point
that relies on domain validation could create amounts outside the stated policy,
leading to inconsistent acceptance or downstream monetary rounding. Those are
potential impacts; API bypass, persisted rounding and actual financial loss were
not verified in this scope.

The product/API owner should confirm the common minimum and permitted precision
or rounding policy. Remediation, severity/priority assignment and release
disposition remain **Pending Review**. No production fix or expectation change
has been made. Keep the three failing cases as evidence until clarification.
