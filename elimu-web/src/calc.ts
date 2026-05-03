// Tiny arithmetic + fraction evaluator. Faithful TypeScript port of
// ElimuSMSMidlet.evaluateMathExpression / parseFrac / fmtFrac / gcd.
//
// Supports: +, -, *, /, on integers and fractions (e.g. "3/4 + 1/2").
// Operator precedence is left-to-right at depth 1 — same as the J2ME
// implementation. Division of "digit/digit" with no surrounding context
// is treated as a fraction, not a division operator (so "5/2" is read as
// the fraction 5/2, while "5/2-1" detects the '-' as subtraction).

export interface CalcResult {
  /** Pretty-printed result. */
  text: string;
  /** True iff `expr` actually parsed as an arithmetic expression. */
  evaluated: boolean;
}

export function evaluateMathExpression(input: string): CalcResult {
  let expr = removeSpaces(input);

  // Strip leading non-numeric prefix (e.g. "math", "science")
  let start = 0;
  while (start < expr.length
         && !isDigit(expr[start]!)
         && expr[start] !== "-") start++;
  if (start > 0) expr = expr.substring(start);

  if (expr.length === 0) {
    return { text: "Type an expression like 2+3, 5*4, or 3/4+1/2", evaluated: false };
  }

  // First scan: find the first +, -, *, or / treating digit/digit as a
  // fraction slash (skip it). If no operator found, second scan accepts
  // any +, -, * after the (possibly-fractional) first operand.
  let plus = -1, minus = -1, mult = -1, div = -1;
  for (let i = 0; i < expr.length; i++) {
    const c = expr[i];
    if (c === "+") { plus = i; break; }
    if (c === "*") { mult = i; break; }
    if (c === "-" && i > 0) { minus = i; break; }
    if (c === "/" && i > 0) {
      const prevDigit = isDigit(expr[i - 1]!);
      const nextDigit = i + 1 < expr.length && isDigit(expr[i + 1]!);
      if (!(prevDigit && nextDigit)) { div = i; break; }
    }
  }
  if (plus < 0 && minus < 0 && mult < 0 && div < 0) {
    for (let i = 0; i < expr.length; i++) {
      const c = expr[i];
      if (c === "+") { plus = i; break; }
      if (c === "*") { mult = i; break; }
      if (c === "-" && i > 0) { minus = i; break; }
    }
  }

  try {
    if (plus > 0) {
      const L = parseFrac(expr.substring(0, plus));
      const R = parseFrac(expr.substring(plus + 1));
      return ok(fmtFrac(L[0] * R[1] + R[0] * L[1], L[1] * R[1]));
    }
    if (mult > 0) {
      const L = parseFrac(expr.substring(0, mult));
      const R = parseFrac(expr.substring(mult + 1));
      return ok(fmtFrac(L[0] * R[0], L[1] * R[1]));
    }
    if (minus > 0) {
      const L = parseFrac(expr.substring(0, minus));
      const R = parseFrac(expr.substring(minus + 1));
      return ok(fmtFrac(L[0] * R[1] - R[0] * L[1], L[1] * R[1]));
    }
    if (div > 0) {
      const L = parseFrac(expr.substring(0, div));
      const R = parseFrac(expr.substring(div + 1));
      if (R[0] === 0) return ok("Cannot divide by zero");
      return ok(fmtFrac(L[0] * R[1], L[1] * R[0]));
    }
    return { text: "Type an expression like 2+3, 5*4, or 3/4+1/2", evaluated: false };
  } catch {
    return { text: "Error parsing numbers", evaluated: false };
  }
}

/** Cheap "does this look like arithmetic?" check used by the routing layer. */
export function looksLikeArithmetic(question: string): boolean {
  if (question.indexOf("+") >= 0) return true;
  if (question.indexOf("*") >= 0) return true;
  // '-' counts only between digits or after a space (not as a leading minus on prose).
  for (let i = 1; i < question.length; i++) {
    if (question[i] === "-"
        && (isDigit(question[i - 1]!) || question[i - 1] === " ")) return true;
  }
  // 'a/b' between digits = fraction (still arithmetic-shaped).
  const noSp = removeSpaces(question);
  for (let i = 1; i < noSp.length - 1; i++) {
    if (noSp[i] === "/" && isDigit(noSp[i - 1]!) && isDigit(noSp[i + 1]!)) return true;
  }
  return false;
}

// ── Helpers ─────────────────────────────────────────────────────────────────

function removeSpaces(s: string): string {
  return s.replace(/\s+/g, "");
}

function isDigit(ch: string): boolean {
  return ch >= "0" && ch <= "9";
}

function parseFrac(s: string): [number, number] {
  const slash = s.indexOf("/");
  if (slash > 0) {
    const n = parseInt(s.substring(0, slash), 10);
    const d = parseInt(s.substring(slash + 1), 10);
    if (!Number.isFinite(n) || !Number.isFinite(d)) {
      throw new Error("bad fraction");
    }
    return [n, d];
  }
  const n = parseInt(s, 10);
  if (!Number.isFinite(n)) throw new Error("bad number");
  return [n, 1];
}

function fmtFrac(numerator: number, denominator: number): string {
  let num = numerator;
  let den = denominator;
  if (den < 0) { num = -num; den = -den; }
  const g = gcd(Math.abs(num), Math.abs(den));
  if (g > 0) { num = (num / g) | 0; den = (den / g) | 0; }
  if (den === 1) return String(num);
  if (Math.abs(num) > den && den > 1) {
    const whole = (num / den) | 0;
    const rem   = num - whole * den;
    if (rem === 0) return String(whole);
    return `${whole} ${Math.abs(rem)}/${den}`;
  }
  return `${num}/${den}`;
}

function gcd(a: number, b: number): number {
  while (b !== 0) { [a, b] = [b, a % b]; }
  return a;
}

function ok(text: string): CalcResult {
  return { text, evaluated: true };
}
