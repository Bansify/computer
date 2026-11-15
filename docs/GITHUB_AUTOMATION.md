# GitHub automation and review gates

The goal is to catch regressions earlier, not to claim that bots prove a TV launcher is safe. Physical TV/tablet remote, Home selection/recovery, OEM, low-memory and long-run acceptance remain required in `task.md`.

## What's configured in this branch

- **Android CI:** debug/release builds, unit tests, debug lint (errors fail), and Android 21/29/34 emulator smoke tests. The job uploads a debug APK and reports. It does not sign a production release or test actual TV remotes. Existing lint warnings still need review.
- **Dependabot:** weekly Gradle and GitHub Actions version proposals in `.github/dependabot.yml`. Grouped minor/patch updates still require review and green CI; major updates are not auto-merged. Dependabot uses the configuration from the default branch, so proposals start only after it reaches `main` and the relevant GitHub repository features are enabled. Verify coverage of both root and `:app` Gradle dependencies in the Dependabot job logs; no update PR is proof of complete coverage.
- **CodeRabbit:** `.coderabbit.yaml` provides Home/recovery, remote-input and security-focused review instructions and opts into draft PR reviews. The file alone does not install or activate the service. A repository owner must install [CodeRabbit's GitHub App](https://github.com/apps/coderabbitai) for this repository and complete any onboarding; its availability and plan limits depend on CodeRabbit. Reviews send repository/PR content to that service; inspect its permissions and privacy terms before authorizing. AI review is advisory, not an approval or a test.
- **CodeQL:** GitHub code scanning for Kotlin/Java (instrumented debug build) and packaged JavaScript on pushes, PRs and weekly on the default branch. Upload requires code scanning availability and `security-events: write`; inspect Security > Code scanning and workflow results, including failures. An empty result does not mean no vulnerabilities.
- **Dependency review:** checks newly introduced known-vulnerable dependencies in PRs, failing on high or critical advisories. It requires GitHub's dependency graph to be enabled and populated. It is not a complete audit or a license sign-off.
- **Workflow validation:** actionlint checks workflow syntax and expressions. This does not prove workflows safe to execute.

## Owner setup and protection

1. In repository **Settings > Code security**, check that dependency graph, Dependabot alerts and security updates are enabled. For Gradle transitive coverage, enable GitHub's **automatic dependency submission** if available, then inspect its resulting graph and failures. These are repository settings, not activated merely by adding YAML.
2. Install/authorize CodeRabbit as described above, then open or update a PR and verify it actually posts a review. Do not give a third-party bot secrets or automatic merge privileges.
3. In **Settings > Rules > Rulesets** (or branch protection), require PRs and the checks actually visible on the PR, including Android CI, workflow validation, CodeQL and dependency review. Set review requirements appropriate to the team. Add requirements only after those checks run reliably; wrong names or unavailable features can block every PR. No ruleset was changed by these files.
4. The branch removes the release workflow trigger on a `main` push. A `v*` tag or manual dispatch can still publish, the workflows have overlapping version schemes, and release signing remains unverified. Check repository-side automation before merging; never treat an unsigned artifact or debug-key build as a production release.
5. Review findings, triage false positives, and test suggested dependency updates. Security scanners and AI can miss defects or produce false alarms. Test rollback and keep an independent alternate Home installed.

## Permissions and untrusted contributions

PR jobs use `pull_request` rather than `pull_request_target`, do not use release secrets, and use read-only repository access except for CodeQL's required `security-events: write` for results upload. GitHub restricts write tokens for forked PRs; verify code-scanning behavior for forks. Do not execute untrusted PR code in a privileged `workflow_run` or `pull_request_target` job. GitHub repository settings and third-party app installation may require owner/admin action outside this branch.
