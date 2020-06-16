package formats.benchmark

import org.scalatest.{Matchers, WordSpec}
import stores.WithResource
import stores.benchmark.BenchmarkTestData

import scala.io.Source

class BenchmarkSubmissionsJsonlReaderSpec extends WordSpec with Matchers with WithResource {
  "Benchmark submissions .jsonl reader" can {
    "read the test data" in {
      withResource(new BenchmarkSubmissionsJsonlReader(Source.fromInputStream(BenchmarkTestData.getBenchmarkSubmissionsJsonlResourceAsStream(), "UTF-8"))) { reader =>
        val submissions = reader.toStream.toList
        for (submission <- submissions) {
          submission.benchmarkId should not be empty
          submission.questionSetId should not be empty
          submission.id should not be empty
        }
      }
    }
  }
}
