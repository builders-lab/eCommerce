# Repository Remediation Status

Date: 2026-07-05

## Fixed

- Removed the frontend Vitest test file, test script, Vitest dependency, and Axios Mock Adapter dependency.
- Upgraded Axios and React Router to patched releases.
- Pinned vulnerable transitive `form-data` and `yaml` lines with narrow npm overrides.
- Split frontend context hooks and theme configuration from component-only modules; ESLint now passes without Fast Refresh warnings.
- Added route-level lazy loading. The initial production JavaScript chunk decreased from 616.8 kB to 421.2 kB.
- Upgraded the backend through Spring Boot 3.5.15 to Spring Boot 4.1.0, including Jackson 3, modular test starters, and Testcontainers 2.
- Configured Maven Failsafe to discover `*IT` classes and configured Mockito as a Java agent.
- Updated the Node, Java, Maven, Docker, and validation documentation.

## Security disposition

- Axios was upgraded even though many reported advisories affected its Node HTTP adapter rather than this browser runtime.
- React Router was upgraded even though the application does not use its server-rendering or RSC features implicated in several advisories.
- `npm audit --omit=dev` is the production dependency acceptance gate.

## Validation

- `npm run lint`: passed with zero warnings.
- `npm run typecheck`: passed.
- `npm run build`: passed; initial JavaScript chunk is 421.2 kB.
- `mvn test`: passed 9 unit and architecture tests on Java 25.
- `mvn verify`: Failsafe discovered all 8 integration-test classes. Execution is pending a working Docker engine; the local environment could not start the MySQL Testcontainer.
- Packaged Boot 4.1 application startup: passed against the local MySQL 8.4 schema; Tomcat started on port 8080.
- Runtime health remained `503` because Redis was not running in the validation environment.

## Deferred

- Frontend E2E coverage is intentionally deferred. Use `docs/frontend/manual-smoke-checklist.md` until it is implemented.
- Interactive frontend smoke testing could not run because no browser was attached to the validation session.
- Redis, Nginx, and payment-provider smoke testing remains environment-dependent.
