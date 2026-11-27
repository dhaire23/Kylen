# Client case studies — how to fill them in

The Results section presents each before/after as an editorial case study. It only shows details that are **verified with the client**. Empty fields stay hidden, so a case can launch with just the photos and grow later.

## 1. Ask each client (copy/paste)

> Hey [name], your progress photos are going on the new Big Grit website. Before they do, can you confirm a few things?
> 1. Are you OK with your before/after photos being shown publicly? (yes / no)
> 2. How should we name you: first name + last initial, initials only, or anonymous?
> 3. Which coaching did you do: online, or private training in DFW?
> 4. Roughly how long between the two photos?
> 5. What was your main goal (fat loss, muscle, recomposition, something else)?
> 6. In one or two sentences, what changed for you? (I'll quote you word for word.)
> Reply "yes" to #1 in writing so we have your OK on record.

Keep the written "yes" (text or email) on file.

## 2. Enter it in `config/media.config.js` → `transformations`

```js
{
  image: "media/transformations/client-01.webp",
  sideBySide: true,
  alt: "Client before and after, side by side",
  client: "Marcus T.",                    // as they asked to be named
  headline: "Down two belt sizes in 12 weeks",   // only if they confirmed it
  program: "Online coaching",
  timeframe: "12 weeks",
  focus: "Fat loss",
  quote: "Exact words they sent you.",
},
```

Rules:
- **Never** round up results, guess timeframes, or edit their words.
- A headline must be a fact the client confirmed. When in doubt, leave it empty.
- Remove a case completely if a client says no or later asks to be taken down.
