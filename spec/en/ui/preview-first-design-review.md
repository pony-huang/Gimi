English · [简体中文](../../zh/ui/preview-first-design-review.md)

# Preview-first UI design review

Reviewed: 2026-11-07. Audience: agents and developers changing Compose UI.

This document owns the preview design and approval workflow linked from `AGENTS.md`. Apply [Gimi brand styling](brand-styling.md) when preparing drafts. This workflow applies to UI changes; editing these specifications alone does not require a Compose draft.

## Workflow

- Small, localized UI adjustments (such as spacing, alignment, theme colors, or repositioning an existing button) may be implemented directly without a separate design draft, an image-delivery preference question, or design approval. If the scope or intended appearance is unclear, the agent may consult the developer about proceeding directly; this consultation is optional, not a mandatory approval gate. Existing preview maintenance and proportional verification requirements still apply.
- For UI changes beyond these small adjustments, begin with a preview design draft before implementation: a `@Preview @Composable`-based draft (using fake/fixture data, themable for light and dark) that renders the proposed layout, new states, and interaction entry points.
- At the start of a UI task requiring a design draft, ask whether the user wants rendered design images for remote review, unless they have already stated a preference for that task. Wait for the answer before choosing the image-delivery branch; draft preparation may continue meanwhile.
- If requested, render the actual Compose draft, visually inspect the captures, and return viewable images in the conversation before asking for design approval. Include light/dark themes and the relevant changed states; keep image artifacts under `temp/`. Source files, file links, and compilation success alone do not fulfill image delivery. If rendering is unavailable, explain the specific blocker and agree on an alternative with the user.
- If declined, skip image rendering and delivery and present the draft with a concise description. Declining images does not waive the design approval below unless the user explicitly says so.
- For changes requiring a design draft, present it to the user for review; implementation work may start only after the user explicitly confirms the design, unless the user explicitly waives this step.
- Keep the draft isolated from production wiring (no ViewModel/repository changes yet); once approved, merge it into the real screens, wire up state, and delete the draft file and any preview-only dependencies it required.

## Interaction continuity and motion

- Translate feedback about feel into an observable interaction problem before choosing an animation: examine input response, state transitions, layout changes, and component lifetime. Choose timing and motion for the context; longer durations alone do not establish smoothness.
- Preserve the control's information and accessibility contract while changing its motion. Visual feedback, gesture position, and committed business state may need separate representations; choose synchronization and settling behavior according to the interaction rather than assuming they must update identically.
- Keep transitions coherent across affected content, geometry, and lifecycle. Where an exit transition is intended, keep the outgoing content available until it completes. Define how repeated input, interruption, cancellation, and external state updates are handled for the relevant interaction.
- Prefer changes within the owning component or feature. Generalize a shared interface only for a current reuse need, and check caller compatibility and framework API constraints before exposing new types.

## Completion and verification

After approval, retain production `@Preview` fixtures for relevant visual and conditional states, using fixture data and light/dark themes. Remove only the isolated draft and dependencies used solely by that draft. Follow the proportional compilation and manual device verification rules in `AGENTS.md`; this workflow does not add automated UI interaction, layout, or screenshot tests.

Before reporting completion, identify the applicable branch (small adjustment or design draft), any image preference and explicit design approval/waiver, the previews maintained, and the verification actually performed. An image-delivery preference, approval, or waiver already supplied for the task remains valid; do not ask again.

Match evidence to the claim: previews establish static states, builds establish compilation, and deployment establishes delivery. Assess motion and interaction through actual use or a recording of the affected paths before reporting their visual quality as verified. Choose representative paths and interruption cases for the change; state any unverified behavior explicitly.
