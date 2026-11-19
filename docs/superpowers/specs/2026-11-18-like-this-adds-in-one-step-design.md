# Picker: Like this… adds in one step (short design)

**Why.** In the sound picker, Like this… opens a pop-up of the sound's groups with checkboxes and a Done button,
while the picker's Cancel and Add stay visible below it. Ticking a group only picks it; Done just closes the pop-up;
Add (below) is what writes to the set. The owner found this confusing: three live-looking buttons, a Done that seems to
do nothing. The owner usually wants one group and is then finished.

**Approaches.**
1. *One-step Add per group* (picked by the owner): each group row has Add, which adds it and closes the picker.
2. Keep the ticks, rename Done to "Back" and show the picked count in the pop-up: still two confirmations for the
   usual one-group case.
3. Make the pop-up's own button add the ticked groups: one step, but keeps checkboxes for a rare multi-group case.

**Design.**
- Each group row in the Like this… pop-up has one **Add** button instead of a checkbox (a group the set already has
  shows "✔ In this set" and no button, as now). Add puts the group in the set and closes the picker, exactly like the
  picker's Add: back in the set editor with the new entry, nothing saved until the editor's Save.
- Sounds already ticked in the list are added too (owner's choice). The group's Add tooltip says so when there are
  some: "Add this group and 2 picked sounds"; otherwise "Add this group".
- If the set is full, the "entries full" toast shows, the pop-up stays open, and nothing is picked.
- The pop-up's **Done** becomes **Back**: it only closes the pop-up (as Esc and a click outside do) and returns focus to
  the row's Like this….
- While any pop-up is open (Like this…, Level…, menus), the screen behind it is dimmed, so the buttons behind it no
  longer look pressable. (They already cannot be pressed: a click outside a pop-up only closes it.)
- Picking several groups at once is gone; open the picker again for another group.

**PRD.** FR-1.7 and FR-4.8 wording ("Like this… to add one of the sound's groups") goes in its own docs PR for the
owner's approval.

**Left to the builder.** The dim colour; how the panel tells its picker screen to add; translation keys and exact
tooltip text; updating the picker and accessibility game tests for Add/Back.
