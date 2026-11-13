// Regenerates Builtin.kt from src/authoring/builtin.ts so the web prototype and the APK ship identical themes.
// Usage: node android/scripts/gen-builtins.mjs
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const ts = fs.readFileSync(path.join(root, "src/authoring/builtin.ts"), "utf8");
const literal = ts.slice(ts.indexOf("{", ts.indexOf("const drafts")), ts.indexOf("export const BUILTIN"));
const drafts = Function(`"use strict"; return (${literal.trim().replace(/;$/, "")});`)();

const q = (s) => JSON.stringify(s).replace(/\$/g, "\\$");
const lines = [
  "package com.drama.app.authoring",
  "",
  "import com.drama.app.core.Scenario",
  "import com.drama.app.core.ScenarioSource",
  "",
  "private val drafts: Map<String, ScenarioDraft> = linkedMapOf(",
];
for (const [id, d] of Object.entries(drafts)) {
  lines.push(`    ${q(id)} to ScenarioDraft(`);
  lines.push(`        title = ${q(d.title)},`, `        premise = ${q(d.premise)},`);
  lines.push(`        personaName = ${q(d.persona.name)},`, `        personaTone = ${q(d.persona.tone)},`);
  const r = d.persona.reactions ?? {};
  if (Object.keys(r).length) lines.push(`        reactions = ReactionsDraft(${Object.entries(r).map(([k, v]) => `${k} = ${q(v)}`).join(", ")}),`);
  lines.push("        chapters = listOf(");
  for (const c of d.chapters) {
    lines.push("            ChapterDraft(", `                title = ${q(c.title)},`, `                opening = ${q(c.opening)},`, "                beats = listOf(");
    for (const b of c.beats) {
      if (!b.choices) {
        lines.push(`                    BeatDraft(${q(b.line)}),`);
        continue;
      }
      lines.push("                    BeatDraft(", `                        ${q(b.line)},`, "                        listOf(");
      for (const ch of b.choices) lines.push(`                            ChoiceDraft(${q(ch.label)}, ${q(ch.reply)}${ch.detour ? ", detour = true" : ""}),`);
      lines.push("                        ),", "                    ),");
    }
    lines.push("                ),");
    if (c.cliffhanger) lines.push(`                cliffhanger = ${q(c.cliffhanger)},`);
    if (c.finale) lines.push(`                finale = ${q(c.finale)},`);
    lines.push("            ),");
  }
  lines.push("        ),", "    ),");
}
lines.push(
  ")",
  "",
  "val BUILTIN_SCENARIOS: List<Scenario> = drafts.map { (id, draft) ->",
  "    when (val result = parseScenarioDraft(draft, id, ScenarioSource.BUILTIN)) {",
  "        is DraftResult.Ok -> result.scenario",
  '        is DraftResult.Invalid -> error("invalid builtin $id: ${result.issues}")',
  "    }",
  "}",
  "",
);
fs.writeFileSync(path.join(root, "android/app/src/main/java/com/drama/app/authoring/Builtin.kt"), lines.join("\n"));
console.log(`wrote ${Object.keys(drafts).length} builtin scenarios`);
