// Chat-style UI rendering. Pure DOM, no framework.

export type Tier =
  | "device"   // answered locally by the on-device classifier
  | "cloud"    // answered by the cloud LLM
  | "fallback" // offline-tier walk (broader CBC frame)
  | "system";  // info / errors

export interface BotMessageOpts {
  tier: Tier;
  intentName?: string;
  confidence?: number;
  onMore?: () => void;
}

const chat = (): HTMLElement => document.getElementById("chat")!;

export function appendUser(text: string): void {
  const el = document.createElement("div");
  el.className = "bubble user";
  el.textContent = text;
  chat().appendChild(el);
  el.scrollIntoView({ behavior: "smooth", block: "end" });
}

export function appendBot(text: string, opts: BotMessageOpts): HTMLElement {
  const el = document.createElement("div");
  el.className = "bubble bot";
  el.textContent = text;

  const meta = document.createElement("span");
  meta.className = "meta";
  const bits: string[] = [opts.tier];
  if (opts.intentName) bits.push(opts.intentName);
  if (typeof opts.confidence === "number") {
    bits.push(`${(opts.confidence * 100).toFixed(0)}%`);
  }
  meta.textContent = bits.join(" · ");
  el.appendChild(meta);

  if (opts.onMore) {
    const actions = document.createElement("div");
    actions.className = "actions";
    const more = document.createElement("button");
    more.textContent = "More";
    more.addEventListener("click", () => opts.onMore!());
    actions.appendChild(more);
    el.appendChild(actions);
  }

  chat().appendChild(el);
  el.scrollIntoView({ behavior: "smooth", block: "end" });
  return el;
}

export function appendSystem(text: string): void {
  appendBot(text, { tier: "system" });
}

export function setStatus(text: string, online: boolean): void {
  const el = document.getElementById("status")!;
  el.textContent = text;
  el.classList.toggle("online", online);
}

export function setFooter(text: string): void {
  const el = document.getElementById("footer");
  if (el) el.textContent = text;
}
