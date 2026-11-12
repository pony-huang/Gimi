English · [简体中文](../../zh/ui/brand-styling.md)

# Gimi brand styling: monochrome and blue theme

Established: 2026-11-10. Audience: agents and developers designing Gimi UI, previews, illustrations, diagrams, and other branded visual artifacts.

> This revision is the target brand and theme specification. Its core palette is live in `core/designsystem`: `Color.kt` provides the brand and neutral constants, both `ColorScheme` values in `Theme.kt` follow the core palette table, and the user-bubble colors in `UserBubbleTheme` follow this document. Current executable behavior remains defined by the active assignments in `GimiTheme`. New screens follow the tables below; when they diverge, update the theme implementation explicitly instead of hard-coding hex colors inside features. Maintain light, dark, and interaction-state previews according to [Preview-first UI design review](preview-first-design-review.md).

## Scope and visual direction

Use a visual language of black-and-white canvases, achromatic hierarchy, and a single brand-blue accent. Conversation content remains visually dominant. Blue is reserved for primary actions, current selection, keyboard focus, links, and meaningful brand emphasis. Establish hierarchy through luminance, weight, spacing, shape, and icons rather than adding more colors.

This specification defines Gimi's own product visual language. It does not imply a partnership, affiliation, or official endorsement with any third-party brand. Preserve the Gimi name, product mark, and existing component geometry; do not substitute a third-party service name or mark for Gimi's product identity.

Design principles:

1. **Black and white foundation**: use pure white for the main light background and pure black for the main dark background. Cards, sheets, and message bubbles use neutral gray layers only; do not use blue-gray, purple-gray, or warm gray as page canvases.
2. **Single accent**: place all decorative accents and interaction emphasis in the brand-blue tonal family. Keep one primary blue focal point per view; do not use multicolor gradients, colored glows, or rotating accent colors.
3. **Content first**: do not cover large areas in brand blue, use blue bubbles for long-form content, or use blue to distinguish ordinary body-text levels.
4. **Independent semantics**: do not replace error, success, warning, or third-party provider identities with brand blue. Communicate state through text, icons, or shape as well as color.
5. **Legibility first**: define the canonical brand blue separately from accessible interaction colors. Never reduce contrast merely to make every literal value identical.

## Evidence and source of truth

### Visual parameters

- Gimi's own icon uses a day/night inverted flat monochrome treatment: day/light mode uses a `#050505` background with a `#FFFFFF` mark; night/dark mode uses a `#FFFFFF` background with a `#3C3C43` mark. Preserve the existing artwork, proportions, transparency, and clear space without adding gradients or shadows.
- Static documents and the README use the day version by default. When a single night-mode asset is required, generate or select it using the night pairing above.
- The canonical brand blue uses the project's existing `BrandBlue = #4D6BFE`, defined separately from the monochrome icon colors.
- `#4D6BFE` has a contrast ratio of `4.33:1` against white and `4.85:1` against black. It is therefore suitable for focus rings, icons, and non-text emphasis, but not for normal-size text on white or normal-size white text on a filled button.

### Source locations

All source paths below are relative to the repository root:

- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Color.kt`: palette constants.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Theme.kt`: active role mapping in `GimiLightColorScheme`, `GimiDarkColorScheme`, and `GimiTheme`.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Type.kt`: typography configuration.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/UserBubbleTheme.kt`: dedicated neutral user-message roles.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/components/` and `ui/preference/` beside it: existing component geometry and spacing.

Use the target tables in this document for new designs; read source when investigating current production behavior. Differences must be resolved through explicit theme changes, never through hard-coded hex values inside features. The theme remains a fixed light/dark scheme; wallpaper-derived dynamic color stays disabled.

## Color architecture and naming

Color has three layers that must not be interchanged:

1. **Brand constant**: `BrandBlue`, representing only the canonical brand blue `#4D6BFE`.
2. **Semantic theme roles**: `primary`, `onPrimary`, `primaryContainer`, `background`, `surface`, `onSurface`, and related roles supplied by `MaterialTheme.colorScheme`. Components consume these roles.
3. **Local dedicated roles**: roles such as `LocalUserBubbleColors` solve a specific content need and do not become new global accents.

Naming rules:

- Name new accessible action constants `BrandPrimaryLight` / `BrandPrimaryDark`. Keep `BrandBlue` as the brand constant; do not create synonymous aliases such as `GimiBlue`, `AccentBlue`, or `BrandPrimary`.
- Active neutral constants use the `NeutralLight*` / `NeutralDark*` naming, not historical prefixes such as `ChatGpt*`. This governs naming only; it does not introduce another palette.
- `GimiTheme` is the product theme API and retains the Gimi name. `GimiLightColorScheme` / `GimiDarkColorScheme` describe the visual scheme and may remain.
- `secondary` is neutral. `tertiary` is not a second decorative accent. It may map to the `primary` family for Material component compatibility, but must not establish an independent colored system.
- Do not restore legacy constants such as `Blue*`, `Yellow*`, or `WarmAmber*`, and do not recreate old palettes based on their names.

## Core palette and theme roles

The following table is the shared target for implementation and external visual artifacts. App UI must consume semantic roles rather than copying these literals into screens or components.

| Semantic role / constant | Light | Dark | Usage |
|---|---|---|---|
| Canonical brand blue `BrandBlue` | `#4D6BFE` | `#4D6BFE` | Focus rings, brand graphics, non-text emphasis; not the normal-text action color |
| Primary action `primary` | `#3D5BDB` | `#AEBBFF` | Accessible color for filled buttons, links, text buttons, and selection indicators |
| Content on primary `onPrimary` | `#FFFFFF` | `#10205C` | Always pair with `primary` |
| Primary container `primaryContainer` | `#E3E8FF` | `#27366D` | Selection, lightweight emphasis, and label backgrounds; not a filled primary-button substitute |
| Content on primary container | `#10205C` | `#E3E8FF` | Pair with the container from the same theme |
| Main background `background` | `#FFFFFF` | `#000000` | Main page canvas |
| Surface `surface` | `#F7F7F7` | `#141414` | Content surfaces and default panels |
| Surface variant `surfaceVariant` | `#EDEDED` | `#242424` | Groups, secondary containers, and unselected control backgrounds |
| Main text `onSurface` / `onBackground` | `#1F1F1F` | `#F5F5F5` | Titles, body text, and primary icons |
| Secondary text `onSurfaceVariant` | `#666666` | `#A3A3A3` | Supporting copy, timestamps, and secondary icons; not the disabled role |
| Standard outline `outline` | `#D4D4D4` | `#3D3D3D` | Dividers and decorative boundaries |
| Strong outline `outlineStrong` | `#737373` | `#737373` | Input and control boundaries that require 3:1 non-text contrast |
| Error `error` | `#BA1B1B` | `#FFB4A9` | Error semantics only, accompanied by an error icon or text |
| Settings canvas | `#F5F5F5` | `#000000` | Settings-specific canvas |
| Settings group card | `#FFFFFF` | `#141414` | One luminance step above the settings canvas |
| User-message container | `#E9E9E9` | `#262626` | Remains neutral; do not change to brand blue |
| User-message text | `#1F1F1F` | `#F5F5F5` | Pair through `LocalUserBubbleColors` |

Use the following neutral surface ramp to prevent Material's default purple tint from entering the theme:

| Role | Light | Dark |
|---|---|---|
| `surfaceDim` | `#E4E4E4` | `#0A0A0A` |
| `surfaceBright` | `#FFFFFF` | `#383838` |
| `surfaceContainerLowest` | `#FFFFFF` | `#000000` |
| `surfaceContainerLow` | `#FAFAFA` | `#0F0F0F` |
| `surfaceContainer` | `#F5F5F5` | `#141414` |
| `surfaceContainerHigh` | `#F0F0F0` | `#1A1A1A` |
| `surfaceContainerHighest` | `#E9E9E9` | `#242424` |

Use the opposite theme's neutral surface/text pair for `inverseSurface` / `inverseOnSurface`, and the opposite theme's `primary` for `inversePrimary`. Foregrounds, containers, and state layers must come from the same theme; do not mix light and dark assignments arbitrarily.

## Primary interaction states

Composite state layers in sRGB over the established base color. Do not apply another global opacity to the entire control. Do not create synonymous hover-blue or pressed-blue constants beyond these state rules.

| State | Color rule | Non-color cue and behavior |
|---|---|---|
| Default | Filled buttons use `primary/onPrimary`; text buttons, links, and primary icons use `primary`; secondary buttons use neutral surfaces | In principle, keep one primary action per view |
| Hover | Add an 8% `onPrimary` layer to filled buttons, an 8% `primary` layer to text/icon buttons, or an 8% `onSurface` layer to neutral controls | Only for hover-capable input environments; do not change layout |
| Pressed | Add a 10% state layer over the corresponding base, using the same color rule as hover; do not increase it to 12%, preserving normal-text contrast | Preserve press position and existing ripple semantics without layout jumps |
| Keyboard focus | A 10% state layer may be used, plus a 2 dp `BrandBlue` focus ring with a 2 dp transparent gap from the control | Focus must be clearly visible, not only a subtle background change |
| Selected / current | Use `primaryContainer` + `onPrimaryContainer`; indicators, radio buttons, and switches use `primary` | Add a check, selection indicator, or shape change rather than only turning text blue |
| Disabled | Light: `#E9E9E9` container and `#8A8A8A` content; dark: `#262626` container and `#737373` content | Remove blue, elevation, and hover feedback; preserve disabled semantics without global alpha affecting children |
| Loading / submitting | Retain the original action color; progress uses `onPrimary`, and text-button progress retains `primary` | Keep the label, show progress, and prevent duplicate submission; do not merely switch to disabled gray |
| Read-only | Use neutral surfaces and main text, not disabled colors or blue borders | Distinguish read-only from editable and disabled states visually and semantically |
| Error | Use `error` and its corresponding container roles, never brand blue as a substitute | Show an error icon and actionable recovery copy |
| Success / warning | Use their own semantic colors rather than adding them to the blue family | Never rely on color alone; any undefined new semantic color requires a separate design decision |

Additional rules:

- Links have an underline or another non-color cue by default. Do not introduce a separate purple visited state into the brand palette.
- Inputs use `outline` by default, `outlineStrong` on hover or when stronger boundaries are needed, a 2 dp `BrandBlue` outline on focus, and `error` for errors.
- Selection and focus can coexist; the selection container must not obscure the focus ring.
- Do not use blue ripples for every click in neutral lists. Neutral lists use `onSurface` state layers; reserve blue for primary actions.

### State-layer contrast baseline

These values verify the state rules; they do not require additional literals in components. Composite results are rounded to the nearest RGB channel value.

| Scenario | Hover result / text contrast | Pressed or focus result / text contrast |
|---|---|---|
| Light filled button | `#4D68DE` / `4.83:1` | `#506BDF` / `4.66:1` |
| Dark filled button | `#A1AFF2` / `7.18:1` | `#9EACEF` / `6.94:1` |
| Light text button on `#F7F7F7` | `#E8EBF5` / `4.74:1` | `#E4E7F4` / `4.58:1` |
| Dark text button on `#141414` | `#202127` / `8.68:1` | `#23252C` / `8.27:1` |

## Legibility and visual hierarchy

- Normal text requires at least `4.5:1` contrast; large text requires at least `3:1`; meaningful non-text elements such as icons, input boundaries, and focus indicators require at least `3:1`. Disabled components may use the applicable accessibility exemption but must remain recognizable.
- Key pairs: light `primary/onPrimary = 5.65:1`; dark `primary/onPrimary = 8.21:1`; light primary-container pair `12.47:1`; dark primary-container pair `9.40:1`.
- On light `#F7F7F7`, main and secondary text provide `15.39:1` and `5.36:1`; on dark `#141414`, they provide `16.90:1` and `7.30:1`.
- The `BrandBlue` focus ring provides `4.04:1` on light `#F7F7F7` and `4.25:1` on dark `#141414`, meeting non-text contrast requirements.
- Do not use all-blue body copy, large blue text highlights, or low-opacity blue-gray text. Use main text, secondary text, and weight for body hierarchy rather than adding a third low-contrast gray.
- Dark mode establishes layers through neutral luminance, not blue shadows, colored ambient light, or large glows. Pure black is the background; body text uses `#F5F5F5` rather than pure white to reduce glare.
- Provide icon, text, position, or shape cues for error, selection, success, and other states. Contrast calculations do not replace checks with real font sizes and component states.

## Typography, shape, and visual language

- Use `MaterialTheme.typography` and the existing system/default font family, preserving Chinese and English fallback coverage. Current `bodyLarge` is normal weight, 16 sp with 24 sp line height and 0.5 sp letter spacing; other roles retain Material defaults unless explicitly configured.
- Establish hierarchy through existing title, body, and label roles. Do not add a font dependency for this visual direction or convert Android sp directly into document points. Preserve Markdown typography and user text sizing.
- Preserve component radii, list geometry, safe insets, and bottom-sheet rules. Existing settings groups use 16 dp corners in compact style and 24 dp otherwise; the shared bottom sheet uses 28 dp top corners. These remain component-specific, not a universal radius.
- Brand recognition comes from a restrained neutral interface and a single blue action focus, not additional decoration. Focus rings, selection indicators, and key actions form the primary blue language; do not add blue gradient cards, colored glassmorphism, or meaningless light effects.
- Start diagrams and illustrations with neutral shapes, using blue only for the main relationship or action. Distinguish categories with labels, line styles, or shapes rather than creating a multicolor brand palette.

## Marks and third-party brand boundaries

- Preserve the Gimi name and existing logo artwork. `doc/icon.webp` is the README's Gimi brand image and uses the day version; launcher icons use `app/src/main/res/drawable-nodpi/ic_launcher_foreground_art.png` and the density-specific launcher assets.
- Launcher icons switch with the system day/night configuration: default resources provide the day black-background/white-mark pairing; `drawable-night`, `drawable-night-nodpi`, and `mipmap-night-*` provide the night white-background/dark-gray-mark pairing. The adaptive-icon XML remains unchanged and resolves the matching version through resource qualifiers.
- The system monochrome icon continues to use a transparent alpha mask for system tinting. Resources update with configuration changes, although launcher caching can delay the visible switch; restart the app or refresh the launcher when verifying.
- Preserve the original artwork, aspect ratio, and transparency. Do not stretch, redraw, outline, or add gradients or shadows. Leave clear space around the mark equal to at least 50% of its height; a minimum digital display width of 24 dp is recommended.
- Use third-party service marks only when introducing, configuring, or linking to their corresponding services. Do not place them in the Gimi app icon, as the primary splash-screen brand, or in positions that imply an official partnership.
- Blue belongs to interface emphasis around marks, not to modifications of third-party artwork. Provider logos retain their original artwork, proportions, and brand colors.
- When external material includes a third-party service name or mark, copy must clearly identify the service or model provider and avoid wording that implies an unsupported affiliation, partnership, or endorsement.

## Acceptance

- Maintain production `@Preview` fixtures when adding or changing UI, covering at least light/dark themes; primary-button default/hover/pressed/focus/disabled/loading states; selected items; input focus/error; links; and user bubbles.
- Check real Chinese fonts, user text sizing, long content, dark-mode safe insets, and focus visibility on devices. Retest contrast for state layers and custom controls rather than assuming Material defaults pass automatically.
- Use the same role names and values in external documents, presentations, and diagrams.

Delivery checklist: the main background and surface hierarchy are clear; blue is used only for emphasis; canonical brand blue is not confused with accessible action colors; states have non-color cues; boundaries between Gimi and third-party brands are explicit; and Chinese/English values match. Documentation-only changes require diff, link, and value checks, not an app build. Actual UI changes still require the preview and proportional verification workflow.
