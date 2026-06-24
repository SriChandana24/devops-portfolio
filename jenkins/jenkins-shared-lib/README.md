# jenkins-shared-lib

Opinionated Jenkins shared library providing standardized CI/CD building
blocks. Currently exposes `deployHelm()` — a complete Helm + kubectl
deploy pipeline callable from a one-line Jenkinsfile.

## Why

- **DRY** — one canonical deploy pipeline reused across every microservice
- **Versioned** — Git tags freeze behavior; consumers pin via `@Library('jenkins-shared-lib@v1.0.0')`
- **Reviewable** — pipeline changes go through PR review on this repo
- **Testable** — `src/` classes are plain Groovy, unit-testable in isolation

## Structure

```
jenkins-shared-lib/
├── vars/
│   ├── deployHelm.groovy           ← entry point: full pipeline as a step
│   └── deployHelm.txt              ← Snippet Generator docs
├── src/
│   └── com/example/
│       └── DeployConfig.groovy     ← reusable config class (OO, testable)
├── resources/
│   └── com/example/
│       └── values-template.yaml    ← shared chart values overlay
└── examples/
    └── Jenkinsfile                 ← minimal consumer example
```

- `vars/` — global pipeline steps; one `.groovy` file per step, filename = step name, must define `call()`
- `src/` — Groovy classes; cannot call `sh`/`echo` directly (pass a `script` reference if needed)
- `resources/` — non-Groovy files (YAML, scripts); load via `libraryResource('path/to/file')`

## Usage

### One-time Jenkins setup

**Manage Jenkins → System → Global Pipeline Libraries → Add:**

- Name: `jenkins-shared-lib`
- Default version: `main` (or a tag like `v1.0.0`)
- Retrieval method: Modern SCM → Git → point at this repo

### Service repo `Jenkinsfile`

```groovy
@Library('jenkins-shared-lib') _

deployHelm(
    chart:       'charts/payments-api',
    releaseName: 'payments-api',
    namespace:   'prod',
    valuesFile:  'values-prod.yaml',
    timeout:     '10m'
)
```

That is the entire Jenkinsfile. The library provides:

1. **Checkout** — `checkout scm`
2. **Helm lint** — validates the chart against the values file
3. **Approve prod deploy** — manual approval gate (auto-enabled when `namespace == 'prod'`)
4. **Helm upgrade --install** — `--atomic --wait` (auto-rollback on failure)
5. **Verify rollout** — `kubectl rollout status` blocks until pods are ready
6. **Post** — success / failure messaging, prints `helm history` on failure

## `deployHelm()` parameters

| Parameter | Required | Default | Description |
| --- | --- | --- | --- |
| `chart` | yes | — | Path to Helm chart |
| `releaseName` | yes | — | Helm release name |
| `namespace` | yes | — | Kubernetes namespace |
| `valuesFile` | no | `values.yaml` | Values overlay file |
| `timeout` | no | `5m` | Per-operation timeout |
| `approve` | no | `true` if `namespace == 'prod'` | Manual approval gate |

## Versioning

Tag releases on Git; consumers pin specific versions in their Jenkinsfiles:

```bash
git tag -a v1.0.0 -m "Initial release"
git push origin v1.0.0
```

Pin in any consumer:

```groovy
@Library('jenkins-shared-lib@v1.0.0') _   // tag (production)
@Library('jenkins-shared-lib@main') _     // branch (dev / testing)
@Library('jenkins-shared-lib@a1b2c3d') _  // commit SHA (pinned)
```

Breaking changes go on a new major tag (`v2.0.0`) so pipelines pinned to
`v1.x` keep working.

## Conventions

- `vars/` filenames are **camelCase**; the matching `.groovy` file must define `call()`
- Classes in `src/` must `implement Serializable` so Jenkins can checkpoint pipeline state across steps
- Resources load by path: `libraryResource('com/example/values-template.yaml')`
- `@Library` must be the **first line** of any Jenkinsfile that uses it
- A syntax error in `vars/` breaks every consumer pipeline → always tag stable versions

## Local testing

Class-level unit tests for `src/` files run with [JenkinsPipelineUnit](https://github.com/jenkinsci/JenkinsPipelineUnit). Tests would live under `test/com/example/` (not included here yet).
