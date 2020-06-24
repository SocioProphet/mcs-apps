package formats.benchmark

import java.io.InputStream

import formats.JsonlReader
import formats.kg.path
import formats.kg.path.KgPathsJsonlReader
import io.circe.generic.semiauto.deriveDecoder
import io.circe.{Decoder, Json}
import models.benchmark.{BenchmarkAnswer, BenchmarkAnswerExplanation, BenchmarkQuestion, BenchmarkQuestionAnswerPath, BenchmarkQuestionAnswerPaths, BenchmarkQuestionChoice, BenchmarkQuestionChoiceAnalysis}

import scala.io.Source

final class BenchmarkAnswersJsonlReader(source: Source) extends JsonlReader[BenchmarkAnswer](source) {
  private implicit val benchmarkQuestionAnswerPathDecoder: Decoder[BenchmarkQuestionAnswerPath] = deriveDecoder
  private implicit val benchmarkQuestionAnswerPathsDecoder: Decoder[BenchmarkQuestionAnswerPaths] = deriveDecoder
  private implicit val benchmarkQuestionChoiceAnalysisDecoder: Decoder[BenchmarkQuestionChoiceAnalysis] = deriveDecoder
  private implicit val benchmarkAnswerExplanationDecoder: Decoder[BenchmarkAnswerExplanation] = deriveDecoder
  protected val decoder: Decoder[BenchmarkAnswer] = deriveDecoder
}

object BenchmarkAnswersJsonlReader {
  def open(inputStream: InputStream): BenchmarkAnswersJsonlReader =
    new BenchmarkAnswersJsonlReader(JsonlReader.openSource(inputStream))
}
