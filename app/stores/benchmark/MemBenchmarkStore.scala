package stores.benchmark

import models.benchmark.{Benchmark, BenchmarkAnswer, BenchmarkQuestion, BenchmarkDataset, BenchmarkSubmission}

class MemBenchmarkStore extends BenchmarkStore {
  private val benchmarks: List[Benchmark] = BenchmarkTestData.benchmarks
  private val benchmarkAnswers: List[BenchmarkAnswer] = BenchmarkTestData.benchmarkAnswers
  private val benchmarkQuestions: List[BenchmarkQuestion] = BenchmarkTestData.benchmarkQuestions
  private val benchmarkSubmissions: List[BenchmarkSubmission] = BenchmarkTestData.benchmarkSubmissions

  final override def getBenchmarks: List[Benchmark] = benchmarks

  override def getBenchmarkAnswerByQuestion(benchmarkQuestionId: String, benchmarkSubmissionId: String): Option[BenchmarkAnswer] =
    benchmarkAnswers.find(answer => answer.questionId == benchmarkQuestionId && answer.submissionId == benchmarkSubmissionId)

  final override def getBenchmarkAnswersBySubmission(benchmarkSubmissionId: String, limit: Int, offset: Int): List[BenchmarkAnswer] =
    benchmarkAnswers
      .filter(answer => answer.submissionId == benchmarkSubmissionId)
      .drop(offset).take(limit)

  final override def getBenchmarkById(benchmarkId: String): Option[Benchmark] =
    benchmarks.find(benchmark => benchmark.id == benchmarkId)

  final override def getBenchmarkQuestionsByDataset(benchmarkDatasetId: String, limit: Int, offset: Int): List[BenchmarkQuestion] =
    benchmarkQuestions
      .filter(question => question.datasetId == benchmarkDatasetId)
      .drop(offset).take(limit)

  override def getBenchmarkQuestionById(benchmarkQuestionId: String): Option[BenchmarkQuestion] =
    benchmarkQuestions
      .find(question => question.id == benchmarkQuestionId)

  override def getBenchmarkSubmissionById(submissionId: String): Option[BenchmarkSubmission] =
    benchmarkSubmissions.find(submission => submission.id == submissionId)

  override def getBenchmarkSubmissionsByBenchmark(benchmarkId: String): List[BenchmarkSubmission] =
    benchmarkSubmissions.filter(submission => submission.benchmarkId == benchmarkId)

  final override def getBenchmarkSubmissionsByDataset(benchmarkDatasetId: String): List[BenchmarkSubmission] =
    benchmarkSubmissions.filter(submission => submission.datasetId == benchmarkDatasetId)
}
