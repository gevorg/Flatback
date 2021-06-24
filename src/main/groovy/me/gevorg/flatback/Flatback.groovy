package me.gevorg.flatback

import geb.Browser
import groovy.util.logging.Slf4j
import io.github.bonigarcia.wdm.WebDriverManager
import me.gevorg.flatback.model.Question
import me.gevorg.flatback.page.Loopy
import me.gevorg.flatback.page.ScanwordContest
import me.gevorg.flatback.page.ScanwordHome
import org.openqa.selenium.chrome.ChromeDriver;


/**
 * App class.
 *
 * @author Gevorg Harutyunyan
 */
@Slf4j
class Flatback {
    String user
    String pass

    Browser scanword
    Browser loopy

    Flatback() {
        // Download drivers.
        WebDriverManager.chromedriver().setup()

        // Init browsers.
        scanword = new Browser()
        loopy = new Browser(driver: new ChromeDriver())
    }

    void quit() {
        scanword.quit()
        loopy.quit()
    }

    /**
     * Solving puzzle.
     */
    void tryToSolve() {
        login(user, pass)

        Stack<Question> questions = extractQuestions().findAll { !it.done }
        Stack<Question> questionsWithoutHints = []

        while(!questions.isEmpty() || !questionsWithoutHints.isEmpty()) {
            if (questions.isEmpty()) {
                questions = questionsWithoutHints
                questionsWithoutHints = []
            }

            Question question = questions.pop()
            if (question.isDone()) continue
            log.info("question: ${question.title}, pattern: ${question.pattern}")

            List<String> hints = extractHints(question)
            if (hints) {
                log.info("hints: ${hints}")

                if (applyHints(hints, question)) {
                    //noinspection GroovyAssignabilityCheck
                    questions.addAll(question.answers.questions.flatten().findAll { Question q -> !q.done })
                }
            } else {
                int lastSpace = question.title.lastIndexOf(" ") + 1

                question.title = question.title.substring(0, lastSpace).trim()
                questionsWithoutHints << question
            }
        }
    }

    /**
     * Applies hints.
     *
     * @param hints hints to apply.
     * @param question question.
     * @return true if hints worked out.
     */
    boolean applyHints(List<String> hints, Question question) {
        boolean isDone = false

        Browser.drive scanword,  {
            ScanwordContest contest = at ScanwordContest
            isDone = contest.tryAnswer(hints, question)
        }

        return isDone
    }

    /**
     * Extracts hints from loopy.
     *
     * @param question question.
     * @return list of hints.
     */
    List<String> extractHints(Question question) {
        List<String> result = []

        Browser.drive loopy, {
            Loopy loopyPage = to new Loopy(pattern: question.pattern, definition: question.title)
            result = loopyPage.getHints()
        }

        return result
    }


    /**
     * Login to scanword.ru
     *
     * @param user username.
     * @param pass password.
     */
    void login(String user, String pass) {
        Browser.drive scanword, {
            ScanwordHome home = to ScanwordHome

            home.username = user
            home.password = pass

            home.loginBtn.click()
        }
    }

    /**
     * Extracts questions from contest.
     *
     * @return questions.
     */
    Stack<Question> extractQuestions() {
        Stack<Question> questions = []
        Browser.drive scanword, {
            ScanwordContest contest = to ScanwordContest
            questions = contest.loadQuestionsAndAnswers()
        }

        return questions
    }

    /**
     * Starts app.
     *
     * @param args program arguments.
     */
    static void main(String[] args) {
        String user = System.getProperty('username')
        String pass = System.getProperty('password')

        Flatback app = new Flatback(user: user, pass:pass)

        app.tryToSolve()
        app.quit()
    }
}
