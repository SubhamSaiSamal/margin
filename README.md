# margin

A maths tutor that checks your working and refuses to give you the answer.

You write on the page. When you rest the pen, margin reads the line you just
wrote, checks whether it actually follows from the line above it, and marks it
in the margin beside your own handwriting — a tick, or a cross and one spoken
question. It never solves the problem for you.

Everything runs on the phone. No network, at any point.

---

## Why

Every tool in this space answers the question for you.

| Approach | Who does it | What it costs you |
| --- | --- | --- |
| Photograph a problem | Photomath, Gauth, Doubtnut | Hands over the answer |
| Read handwriting, then solve it | OneNote Ink Math, MyScript | Recognition is excellent; it still solves it for you |
| Live step-by-step tutoring | AmIWrite (CHI 2026), KedMathic | Needs a tablet and a stylus |
| Marking-scheme evaluation | OzymorLab, ExamPredict | Post-mortem, after the exam |

The recognition problem is solved. The pedagogy problem is not: a tool that
finishes your working trains you to reach for it again tomorrow.

## How it decides

The verdict is computed, not guessed. An equation reduces to

```
c₁·v₁ + c₂·v₂ + … + k = 0
```

and two lines of working are equivalent when one is a non-zero multiple of the
other. So `2x + 6 = 0` and `x + 3 = 0` agree, while `3x + y = 0` and `3x = y`
do not. No model is asked anything at this stage — an on-device model is used
only to phrase the hint.

When a step breaks, `diagnose()` names the term rather than describing the
problem. A term that flips sign moves the constant by exactly twice its value,
so the number is solved for and checked against what was actually written:

```
2x − 3x = −4 − 5
−x = −4 + 5        ✗  "check the sign on the 5"
```

That sentence is derived, not scripted.

## Building

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Requires the Android SDK. Create `local.properties` with your SDK path:

```properties
sdk.dir=/path/to/Android/Sdk
```

The build pins JDK 21 in `gradle.properties`, because the Android Gradle Plugin
does not yet support JDK 25.

On first launch the app downloads the ML Kit handwriting model once. After
that it runs with the radio off.

## Layout

| File | What it is |
| --- | --- |
| `Algebra.kt` | Parser and equivalence engine. No Android dependencies. |
| `AlgebraTest.kt` | 23 tests, including every case that has broken on a real phone |
| `Notebook.kt` | The page: stroke capture, ruled lines, the margin |
| `InkRecognizer.kt` | On-device handwriting, candidate selection |
| `InkBridge.java` | Every ML Kit static factory call (see below) |
| `algebra.js` | Reference implementation the Kotlin tests mirror case for case |

## Two things worth knowing

**ML Kit interop lives in Java.** Kotlin will not resolve some of this
library's static factories — `Ink.builder()` and friends are present in the jar,
verified by decompiling it, but the Kotlin frontend refuses them. Calling them
from `InkBridge.java` sidesteps the problem rather than working around it one
method at a time.

**`RecognitionContext` requires `preContext`.** The parameter is nullable, but
the builder throws `Missing required properties: preContext` if it is never
set. Passing `null` silently killed every recognition attempt in the app until
it was found.

## Scope

Linear equations, one or more unknowns, written inline.

Stacked fractions are out of scope and not a tuning problem: ML Kit Digital Ink
is a *text* recogniser that reads left to right and has no concept of vertical
structure. Inline division (`x/2`) reads fine.

## Prior work

The idea of finding the first line where an invariant breaks comes from
[subgrad](https://subgrad.vercel.app), whose shape checker walks a model
definition and locates the exact layer where tensor dimensions stop matching.
margin is that idea pointed at algebra instead of tensors.
