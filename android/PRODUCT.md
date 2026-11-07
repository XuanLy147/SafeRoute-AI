# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Vietnamese-speaking people who want to inspect recorded road hazards and compare routes on a phone. Community reporters and reviewers belong to the wider SafeRoute AI workflow; their TV2 screens are outside this TV1 implementation.

## Product Purpose

SafeRoute AI, by COME FOR FOOD for Hackathon SGU 2026-2027, connects community road-damage reports with route comparison based on recorded hazards. It does not promise an absolutely safe route.

## Operating Context

TV1 owns the shared app shell, map, foreground location, report markers/filter/preview, A/B entry and route comparison. The implemented language is Vietnamese and the target is Android phones. Java 17 and Android XML Views with Material Components are the locked UI stack; Google Maps SDK provides the map when configured. There is one Android project and one MainActivity.

The map can open a report preview and transfer its coordinates to destination B. A/B can be typed as latitude, longitude, swapped, or chosen on a configured map; foreground location can supply A. This version does not provide address autocomplete or geocoding. Results display only candidates delivered through the presentation adapter.

## Capabilities and Constraints

- TV1 UI supports loading, empty, disconnected, error and result states; report type and verification filters; route selection, metrics, risk value, hazard counts and service-supplied explanation.
- Missing Maps configuration has an explicit fallback. Coordinate entry remains available, and a location obtained on the map screen is retained as origin A even when the map is unavailable.
- A single returned route remains a single route. Missing risk score, scale or explanation is stated explicitly; the UI does not calculate a substitute score or invent geometry.
- Pending reports are labeled unverified. Rejected reports are excluded from warnings. Community voting is not reviewer verification. AI confidence is not hazard severity; Repair is a damage class, not a resolved status.
- TV2 owns auth, report creation/details/voting/review, shared Retrofit and API DTOs. The report and profile navigation items currently explain that these features are not connected.
- TV3 owns the real OpenAPI contract; TV5 owns routing/scoring. Their integration contract is not yet locked. Tv1DataSource is a Java presentation boundary, not a JSON schema or HTTP client. The default application data source is disconnected.
- Backend integration, live reports, live route comparison and authentication are not delivered by this UI task. Device location and Google Maps require runtime validation and configuration.

## Brand Commitments

Preserve the supplied Smart Mobility Blue identity and native Java/XML Material interface. Use clear Vietnamese descriptions of recorded evidence and verification state. Do not strengthen a relative recommendation into a safety guarantee. Visual tokens are recorded separately in DESIGN.md.

## Evidence on Hand

The source of truth for this record is AGENTS.md, README.md, docs/architecture.md, docs/api/README.md and the Android implementation under app/src/main. UI/model tests are under app/src/test. Build and test evidence belongs in the current handoff/tracker; this product record does not certify a release.

No device/emulator screenshots or visual acceptance evidence were available for this documentation pass. Live Maps, GPS permissions, API integration and end-to-end behavior have not been accepted on a device. No API payloads, benchmark results or route scores are asserted here.

## Product Principles

- Show recorded evidence and its verification status.
- Preserve useful input and provide a clear recovery action when a dependency is unavailable.
- Display actual service results, including one route, no routes and missing data.
- Keep module ownership and the shared Android/network architecture intact.

## Accessibility & Inclusion

Use native controls, scalable sp text, labeled navigation and status text alongside color. The current layouts include minimum touch heights, polite live status announcements, system/IME insets and scrolling for limited height. Dark mode, enlarged text, TalkBack and physical-device interaction still require device-level verification; no accessibility conformance claim is made.
