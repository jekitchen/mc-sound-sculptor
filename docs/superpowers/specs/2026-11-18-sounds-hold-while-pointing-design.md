# Sounds list: hold while pointing (short design)

**Why.** On the Sounds tab the rows keep moving while you aim at one: with *Most recent* a sound that plays again
jumps to the top, and with any sort a newly heard sound is inserted and a sound older than 3 minutes drops out. The
list keeps the keyboard-focused row and the scroll position on a refresh, but not the row under the mouse, so clicking
a row or its buttons is hard. Pause (FR-1.3) helps only if you remember to turn it on and off.

**Approaches.**
1. *Hold while the mouse is over the list* (picked by the owner). Nothing to remember, and it fixes clicking where it
   goes wrong.
2. Make *Name* the default sort and Pause more prominent: cheap, but rows still shift on inserts and expiry.
3. Keep the order fixed until a "Re-sort" button is pressed: predictable, but the newest sound is no longer near the
   top, which is the point of Recently played.

**Design.**
- While the mouse pointer is over the list (its rows or its scrollbar), the list is **held**: no row moves, appears
  or disappears. The values a row already shows still update in place (play count, status, "12s ago").
- When the pointer leaves the list, the next refresh (at most a quarter second later, FR-1.6) catches up.
- While held, the result count reads "42 sounds · Held", or "42 sounds · Held · 3 new" when sounds are waiting to join.
- The player's own changes are not held: typing a search, changing the filter, sort or Apply to, and the row actions
  (Mute, Level, Edit, Add to set) update at once. A row the player acted on is rebuilt in its place.
- Pause and Sort stay as they are. Pause still freezes everything, values included.
- Applies to the Sounds tab and to the sound picker (FR-1.7), in both filter states.
- Keyboard use is unchanged (the focused row is already kept).

**PRD.** FR-1.3 gains one sentence about the hold. That wording change goes in its own docs PR for the owner's
approval.

**Left to the builder (Claude's calls).** How the pointer position is read in the tick; the exact "Held" text and its
translation keys; how waiting rows are counted; tests (a core test of the hold rule, and a game test that a sound
played while the pointer is over the list does not move rows until the pointer leaves).
