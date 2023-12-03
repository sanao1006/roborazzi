package com.github.takahirom.roborazzi.sample

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [30],
  qualifiers = RobolectricDeviceQualifiers.NexusOne
)
class FilePathTest {
  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun relativePathShouldNotHaveDuplicatedPath() {
    boxedEnvironment {
      ROBORAZZI_DEBUG = true
      val expectedOutput =
        File("$DEFAULT_ROBORAZZI_OUTPUT_DIR_PATH/${this::class.qualifiedName}.relativePathShouldNotHaveDuplicatedPath.png")
      expectedOutput.delete()
      System.setProperty(
        "roborazzi.test.record",
        "true"
      )
      System.setProperty(
        "roborazzi.record.filePathStrategy",
        "relativePathFromRoborazziContextOutputDirectory"
      )
      provideRoborazziContext().setRuleOverrideOutputDirectory("build/outputs/roborazzi")

      onView(ViewMatchers.isRoot())
        .captureRoboImage()

      assert(
        expectedOutput
          .exists()
      ) {
        "File not found: ${expectedOutput.absolutePath} \n" +
          File("build/outputs/roborazzi").listFiles()?.joinToString("\n") { it.absolutePath }
      }

      provideRoborazziContext().clearRuleOverrideOutputDirectory()
    }
  }

  private fun boxedEnvironment(block: () -> Unit) {
    val originalRecord = System.getProperty("roborazzi.test.record")
    val originalFilePathStrategy = System.getProperty("roborazzi.record.filePathStrategy")
    block()
    originalFilePathStrategy?.let {
      System.setProperty("roborazzi.record.filePathStrategy", it)
    } ?: System.clearProperty("roborazzi.record.filePathStrategy")
    originalRecord?.let {
      System.setProperty("roborazzi.test.record", it)
    } ?: System.clearProperty("roborazzi.test.record")
  }
}