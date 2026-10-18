# Documentation Index

This repository uses the root `docs/` directory as the source of truth for architecture, frontend behavior, operations guidance, API reference material, and review reports.

## Architecture

- `architecture/module-boundaries.md`: backend package boundaries and dependency rules
- `architecture/readability-recommendations.md`: maintainability and readability guidance
- `architecture/payment-db-evolution-plan.md`: architecture history and DB/payment evolution notes
- `architecture/runtime-cache-portability.md`: Redis cache policy, TTL strategy, and cross-platform runtime notes

## Frontend

- `frontend/route-map.md`: route inventory, route protection, and navigation behavior
- `frontend/state-and-auth.md`: `AuthContext`, `CartContext`, token lifecycle, and access control behavior
- `frontend/api-integration.md`: API prefixes, endpoint usage, and known frontend/backend coupling points
- `frontend/page-flow.md`: page-to-endpoint interaction flows for key UI journeys
- `frontend/manual-smoke-checklist.md`: temporary frontend validation checklist pending E2E coverage

## Operations

- `operations/environment-runbook.md`: local env vars, secrets file, DB config, payment gateway settings, and startup troubleshooting
- `operations/nginx-deployment-runbook.md`: Nginx template/rendering workflow and deployment paths
- `operations/db-change-log.md`: schema change history while runtime migrations remain disabled
- `operations/backend-remediation-status.md`: current remediation status and operational notes

## Reference

- `reference/api-reference.md`: backend API inventory, role/access matrix, frontend route summary, and payment config overview

## Reports

- `reports/security-vulnerability-report.md`: current security-focused findings and mitigations
- `reports/scalability-maintainability-report.md`: runtime scalability and maintainability status
- `reports/documentation-audit.md`: documentation accuracy audit and remaining gaps
- `reports/cart-auth-performance-audit.md`: cart, auth, query-shape, contention, caching, and virtual-thread audit
- `reports/query-shape-followup-audit.md`: follow-up review of cart, catalog, product, review, and related query shapes
- `reports/redundancy-readability-audit.md`: whole-codebase redundancy and readability findings
- `reports/remediation-2026-07-05.md`: current dependency, framework, testing, bundle, and documentation remediation status
- `reports/style-violation-audit.md`: historical audit snapshot retained for traceability
