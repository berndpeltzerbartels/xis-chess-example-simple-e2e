package one.xis.chess.example.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChessExampleE2ETest {
    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void startBrowser() {
        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "true"));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void openChessPage() {
        context = browser.newContext();
        page = context.newPage();
        page.navigate(baseUrl() + "/game.html");
        assertChessPageOpen();
        assertThat(square("e2")).hasAttribute("data-piece", "P");
    }

    @AfterEach
    void closePage() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void playsTwoMovesAndReceivesEngineAnswers() {
        setLevel("3");

        playMove("e2", "e4");
        waitForEngineMove(2);
        assertMoveRowsContainAtLeast(1);
        assertStoredGame("WHITE", 3, 2);
        assertBlackMove(storedMoves().get(1));

        playMove("g1", "f3");
        waitForEngineMove(4);
        assertMoveRowsContainAtLeast(2);
        assertStoredGame("WHITE", 3, 4);

        assertThat(page.locator(".status-card")).containsText(Pattern.compile("Engine moved|Your move|Check"));
    }

    @Test
    void changesLevelSwapsColorAndStartsNewGame() {
        setLevel("8");
        assertThat(page.locator("#levelSelect")).hasValue("8");
        assertThat(page.locator(".info-panel")).containsText("Level");

        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Swap color")).click();
        assertThat(page.locator(".info-panel")).containsText("You Play:");
        assertThat(page.locator(".info-panel")).containsText("Black");

        waitForEngineMove(1);
        assertMoveRowsContainAtLeast(1);
        assertStoredGame("BLACK", 8, 1);

        page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("New game")).click();
        assertChessPageOpen();
        assertThat(square("e2")).hasAttribute("data-piece", "P");
        assertThat(square("e4")).not().hasAttribute("data-piece", "P");
        assertThat(page.locator(".move-history")).hasCount(0);
        assertThat(page.locator(".player-color")).containsText("Black");
        assertStoredGame("BLACK", 8, 0);
    }

    @Test
    void receivesBlackEngineMoveAfterLeavingAndReturningToGamePage() {
        setLevel("1");

        playMove("e2", "e4");
        waitForStoredMoveCount(1);

        page.navigate(baseUrl() + "/setup.html");
        assertTrue(page.url().endsWith("/setup.html"), "Expected browser to leave the game page: " + page.url());

        page.navigate(baseUrl() + "/game.html");
        assertChessPageOpen();
        waitForEngineMove(2);

        List<String> moves = storedMoves();
        assertTrue(moves.size() >= 2, "Expected a player move and a black engine answer in localStorage: " + moves);
        assertTrue("e2e4".equals(moves.get(0)), "Expected first stored move to be e2e4: " + moves);
        assertBlackMove(moves.get(1));
        assertStoredGame("WHITE", 1, 2);
    }

    private void setLevel(String level) {
        page.locator("#levelSelect").selectOption(level);
        assertThat(page.locator("#levelSelect")).hasValue(level);
    }

    private void playMove(String from, String to) {
        assertThat(square(from)).not().hasAttribute("data-piece", "");
        square(from).click();
        square(to).click();
        assertThat(square(to)).not().hasAttribute("data-piece", "");
    }

    private void waitForEngineMove(int minimumHalfMoves) {
        page.waitForFunction(
                """
                        minimumHalfMoves => {
                          const rows = Array.from(document.querySelectorAll('.move-history li'));
                          const halfMoves = rows.flatMap(row => row.textContent
                            .trim()
                            .split(/\\s+/)
                            .filter(token => token.length > 0 && !/^\\d+\\.$/.test(token)));
                          return halfMoves.length >= minimumHalfMoves;
                        }
                        """,
                minimumHalfMoves,
                new Page.WaitForFunctionOptions().setTimeout(20_000)
        );
        assertThat(page.locator(".status-card")).not().containsText("Waiting",
                new LocatorAssertions.ContainsTextOptions().setTimeout(20_000));
    }

    private void assertMoveRowsContainAtLeast(int rows) {
        List<String> rowTexts = page.locator(".move-history li").allInnerTexts();
        assertTrue(rowTexts.size() >= rows, "Expected at least " + rows + " move rows, got " + rowTexts);
        assertTrue(rowTexts.stream().allMatch(text -> text.matches("\\d+\\.\\s+\\S+.*")),
                "Move rows should be numbered and contain moves: " + rowTexts);
    }

    private void assertChessPageOpen() {
        assertThat(page.getByRole(com.microsoft.playwright.options.AriaRole.HEADING,
                new Page.GetByRoleOptions().setName("Play against the engine"))).isVisible();
    }

    private void assertStoredGame(String playerColor, int level, int minimumHalfMoves) {
        waitForStoredMoveCount(minimumHalfMoves);
        assertTrue(playerColor.equals(storedString("playerColor")), "Expected stored playerColor " + playerColor);
        assertTrue(level == storedNumber("level"), "Expected stored level " + level);
        assertTrue(storedMoves().size() == visibleHalfMoveCount(),
                "Stored moves and visible move history should describe the same number of half moves");
    }

    private int visibleHalfMoveCount() {
        return ((Number) page.evaluate(
                """
                        () => Array.from(document.querySelectorAll('.move-history li'))
                          .flatMap(row => row.textContent
                            .trim()
                            .split(/\\s+/)
                            .filter(token => token.length > 0 && !/^\\d+\\.$/.test(token)))
                          .length
                        """
        )).intValue();
    }

    private void waitForStoredMoveCount(int minimumHalfMoves) {
        page.waitForFunction(
                """
                        minimumHalfMoves => {
                          const stored = JSON.parse(window.localStorage.getItem('chessGame') || '{}').value || {};
                          return Array.isArray(stored.moves) && stored.moves.length >= minimumHalfMoves;
                        }
                        """,
                minimumHalfMoves,
                new Page.WaitForFunctionOptions().setTimeout(20_000)
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> storedMoves() {
        return (List<String>) page.evaluate(
                "() => (JSON.parse(window.localStorage.getItem('chessGame') || '{}').value || {}).moves || []"
        );
    }

    private String storedString(String field) {
        return (String) page.evaluate(
                "field => (JSON.parse(window.localStorage.getItem('chessGame') || '{}').value || {})[field]",
                field
        );
    }

    private int storedNumber(String field) {
        return ((Number) page.evaluate(
                "field => (JSON.parse(window.localStorage.getItem('chessGame') || '{}').value || {})[field]",
                field
        )).intValue();
    }

    private void assertBlackMove(String move) {
        assertTrue(move.matches("[a-h][78][a-h][1-8].*"),
                "Expected a black move from rank 7 or 8, got: " + move);
    }

    private Locator square(String field) {
        return page.locator("[data-field=\"" + field + "\"]");
    }

    private static String baseUrl() {
        return System.getProperty("baseUrl", "http://localhost:8080").replaceAll("/+$", "");
    }
}
