# Kozeki

Kozeki is a personal Android app for books and comics (package `dev.gavenda.kozeki`). It reads EPUBs, keeps a library with reading history, notes, goals and statistics, and looks books up on Hardcover or Google Books. It reads comics through installed Mihon extensions and tracks them on AniList. The developer is its only user.

It is named after Kozeki Ui, the librarian of Blue Archive, who is its character. [BRANDING.md](BRANDING.md) records how the app looks and sounds: the halo, the theme colours, her faces and the voice of its text. Read it before changing any of those.

## Icons

Every icon in `app/src/main/res/drawable/*_48px.xml` is a Google Material Symbol: **Outlined, optical size 48, weight 400, grade 0**. 
The `_fill` ones are the FILL 1 version. 

Exceptions: 
- `purchased_book_fill` (custom)
- `purchased_book_off_fill` (custom) 
- `splash_halo` (brand mark)

IMPORTANT: Don't take icons from the Android SDK's local copy (`$ANDROID_HOME/icons/material/...`).

To add a Material Symbol icon:
1. Fetch the web SVG from Google's repo, using the symbol's name from [fonts.google.com/icons](https://fonts.google.com/icons):

   ```sh
   # Outlined
   curl -fO https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/<name>/materialsymbolsoutlined/<name>_48px.svg
   # Filled
   curl -fO https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/<name>/materialsymbolsoutlined/<name>_fill1_48px.svg
   ```

2. Copy its `d` attribute into a vector drawable named `<name>_48px.xml` (or `<name>_fill_48px.xml`). The web SVG's viewBox is `0 -960 960 960` (y runs -960..0), so wrap the path in a group moved down by 960:

   ```xml
   <vector xmlns:android="http://schemas.android.com/apk/res/android"
       android:width="48dp"
       android:height="48dp"
       android:viewportWidth="960"
       android:viewportHeight="960"
       android:tint="?attr/colorControlNormal">
     <group android:translateY="960">
       <path
           android:fillColor="@android:color/white"
           android:pathData="PASTE_D_HERE"/>
     </group>
   </vector>
   ```

When using them in compose:
- Draw them at 24dp: `Icon(painterResource(R.drawable.<name>_48px), ..., modifier = Modifier.size(24.dp))`.

## Strings

UI text in `app/src/main/res/values/strings.xml` is dry and short. `BRANDING.md` ("Voice") has the full rules; read it before writing copy.

Commit `98d3257` "Shorter strings" cut about 30 strings that said too much. To find them, every string was read against the voice rules in `BRANDING.md` and the user's own example ("That is the latest chapter the source has." → "This is the last chapter"). Each candidate was looked up in the code to see where it shows. Before writing a new string, check it against what that pass removed:

- **No behind-the-scenes explanation.** Don't say how the app works inside: caches, sources, sync timing, what happens "until then". Before: "…I'll look it up. I keep what I find on this device, so asking twice costs nothing." After: "…I'll look it up."
- **One sentence is usually enough.** Drop a second sentence that only reassures or restates. Before: "The details you changed are not saved. The book stays as it was." After: "The details you changed are not saved."
- **Don't repeat what the screen already says.** If the title says "Not in your library", the body is just "Nothing matches "%1$s"."
- **No trailing clauses** such as "left for later", "left as it is" or ", so nothing was connected".
- **Errors name the problem and, if there is one, the fix.** Before: "The catalogue's request limit has been reached. Try again later." After: "Too many lookups. Try again later." Keep a code or reason when there is one: "Google Drive refused the request (%1$s)."
- **No "Contact the developer".** The developer is the only user.
- **Subtitles and buttons are a few words.** Use "Tap to sign in", "Track on AniList", "How often to check followed comics".
- **Ui says "I" only where her face is shown.** Elsewhere the text is plain or names her in the third person. This came up twice in that pass.
- **Ui is not chirpy.** "The set is complete!" became "The set is complete."
- **Check every place a string is used.** One string can show in more than one place. `lookup_error_offline` was also shown for failed sign-ins, where "the catalogue" made no sense.

Leave alone: lines quoted in `BRANDING.md`, delete and restore warnings (there the detail is the point), and plain labels. If a change touches a quoted line, update `BRANDING.md` too.
