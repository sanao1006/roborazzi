package com.github.takahirom.roborazzi

import com.dropbox.differ.ImageComparator
import com.dropbox.differ.SimpleImageComparator
import com.github.takahirom.roborazzi.CaptureResults.Companion.json
import kotlinx.serialization.json.encodeToJsonElement
import java.io.File

@ExperimentalRoborazziApi
sealed interface RoborazziRecordFilePathStrategy {
  val propertyValue: String

  object RelativePathFromCurrentDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromCurrentDirectory"
  }

  object RelativePathFromRoborazziContextOutputDirectory : RoborazziRecordFilePathStrategy {
    override val propertyValue: String
      get() = "relativePathFromRoborazziContextOutputDirectory"
  }
}


/**
 * Specify the naming strategy for the recorded image.
 * Default: roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If set to testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If set to escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If set to testClassAndMethod, the file name will be MyTest.testMethod.png
 */
@ExperimentalRoborazziApi
fun roborazziRecordFilePathStrategy(): RoborazziRecordFilePathStrategy {
  return when (
    getSystemProperty(
      "roborazzi.record.filePathStrategy",
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue
    )
  ) {
    RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory

    RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory.propertyValue ->
      RoborazziRecordFilePathStrategy.RelativePathFromRoborazziContextOutputDirectory

    else -> RoborazziRecordFilePathStrategy.RelativePathFromCurrentDirectory
  }
}

/**
 * You can specify the naming strategy of the image to be recorded.
 * The default is roborazzi.record.namingStrategy=testPackageAndClassAndMethod
 * If you specify testPackageAndClassAndMethod, the file name will be com.example.MyTest.testMethod.png
 * If you specify escapedTestPackageAndClassAndMethod, the file name will be com_example_MyTest.testMethod.png
 * If you specify testClassAndMethod, the file name will be MyTest.testMethod.png
 */
fun roborazziDefaultNamingStrategy(): DefaultFileNameGenerator.DefaultNamingStrategy {
  return DefaultFileNameGenerator.DefaultNamingStrategy
    .fromOptionName(
      optionName = checkNotNull(
        getSystemProperty(
          "roborazzi.record.namingStrategy",
          DefaultFileNameGenerator.DefaultNamingStrategy.TestPackageAndClassAndMethod.optionName
        )
      )
    )
}

data class RoborazziOptions(
  /**
   * This option, taskType, is experimental. So the API may change.
   * Please tell me your opinion about this option
   * https://github.com/takahirom/roborazzi/issues/215
   */
  val taskType: RoborazziTaskType = roborazziSystemPropertyTaskType(),
  val contextData: Map<String, Any> = emptyMap(),
  val captureType: CaptureType = if (canScreenshot()) CaptureType.Screenshot() else defaultCaptureType(),
  val reportOptions: ReportOptions = ReportOptions(),
  val compareOptions: CompareOptions = CompareOptions(),
  val recordOptions: RecordOptions = RecordOptions(),
) {
  interface CaptureType {
    class Screenshot : CaptureType {
      override fun shouldTakeScreenshot(): Boolean {
        return true
      }
    }

    fun shouldTakeScreenshot(): Boolean

    companion object
  }

  @ExperimentalRoborazziApi
  data class ReportOptions(
    val captureResultReporter: CaptureResultReporter = CaptureResultReporter(),
  )

  data class CompareOptions(
    val outputDirectoryPath: String = roborazziSystemPropertyOutputDirectory(),
    val imageComparator: ImageComparator = DefaultImageComparator,
    val comparisonStyle: ComparisonStyle = ComparisonStyle.Grid(),
    val aiAssertionOptions: AiAssertionOptions? = null,
    val resultValidator: (result: ImageComparator.ComparisonResult) -> Boolean = DefaultResultValidator,
  ) {

    @ExperimentalRoborazziApi
    sealed interface ComparisonStyle {
      @ExperimentalRoborazziApi
      data class Grid(
        val bigLineSpaceDp: Int? = 16,
        val smallLineSpaceDp: Int? = 4,
        val hasLabel: Boolean = true
      ) : ComparisonStyle

      object Simple : ComparisonStyle
    }

    constructor(
      outputDirectoryPath: String = roborazziSystemPropertyOutputDirectory(),
      /**
       * This value determines the threshold of pixel change at which the diff image is output or not.
       * The value should be between 0 and 1
       */
      changeThreshold: Float,
      imageComparator: ImageComparator = DefaultImageComparator,
    ) : this(
      outputDirectoryPath = outputDirectoryPath,
      resultValidator = ThresholdValidator(changeThreshold),
      imageComparator = imageComparator,
    )

    companion object {
      val DefaultImageComparator = SimpleImageComparator(maxDistance = 0.007F)
      val DefaultResultValidator = ThresholdValidator(0F)
    }
  }

  @ExperimentalRoborazziApi
  interface CaptureResultReporter {
    fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType)

    companion object {
      operator fun invoke(): CaptureResultReporter {
        return DefaultCaptureResultReporter()
      }
    }

    class DefaultCaptureResultReporter : CaptureResultReporter {
      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        if (roborazziTaskType.isVerifying()) {
          VerifyCaptureResultReporter().report(captureResult, roborazziTaskType)
        } else {
          JsonOutputCaptureResultReporter().report(captureResult, roborazziTaskType)
        }
        AiCaptureResultReporter().report(captureResult, roborazziTaskType)
      }
    }

    class AiCaptureResultReporter : CaptureResultReporter {
      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        val aiResult = when (captureResult) {
          is CaptureResult.Changed -> {
            captureResult.aiAssertionResults
          }

          is CaptureResult.Added -> {
            captureResult.aiAssertionResults
          }

          else -> {
            null
          }
        }
        aiResult?.aiAssertionResults
          ?.filter { conditionResult -> conditionResult.requiredFulfillmentPercent != null && conditionResult.failIfNotFulfilled }
          ?.forEach { conditionResult ->
          if (conditionResult.fulfillmentPercent < conditionResult.requiredFulfillmentPercent!!) {
            throw AssertionError(
              "The generated image did not meet the required prompt fulfillment percentage.\n" +
                "prompt:${conditionResult.assertPrompt}\n" +
                "aiAssertion.fulfillmentPercent:${conditionResult.fulfillmentPercent}\n" +
                "requiredFulfillmentPercent:${conditionResult.requiredFulfillmentPercent}\n" +
                "explanation:${conditionResult.explanation}"
            )
          }
        }
      }
    }

    class JsonOutputCaptureResultReporter : CaptureResultReporter {

      init {
        File(roborazziSystemPropertyResultDirectory()).mkdirs()
      }

      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        val absolutePath = File(roborazziSystemPropertyResultDirectory()).absolutePath
        val nameWithoutExtension = when (captureResult) {
          is CaptureResult.Added -> captureResult.compareFile
          is CaptureResult.Changed -> captureResult.goldenFile
          is CaptureResult.Unchanged -> captureResult.goldenFile
          is CaptureResult.Recorded -> captureResult.goldenFile
        }.nameWithoutExtension
        val reportFileName =
          getReportFileName(absolutePath, captureResult.timestampNs, nameWithoutExtension)

        val jsonResult = json.encodeToJsonElement(captureResult)
        KotlinxIo.writeText(reportFileName, jsonResult.toString())
        debugLog { "JsonResult file($reportFileName) has been written" }
      }

    }

    @InternalRoborazziApi
    class VerifyCaptureResultReporter : CaptureResultReporter {
      private val jsonOutputCaptureResultReporter = JsonOutputCaptureResultReporter()
      override fun report(captureResult: CaptureResult, roborazziTaskType: RoborazziTaskType) {
        jsonOutputCaptureResultReporter.report(captureResult, roborazziTaskType)
        val assertErrorOrNull = getAssertErrorOrNull(captureResult)
        if (assertErrorOrNull != null) {
          throw assertErrorOrNull
        }
      }
    }
  }

  data class RecordOptions(
    val resizeScale: Double = roborazziDefaultResizeScale(),
    val applyDeviceCrop: Boolean = false,
    val pixelBitConfig: PixelBitConfig = PixelBitConfig.Argb8888,
  )

  enum class PixelBitConfig {
    Argb8888,
    Rgb565;

    fun toBufferedImageType(): Int {
      return when (this) {
        Argb8888 -> 2 // BufferedImage.TYPE_INT_ARGB
        Rgb565 -> 8 // BufferedImage.TYPE_USHORT_565_RGB
      }
    }
  }

  internal val shouldTakeBitmap: Boolean = captureType.shouldTakeScreenshot()

  @ExperimentalRoborazziApi
  fun addedAiAssertion(
    assert: String,
    requiredFulfillmentPercent: Int
  ): RoborazziOptions {
    return addedAiAssertions(
      AiAssertionOptions.AiAssertion(
        assertPrompt = assert,
        requiredFulfillmentPercent = requiredFulfillmentPercent
      )
    )
  }

  @ExperimentalRoborazziApi
  fun addedAiAssertions(vararg assertions: AiAssertionOptions.AiAssertion): RoborazziOptions {
    return copy(
      compareOptions = compareOptions.copy(
        aiAssertionOptions = compareOptions.aiAssertionOptions!!.copy(
          aiAssertions = compareOptions.aiAssertionOptions.aiAssertions + assertions
        )
      )
    )
  }
}

expect fun canScreenshot(): Boolean

expect fun defaultCaptureType(): RoborazziOptions.CaptureType

private fun getAssertErrorOrNull(captureResult: CaptureResult): AssertionError? =
  when (captureResult) {
    is CaptureResult.Added -> AssertionError(
      "Roborazzi: The original file(${captureResult.goldenFile}) was not found.\n" +
        "See the actual image at ${captureResult.actualFile}"
    )

    is CaptureResult.Changed -> AssertionError(
      "Roborazzi: ${captureResult.goldenFile} is changed.\n" +
        "See the compare image at ${captureResult.compareFile}"
    )

    is CaptureResult.Unchanged, is CaptureResult.Recorded -> {
      null
    }
  }