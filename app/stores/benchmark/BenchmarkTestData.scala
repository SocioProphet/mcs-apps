package stores.benchmark

import java.io.{BufferedInputStream, InputStream}

import formats.benchmark.{BenchmarkQuestionSetsJsonlReader, BenchmarkQuestionsJsonlReader, BenchmarkSubmissionsJsonlReader, BenchmarksJsonlReader}
import formats.kg.cskg.{CskgEdgesCsvReader, CskgNodesCsvReader}
import formats.kg.path.KgPathsJsonlReader
import models.benchmark.{Benchmark, BenchmarkQuestion, BenchmarkQuestionSet, BenchmarkSubmission}
import models.kg.{KgEdge, KgNode, KgPath}
import stores.WithResource

import scala.io.Source

object BenchmarkTestData extends WithResource {
  val BenchmarksJsonlResourceName = "/test_data/benchmark/benchmarks.jsonl"
  val BenchmarkQuestionsJsonlResourceName = "/test_data/benchmark/benchmark_questions.jsonl"
  val BenchmarkQuestionSetsJsonlResourceName = "/test_data/benchmark/benchmark_question_sets.jsonl"
  val BenchmarkSubmissionsJsonlResourceName = "/test_data/benchmark/benchmark_submissions.jsonl"

  val benchmarks = readBenchmarks()
  val benchmarkQuestions = readBenchmarkQuestions()
  val benchmarkQuestionSets = readBenchmarkQuestionSets()
  val benchmarkSubmissions = readBenchmarkSubmissions()
  validate()

  def getBenchmarksJsonlResourceAsStream(): InputStream =
    getResourceAsStream(BenchmarksJsonlResourceName)

  def getBenchmarkQuestionsJsonlResourceAsStream(): InputStream =
    getResourceAsStream(BenchmarkQuestionsJsonlResourceName)

  def getBenchmarkQuestionSetsJsonlResourceAsStream(): InputStream =
    getResourceAsStream(BenchmarkQuestionSetsJsonlResourceName)

  def getBenchmarkSubmissionsJsonlResourceAsStream(): InputStream =
    getResourceAsStream(BenchmarkSubmissionsJsonlResourceName)

  private def getResourceAsStream(resourceName: String) =
    new BufferedInputStream(getClass.getResourceAsStream(resourceName))

  private def readBenchmarks(): List[Benchmark] = {
    withResource(new BenchmarksJsonlReader(Source.fromInputStream(getBenchmarksJsonlResourceAsStream()))) { reader =>
      reader.toStream.toList
    }
  }

  private def readBenchmarkQuestions(): List[BenchmarkQuestion] = {
    withResource(new BenchmarkQuestionsJsonlReader(Source.fromInputStream(getBenchmarkQuestionsJsonlResourceAsStream()))) { reader =>
      reader.toStream.toList
    }
  }

  private def readBenchmarkQuestionSets(): List[BenchmarkQuestionSet] = {
    withResource(new BenchmarkQuestionSetsJsonlReader(Source.fromInputStream(getBenchmarkQuestionSetsJsonlResourceAsStream()))) { reader =>
      reader.toStream.toList
    }
  }

  private def readBenchmarkSubmissions(): List[BenchmarkSubmission] = {
    withResource(new BenchmarkSubmissionsJsonlReader(Source.fromInputStream(getBenchmarkSubmissionsJsonlResourceAsStream()))) { reader =>
      reader.toStream.toList
    }
  }


  private def validate(): Unit = {
    for (question <- benchmarkQuestions) {
      if (!benchmarkQuestionSets.exists(questionSet => question.questionSetId == questionSet.id)) {
        throw new IllegalArgumentException(s"benchmark question ${question.id} refers to missing benchmark question set ${question.questionSetId}")
      }
      if (!benchmarks.exists(benchmark => question.benchmarkId == benchmark.id)) {
        throw new IllegalArgumentException(s"benchmark question ${question.id} refers to missing benchmark ${question.benchmarkId}")
      }
    }
    for (submission <- benchmarkSubmissions) {
      if (!benchmarkQuestionSets.exists(questionSet => submission.questionSetId == questionSet.id)) {
        throw new IllegalArgumentException(s"benchmark question ${submission.id} refers to missing benchmark question set ${submission.questionSetId}")
      }
      if (!benchmarks.exists(benchmark => submission.benchmarkId == benchmark.id)) {
        throw new IllegalArgumentException(s"submission ${submission.id} refers to missing benchmark ${submission.benchmarkId}")
      }
      for (answer <- submission.answers) {
        val question = benchmarkQuestions.find(question => question.id == answer.questionId && question.benchmarkId == submission.benchmarkId && question.questionSetId == submission.questionSetId)
        if (!question.isDefined) {
          throw new IllegalArgumentException(s"submission ${submission.id} refers to missing question ${answer.questionId}")
        }
        if (!question.get.choices.exists(choice => choice.label == answer.choiceLabel)) {
          throw new IllegalArgumentException(s"submission ${submission.id} refers to missing choice ${answer.choiceLabel}")
        }
      }
    }
  }
}
