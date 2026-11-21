English · [简体中文](../../zh/ui/brand-styling.md)

# Gimi brand styling: M3 monochrome ink-gray theme

Established: 2026-11-18. Audience: agents and developers designing Gimi UI, previews, illustrations, diagrams, and other branded visual artifacts.

> This revision is the target brand and theme specification. Its core palette is live in `core/designsystem`: `Color.kt` provides the monochrome ink-gray constants, `Shape.kt` provides `GimiShapes`, `Type.kt` provides the complete M3 `Typography`, both `ColorScheme` values in `Theme.kt` follow the core palette table, and the user-bubble colors in `UserBubbleTheme` follow this document. Current executable behavior remains defined by the active assignments in `GimiTheme`. New screens follow the tables below; when they diverge, update the theme implementation explicitly instead of hard-coding hex colors inside features. Maintain light, dark, and interaction-state previews according to [Preview-first UI design review](preview-first-design-review.md).

## Scope and visual direction

Use a Material Design 3 **Monochrome Ink-Gray (黑白墨灰)** visual language: paper-white and deep-ink canvases, a 5-level ink-gray `surfaceContainer` hierarchy, and high-contrast ink-black (`#18181B` in light mode) / paper-white (`#F4F4F6` in dark mode) primary actions that directly mirror Gimi's flat monochrome day/night brand mark. Conversation content remains visually dominant. Establish hierarchy through luminance, M3 tonal container steps, weight, spacing, shape, and icons rather than decorative chromatic accents.

This specification defines Gimi's own product visual language. It does not imply a partnership, affiliation, or official endorsement with any third-party brand. Preserve the Gimi name, product mark, and existing component geometry; do not substitute a third-party service name or mark for Gimi's product identity.

Design principles:

1. **Paper-white and deep-ink foundation**: use soft paper white (`#FAFAFA`) for the main light background and anti-smear deep ink black (`#0D0D0F`) for the main dark background. Cards, sheets, and message bubbles use the M3 5-step ink-gray surface container ramp.
2. **Monochrome primary emphasis**: primary actions, active switches, mode thumbs, and selected indicators use deep ink (`#18181B`) in light mode and paper white (`#F4F4F6`) in dark mode; selected containers use soft ink-wash (`#E4E4E7` light / `#2C2C31` dark).
3. **Content first**: keep long-form assistant responses on the open reading canvas; user bubbles use `surfaceContainerHighest` (`#E0E0E5` light / `#2E2E33` dark).
4. **Independent semantics**: preserve standard M3 error semantics (`#BA1A1A` light / `#FFB4AB` dark) and third-party provider brand colors. Communicate state through text, icons, or shape as well as color.
5. **Legibility first**: interactive boundaries (`outline`: `#71717A` light / `#8A8A93` dark) exceed the 3:1 non-text contrast requirement, while decorative dividers use `outlineVariant` (`#D4D4D8` light / `#2E2E33` dark).

## Evidence and source of truth

### Visual parameters

- Gimi's own icon uses a day/night inverted flat monochrome treatment: day/light mode uses a `#050505` background with a `#FFFFFF` mark; night/dark mode uses a `#FFFFFF` background with a `#3C3C43` mark. Preserve the existing artwork, proportions, transparency, and clear space without adding gradients or shadows.
- The README uses adaptive transparent-background mark assets selected with the `prefers-color-scheme` `<picture>` element: `doc/icon-light.png` (a `#050505` mark on transparency) for day/light mode and `doc/icon-dark.png` (a `#F5F5F5` mark on transparency) for night/dark mode. `doc/icon.webp` remains the original day-version source artwork.
- `BrandBlue = #4D6BFE` is retained solely as a particle-tint constant for the realtime voice orb (`VoiceOrb`), never as the general UI primary color.

### Source locations

All source paths below are relative to the repository root:

- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Color.kt`: palette constants.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Shape.kt`: `GimiShapes` M3 corner token definitions.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Theme.kt`: active role mapping in `GimiLightColorScheme`, `GimiDarkColorScheme`, and `GimiTheme`.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/Type.kt`: complete M3 typography configuration.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/theme/UserBubbleTheme.kt`: dedicated neutral user-message roles.
- `core/designsystem/src/main/java/github/ponyhuang/gimi/ui/components/` and `ui/preference/` beside it: standardized M3 switches, outlined text fields, search fields, sheets, and grouped cards.

## Core palette and theme roles

The following table is the shared target for implementation and external visual artifacts. App UI must consume semantic roles rather than copying these literals into screens or components.

| Semantic role / constant | Light | Dark | Usage |
|---|---|---|---|
| Primary action `primary` (`BrandPrimaryLight` / `BrandPrimaryDark`) | `#18181B` | `#F4F4F6` | Filled buttons, active switches, mode thumbs, selection checkmarks, focus borders |
| Content on primary `onPrimary` | `#FFFFFF` | `#111113` | Always pair with `primary` |
| Primary container `primaryContainer` | `#E4E4E7` | `#2C2C31` | Selected picker rows, active segmented buttons, primary tonal containers |
| Content on primary container `onPrimaryContainer` | `#111113` | `#F4F4F6` | Pair with `primaryContainer` from the same theme |
| Secondary `secondary` (`InkSecondary*`) | `#52525B` | `#A1A1AA` | Secondary actions and filter controls |
| Content on secondary `onSecondary` | `#FFFFFF` | `#18181B` | Pair with `secondary` |
| Secondary container `secondaryContainer` | `#ECECEF` | `#252529` | `FilledTonalButton`, preference icon badges, info banners, model capsules |
| Content on secondary container `onSecondaryContainer` | `#18181B` | `#E4E4E7` | Pair with `secondaryContainer` |
| Tertiary `tertiary` (`InkTertiary*`) | `#3F4850` | `#B8C2CC` | Cool graphite accent for badges and auxiliary tags |
| Tertiary container `tertiaryContainer` | `#E2E7EC` | `#303840` | Auxiliary badge containers |
| Content on tertiary container `onTertiaryContainer` | `#141C22` | `#E2E7EC` | Pair with `tertiaryContainer` |
| Main background & surface `background` / `surface` | `#FAFAFA` | `#0D0D0F` | Default page canvas |
| Surface variant `surfaceVariant` | `#E4E4E7` | `#27272A` | Secondary containers and track backgrounds |
| Main text `onSurface` / `onBackground` | `#111113` | `#F4F4F6` | Titles, body text, and primary icons |
| Secondary text `onSurfaceVariant` | `#52525B` | `#A1A1AA` | Supporting copy, timestamps, and secondary icons |
| Interactive outline `outline` | `#71717A` | `#8A8A93` | 3:1 accessible boundary for text fields, outlined buttons, segmented buttons, and unchecked switches |
| Decorative outline `outlineVariant` | `#D4D4D8` | `#2E2E33` | Inset list dividers and subtle card borders |
| Error `error` / `onError` | `#BA1A1A` / `#FFFFFF` | `#FFB4AB` / `#690005` | Error semantics only, accompanied by an error icon or text |
| Error container `errorContainer` / `onErrorContainer` | `#FFDAD6` / `#410002` | `#93000A` / `#FFDAD6` | Error banners and error containers |
| Settings canvas (`preferenceCanvasColor()`) | `#F4F4F5` | `#0D0D0F` | Settings and drawer canvas (`surfaceContainerLow` light / `surface` dark) |
| Settings group card (`preferenceGroupCardColor()`) | `#FFFFFF` | `#161618` | One luminance step above the settings canvas (`surfaceContainerLowest` light / `surfaceContainerLow` dark) |
| User-message container | `#E0E0E5` | `#2E2E33` | Aligned with `surfaceContainerHighest` via `LocalUserBubbleColors` |
| User-message text | `#111113` | `#F4F4F6` | Pair through `LocalUserBubbleColors` |

### M3 5-level surface container ramp

| Role | Light | Dark |
|---|---|---|
| `surfaceDim` | `#DADADD` | `#0D0D0F` |
| `surfaceBright` | `#FFFFFF` | `#34343A` |
| `surfaceContainerLowest` | `#FFFFFF` | `#08080A` |
| `surfaceContainerLow` | `#F4F4F5` | `#161618` |
| `surfaceContainer` | `#EFEFF1` | `#1C1C1F` |
| `surfaceContainerHigh` | `#E8E8EC` | `#242428` |
| `surfaceContainerHighest` | `#E0E0E5` | `#2E2E33` |

## Typography, shape, and component rules

- **Typography**: `Type.kt` configures `headlineSmall` (24sp SemiBold), `titleLarge` (20sp SemiBold), `titleMedium` (16sp Medium), `titleSmall` (14sp SemiBold), `bodyLarge` (16sp Normal), `bodyMedium` (14sp Normal), `bodySmall` (12sp Normal), `labelLarge` (14sp SemiBold), `labelMedium` (12sp Medium), and `labelSmall` (11sp Medium).
- **Shapes**: `Shape.kt` defines `GimiShapes` (`extraSmall = 4.dp`, `small = 8.dp`, `medium = 12.dp`, `large = 16.dp`, `extraLarge = 28.dp`).
  - `PreferenceGroupCard`, `GimiPickerItemRow`, `PluginRowCard`, and user message bubbles use `MaterialTheme.shapes.large` (16.dp).
  - `PreferenceTextField` uses M3 `OutlinedTextField` with `MaterialTheme.shapes.medium` (12.dp), `outline` unfocused borders, and `primary` focused borders.
  - `GimiSearchField` uses `CircleShape` with `surfaceContainerHigh` fill.
  - `GimiSheetBase` and `ChatComposer` use `28.dp` (`extraLarge`) corners.
- **Switches (`GimiSwitch`)**: standardized 44×26dp M3 track inside a 48dp touch target, with `surfaceContainerHighest` fill + `1.5.dp outline` border + `14.dp outline` thumb when unchecked, and `primary` fill + `18.dp onPrimary` thumb when checked.
- **Theme detection**: never branch on `MaterialTheme.colorScheme.background.luminance() < 0.5f` in components; read `LocalGimiDarkTheme.current` when a non-color or theme-specific branch is required.

## Marks and third-party brand boundaries

- Preserve the Gimi name and existing logo artwork (`doc/icon-light.png` / `doc/icon-dark.png` and launcher assets).
- Third-party service marks sit on a fully transparent container: monochrome black marks (OpenAI / Anthropic / XiaomiMiMo) use their inverted `*_night` drawable selected by `LLMModelServiceIcon` through `LocalGimiDarkTheme`, Kimi uses its brand-color version, and colored marks share the same original brand resource in both themes. Never use `res/drawable-night/` qualifiers for in-app provider or plugin-store icons; dark variants are always selected in code from the app theme.
