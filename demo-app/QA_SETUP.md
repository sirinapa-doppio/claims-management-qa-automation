# Local QA setup

Use this guide for the implemented unit, component and API integration tests.
The environment is for local development. CDC is not required.

## Prerequisites

- Docker with Compose v2 or later, running with enough memory for PostgreSQL,
  Keycloak, Kafka/Zookeeper, Redis, the two Java services and UI.
- Java 21 and Maven, Python 3.9+, and Node/npm
  compatible with package-lock.json. The recorded frontend runs used Node 26.13.
- Network access on first setup for Maven/npm dependencies and Docker images.
- Host ports 5432, 8180, 9092, 9093, 8080, 8090 and 3001 available.
  Container and network names are fixed: use a separate Docker host/context if
  another copy is already running. Do not stop another engineer's environment.

## First setup and application startup

From the repository root:

```sh
cd demo-app
python3 configure-local.py
bash start-infra.sh --wait
bash start-app.sh -d --wait
```

The helper uses .env.example and config/demo-app-realm.example.json to generate
random passwords and the BFF service-account secret. Only the ignored .env and
docker/compose/keycloak/demo-app-realm.json contain usable credentials. The .env
has mode 0600; the realm is readable by the container's UID 1000 (mode 0644)
inside a private host directory (mode 0700), and is mounted read-only.
Existing local files are never overwritten. Keep these two files
together; changing credentials does not rotate an existing database's password.
If only an existing realm is present, supply a matching .env rather than generating
different credentials. Do not use docker/compose/.env: it is a separate legacy file.

start-app.sh packages both services before Docker builds their images. It skips
tests/static analysis explicitly for packaging, so the known failing tests do not
prevent application startup. It is not evidence that those checks passed.
Liquibase creates the database tables. BFF login provisions the test users in
Claims; no manual SQL seed, CDC user, connector, or additional test is required.

Open http://localhost:3001. Use the QA_CLAIMANT_EMAIL / QA_CLAIMANT_PASSWORD or
QA_ADMIN_EMAIL / QA_ADMIN_PASSWORD in your private .env to sign in. The Keycloak
bootstrap administrator is a separate account. Fresh setups use the same
KEYCLOAK_ADMIN_PASSWORD for bootstrap and BFF admin access. The optional
KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD override only preserves an existing local setup.

Readiness and configuration checks (Compose config --quiet does not print secrets):

```sh
docker compose --env-file .env -f docker-compose.infra.yml -f docker-compose.apps.yml config --quiet
docker compose --env-file .env -f docker-compose.infra.yml -f docker-compose.apps.yml ps
curl --fail http://localhost:8080/actuator/health
curl --fail http://localhost:8090/actuator/health
```

## Run the existing tests

From the repository root, install frontend dependencies with the committed lockfile:

```sh
npm --prefix demo-app/code/demo-app-ui ci --no-audit --no-fund
```

Unit status tests (recorded result: 59 passed):

```sh
mvn -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimStatusTest,ClaimStatusTransitionTest -Dcheckstyle.skip=true test
```

Creation/validation tests (recorded result: 56 passed, 3 failed):

```sh
mvn -f demo-app/code/claims-service/pom.xml -Djunit-jupiter.version=5.11.4 -Dtest=ClaimCreationTest,ClaimFieldValidationTest -Dcheckstyle.skip=true test
```

The three active sub-cent cases preserve the API minimum 0.01 versus domain >0
discrepancy. Expect a nonzero exit until that potential defect is resolved through
review. No exclusion or failure-ignore switch is needed. The version override
keeps JUnit 5 without changing the production POM. These commands allow initial
dependency downloads; use -o only after dependencies have been cached.

Component validation (recorded result: 30 passed):

```sh
cd demo-app/code/demo-app-ui
npm test -- tests/components/claim-form-validation.test.tsx
```

API integration, from the same UI directory (recorded result: 1 passed):

```sh
npx --no-install playwright test --config=playwright.api.config.ts
```

The API test reads the rendered local realm's claimant/admin credentials by
default, so no shell export is required. QA_CLAIMANT_EMAIL / QA_CLAIMANT_PASSWORD,
QA_ADMIN_EMAIL / QA_ADMIN_PASSWORD and QA_BFF_URL can override the defaults.
It uses separate API cookie contexts; no browser installation is required.
Each run creates and retains one unique claim, and updates only that new claim.
Reports, node_modules and Maven target outputs remain ignored.

## Verification limits

See QA_AI_WORK_JOURNAL.md for this delivery's actual configuration/build checks.
Existing-environment health and a successful build do not establish that a fresh
Docker stack imported the new realm and completed the workflow. This cycle does
not verify CDC, production deployment, TLS, or credential rotation.
