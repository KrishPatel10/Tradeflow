# Continuous delivery

TradeFlow includes a GitHub Actions pipeline at `.github/workflows/ci.yml`. It runs for every push and pull request and can also be started manually.

## Quality gates

The pipeline runs four independent concerns:

1. The Maven `verify` lifecycle, including PostgreSQL, Redis, and Kafka Testcontainers tests.
2. Angular unit tests and the production bundle build from the locked pnpm dependency graph.
3. Playwright journeys against a disposable five-container stack, including administrator funding, a lost transfer response, safe retry, and Kafka notification delivery.
4. Backend and frontend image builds followed by Trivy scans that reject fixable critical vulnerabilities.

The image job runs only after every test job succeeds. Images use the Git commit SHA as the immutable tag. A successful push to `main` publishes them to GitHub Container Registry as:

```text
ghcr.io/<owner>/<repository>/backend:<commit-sha>
ghcr.io/<owner>/<repository>/frontend:<commit-sha>
```

Pull requests build and scan the same images but never publish them. No long-lived registry password is needed; publishing uses the workflow-scoped `GITHUB_TOKEN` with package-write permission.

## Repository setup

After the project is pushed to GitHub, protect `main` and require these checks before merge:

- Backend verification
- Angular verification
- Browser journeys
- Build and scan images

Dependabot checks GitHub Actions, npm/pnpm, and Maven dependencies weekly. Review and merge its updates through the same quality gates.
