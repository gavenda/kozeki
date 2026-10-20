# Branding

Kozeki is named after Kozeki Ui (古関ウイ), the head of her school's Library Committee in the game Blue Archive. What makes the app look and sound like itself is taken from her: her halo is its mark, her colours are its theme, her face is on its empty screens, and its text is written as a librarian looking after the user's books.

This file records what is used, where, and the rules that hold it together, as of Kozeki 2.1.0.

## In short

1. **Kozeki is the app and Ui is the character.** Whoever does something in the text is Ui, and she is never called Kozeki.
2. **The halo keeps its own colour**, `#DACBBD`, in both themes. It is the one thing the app draws that does not take its colour from the theme.
3. **The theme is the app's own.** The wallpaper's colours are not taken up, and there is no setting for them.
4. **The launcher icon is her portrait**, not the halo.
5. **Ui says "I" only where her face is shown.** Elsewhere the text is plain, or names her in the third person.

## Names

Kozeki is her family name and Ui her given name. The app has the first and she goes by the second.

**Kozeki** is the app. It has versions and builds, and books can be in it: "Kozeki 2.0", "this build of Kozeki", "This book is no longer in Kozeki." It is a thing, so "it" is right for it.

**Ui** is the character. Whatever a person would be doing, she does: "Ui will look this book up once you are online and signed in to Hardcover." She is never "Kozeki" and never "it".

This holds for comments in the code and for anything written about the app as much as for the app's own text. `Expression.kt` opens with "A face of Ui, the app's character".

## Who Ui is

Her character sheet gives the facts, and her [Relationship Story](https://bluearchive.wiki/wiki/Ui/Relationship_Story) on the Blue Archive wiki shows how she behaves. The story's scripts are on that page's subpages, `/1` to `/4`.

- She heads Trinity General School's Library Committee and spends her days shut in the Old Library (古書館), deciphering and looking after old books. She is known as the Magician of the Old Library (古書館の魔術師). Her hobbies are reading, managing books and studying old books.
- She loves books and avoids people. Outsiders get the door shut on them, but the one person she trusts is an exception, and with them she is warm, earnest and eager to help. The app treats its user as that person.
- Caught out, she is flustered and apologetic. She stutters on a first letter, hesitates, and worries about being a burden.
- She minds the condition of books. A folded corner where a bookmark should be upsets her.
- She falls asleep a lot.
- She keeps a guest book in which an entry is written at every visit, and treasures it.

[Voice](#voice) says which of these the text draws on.

## The halo

The halo over her head is the app's mark: two rings tied together at the four points of the compass, around a dial with its hands at twelve and three.

It is drawn, not loaded as an image, so it is sharp at any size. `HaloMark` and `HaloLoadingIndicator` in `ui/components/Halo.kt` draw it from proportions measured off the artwork, and the launch screen has the same halo as an animated vector in `res/drawable/splash_halo.xml`. A change to its proportions has to be made in both files.

Its colour is `#DACBBD`, measured off the HALO panel of her sheet and kept in `@color/halo`, which both files read. Neither composable takes a colour. On the light theme that leaves the halo pale, about 1.5:1 against the surface where the dark theme gives it 12:1, just as it is pale on the sheet's white page. This comes with the fixed colour and is not to be corrected by tinting.

The halo is used where it means something: starting up, waiting, a goal reached, the sum of everything read. It is not scattered over every card.

| Where | What it does |
|---|---|
| Launch screen | Draws itself in over half a second. The rings run round from the top, then the second hand swings from twelve to three. |
| Anything loading, at 32dp and up | `HaloLoadingIndicator`, 48dp unless given another size. The rings drift one way while the hands go round the other. |
| Checking followed comics for new chapters | A 96dp loading halo in the middle of the sheet a pull to refresh opens in the library. Its hands go round once over the whole check (`progress`), and it is the plain wait until the comics are counted. |
| A cover whose details are being looked up | The loading halo over the dimmed cover, with `glow = true`. Nothing else glows, since the glow only shows against something dark. |
| A goal that is met | The day's and the year's goal ring (`GoalRing`) become the halo. The full ring takes the halo's colour and gains the inner ring and the ties, and the percentage stays where the dial would be. |
| The calendar | The cell for 23 April, her birthday and World Book Day, is ringed with the halo every year. It is the rings only (`dial = false`), as wide as the cell's cover box and centred on it, over whatever the cell shows. |
| All-time statistics | A 200dp halo behind the summary card's text, cut off by the card's trailing edge. The card is leather (`tertiaryContainer`) and the halo is at full strength on it in both themes. |

Three places are left without it on purpose:

- **The launcher icon**, which stays her portrait.
- **Spinners of 24dp and under**, inside buttons and rows. They are the stock `LoadingIndicator`, because the halo's rings run together at that size.
- **Empty and error screens.** Her portrait is there and brings her own halo with it.

## Colour

The app has one theme, its own, the same on every device in light and in dark. `AppTheme` in `ui/theme/Theme.kt` has MaterialKolor build it from three colours kept in `ui/theme/Color.kt`, in the `PaletteStyle.Cmf()` style at a contrast level of 0.5.

| Input | Value | Taken from | Becomes |
|---|---|---|---|
| Seed | `#665EC2` | Chosen before her sheet was in the project. It is within a few degrees of the blue-violet of her scarf and hairband. | `primary` and the surfaces |
| Gold | `#DBBC95` | Her pendant | `secondary` |
| Leather | `#998584` | Her trunk | `tertiary` |

Each input sets a hue and the style picks the tones, so the gold and the leather are never on screen as written. The gold shows as bronze. These are the roles as MaterialKolor 6.0.0-beta01 resolves them:

| Role | Light | Dark |
|---|---|---|
| `primary` | `#3D3396` | `#B6B0FF` |
| `primaryContainer` | `#665EC2` | `#6F68CC` |
| `secondary` | `#543F21` | `#D2B48D` |
| `secondaryContainer` | `#8A704E` | `#8A704E` |
| `tertiary` | `#4F3F3E` | `#CAB4B2` |
| `tertiaryContainer` | `#796766` | `#83706F` |
| `surface` | `#FCF8FF` | `#0E0D18` |

A later version of the library may move these by a shade. The three inputs are what is fixed.

- **Violet** is the default accent: filled buttons, links, EPUB progress, and the plate behind Ui's portrait.
- **Gold** fills whatever Material draws in the secondary container, such as the selected navigation item, selected chips and tonal buttons. The app also asks for it by name for a book's reading-state label, the badge on a search result that is already owned, the tint behind a selected book or day, and the badge on an empty state's portrait.
- **Leather** is on the goal ring and the week's day rings, on chart bars, and on the physical-copy card of a book's page, where it tells paper progress from EPUB progress. In all-time statistics it is also the summary card itself (`tertiaryContainer`), the one card there with no leather marks on it.
- **The display mode buttons** in settings take one colour each: System violet, Light gold, Dark leather. Each sits in its colour's container and takes the solid colour when selected.
- **The range buttons** in statistics do the same by kind of range: Day violet; Week, Month and Year gold; All leather. On the year's longest-books card, Physical is leather and EPUB violet, as on a book's page.
- **A generated cover** takes one of the three containers.

In the light theme `secondary` and `tertiary` are two dark browns that cannot be told apart, so anything that has to separate gold from leather there should use their containers.

Every colour drawn in Compose comes from `MaterialTheme.colorScheme`, the halo's excepted. Three colours are written out as resources:

| Resource | Value | For |
|---|---|---|
| `@color/halo` | `#DACBBD` | The halo |
| `@color/ic_launcher_background` | `#E8EAF6` | Behind the portrait on the launcher icon |
| `@color/window_background` | `#F9FAEF` in light, `#12140E` in dark | The window before the first frame, which the launch halo is drawn on |

The window background still has its values from 1.0, when the app followed the wallpaper and had no surface of its own to match. The theme's surface is now `#FCF8FF` and `#0E0D18`.

The page of a book is a separate matter. The reader's Light, Sepia and Dark are the EPUB renderer's own, and only its App setting takes the theme's surface.

Type, shapes and motion are Material 3 Expressive's defaults, with `MotionScheme.expressive()`. The app has no typeface and no wordmark of its own.

## The launcher icon

The icon is her portrait: her face, with the edge of her halo in the corner, on `#E8EAF6`. Its themed (monochrome) layer is the same portrait in black and white. Both are bitmaps under `res/mipmap-*`.

It stays the portrait, themed layer included. The halo does not go here.

## Ui's portraits

`Portrait(expression)` in `ui/components/Expression.kt` shows Ui from the shoulders up in front of a plate, Material's nine-sided cookie shape in `primaryContainer`. The plate cuts her off below, while her head stands clear of it and her halo rises out of its top. It is 128dp wide unless given another width.

Inside the app her portrait is in two kinds of place and nowhere else: wherever a screen has nothing to show or has failed, and About.

**Empty and error screens.** `EmptyState` requires an `expression` and an `icon`. The icon sits on a 40dp gold badge (`secondaryContainer`) at the plate's lower end corner, over the book she holds. Smaller messages inside a sheet or a card, such as a book with no notes yet, are plain text with no face.

**About.** The last thing in Settings, with no card and no heading: her `CONTENT` face at 96dp, a speech bubble beside it with her line, and "Kozeki" with the version underneath. Under the version are two plain lines in the third person: "Kept by Ui, Magician of the Old Library" and "Character design and illustration by CHILD, from Blue Archive". On 23 April the bubble says "It's my birthday today. A book read would be present enough." instead. It is the only speech bubble in the app.

Eleven faces are cut, and a new empty state picks from them first. The source number is the `<n>` of `branding/expressions/Ui_Portrait_Expression_<n>.webp`.

| Face | Shows | Source | Used for |
|---|---|---|---|
| `HAPPY` | Closed-eye smile | 22 | The library with no books; a day with nothing read that is her birthday |
| `CONTENT` | Slight smile | 3 | All-time statistics with nothing on record; About |
| `NEUTRAL` | Her usual flat look | 1 | A library filter with nothing under it |
| `CURIOUS` | A small "o" | 8 | Library search, before typing |
| `STUDIOUS` | Monocle | 23 | Add book, before searching |
| `ASLEEP` | Eyes closed | 7 | A day with nothing read |
| `DOWNCAST` | Half-lidded eyes | 15 | Library search with nothing found; an author with no books listed; Add book on Comics with no source installed |
| `WORRIED` | Mouth open, a bead of sweat | 13 | Add book, nothing found |
| `DISMAYED` | White eyes, sweat | 10 | A failed lookup, in Add book and on an author's page |
| `DUMBFOUNDED` | Black dots for eyes | 21 | An author whose page is gone |
| `SHOCKED` | Comic yell | 14 | A book the reader cannot open |

Thirteen more are in the sources and not cut: 2 neutral with her mouth open, 4 uneasy, 5 tired, 6 a sigh with eyes closed, 9 smug with her tongue out, 11 aghast, 12 grumbling with eyes shut, 16 a shadow over her eyes, 17 and 18 blushing, 19 speaking brightly, 20 yelling in tears, and 99 eyes closed and calm, which is close to `ASLEEP`.

### Cutting a new face

The sources are 1000×1625 and hold colour premultiplied by alpha. Drawn as they are, soft edges get a grey fringe, plainest on the halo over a light theme.

1. Divide RGB by alpha (`rgb * 255 / a`, clipped to 255).
2. Crop 432×546 from the top, starting 234px in from the left. The 432px square at the foot is the plate, centred on her face, and the 114px above it is where the halo stands out. `Portrait` holds this ratio as constants.
3. Save as WebP at quality 90 to `res/drawable-nodpi/expression_<name>.webp`, about 70 KB, and add it to `enum class Expression`.

## The generated cover

A book with no cover art gets one drawn as the book Ui carries: a shaded spine down one side and, on the board, a line that frames the title, with a pair of S-shaped scrolls in each corner. It is `CoverPlaceholder` in `ui/components/BookCover.kt`.

- It takes one of the three container colours, picked by a hash of the title so that a book always gets the same one. The frame and the title are in the matching on-container colour.
- A cover narrower than 80dp has no scrolls, and the line goes all the way round instead. Small covers, such as a day of the calendar, show the title's first letter in place of the title.
- It mirrors for right-to-left, where a book is bound on the right.
- It is always drawn underneath the real cover, so one that fails to load never leaves a blank box.

Search results from the catalogue are not books yet, and one without a cover keeps a plain box (`MetadataResultItem.kt`).

## Voice

The text reads as a librarian keeping the user's books. It is dry and short and never chirpy: Ui avoids people, and the user is the one person she lets in.

### Who is speaking

Where her face is shown, Ui speaks for herself in the first person, in either the title or the message. Away from her face the text is plain, and where someone has to be doing the work it names her: "Ui will look this book up once you are online and signed in to Hardcover."

- Her line is usually the message under a plain title, as in "The shelves are empty" over "W-Welcome. Hand me an EPUB to start reading…". When a lookup fails, her apology is the title ("S-Sorry. I can't search right now.") and the reason under it stays plain.
- Nothing under her face speaks of her in the third person, with one exception. On a day with nothing read she is asleep, so she is narrated: "Nothing was read, so Ui got some sleep."
- A message shown both under her face and away from it is two strings. The reader says "I can't find this book's EPUB on this device." and the book's page says "Ui cannot find this book's EPUB on this device."
- Two buttons say "I" for the user: "I want this" and "I own this". They are the user speaking.

### Words

| Say | For |
|---|---|
| the catalogue | Where a book's details are looked up. Hardcover and Google Books are named only where it matters which one. |
| your library, your shelves | The user's own books, wishlist and purchases included. |
| look up | Getting a book's details from the catalogue. |

So the Settings heading is "Catalogue", a book offers "Look up in the catalogue", and a search that finds nothing is "Not on your shelves" in the library and "Not in the catalogue" online.

### What comes from her story

- **The visitor she lets in.** The empty library opens with "W-Welcome.", and the add-book screen asks "Was there something you needed?"
- **Flustered apologies.** She stutters on a first letter in four strings and no others: "W-Welcome.", the two "S-Sorry." titles of a failed lookup, and "Wh-What happened to this one?" for a damaged EPUB. Lighter hesitations, "Huh…?" and "I couldn't find it…", are as rare.
- **The condition of books.** "Tell Ui the page you are on, so no corner needs folding.", and her alarm at a damaged EPUB.
- **Sleep.** Her `ASLEEP` face on a day with nothing read.
- **Her birthday.** 23 April, also World Book Day. A day with nothing read becomes "World Book Day" over "It's also my birthday… Will you read something today?" with her `HAPPY` face, and About has "It's my birthday today. A book read would be present enough."
- **The guest book.** All-time statistics with nothing on record yet: "The guest book is empty", then "I'll write an entry every time you read".

### What stays plain

Labels, tab names, reading states, buttons, confirmations, Settings and backup, and the reason given for an error.

### Left out on purpose

- "Children" for the user's books, which is what she calls books in her story.
- "Sensei" for the user.
- The stutter anywhere beyond the four strings above.

## Source artwork

The originals are in `/branding`, which is git-ignored and so only on the developer's machine.

| File | What it is |
|---|---|
| `sheet.jpg` | Her character sheet, 2263×1600. The halo's colour, the gold and the leather were measured off it. Its metadata records a 4677×3307 original. |
| `halo.webp` | The halo on its own. |
| `expressions/` | 24 full-length portraits, 1000×1625, in one pose with different faces. |
| `sisterhood.png` | The emblem on her trunk, 880×1364. Not in use. |

What ships in the app is the launcher icon under `res/mipmap-*` and the eleven cut portraits in `res/drawable-nodpi`. The halo, the cover and the speech bubble are drawn in code.

Ui and this artwork are from Blue Archive. Her sheet credits her design and illustration to CHILD.

## Proposed and not built

These were put forward and neither taken up nor turned down. Each has a label to ask for it by.

- **T1.** "Shelve" in place of "import", through the button, the progress and the result: "Shelve an EPUB", "Shelving…", "Shelved “x”", "Shelved 3 of 4 files.", "already on your shelves", and "Shelved EPUB" for the badge. The button on a book's own page would become "Add the EPUB", which would also settle the message there that says "Add it again" over a button reading "Import". The share sheet's label would stay, being read outside the app. It is planned in full and waiting for a go-ahead.

The rest are from her sheet, which also gave the bound cover and the gold and leather that are already built.

- **A.** Text from her profile, partly built (her title and illustrator credit are in About, and her birthday is marked). Still to do: her hobbies line for About, and "Old Library" as a name for something.
- **B.** The cord and tassel that hang from her book, as a marker on a book being read.
- **C.** A "KOZEKI" wordmark with 古関, for the launch screen's branding slot.
