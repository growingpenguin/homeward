# Black-box test cases for Homeward

**What this is.** Test cases for the DALI Lab Fullstack Challenge 26F, written and run by an outside evaluator. The tester worked only from the challenge text (families separated after a hurricane evacuation; no phones, no ID, some people do not want to be found, tired non-technical volunteers; frontend + backend + database + live link). The tester did **not** read any code or design documents. The only file read was `README.md`, for the live link and the demo logins.

- **Date:** 2026-10-20
- **Live app:** https://hilarious-cassata-a51a3c.netlify.app
- **How it was run:** every case was run on the live site with Playwright (headless Chromium), each in a brand-new browser context (the same as a private window: no cookies, no saved data). Logins were the demo staff from the README (PIN 2468).
- **Data:** all people created were made up (for example "Calla Northrup", "Orla Fennimore", "Nimbus Quarrington"). Nothing real was entered.
- **Honest notes about the run:**
  - Printing was stubbed out (headless browser has no printer), so "Print" was checked only up to the slip appearing on screen.
  - I sent 5 wrong PINs to one demo account (Volunteer Riley) while setting up, which is more than "a few". That account was locked for 15 minutes (this is itself test T02). I used Lead Morgan for the Eastside desk after that.
  - Some cases were re-run after my own script mistakes, so the shared demo database now holds a few duplicate made-up people (for example five "Zorbal Xyzzyx"). This is described where it matters (T30).

## Summary

**35 cases: 30 PASS, 4 PARTIAL, 1 FAIL, 0 NOT APPLICABLE.**

| ID | Title | Result |
|---|---|---|
| T01 | Open the link in a private window | PASS |
| T02 | Staff log in; wrong PIN; too many tries | PASS |
| T03 | Register someone with only a nickname | PASS |
| T04 | Nothing asks for ID or immigration status; made-up name works end to end | PASS |
| T05 | A person with no phone gets a paper slip (code + PIN) | PASS |
| T06 | Happy path: family finds each other across three shelters | PASS |
| T07 | Code check is forgiving and gives nothing away on a wrong guess | PASS |
| T08 | Lost slip can be replaced | PASS |
| T09 | Spelling mistakes and sound-alike names | PASS |
| T10 | Nicknames | PASS |
| T11 | Age unknown | PARTIAL |
| T12 | Wrong age guess | PASS |
| T13 | Accents, hyphens and non-English letters | PASS |
| T14 | Looking for several relatives in one search | PASS |
| T15 | Looking for several relatives in separate searches on one slip | FAIL |
| T16 | "Keep me out of searches" is respected | PASS |
| T17 | Someone dangerous searching cannot tell hidden from not-here | PASS |
| T18 | Search results never show where a person is | PASS |
| T19 | "No" and "Not now" look the same as waiting | PASS |
| T20 | "Tell them I'm safe (not where I am)" | PASS |
| T21 | "Connect" shows exactly what will be shared | PASS |
| T22 | A found child is released only by a lead | PARTIAL |
| T23 | Unknown age is treated as a child | PASS |
| T24 | A child who is searching | PASS |
| T25 | A volunteer cannot open the lead page | PASS |
| T26 | A lead can protect someone completely | PASS |
| T27 | Fix a typo; change of mind about being found | PASS |
| T28 | Typing up a stack of paper forms | PASS |
| T29 | Someone registered after a search was saved | PARTIAL |
| T30 | The same person entered twice; searchers appearing as matches | PARTIAL |
| T31 | Double-clicking Save | PASS |
| T32 | Bad connection: internet drops, slow server | PASS |
| T33 | Reloading the page in the middle of a form | PASS |
| T34 | Shared computer: idle lock, log out, back button, direct links | PASS |
| T35 | Tablet and phone screen sizes | PASS |

---

## Test cases in detail

### T01 Open the link in a private window
- **Why:** The challenge asks for a live link and says to try it in a private window. The server may be asleep.
- **Steps:** Open the link in a new, empty browser context. Wait for the log-in form.
- **Expected:** Page loads over https with no saved data needed. If the server is slow, the page says so in plain words.
- **Actual:** The log-in form appeared in about half a second (server was awake). While waking, the page text is: "Connecting. Please wait, this can take up to a minute." The form has "Your shelter" (Eastside Community Center, Lincoln High Shelter, Westfield High Gym), "Your name", "Your PIN", "Log in".
- **Result:** PASS

### T02 Staff log in; wrong PIN; too many tries
- **Why:** Volunteers are tired and not technical, so errors must be kind and clear. The app holds private information, so staff access must be protected.
- **Steps:** Log in with a wrong PIN. Log in with a wrong name. Log in correctly. (Setup mistakes took me to 5 wrong PINs on one account.)
- **Expected:** A plain message that does not say which part was wrong. Repeated guessing is slowed down.
- **Actual:** Wrong PIN and wrong name gave the same line: "That didn't work. Check the shelter, your name and your PIN." The shelter and name stayed filled in; the PIN was cleared. Leaving a field empty: "Please choose your shelter, and write your name and PIN." After 5 wrong PINs: "Too many tries. Please wait 15 minutes or ask a lead." The correct PIN was also refused during the lock. Only that one account was locked; other staff could still log in.
- **Result:** PASS. Note: anyone typing 5 wrong PINs on a staff name locks that volunteer out for 15 minutes.

### T03 Register someone with only a nickname
- **Why:** Many people have no ID and may not give a real name. A volunteer at 2 a.m. with a line of families needs it quick.
- **Steps:** Choose "Someone is here" then "No, the person is here with me". Try pressing Next with an empty name. Type only "Mouse". Click through to Save. Also press the Enter key instead of clicking Next.
- **Expected:** Only a name or nickname is required. Few steps. Unknown answers are allowed.
- **Actual:** Empty name: "Please write a name or nickname." Five short steps (name, age, what to do if someone looks for them, optional extras, check and save). Age is optional ("A guess is fine; leave it empty if nobody knows"). The summary showed "Not given" for empty items. The screen gives the volunteer a line to say: "It doesn't have to be your legal name. A nickname is fine." Enter moves to the next step. Result: "Mouse is saved." with a slip.
- **Result:** PASS

### T04 Nothing asks for ID or immigration status; made-up name works end to end
- **Why:** Undocumented people may be afraid to give a real name; people without ID must not be blocked.
- **Steps:** Read every question on all five registration steps. Then search for "Mouse" from another shelter.
- **Expected:** No question about ID, papers, status, address or a real name. A nickname can be found.
- **Actual:** The questions are only: name or nickname, other names, age, who is with them (children), what to do if someone looks for them, home area, "anything that helps recognise them", phone. No ID or status question anywhere. Searching "Mouse" at Westfield returned the "Mouse" card.
- **Result:** PASS

### T05 A person with no phone gets a paper slip (code + PIN)
- **Why:** Many phones are lost or dead, so people cannot be told by phone or look up contacts.
- **Steps:** After saving, click "Print a slip". Read it. Reload the page.
- **Expected:** A short code and PIN on paper that works without a phone. The PIN is not left on screen for others.
- **Actual:** Slip shows "Code: HW-GNLH", "PIN: 1405", the shelter name, "Shelter desk phone: 555-0101" and "Keep this slip. Family looking for you can use this code at any shelter." Also: "The PIN is shown only now. Print the slip or write the PIN down before leaving this screen." After reload: "This person already has a slip." and no PIN. The code uses letters and digits without look-alikes (the on-screen keypad has no 0, 1, I or O).
- **Result:** PASS

### T06 Happy path: family finds each other across three shelters
- **Why:** The core of the challenge: separated relatives at different shelters.
- **Steps:** At Eastside, register "Calla Northrup", about 50, "Ask me first". At Lincoln, "Ferris Northrup" (brother) searches for her, asks, adds a message, prints his slip. At Eastside, open "Answers waiting", choose "Connect with Ferris Northrup", write how to meet, confirm. At Westfield, enter Ferris's code and PIN, turn the screen around.
- **Expected:** Ferris learns how to reach his sister, without anyone's location leaking before she agreed.
- **Actual:** Before the answer, the Westfield screen said: "Your request is saved. If they choose to connect, we'll let you know here." After Eastside connected, it said: "They want to connect. Here's how: Call the Eastside desk and ask for Calla". No phone was needed at any point.
- **Result:** PASS

### T07 Code check is forgiving and gives nothing away on a wrong guess
- **Why:** Volunteers copy codes by hand from paper. A stranger guessing codes must learn nothing.
- **Steps:** At Westfield, check a real slip with: a wrong PIN, a wrong code, a second wrong PIN, then the right code and PIN, then the code in lower case.
- **Expected:** Same neutral message for every wrong guess. Right details work, case does not matter.
- **Actual:** Every wrong try gave "That code and PIN didn't match. Please check the slip and try again." (no hint about which was wrong). Right details worked after two misses. Lower-case code worked. The screen also has a big on-screen letter keypad.
- **Result:** PASS

### T08 Lost slip can be replaced
- **Why:** On paper, slips get lost. Without a phone there is no other way back.
- **Steps:** As Lead Morgan, open a person's slip page and choose "Reprint a lost slip (lead)". Confirm. Then try the old PIN at another shelter.
- **Expected:** Only a lead can do it, with a warning; the old slip stops working.
- **Actual:** Button exists only for the lead. Warning: "Their old slip and PIN will stop working, and the new PIN is shown only once. Print it now?" with "Yes, make a new slip" / "Cancel". After confirming, the old code and PIN gave "That code and PIN didn't match." (I did not capture the new slip's screen text.)
- **Result:** PASS

### T09 Spelling mistakes and sound-alike names
- **Why:** Names are written in a hurry, heard over noise, and spelled differently.
- **Steps:** Search for "Orla Fennimoor", "Robrt Vantwel", "Josey Nunyes-Balard", "Zelda Quillfrd" (registered as Fennimore, Vantwell, Núñez-Ballard, Quillford).
- **Expected:** The right person shows up, with a plain reason.
- **Actual:** All four found. Reasons in plain English, for example "name spelled a bit like Robert Vantwell", "name sounds like Robert Vantwell".
- **Result:** PASS

### T10 Nicknames
- **Why:** Families use nicknames; people may register under one.
- **Steps:** Register "Robert Vantwell" with other names "Bobby, Bob". Search "Bob Vantwell" and "Bobby".
- **Expected:** Found either way.
- **Actual:** "Bob Vantwell" found with "a nickname matches Robert Vantwell". "Bobby" alone found him ("name sounds like Robert Vantwell").
- **Result:** PASS

### T11 Age unknown
- **Why:** Many people cannot state an age and have no ID.
- **Steps:** Register "Mama Thessaly" with no age. Search for her with an age range of 30 to 50, and again with no age.
- **Expected:** Found. The missing age does not hide her or block the family unnecessarily.
- **Actual:** Found both times, but with the label "a child: a lead decides before anything is shared", and she cannot be answered by an ordinary volunteer. Safe for real children, but a grown woman with no recorded age is treated as a child.
- **Result:** PARTIAL. Many adults without ID will have no age, and each one then needs a lead (see Findings).

### T12 Wrong age guess
- **Why:** Searchers guess ages badly.
- **Steps:** Search "Orla Fennimore" (registered about 62) with an age range of 20 to 30.
- **Expected:** Still found; the mismatch is noted but does not hide her.
- **Actual:** Found, with "age doesn't fit (about 62)".
- **Result:** PASS

### T13 Accents, hyphens and non-English letters
- **Why:** Families have names from many languages; keyboards at shelters may not type accents.
- **Steps:** Register "José Núñez-Ballard". Search "Jose Nunez Ballard". Also register `<b>Tess</b> O'Brien "Tessie" ñandú 李小龍` and search "Tess OBrien".
- **Expected:** Plain-letter search finds the accented name. Odd characters are saved and shown safely.
- **Actual:** "Jose Nunez Ballard" found "José Núñez-Ballard" (it also listed two other "José" people, a little noise). The odd name was saved and shown exactly as typed, as text (no bold formatting appeared, no page break).
- **Result:** PASS

### T14 Looking for several relatives in one search
- **Why:** A mother is usually looking for more than one person.
- **Steps:** Search "Orla Fennimore, Wren Ashdown".
- **Expected:** Cards for both people.
- **Actual:** Two cards, one for each.
- **Result:** PASS

### T15 Looking for several relatives in separate searches on one slip
- **Why:** Same family need. A parent searches for one child, then another, in one visit, and gets one slip.
- **Steps:** Register "Cressida Dalloway" and "Dorian Dalloway" at Eastside. At Lincoln, "Ilse Dalloway" searches for Cressida and sends a request, then searches for Dorian and sends a request, then prints one slip. At Eastside, connect Ilse with Dorian. At Westfield, check Ilse's slip.
- **Expected:** The slip shows the good news about Dorian.
- **Actual:** The volunteer screen said "2 searches". After turning the screen around, it showed only: "Your request is saved. If they choose to connect, we'll let you know here." The connect for Dorian was **not shown**. (Reproduced a second time by accident with "Linnea Fennimore": 11 searches on one slip, the one that was connected was not shown.) When each search got its own slip, the connect did show: "They want to connect. Here's how: Meet at the Eastside desk at noon".
- **Result:** FAIL. The family would never hear the good news.

### T16 "Keep me out of searches" is respected
- **Why:** Some people do not want to be found (fled an abusive partner, afraid of authorities).
- **Steps:** Register "Isolde Marchbanks", choose "Keep me out of searches". Search her exact name, age and a relationship.
- **Expected:** Not found at all.
- **Actual:** "No match yet. The search is saved: someone registered later can still be found." (the same page as for a name nobody registered).
- **Result:** PASS

### T17 Someone dangerous searching cannot tell hidden from not-here
- **Why:** An abuser may search for the person who fled. Any difference in the reply could reveal her.
- **Steps:** Make a search for the hidden person ("Ansel Marchbanks" looking for "Isolde Marchbanks") and a search for a name that does not exist. Print both slips. Check both slips at Westfield. Also compare with a search that was asked and not yet answered, and with "no" and "not now".
- **Expected:** Every one of those looks identical.
- **Actual:** Search pages were word for word the same. At the desk, hidden, not-here, still-waiting, "Say no" and "Not now" all gave exactly: "Your request is saved. If they choose to connect, we'll let you know here."
- **Result:** PASS (response timing was not tested).

### T18 Search results never show where a person is
- **Why:** Location is the dangerous piece of information.
- **Steps:** Run searches that match people at Eastside from Lincoln and Westfield. Read the whole screen and the data the page receives.
- **Expected:** No shelter, address or room.
- **Actual:** Cards show name, about age and reasons only. The data sent to the browser for a match has name, age, a "child" flag, score, reasons and whether a request was made. No place. A banner tells the volunteer: "Don't say whether anyone is here. Say: 'We'll ask, and let you know.'" Note: the cards do show the found person's name and age on the volunteer's screen, so a volunteer must keep that screen turned away from the searcher.
- **Result:** PASS

### T19 "No" and "Not now" look the same as waiting
- **Why:** The person has the right to refuse without the searcher knowing it was a refusal.
- **Steps:** Eastside volunteer chooses "Say no to this request" for one request and "Not now" for another. Check both searchers' slips before and after.
- **Expected:** No visible change for the searcher.
- **Actual:** Both before and after: "Your request is saved. If they choose to connect, we'll let you know here." (identical).
- **Result:** PASS

### T20 "Tell them I'm safe (not where I am)"
- **Why:** A middle option: reassure without revealing a location.
- **Steps:** Choose "Tell Hartley Hartwood I'm safe (not where I am)". Check the searcher's slip.
- **Expected:** A reassurance only.
- **Actual:** "They asked us to tell you they are safe." No place, no contact detail.
- **Result:** PASS

### T21 "Connect" shows exactly what will be shared
- **Why:** The found person must know what is going out, in her own words.
- **Steps:** Choose "Connect with Linnea Fennimore", type a meeting plan.
- **Expected:** A preview and a confirm button before anything is sent.
- **Actual:** The card showed "This will be shared with the person looking: 'Come to the Eastside desk at 3 p.m. and ask for Orla'" with "Yes, share this" and "Back". The searcher side later showed only the plan (see T06).
- **Result:** PASS

### T22 A found child is released only by a lead
- **Why:** Children must not be handed to a stranger because of a name match.
- **Steps:** At Lincoln, register "Nimbus Quarrington", 7, no adult with them. At Westfield, "Rosalind Quarrington" (aunt) searches and asks. At Lincoln, the volunteer opens "Answers waiting". Then Lead Jordan opens the lead page.
- **Expected:** The volunteer cannot release. The lead must check the guardian and leave a note.
- **Actual:** The card showed "a child: a lead decides before anything is shared". The volunteer's answer screen had no Connect buttons, only: "A child is involved, so a lead decides. Please ask a lead." On the lead page the "Check what will be shared" button was grey until the guardian box, a note and the meeting plan were filled in, **and** the box "The person here agreed, in person (if they are the adult being asked)" was ticked. Then a preview and "Yes, release: connect them". The aunt's slip then said: "They want to connect. Here's how: Auntie Ros comes to the Lincoln desk at 4 p.m."
- **Result:** PARTIAL. The flow is strong, but the lead is forced to tick "the person here agreed" even though the label says it is for adults and the person here is a child. It makes the lead sign something that does not apply.

### T23 Unknown age is treated as a child
- **Why:** A lost child may not know or say their age.
- **Steps:** Register "Fenwick Oddly" as "Not sure" about under 18, no age, no adult. Search for him.
- **Expected:** Treated with child protection.
- **Actual:** "a child: a lead decides before anything is shared"; the volunteer was told to ask a lead.
- **Result:** PASS (the side effect on adults is in T11)

### T24 A child who is searching
- **Why:** A child looking for a parent should not get information handed straight over either.
- **Steps:** Search as "Kit Tellerby", answering "No" to "Is the person looking 18 or over?", for "Maud Tellerby", an adult.
- **Expected:** A lead decides.
- **Actual:** The volunteer answer screen: "A child is involved, so a lead decides. Please ask a lead."
- **Result:** PASS

### T25 A volunteer cannot open the lead page
- **Why:** Staff powers should match staff roles.
- **Steps:** Logged in as Volunteer Alex, go straight to `/lead`.
- **Expected:** Refused or sent away.
- **Actual:** Sent back to the home screen (three buttons and "Answers waiting"). No lead button shown to volunteers.
- **Result:** PASS

### T26 A lead can protect someone completely
- **Why:** Staff will learn of danger the app cannot know.
- **Steps:** As Lead Jordan, open "Maud Tellerby" slip page, choose "Protect this person: never share anything (lead)", confirm. Search for her from Westfield.
- **Expected:** She disappears from all searches.
- **Actual:** Warning "Nothing about this person will be shared with anyone searching, until a lead lifts it. Protect them?" then "Protected. Nothing about them will be shared." The next search for her showed no card for her. The lead page lists active flags with a "Lift this flag" button.
- **Result:** PASS

### T27 Fix a typo; change of mind about being found
- **Why:** Paper and hurry make mistakes; people change their mind.
- **Steps:** Register "Jonquill Tipo". Use "Fix a mistake" to correct to "Jonquil Typo", search the corrected name. Then edit again to "Keep me out of searches" and search again.
- **Expected:** Both changes take effect straight away.
- **Actual:** Corrected name found. After switching to hidden: "No match yet. The search is saved: someone registered later can still be found."
- **Result:** PASS

### T28 Typing up a stack of paper forms
- **Why:** Some shelters keep paper records that must be copied in.
- **Steps:** Choose "Yes, I'm copying paper forms". Enter "Sheet 4, line 12" with date 2026-10-18, a name, save; then "Next person" for line 13 and 14.
- **Expected:** The source is kept; repeat typing is reduced.
- **Actual:** An extra first step: "Where is it on the paper? (e.g. Sheet 4, line 12)", "Date written on the paper", "Slip code, if a pre-printed slip was handed out". The date stayed filled in for the next form; the sheet/line field was cleared. The app also remembers paper mode: the next "Someone is here" goes straight to the paper form. Small wording issue: in paper mode the name question still says "What should we call you?" (talking to the person, not the volunteer).
- **Result:** PASS

### T29 Someone registered after a search was saved
- **Why:** Paper lists and late arrivals mean the person may be entered hours later.
- **Steps:** Search for a name nobody has (a search is saved and a slip printed). Then register that person from paper at another shelter. Check the searcher's slip, and look at the answers screen at the new person's shelter.
- **Expected:** The earlier searcher is told, or a request is raised, or there is an obvious "check again".
- **Actual:** The search page promised: "No match yet. The search is saved: someone registered later can still be found." After the person was registered, the slip still said "Your request is saved..." and no request appeared at the new person's shelter. The slip screen offers only "Turn the screen around" and "Finished with this person". A brand-new search did find the person.
- **Result:** PARTIAL. The promise only holds if a volunteer repeats the search by hand.

### T30 The same person entered twice; searchers appearing as matches
- **Why:** Many volunteers, many shifts, paper and live entry: duplicates are certain. Cards should not confuse.
- **Steps:** (a) Register the same name and age more than once, search for them. (b) After some searches, search for names of people who had only come to look.
- **Expected:** A warning or merge on duplicates. People who only came to search are not shown as shelter residents.
- **Actual:** (a) There was no warning when saving a repeat. Searching "Zorbal Xyzzyx" returned five identical cards (same name, age 30) because of my repeated runs; a volunteer cannot tell which is real. (b) People who only came to look for relatives (for example "Gilbert Pellworth", "Kit Tellerby") came up as possible matches in other people's searches and could be sent requests.
- **Result:** PARTIAL

### T31 Double-clicking Save
- **Why:** Nervous or tired users double-click; the same family must not be entered twice.
- **Steps:** Register "Dupin Clickwell" and double-click Save. Count cards in a search. Run twice.
- **Expected:** One record per attempt.
- **Actual:** After two test runs there were exactly two "Dupin Clickwell" cards (one per run).
- **Result:** PASS

### T32 Bad connection: internet drops, slow server
- **Why:** Shelter Wi-Fi is bad; free servers fall asleep.
- **Steps:** (a) On the last step, switch the browser offline, click Save, wait, switch back online. (b) Delay the save by 7 seconds and watch the screen.
- **Expected:** What was typed is kept, the user is told, nothing is saved twice.
- **Actual:** (a) Offline: "No internet right now. What you typed is kept on this device." with the button showing "Saving…". After the connection returned, the person was saved by itself ("Offley Netless is saved.") and a search showed one record per run. (b) Slow: "Connecting. Please wait, this can take up to a minute." with the Save button greyed ("Saving…"), then "Slowpoke Lagwell is saved."
- **Result:** PASS

### T33 Reloading the page in the middle of a form
- **Why:** A volunteer's tablet refreshes or someone closes the tab.
- **Steps:** Type a name, go to step 2, reload.
- **Expected:** Still logged in; typed information kept.
- **Actual:** Still logged in. Back on step 1 with "Reload Testwick" still in the name box. (The age typed on step 2 had not been moved on from, and was not kept.)
- **Result:** PASS

### T34 Shared computer: idle lock, log out, back button, direct links
- **Why:** A desk computer is shared; the next person in line must not see the last person's details.
- **Steps:** (a) Log in, start a form, then move the browser clock forward 9.5 and then 11 minutes. (b) Log out in the middle of a form, press Back, log in again, open the form. (c) In a clean window open `/answers`, `/lead`, a person page, `/code` and a nonsense address.
- **Expected:** Locked after a while; nothing left behind; private pages ask for a log in.
- **Actual:** (a) Still open at 9.5 minutes; at 11: "Screen locked | Enter your PIN to carry on | Unlock" and nothing else on screen. (b) After log out: log-in page. Back button: still the log-in page. The next login showed an empty form. (c) Every address, including the nonsense one, showed the log-in page. The full-screen "turned around" view also hides the Log out button until "Give the screen back to the volunteer" is pressed.
- **Result:** PASS

### T35 Tablet and phone screen sizes
- **Why:** Volunteers use tablets and phones.
- **Steps:** At 768x1024 and 375x667, open log in, home, register, check a code and answers. Measure sideways scrolling, small tap targets, text size.
- **Expected:** No sideways scrolling; big buttons; readable text.
- **Actual:** At both sizes: no sideways scroll on any of the five screens, no tap target under 40 px, smallest text 18 px.
- **Result:** PASS

---

## Findings

Ordered by how badly a real user is affected.

1. **FAIL T15: a good answer can stay hidden.** If one person searches for more than one relative before printing a slip (the normal case for a parent), the slip screen shows only the first search. Ilse's "They want to connect" for her brother never appeared; it said "Your request is saved" instead. A family that has been found never learns it. This is the one failure that breaks the main promise of the app.
2. **PARTIAL T11 / T23: adults with no known age are treated as children.** Anyone registered without an age (very common when there is no ID, for example "Mama Thessaly" or "Mouse") needs a lead to release them. At one busy desk this makes the lead a bottleneck, and the volunteer cannot help the family in the queue. It is safe for children but over-cautious for adults.
3. **PARTIAL T29: people registered later are not matched automatically.** The app says a saved search can still find "someone registered later", but nothing happened on the searcher's slip or at the new person's shelter, and there is no "check again" on the slip screen. With paper lists typed in hours later, families would keep hearing "saved" while their relative is already in the system. They are found only if someone runs a fresh search.
4. **PARTIAL T22: lead form asks for a tick that does not apply to a child.** The release button stays grey until "The person here agreed, in person (if they are the adult being asked)" is ticked, even when the person is a child. A careful lead will hesitate, and a hurried one will tick something untrue.
5. **PARTIAL T30: duplicates and "ghost" people.** There is no warning when the same person is entered twice, so volunteers see several identical cards. People who only came to look for someone also appear as matches for others' searches. Both cause confusion and wasted questions at the desk.

Smaller notes (still PASS):
- T02: 5 wrong PINs lock a volunteer's account for 15 minutes, so a stranger or a forgetful colleague can lock the volunteer out. The message points to a lead.
- T18: a match card shows the found person's name and age on the volunteer's screen. If the searcher can see that screen, they learn that a person with that name is registered.
- T28: paper mode asks "What should we call you?" to the volunteer, and the mode is remembered for the next person.

---

## Fixed after this run (release 1.0.3)

These findings were fixed in the app and checked again (locally against the live database, then on the live site after release):

| Finding | What changed | Checked |
|---|---|---|
| **T15 FAIL**: a person searching for several relatives got a new identity per search, so their slip only showed the first search | After the results, **Look for someone else too** starts a new search for the **same** person; one slip shows every search | Brother + sister searched by one person: the slip lists "2 searches"; the brother's "They want to connect…" and the sister's "Your request is saved…" both shown |
| **T29 PARTIAL**: a search saved before the person registered never found them; no "check again" in the app | **Check a code** lists the person's searches; **Check again for new matches** (at the shelter where the search was made) runs the search again | Sister registered after the search; **Check again for new matches** found her |
| **T22 PARTIAL**: the release box "The person here agreed… (if they are the adult being asked)" was confusing for a child | Reworded: "The person at this desk agreed in person (if the child is the one searching, the adult here said yes)" | Lead page test |
| Answers gave no feedback | "Answer saved." after an answer; "Not now" says the request is still waiting | Seen after Connect |

**Kept on purpose** (explained in the user guide and README): an adult with **no age** and **Not sure** is treated as a child (T11/T23). That is the safe side; choosing **18 or over** avoids it. Registering the same person twice gives no warning yet (T30); merging duplicates is listed as future work.

**Demo accounts:** the wrong-PIN tests locked Volunteer Riley for 15 minutes. That is the login protection working; the README and guide now say to use another demo login.
