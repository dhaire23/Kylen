# Big Grit Performance website — start here

Delone, this folder is the complete website. Everything in it is yours to edit.

## 1. Look at it on your computer

**Easiest:** put it online privately (no coding):
1. Go to **app.netlify.com/drop** and create a free account.
2. Drag this whole `big-grit` folder onto the page.
3. Netlify gives you a link (like `something.netlify.app`). Open it on your phone and computer.

Everything works there: the videos, the career reel, the playbook buttons and the application form. Applications show up in your Netlify account under **Forms**.

**On your computer only:** double-clicking `index.html` gives a rough look, but videos and the form need the Netlify link above.

## 2. What to edit, and where

| To change… | Open this file |
|---|---|
| Email, Instagram, booking link, pricing note, playbooks (titles, prices, Payhip links), testimonials, analytics | `config/site.config.js` |
| Any photo or video, the career reel clips and their start/end times | `config/media.config.js` (+ drop files into `media/`) |
| Any wording on the page: headlines, your story, coaching descriptions, FAQ | `index.html` (search for the sentence you want to change) |
| Privacy policy | `privacy.html` |

Both config files have plain-English instructions at the top. Only change text **between the quotes** and keep the commas.

## 3. Editing with Claude

Upload the **zipped folder** to Claude (claude.ai or Claude Code) and say what you want, for example:

> "This is my website. In Meet Delone, change chapter 02 to say … and swap the founder portrait for this photo."

Ask Claude to give you back the updated files (or a new zip), then drag the folder to Netlify again to update the site.

## 4. Before it goes live — please check

- [ ] Your story (Syracuse, Pinstripe Bowl MVP, Colts 2011, the hip replacement, IFBB Pro Men's Physique) is accurate
- [ ] Coaching descriptions, the $1,500–$5,000 investment line and the FAQ answers are right
- [ ] Each playbook's Payhip link works (do one test purchase)
- [ ] Every client in the before/after photos agreed to be shown
- [ ] `thecarter2287@gmail.com` is the email you want public
- [ ] The Tape: set the best seconds of each football clip (`start` / `end` in `config/media.config.js`)
- [ ] If you use **Trainerize**, add your sign-up link (or ask Claude to wire it to the online coaching buttons)

## 5. Going live

Full steps are in `docs/DEPLOY.md`: connect your domain in Netlify, turn on form notifications to your email, then run through `docs/LAUNCH-CHECKLIST.md`.

Other guides in `docs/`: `MEDIA-GUIDE.md` (photos and video), `CREATIVE-DIRECTION.md` (design and motion).
