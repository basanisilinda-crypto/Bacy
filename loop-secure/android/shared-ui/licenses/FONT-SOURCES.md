# Typeface sources and licenses

These Android font resources are static TrueType instances derived from the
upright variable fonts published by the official Google Fonts repository.
Both families are licensed under the SIL Open Font License, Version 1.1;
the corresponding upstream license files are included in this directory.

Downloaded from the Google Fonts `main` branch on November 6, 2026:

- Fraunces: https://github.com/google/fonts/tree/main/ofl/fraunces
  - Source: `Fraunces[SOFT,WONK,opsz,wght].ttf`
  - Original project: https://github.com/undercasetype/Fraunces
  - License: `Fraunces-OFL.txt`
- DM Sans: https://github.com/google/fonts/tree/main/ofl/dmsans
  - Source: `DMSans[opsz,wght].ttf`
  - Original project: https://github.com/googlefonts/dm-fonts
  - License: `DM-Sans-OFL.txt`

The installed `fontTools.varLib.instancer.instantiateVariableFont` API generated
these complete static instances. Family, subfamily, full, PostScript, and
typographic name records were updated to match each static style:

| Resource | Weight | Optical size | Other axes |
| --- | ---: | ---: | --- |
| `fraunces_regular.ttf` | 400 | 32 | SOFT=0, WONK=1 |
| `dm_sans_regular.ttf` | 400 | 14 | None |
| `dm_sans_medium.ttf` | 500 | 14 | None |

Fraunces uses an optical size intended for headings. DM Sans uses an optical size
intended for body text. Each output has a TrueType signature, an OS/2 weight that
matches the resource, and no remaining variable-font tables (`fvar` or `gvar`).
No glyph subsetting or outline editing was performed.
