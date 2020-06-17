package models.benchmark

final case class BenchmarkQuestion(
                                    choices: List[BenchmarkQuestionChoice],
                                    concept: Option[String],
                                    id: String,
                                    datasetId: String,
                                    text: String
                                  )
