# Zabankhan design system

Zabankhan follows the editorial design of French Reader (`/Users/ziaee/git/FrenchReaderSpike`, `docs/Redesign the application’s user interfac.md` and `ui/theme`, `ui/components` on `main`): a premium digital magazine combined with a calm language-learning dashboard. The editorial experience dominates; learning information is useful and motivating without looking like a game.

One deliberate difference: the primary accent is lapis blue from the Zabankhan logo instead of French Reader's oxblood. To switch, change only the primary roles in `ui/theme/Theme.kt`.

## Colour tokens

Light:

| Role | Hex |
|---|---|
| primary / onPrimary | `#24418F` / `#FFFFFF` |
| primaryContainer / on | `#DFE5F6` / `#0F2560` |
| secondary (muted teal) / on | `#2F6F6A` / `#FFFFFF` |
| secondaryContainer / on | `#D7ECE8` / `#163F3C` |
| tertiary (amber, progress) / on | `#A66A16` / `#FFFFFF` |
| tertiaryContainer / on | `#F9E3BB` / `#5D3908` |
| background (paper) | `#F7F1E7` |
| surface (warm white) | `#FFFBF4` |
| surfaceVariant / onSurfaceVariant | `#EFE6D9` / `#655B55` |
| onBackground, onSurface (carbon) | `#211C19` |
| outline / outlineVariant | `#8C8178` / `#D8CEC3` |

Dark: background `#181513`, surface `#211D1A`, surfaceVariant `#2D2825`, text `#F2EAE0`, muted `#D2C6BC`, primary `#B4C5FF` on `#102A6B`, primaryContainer `#22397A`, secondary `#86BDB6`, tertiary `#E3B66F`, outlineVariant `#49413C`.

No purple, neon, gradients as decoration, heavy shadows or glass effects. Cards are warm white or tonal with subtle separation; elevation at most 1dp for cards, 3dp raised, 6dp overlays.

## Typography

- Interface (navigation, buttons, metadata, settings, labels): Vazirmatn 400/500/600/700. It covers Persian and Latin.
- Editorial (screen titles, section titles, article headlines, the review word): Literata (Regular, SemiBold) for Latin-script text; Persian editorial text uses Vazirmatn SemiBold. Choose by the text's script, not by UI language (`editorialStyle(text, role)`).
- Scale: screenTitle 32/40 SemiBold, sectionTitle 24/30 SemiBold, articleHeadline 20/26 SemiBold, body 16/24, metadata 13/18, label 14/20 Medium.

## Shapes, spacing, sizes

- Shapes: extraSmall 8, small 12, medium 16, large 24, extraLarge 32.
- Spacing (8-point): 4, 8, 16, 24, 32, 48. Screen gutter 16–20.
- Sizes: icon 24, iconSmall 18, touch target 48, reader measure 640dp max width.

## Components (`ui/Design.kt`)

`EditorialTopAppBar` (background-coloured, back is an auto-mirrored arrow icon), `EditorialSearchEntry`, `EditorialSectionHeader`, `MetadataBadge`, `EditorialProgressIndicator` (bar plus visible label), `EditorialPrimaryButton` (with loading), `EditorialIconButton` (48dp, content description), `EditorialEmptyState` (centred icon, title, body, action), `LearningSummaryStrip` (streak, words learned, goal progress; only real data), `EditorialCard`. Bottom navigation colours the selected icon and label, without the capsule indicator.

## Rules

- Content direction follows the content (`contentStyle`); the screen follows the UI language. Persian meanings align RTL, French text stays LTR, and content text fills its width.
- Zabankhan rule (overrides French Reader): no horizontally scrolling menus or chip rows. Chips wrap in `FlowRow` or go in a dropdown. A news carousel of cards is allowed.
- Destructive and secondary actions go in an overflow menu, with confirmation for delete.
- Every icon has a content description; nothing is communicated by colour alone; 48dp touch targets; text scaling must not clip.
- All strings come from compose resources (en, fa, fr).
