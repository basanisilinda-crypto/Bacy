# MBMF Ledger — Design language

This is an original, quiet financial service UI, not an entertainment/game or futuristic MDM control centre.

## Identity
- **Ink** #1D2521: primary typography and bottom navigation
- **Forest** #244E3C: verified account highlight and interactive selection
- **Paper** #F7F5EF: warm reading background
- **Bone** #FFFEF9: form surfaces and quick links
- **Clay** #AE5A39: single restrained logo accent
- **Rule** #DCD8CF: section dividers

No neon, cobalt, glassmorphism, glowing edges, animated robots, orbital-phone logo or fake finance figures.

## Real Android screen language
- **Admin:** an editorial live portfolio dashboard, 4 business KPIs, recent contracts, real EFT queue, finance registration and setup QR; existing online functionality preserved.
- **Client:** large outstanding balance from its assigned contract, next due date, finance status, phone identification, horizontal compact shortcuts, EFT instructions, payment-claim history, device owner information, and support. No signup/login.
- Top header is native Jetpack Compose; the existing screens remain native Android Views and are being migrated to Compose incrementally.
- Bottom navigation uses a solid ink rectangle, not a floating colorful dock.
- Input fields are labeled above the field so they remain understandable after data is entered.
- Empty values use an em dash until an authenticated backend snapshot exists.

## Safety and accessibility rules
- No fake numbers or financial confirmation screens.
- The Android theme sets its dialogs, selection accents and system bars to forest/ink.
- Emergency calling and help must remain available during any future legally permitted restriction.
- Verify at least 360dp width, system large text, screen readers, real LOOP hardware and Android QR enrolment.
- This design change does not imply remote policy enforcement, commercial signing or settlement release is complete.

## Client interaction pattern
The old two-column icon-tile grid has been removed entirely. Home actions are now four numbered, full-width rows with clear supporting labels. The payment, device and support rows navigate to their actual native screens.
