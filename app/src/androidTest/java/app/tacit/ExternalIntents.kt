package app.tacit

import android.content.Intent
import androidx.test.espresso.intent.matcher.ComponentNameMatchers.hasClassName
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.isInternal
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not

object ExternalIntents {

    fun matcher(): Matcher<Intent> =
        allOf(not(isInternal()), not(hasComponent(hasClassName(containsString("androidx.test")))))
}
