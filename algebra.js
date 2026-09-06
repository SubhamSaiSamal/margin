// Linear equation equivalence, computed rather than guessed.
//
// Every expression in scope reduces to a*x + b, so a whole equation reduces to
// a single (A, B) pair meaning A*x + B = 0. Two lines of working are equivalent
// when they describe the same solution set. Nothing here consults a model.

const MINUS = /[−–—]/g; // unicode minus and dashes students actually type
const TIMES = /[×⋅]/g;

function tokenize(src) {
  const clean = src.replace(MINUS, "-").replace(TIMES, "*").replace(/\s+/g, "");
  const tokens = [];
  let i = 0;

  while (i < clean.length) {
    const ch = clean[i];

    if (/[0-9.]/.test(ch)) {
      let num = "";
      while (i < clean.length && /[0-9.]/.test(clean[i])) num += clean[i++];
      tokens.push({ type: "num", value: parseFloat(num) });
      continue;
    }

    if (/[a-zA-Z]/.test(ch)) {
      tokens.push({ type: "var", value: ch.toLowerCase() });
      i++;
      continue;
    }

    if ("+-*/()".includes(ch)) {
      tokens.push({ type: ch });
      i++;
      continue;
    }

    throw new Error(`can't read "${ch}"`);
  }

  return tokens;
}

// A parsed value is always {a, b} standing for a*x + b.
const add = (p, q) => ({ a: p.a + q.a, b: p.b + q.b });
const sub = (p, q) => ({ a: p.a - q.a, b: p.b - q.b });

function mul(p, q) {
  if (p.a !== 0 && q.a !== 0) throw new Error("not linear");
  return p.a === 0
    ? { a: p.b * q.a, b: p.b * q.b }
    : { a: p.a * q.b, b: p.b * q.b };
}

function div(p, q) {
  if (q.a !== 0) throw new Error("can't divide by a term in x");
  if (q.b === 0) throw new Error("divide by zero");
  return { a: p.a / q.b, b: p.b / q.b };
}

function parseExpression(tokens, pos) {
  let [value, next] = parseTerm(tokens, pos);

  while (next < tokens.length && (tokens[next].type === "+" || tokens[next].type === "-")) {
    const op = tokens[next].type;
    const [rhs, after] = parseTerm(tokens, next + 1);
    value = op === "+" ? add(value, rhs) : sub(value, rhs);
    next = after;
  }

  return [value, next];
}

function parseTerm(tokens, pos) {
  let [value, next] = parseFactor(tokens, pos);

  while (next < tokens.length) {
    const t = tokens[next];

    if (t.type === "*" || t.type === "/") {
      const [rhs, after] = parseFactor(tokens, next + 1);
      value = t.type === "*" ? mul(value, rhs) : div(value, rhs);
      next = after;
      continue;
    }

    // implicit multiplication: 3x, 2(x+1), (x+1)(2)
    if (t.type === "num" || t.type === "var" || t.type === "(") {
      const [rhs, after] = parseFactor(tokens, next);
      value = mul(value, rhs);
      next = after;
      continue;
    }

    break;
  }

  return [value, next];
}

function parseFactor(tokens, pos) {
  const t = tokens[pos];
  if (!t) throw new Error("line ends early");

  if (t.type === "-") {
    const [value, next] = parseFactor(tokens, pos + 1);
    return [{ a: -value.a, b: -value.b }, next];
  }

  if (t.type === "+") return parseFactor(tokens, pos + 1);
  if (t.type === "num") return [{ a: 0, b: t.value }, pos + 1];
  if (t.type === "var") return [{ a: 1, b: 0 }, pos + 1];

  if (t.type === "(") {
    const [value, next] = parseExpression(tokens, pos + 1);
    if (!tokens[next] || tokens[next].type !== ")") throw new Error("missing )");
    return [value, next + 1];
  }

  throw new Error("unexpected symbol");
}

function parseSide(src) {
  const tokens = tokenize(src);
  const [value, next] = parseExpression(tokens, 0);
  if (next !== tokens.length) throw new Error("trailing symbols");
  return value;
}

// "2x + 5 = 3x - 4"  ->  {A, B} meaning A*x + B = 0
export function parseEquation(src) {
  const halves = src.split("=");
  if (halves.length !== 2) throw new Error("needs exactly one =");

  const left = parseSide(halves[0]);
  const right = parseSide(halves[1]);
  return { A: left.a - right.a, B: left.b - right.b };
}

const NEAR = 1e-9;

// Do these two equations have the same solution set?
export function equivalent(prev, next) {
  const p = parseEquation(prev);
  const q = parseEquation(next);

  const pHasX = Math.abs(p.A) > NEAR;
  const qHasX = Math.abs(q.A) > NEAR;

  // Both still contain x: same root means same equation.
  if (pHasX && qHasX) return Math.abs(-p.B / p.A - -q.B / q.A) < NEAR;

  // Neither contains x: both are identities, or both are contradictions.
  if (!pHasX && !qHasX) return (Math.abs(p.B) < NEAR) === (Math.abs(q.B) < NEAR);

  // One lost its x. The step dropped a term.
  return false;
}

// The root, for lines that still have one. Used for explaining, never for answering.
export function root(src) {
  const { A, B } = parseEquation(src);
  return Math.abs(A) > NEAR ? -B / A : null;
}

const numbersIn = (src) => (src.match(/\d+(?:\.\d+)?/g) || []).map(Number);

// Why the step broke — reported from what actually changed between the two
// lines, never inferred. Only ever called once a step is already known wrong.
export function diagnose(previous, current) {
  const before = parseEquation(previous);
  const after = parseEquation(current);

  const lostX = Math.abs(before.A) > NEAR && Math.abs(after.A) < NEAR;
  if (lostX) return { kind: "lost-x" };

  const sameX = Math.abs(before.A - after.A) < NEAR;
  const sameConstant = Math.abs(before.B - after.B) < NEAR;

  // A term that flipped sign moves the constant by exactly twice its value.
  if (sameX && !sameConstant) {
    const shift = Math.abs(after.B - before.B) / 2;
    const written = new Set([...numbersIn(previous), ...numbersIn(current)]);

    for (const value of written) {
      if (Math.abs(value - shift) < NEAR) return { kind: "sign", value };
    }
    return { kind: "constant" };
  }

  if (!sameX && sameConstant) return { kind: "x-term" };
  return { kind: "unclear" };
}
