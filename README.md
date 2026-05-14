# XIS Chess Example Simple E2E

JUnit/Playwright tests for the simple XIS chess example.

## Run

Keep this project next to `xis-chess-example-simple`:

```text
some-folder/
  xis-chess-example-simple/
  xis-chess-example-simple-e2e/
```

Then run:

```bash
gradle playwrightInstall
gradle test
```

By default, `gradle test` starts the sibling project with:

```bash
../xis-chess-example-simple/gradlew xisRun
```

Until this E2E project is moved next to the example, it falls back to:

```text
/Users/bernd/projects/xis-chess-example-simple
```

and tests:

```text
http://localhost:8080/game.html
```

Use another server with either a Gradle property or an environment variable:

```bash
gradle test -PbaseUrl=http://localhost
BASE_URL=http://192.168.2.44 gradle test
```

If the example checkout is somewhere else:

```bash
gradle test -PchessAppDir=/Users/bernd/projects/xis-chess-example-simple
```

Run visibly:

```bash
gradle test -Pheadless=false
```

## Covered Flow

- opens the chess page with empty browser storage
- changes the level
- plays `e2-e4`
- waits for an engine answer
- plays `Ng1-f3`
- checks move history
- checks the persisted `chessGame` localStorage state
- leaves the chess page and verifies that the black engine answer is still received after returning
- swaps color
- starts a new game with a single click

If the configured `BASE_URL` is already reachable, the test run uses the running app and does not start another process.
