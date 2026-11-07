# M10: Research data program

**Goal:** people who **choose to** can send weekly summaries of how they eat (and, separately, body, cycle and supplement summaries) to one central database, so the owner can study how well the population eats.
**Users get:** a clear, optional "Help research" feature. They see exactly what is sent, can stop at any time, and can delete their data from the server.
**Depends on:** M1, M2, M7, M8 (the data categories); owner inputs #3 and #4 to switch it on.
**Read first:** [`../PRIVACY_POLICY.md`](../PRIVACY_POLICY.md) (all of it, especially appendices A and B), [algorithms.md](algorithms.md) A21, [data-model.md](data-model.md) (M10).
**New dependencies:** app: none (`HttpURLConnection` as today). Server: Ktor, the PostgreSQL JDBC driver, a test framework.

## Why it is opt-in

The owner said privacy doesn't matter much to them, and asked for a detailed policy and a way for users to refuse. In the EU, though, health data (food diary, weight, body fat) and especially cycle data are "special category" data under the GDPR. They may only be collected with **explicit consent**: the person actively says yes. A refuse option hidden in a long policy, or a box that is ticked in advance, is not valid consent, and the Czech data protection office (ÚOOÚ) can fine for it. So:

- research sharing is **off by default**;
- every category has its **own switch**, and cycle data has its own, separate consent;
- the **full policy** is written out (`PRIVACY_POLICY.md`), but the consent screen also explains the essentials in plain words;
- people can **stop** at any time, and **delete** their data on the server.

This still gives the owner what they want: a central database for population research, from everyone who agrees.

## Tasks

### M10.1 Policy and consent texts in the app

- [x] Show the privacy policy (Czech and English) in Me → About → **Privacy**, and from the research screens. *As built:* `assets/legal/privacy-policy-en.md` is a copy of `docs/PRIVACY_POLICY.md` (a test keeps them equal), shown with a small Markdown reader. English only for now, with a Czech note, until the translation (input #4). In About the link shows only with the research feature, because the policy is still a draft.
- [x] The consent texts from `PRIVACY_POLICY.md` appendix B, in strings. *As built:* the Czech texts are a draft translation for the legal review (input #4).
- [x] A `RESEARCH_CONSENT_VERSION` constant. Changing what is collected means a new version and asking again.

### M10.2 The consent flow

Me → **Help research**:

1. [x] **Intro:** why (understanding how people eat), what is sent (weekly summaries, never food names, names or exact dates), who gets it (the project owner, stored in the EU), and that it is optional and can be stopped any time.
2. [x] **Age:** "Are you 18 or older?" Under 18, or no answer: the feature is not available.
3. [x] **Choose what to share:** four switches, **all off**: *Food and nutrition*, *Body* (BMI band, weight trend, body fat band), *Cycle* (with an extra line: "This is sensitive health data. It is only sent if you switch this on."), *Supplements*. Optional: region (kraj).
4. [x] **Read the full policy** link.
5. [x] A button that says exactly what happens: **"I agree to send the selected data"**. It is disabled until at least one switch is on.
6. [x] On agreeing: create `participantId` (UUID) and `participantSecret` (32 random bytes), register with the server (M10.4), and store the consent (version, date, categories) in `research_prefs`.

The status screen afterwards shows the categories, the last report sent, and three buttons: **See what we send**, **Stop sharing**, **Delete my data from the server**.

**Done when:** a UI test can't reach "agree" without choosing a category, and the stored consent matches what was chosen.
*As built:* `ResearchConsentTest` (instrumented, compiled here). Offline, the registration waits and is sent with the first report.

### M10.3 Building the reports (database version 11)

- [x] Table `research_reports` and the `research_prefs` DataStore (data-model M10). *As built:* database version 11; one report per ISO week; backup format 10 includes the reports (not the secret).
- [x] Domain: A21. One weekly report with a section for each agreed category; the fields exactly as in policy appendix A. *As built:* `domain/research/ResearchReports`; a section with nothing to say (for example no cycle ended this week) is left out.
- [x] Tests: *As built:* `ResearchReportsTest` (JVM, all five), `ResearchRepositoryTest` (instrumented).
  - a report never contains a field outside appendix A (compare JSON keys against an allowed list);
  - no dates finer than an ISO week;
  - bands and rounding are correct;
  - nutrition is skipped below 3 counted days;
  - categories that weren't agreed to are absent.

### M10.4 Sending

- [x] Server address from the Gradle property `calore.researchServerUrl` → `BuildConfig.RESEARCH_SERVER_URL`. If it is empty, the whole feature stays hidden (`FeatureFlags`). *As built:* empty in this repository, as the owner asked.
- [x] WorkManager: weekly, Monday, needs a network, not when the battery is low. It builds the report, stores it as `PENDING` (the exact JSON), and sends it with `Authorization: Bearer <participantSecret>`. Retries with exponential backoff; after 5 failed attempts the status is `FAILED` and the next week tries again.
- [x] **See what we send:** a list of reports (week, status) with the full JSON of each.

### M10.5 Stopping and deleting

- [x] **Stop sharing:** cancel the job, delete pending reports, keep a note that consent was withdrawn (date).
- [x] **Delete my data from the server:** `DELETE /v1/participants/{id}` with the secret. If the phone is offline, keep retrying (stored as a pending deletion) and show "Waiting to delete". Then remove the participant id and secret from the phone.
- [x] Uninstalling the app loses the secret, so the policy tells users to delete their data before uninstalling, or to email the owner with their participant id (shown on the status screen).

**Done when:** stop and delete work offline and online; after deletion the server has no rows for that participant (server test).
*As built (app):* `ResearchRepositoryTest` checks stop and delete offline and online against a fake server.

### M10.6 The research server

A separate Gradle build in `server/` (own `settings.gradle.kts`), so the Android build stays as it is.

- [ ] Kotlin + Ktor + PostgreSQL.
- [ ] Endpoints:

| Method and path | Auth | What it does |
|---|---|---|
| `POST /v1/participants` | none (body has the secret) | Register `{participantId, secret, consentVersion, categories, profile}`; stores a SHA-256 hash of the secret, never the secret |
| `PUT /v1/participants/{id}` | Bearer secret | Update categories, profile, consent version |
| `POST /v1/reports` | Bearer secret | Store one report; idempotent by `reportId` |
| `DELETE /v1/participants/{id}` | Bearer secret | Delete the participant and all their reports |
| `GET /v1/health` | none | For monitoring |

- [ ] **Validation:** reject unknown fields, values out of range and bodies over 16 KB. The allowed fields are exactly appendix A.
- [ ] **No IP addresses stored:** no IPs in the app logs, and the reverse proxy configured not to log them. Store the received ISO week, not a timestamp.
- [ ] **Rate limits:** per participant and per IP, in memory only.
- [ ] **Statistics export:** a command that writes CSV aggregates (by week, age band, sex, region) and **suppresses any group smaller than 10 people**. Only aggregates leave the database.
- [ ] **Deployment:** `Dockerfile` and `docker-compose.yml` (server, PostgreSQL, Caddy for HTTPS), configuration via environment variables, and a short `server/README.md` that also covers backups (encrypted) and updates.
- [ ] Tests with Ktor `testApplication` and a test database.

### M10.7 Switching it on (owner and builder together)

Not before every item is done:

- [ ] Hosting chosen, **in the EU** (owner input #3). The cheapest option that meets the policy is enough; the traffic is tiny (a few KB per user per week).
- [ ] The controller's name and contact filled in in the policy; the policy reviewed and translated into Czech (owner input #4).
- [ ] The owner's legal checklist in policy appendix C done (data protection impact assessment, records of processing, a processing agreement with the host).
- [ ] `calore.researchServerUrl` set for release builds; the feature flag on.

## Out of scope

Sending raw diaries, food names or exact dates (never); research by children under 18 (not allowed); selling or advertising use of the data (excluded by the policy).
