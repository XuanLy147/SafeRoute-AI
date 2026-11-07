---
name: SafeRoute AI Android
description: Smart Mobility Blue for recorded road hazards and route comparison
colors:
  primary: "#2563EB"
  on-primary: "#FFFFFF"
  surface: "#FFFFFF"
  background: "#FFFFFF"
  ink: "#0F172A"
  muted: "#596579"
  outline: "#CBD5E1"
  input-surface: "#F7F8FA"
  soft-line: "#E9EDF3"
  info: "#EFF6FF"
  on-info: "#1D4ED8"
  warning: "#92400E"
  verified: "#047857"
  error: "#B91C1C"
  route-alternative: "#64748B"
  primary-night: "#A9C7FF"
  on-primary-night: "#003064"
  surface-night: "#162032"
  background-night: "#0C1423"
  ink-night: "#F1F5F9"
  muted-night: "#CBD5E1"
  outline-night: "#64748B"
  info-night: "#18345B"
  on-info-night: "#BED7FF"
  warning-night: "#FCD34D"
  verified-night: "#6EE7B7"
  error-night: "#FCA5A5"
  route-alternative-night: "#94A3B8"
typography:
  title:
    fontFamily: "sans"
    fontWeight: 700
  body:
    fontFamily: "sans"
  note:
    fontFamily: "sans"
  status:
    fontFamily: "sans"
    fontSize: "14sp"
rounded:
  card: "12dp"
  map-panel: "16dp"
  navigation-row: "14dp"
  pill-control: "28dp"
spacing:
  compact: "4dp"
  small: "8dp"
  medium: "12dp"
  content: "16dp"
  screen: "20dp"
  section: "24dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
  button-outlined:
    textColor: "{colors.primary}"
  button-text:
    textColor: "{colors.primary}"
  input-outlined:
    textColor: "{colors.ink}"
  navigation:
    backgroundColor: "{colors.surface}"
  filter-chip:
    textColor: "{colors.ink}"
  card:
    rounded: "{rounded.card}"
    padding: "{spacing.content}"
  status-panel:
    backgroundColor: "{colors.info}"
    textColor: "{colors.on-info}"
    padding: "{spacing.medium}"
---

# Design System: SafeRoute AI Android

## Overview

**Creative North Star: "Smart Mobility Blue"**

Smart Mobility Blue is the supplied visual direction. The implementation uses native Material Components, clear Vietnamese labels, pale backgrounds and blue actions to make recorded road conditions and route choices legible. This document preserves that incumbent system; it does not introduce a new identity.

The screen hierarchy is practical and compact: a clear title, the relevant map or inputs, explicit status, then supporting details. Verification and selected-route state have both text and color cues. The documented system is extracted from source. Native-device visual evidence and remaining limitations are recorded in docs/integration/frontend-visual-refresh.md.

**Key Characteristics:**

- Native Java/XML Material controls.
- Blue actions, neutral surfaces and explicit semantic status.
- Scrollable phone layouts with persistent, labeled navigation.
- Flat outlined cards with visible selection.

## Reference refresh (2026-11-07)

The supplied welcome/login/register image defines the current presentation: white canvas, strong blue primary action, pale rounded inputs, centered authentication headings and a small smiling wheeled mark. The mark is a native vector reconstruction from the screenshot, not an original production logo asset or a claim of pixel identity. It is also used for the application icon.

Shared styles live in `values/styles_polish.xml`; light/night input surfaces live in `colors_polish.xml`. Authentication uses a centered 440dp maximum content width and scrolls on short screens. Other screens reuse the same palette and control hierarchy: map search, report step indicators, account rows and reviewer/admin navigation. This pass does not connect any backend services.

## Colors

The palette combines a clear blue action color with cool neutral surfaces. YAML values are normative extracts from Android resources; unsuffixed tokens are light mode and matching -night tokens are the existing dark-mode overrides. In application code use the original Android resource name rather than a literal or a suffixed documentation key.

### Primary

Primary is the action and selected-route accent; on-primary provides filled-control content. Info and on-info form the readable map/configuration status panel.

### Neutral

Background supports the overall screen; surface supports navigation and Material surfaces. Ink is primary content, muted is supporting text, and outline separates cards. Route-alternative is reserved for unselected map polylines.

### Semantic status

Verified is the report-list verification label; warning is the pending label; error belongs to validation and failure. Google Maps markers use the SDK's azure/orange default marker hues, not the text colors. Their title/snippet and preview repeat the verification state.

**The Evidence Label Rule.** Verification and route selection must remain understandable without color.

## Typography

The theme declares Android sans. Title uses MaterialComponents Headline5 with bold weight; body uses Body1; note uses Body2. Route names use Headline6 and report titles use bold Subtitle1. These roles inherit their size and line-height from the installed Material library; they are not independent hardcoded brand tokens. Explicit map/status helper text uses the documented status size.

Use sp for text and retain Vietnamese accents. The implementation has no separate display or monospaced font, custom font asset, or bespoke type animation.

## Layout

The phone app uses a vertical screen with one fragment container and a labeled bottom navigation bar. Window, cutout and IME insets are applied at the root; the bar hides while the keyboard is visible. Map, route input and route-result content scroll vertically.

The screen inset is 20dp; cards use 16dp internal padding, with the extracted spacing steps for related content. Map action rows use 16dp horizontal padding and 8dp gaps. Buttons have a minimum height of 48dp; the comparison action is 56dp. Filter chips request minimum touch-target sizing and sit in a horizontal scroll region.

The map overview layout uses a rounded 260dp frame; existing runtime landscape sizing still applies. Route picking uses 220dp; results use 240dp. These are observed sizes, not a tablet layout contract. Expanded-width navigation has not been implemented or accepted. Long translations, large font settings and small screens require native-device review.

## Elevation & Depth

Report and route cards explicitly use zero elevation with an outline. A selected route increases its stroke from 1dp to 2dp and uses primary. Status panels use a different surface tone. Buttons, dialogs, chips and navigation retain the Material library's native behavior; the app does not define custom shadow or motion tokens. Map camera animation is SDK-driven.

**The Flat Card Rule.** Keep list-card hierarchy in content, spacing and stroke rather than added decorative shadows.

## Shapes

Report/route cards retain their 12dp radius. Map/status panels use 16dp, navigation rows 14dp, primary/secondary buttons and authentication fields 28dp, and route inputs 24dp. Chips, switches and dialogs retain their native Material shapes. Route polylines have round caps, with selected/unselected widths of 6dp/4dp.

## Components

### Buttons

Filled comparison/retry actions, outlined route selection/edit actions and text utility actions use MaterialButton variants. Retain native ripple, keyboard focus and disabled treatment. Loading disables the relevant refresh/location action; the active route's selection button reads as selected and is disabled.

### Chips

The hazard category group permits one required selection: all, potholes or cracks. A separate native switch filters verified reports. Use native selected semantics and visible labels rather than color alone.

### Cards / Containers

Report cards show title, type and verification, and open a native preview dialog. Route cards show route name, recommendation, travel metrics, risk, counts, explanation and selection action. Shape, padding and selection stroke follow the shared card system. Missing service details have explicit text.

### Inputs / Fields

Outlined TextInputLayout fields identify A and B and accept a place name or address. Users confirm a geocoding result; coordinates remain separate from its display label. GPS, swap and map picking supplement address entry. Authentication uses visible labels, pale fields, password masking and native autofill hints.

### Navigation

Four labeled Material bottom-navigation items adapt to the session role. User destinations are map, report, my reports and account; routes open from the map. Reviewer destinations are overview, queue, history and map; admin destinations are overview, reports, audit and accounts. Selected destinations use a pale blue rounded background. Backend-dependent screens explicitly show unavailable/empty states; UI navigation does not establish backend integration or server authorization.

### Map and result states

Maps are SDK surfaces, never illustrative route artwork. The missing-configuration panel, connection message, report list and route cards explain available data independently of map rendering. Marker previews include verification status and can transfer a destination into the route form. Draw only adapter-supplied route points. Empty, disconnected, loading and failed states have distinct Vietnamese copy.

The sidecar's HTML/CSS components are lightweight browser previews for the documentation panel, translated from the native patterns. They are not shipped UI, exact Material measurements or a replacement for emulator/device screenshots. Synthesized tonal ramps are panel metadata only; they do not create Android colors.

## Do's and Don'ts

### Do:

- Do use Android resource roles so light and dark schemes resolve together.
- Do keep status labels and route explanations alongside visual cues.
- Do preserve native sp text, touch targets and keyboard/system insets.
- Do keep all controls reachable when the screen is short or text is enlarged.

### Don't:

- Don't use a risk color or score to imply guaranteed safety.
- Don't fabricate route alternatives, geometry, scores or verification.
- Don't replace native controls with browser-rendered UI.
- Don't treat sidecar browser previews as Android screenshot evidence.
